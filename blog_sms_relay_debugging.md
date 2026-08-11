# 蓝牙验证码中继工具：从踩坑到上线的完整排错纪实

> 一个"手机收验证码自动转发到电脑剪贴板"的小工具，却在开发过程中踩遍了 Android 后台限制、Windows 蓝牙驱动、COM 端口冲突、Winsock2 结构体对齐等一系列深坑。本文记录了完整的排错过程和经验总结。

## 项目背景

日常开发中，手机收到验证码后需要手动看一眼再敲到电脑上，繁琐且容易输错。于是想做一个工具：**手机收到验证码短信后，通过蓝牙自动转发到 PC，直接写入剪贴板，Ctrl+V 即可粘贴。**

技术选型：
- **Android App (Kotlin)**：监听短信 → 正则提取验证码 → 蓝牙发送
- **PC 端 (Python)**：蓝牙接收 → token 校验 → 写剪贴板 + 通知 + 系统托盘
- **通信**：蓝牙 RFCOMM SPP，JSON 格式，token 防伪

看起来很简单，实际上……

---

## 第一坑：Android 短信广播被权限阻挡

### 现象

手机收到短信后，App 完全无反应，日志区没有任何更新。

### 排查

最初在 `AndroidManifest.xml` 中注册了 `SMS_RECEIVED` 广播：

```xml
<receiver android:name=".SmsReceiver"
    android:exported="true"
    android:permission="android.permission.BROADCAST_SMS">
```

看起来没问题，但仔细看 Android 文档后发现：`android:permission="android.permission.BROADCAST_SMS"` 这个属性**要求广播发送者必须持有系统级 BROADCAST_SMS 权限**。系统的 SMS_RECEIVED 广播由无线电进程发送，它确实有这个权限——但在某些 ROM 上，这个属性会导致广播被拦截。

### 修复

去掉 `android:permission` 属性：

```xml
<receiver android:name=".SmsReceiver" android:exported="true">
```

### 经验

> Manifest 中 receiver 的 `android:permission` 属性是**限制谁能发广播给你**，不是声明你自己需要什么权限。对于系统广播，一般不需要加这个属性。

---

## 第二坑：华为/荣耀后台冻结短信广播

### 现象

修复权限后，App 在华为/荣耀手机上仍然收不到短信广播。logcat 确认 receiver 已注册，但 `onReceive()` 从未被调用。

### 排查

这是华为/荣耀的**后台管控机制**：当 App 不在前台时，系统会冻结后台进程，静态注册的 `SMS_RECEIVED` 广播被拦截。即使改为动态注册（前台服务内），部分华为机型仍然会冻结。

尝试了 ContentObserver 监听短信数据库变化——`onChange()` 回调触发了，但查询数据库时拿到的还是旧短信（新短信还没落盘），且不会二次触发。

### 修复

**放弃广播和 ContentObserver，改用定时轮询短信数据库**：

```kotlin
class SmsPoller(private val context: Context) {
    private var maxSeenId: Long = 0

    fun start() {
        maxSeenId = getLatestSmsId()  // 记录启动时的最新 ID
        Thread {
            while (running) {
                pollOnce()  // 查询 id > maxSeenId 的新短信
                Thread.sleep(2000)
            }
        }.start()
    }

    private fun pollOnce() {
        // 查询 content://sms/inbox，按 _id DESC 排序
        // 比较 _id 发现新短信就处理
    }
}
```

每 2 秒主动查一次 `content://sms/inbox`，用 `_id` 增量检测新短信。不依赖任何系统回调，华为无法限制 App 读取有权限的数据库。

### 经验

> 国产 ROM（华为/荣耀/小米/OPPO/vivo）的后台限制极其严格。**不要依赖系统广播**，改用轮询等主动机制更可靠。ContentObserver 的回调时机也不可靠（可能在数据写入前触发），轮询 + ID 增量是最稳的方案。

---

## 第三坑：UI 线程崩溃（CalledFromWrongThreadException）

### 现象

轮询方案上线后，"测试蓝牙转发"按钮能成功发送数据到 PC，但 App 日志区不更新，收短信后也"无反应"。

### 排查

logcat 终于抓到了关键错误：

```
E AndroidRuntime: android.view.ViewRootImpl$CalledFromWrongThreadException:
    Only the original thread that created a view hierarchy can touch its views.
    at MainActivity.refreshLog(MainActivity.kt:99)
    at MainActivity$Companion.log(MainActivity.kt:47)
    at MainActivity.testForward$lambda$12(MainActivity.kt:203)
```

**根因**：`MainActivity.log()` 在后台线程（测试线程、SmsProcessor 的蓝牙线程、SmsPoller 的查询线程）被调用时，直接更新 `TextView.setText()`。Android 禁止非 UI 线程更新视图 → 抛异常 → 后台线程崩溃 → 日志不显示。

**这意味着 ContentObserver 一直在工作**，只是每次回调想写日志时就崩溃了，所以用户"看不到任何响应"。

### 修复

所有 UI 更新通过 `Handler.post()` 切到主线程：

```kotlin
companion object {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun log(msg: String) {
        synchronized(logLines) { logLines.add("[$ts] $msg") }
        mainHandler.post { current?.refreshLog() }  // 切到主线程
    }
}
```

### 经验

> 这个坑隐藏极深——后台线程崩溃不会导致 App 闪退（不像主线程崩溃），只是静默失败。**任何被后台线程调用的方法，如果涉及 UI 更新，必须用 Handler.post() 切线程。** 调试时如果"看起来什么都没发生"，第一时间查 logcat 有没有 CalledFromWrongThreadException。

---

## 第四坑：BLUETOOTH_SCAN 权限缺失

### 现象

UI 线程修复后，测试蓝牙转发报错：

```
E BtClient: 发送失败: Need android.permission.BLUETOOTH_SCAN permission
    for cancelDiscovery()
```

### 排查

Android 12 (API 31) 起，`BluetoothAdapter.cancelDiscovery()` 需要 `BLUETOOTH_SCAN` 权限，而 Manifest 中只声明了 `BLUETOOTH_CONNECT`。

### 修复

Manifest 加权限 + 运行时请求 + cancelDiscovery 失败不阻断：

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
```

```kotlin
try { adapter.cancelDiscovery() } catch (e: SecurityException) {
    Log.w(TAG, "cancelDiscovery 被权限拒绝，继续: ${e.message}")
}
```

### 经验

> Android 12 的蓝牙权限拆分（CONNECT/SCAN）很容易遗漏。**`cancelDiscovery()` 虽然不是必须的（连接已配对设备时），但调用了就需要权限。** 加 try-catch 降级处理，不因非关键调用失败而阻断整个流程。

---

## 第五坑：COM 端口幽灵化（其他软件扫描导致）

### 现象

蓝牙转发功能正常工作了一段时间后，突然全部失败。手机报错 `read failed, socket might closed or timeout`。PC 端日志显示 COM 端口 `FileNotFoundError`。**只有重启电脑才能恢复。**

### 排查

用 Win32 API 直接检测：

```python
handle = kernel32.CreateFileW(r'\\.\COM6', ...)
err = kernel32.GetLastError()  # 返回 2 = FILE_NOT_FOUND
```

COM6 变成了**幽灵端口**——在注册表中注册了，但驱动层不存在。进一步排查发现：**另一个串口调试软件扫描了 COM 端口**，导致蓝牙 SPP 传入端口损坏。

尝试了重启蓝牙服务 (`bthserv`)、禁用/启用蓝牙适配器，都无法恢复——COM 端口映射层已永久损坏。

我之前加的"静默 3 秒后关闭重开 COM 端口"逻辑反而**制造了漏洞窗口**——关闭的瞬间其他软件就能抢走端口。

### 第一次修复：持久独占

改为持久独占 COM 端口，永不主动关闭。pyserial 在 Windows 上以 `dwShareMode=0`（独占）打开，一旦持有句柄，其他软件无法打开。

但问题在于：**一旦端口已经幽灵化，独占也打不开。** 强制重连只是重复 `CreateFile`，无法重置驱动层状态。

### 经验

> Windows 蓝牙 SPP 的 COM 端口是**脆弱的中间层**——任何软件扫描/打开 COM 端口都可能破坏它，且破坏后不可恢复（只能重启）。**依赖 COM 端口的方案从根本上不可靠。**

---

## 第六坑：Winsock2 直连绕过 COM 端口

### 决策

既然 COM 端口不可靠，那就**绕过它**——直接用 Winsock2 蓝牙 API 监听 RFCOMM 连接。

Windows 提供了 `AF_BTH` (address family 32) 蓝牙 socket，可以直接 `bind + listen + accept`，不需要 COM 端口。

### 实现

用 Python ctypes 调用 Winsock2：

```python
ws2_32 = ctypes.WinDLL("ws2_32")

# 创建蓝牙 RFCOMM socket
sock = ws2_32.socket(AF_BTH, SOCK_STREAM, BTPROTO_RFCOMM)

# bind 到固定端口
addr = SOCKADDR_BTH()
addr.addressFamily = AF_BTH
addr.port = 5  # 固定 RFCOMM 端口
ws2_32.bind(sock, ctypes.byref(addr), sizeof(addr))

# listen
ws2_32.listen(sock, 1)

# 非阻塞 accept 循环
ws2_32.ioctlsocket(sock, FIONBIO, ...)
```

### 经验

> Winsock2 蓝牙 API 是 Windows 上最底层的蓝牙通信方式，完全绕过 COM 端口和 SPP 中间层。**当中间层不可靠时，向下一层是正确的方向。**

---

## 第七坑：SDP 注册失败（10022）

### 现象

Winsock2 `bind + listen` 成功了，但 Android 的 `createRfcommSocketToServiceRecord(UUID)` 连接时报 `IOException`。

### 排查

Android 的 `createRfcommSocketToServiceRecord` 会先通过 **SDP（Service Discovery Protocol）查询** PC 是否有该 UUID 的服务。Winsock2 的 `bind + listen` 不会自动注册 SDP 记录——之前用 COM 端口时驱动会自动注册，现在绕过了 COM 端口就没有了。

需要手动调用 `WSASetService` 注册 SDP 记录。但这引发了一系列 ctypes 结构体对齐问题：

#### 7a. ctypes 结构体 `_pack_` 陷阱

`SOCKADDR_BTH` 在 Windows SDK 中是紧凑布局（30 字节），用于 `bind`。但 `WSASetService` 的 `CSADDR_INFO` 和 `WSAQUERYSETW` 是自然对齐（64 位下有 padding）。

如果全部用 `_pack_=1`，`bind` 能工作但 SDP 注册的指针偏移全错 → 访问违规。如果全部用自然对齐，`bind` 失败（10049）。

**解决方案**：SOCKADDR_BTH 保持 `_pack_=1`，其他结构体用自然对齐。但 ctypes 的 `POINTER` 与 `_pack_=1` 结构体在 64 位下有兼容性 bug，仍然访问违规。

#### 7b. 64 位 argtypes 必须

ctypes 默认把参数当 32 位 int 传递，但 64 位下指针是 8 字节。不设置 `argtypes` 会导致访问违规：

```python
# 必须设置 argtypes
ws2_32.bind.argtypes = [ctypes.c_uint64, ctypes.c_void_p, ctypes.c_int]
ws2_32.bind.restype = ctypes.c_int
```

#### 7c. 最终仍然 10022

修复了所有对齐和 argtypes 问题后，`WSASetServiceW` 不再崩溃，但返回 10022 (WSAEINVAL)。尝试了：
- 自然对齐 vs packed SOCKADDR_BTH
- SPP UUID vs 自定义 UUID
- 先注销再注册
- ANSI vs Unicode 版本

全部 10022。可能是 Windows 蓝牙驱动版本层面的限制，SPP UUID 被 COM 端口服务保留。

### 经验

> ctypes 操作复杂 Win32 结构体在 64 位下极易出错。**如果 API 持续返回错误码且结构体定义复杂，考虑放弃这个 API，换一个更简单的方案。** 不要在一个坑里无限投入。

---

## 第八坑：反射直连 RFCOMM 端口绕过 SDP

### 决策

既然 SDP 注册走不通，那就**绕过 SDP**——PC 端只 `bind + listen` 固定端口，Android 端用反射直接连接该端口。

### 原理

SDP 只是服务发现机制（UUID → 端口号映射），实际连接是到 RFCOMM 端口。Android 隐藏 API `createRfcommSocket(int port)` 可以直接指定端口号连接，跳过 SDP 查询。

### 实现

Android 端用反射调用：

```kotlin
val method = device.javaClass.getMethod(
    "createRfcommSocket", Int::class.javaPrimitiveType
)
socket = method.invoke(device, 5) as BluetoothSocket  // 端口 5
socket.connect()
```

PC 端只保留 `bind + listen`，移除所有 SDP 代码。

### 结果

**成功！** Android 反射直连端口 5 → PC `accept` 成功 → 数据传输 → 剪贴板更新。不再依赖 COM 端口，不再需要 SDP 注册，其他软件扫描 COM 端口完全无影响。

### 经验

> 当标准 API 走不通时，**反射调用隐藏 API 是 Android 蓝牙开发的常用"后门"**。`createRfcommSocket(port)` 是广泛使用的技巧，虽然不在公开 API 中，但在所有 Android 版本上都可用。**SDP 不是必须的——如果双方约定好端口号，可以直接连接。**

---

## 排错过程中的其他坑

### bat 文件 LF 行尾导致 cmd 解析错误

Windows `cmd.exe` 需要 CRLF 行尾。Python 写出的文件默认 LF，导致 `@echo off` 被解析成 `cho off`（`@` 吞掉了 `e`）。用 PowerShell 显式写入 CRLF 解决。

### PyInstaller 打包后 config.yaml 路径

PyInstaller `--onefile` 模式下，`__file__` 指向临时解压目录。需要用 `sys._MEIPASS` 获取内嵌资源路径，用 `%APPDATA%` 存用户配置。

### exe 图标在"详细信息"视图显示 Python 默认图标

PyInstaller `--icon` 已嵌入图标，但 Windows 图标缓存导致旧图标残留。清理 `%LOCALAPPDATA%\IconCache.db` 和 `explorer` 进程重启解决。

### 深色模式下日志区白字不可读

硬编码 `#F5F5F5` 背景在深色模式下与系统白色文字冲突。改用 `values/colors.xml` + `values-night/colors.xml` 资源引用，随系统主题切换。

---

## 经验总结

### 1. 国产 ROM 不信任系统广播

华为/荣耀/小米等 ROM 的后台限制会冻结广播。**轮询 > ContentObserver > 广播**，可靠性依次降低。短信用 `content://sms/inbox` 轮询 + `_id` 增量是最稳的方案。

### 2. Windows COM 端口是脆弱的中间层

任何软件扫描 COM 端口都可能破坏蓝牙 SPP 映射，且破坏后不可恢复。**能用 Winsock2 直连就不要走 COM 端口。** 向下一层通常更可靠。

### 3. SDP 不是必须的

Android 的 `createRfcommSocketToServiceRecord(UUID)` 需要 SDP 查询，但 `createRfcommSocket(port)` 反射调用可以直接指定端口连接。**双方约定端口号即可绕过 SDP。**

### 4. 后台线程崩溃是静默的

`CalledFromWrongThreadException` 在后台线程抛出不会导致 App 闪退，只会静默终止该线程。**如果"看起来什么都没发生"，第一时间查 logcat 的 `AndroidRuntime` tag。**

### 5. 64 位 ctypes 必须设 argtypes

不设 `argtypes` 会让 ctypes 把 64 位指针当 32 位 int 传，导致访问违规。**任何 ctypes 调用都要显式设置 `argtypes` 和 `restype`。**

### 6. 结构体对齐是 64 位的陷阱

Windows SDK 中 socket 地址结构体（如 `SOCKADDR_BTH`）是 packed 的，而信息结构体（如 `WSAQUERYSETW`）是自然对齐的。**不能全部用 `_pack_=1` 也不能全部用自然对齐，必须按 Windows SDK 定义区分。**

### 7. 一个坑不要无限投入

SDP 注册踩了无数对齐/指针/argtypes 的坑，最终仍然 10022。**当投入产出比不合理时，果断换方案。** 反射直连端口 5 行代码就解决了 SDP 几百行没解决的问题。

### 8. 渐进式排错法

每个问题先确认"哪一环失败了"，再针对性修复。链条越长（短信接收 → 验证码提取 → 蓝牙发送 → PC 接收 → 剪贴板），越需要分环节验证。**日志是最重要的排错工具——没有日志就加日志，加到每一环都有输出为止。**

---

## 最终架构

经过 8 轮排错，最终架构确定为：

```
Android 手机                              PC (Windows)
┌─────────────────────┐  蓝牙 RFCOMM  ┌──────────────────────┐
│ SmsPoller 轮询数据库  │ ────────────> │ Winsock2 直连监听     │
│ CodeExtractor 正则    │  JSON 行      │ 端口 5 (无 SDP)       │
│ BtClient 反射直连端口5│              │ CodeHandler 校验+剪贴板│
│ KeepAliveService 保活 │              │ TrayApp 托盘+UI       │
└─────────────────────┘              └──────────────────────┘
```

- **短信**：轮询 `content://sms/inbox`，`_id` 增量检测（绕过后台广播冻结）
- **蓝牙**：PC 端 Winsock2 `bind+listen` 端口 5，Android 反射 `createRfcommSocket(5)` 直连（绕过 COM 端口和 SDP）
- **UI**：Handler.post 切主线程更新（避免 CalledFromWrongThreadException）

开源地址：[GitHub: ThomasOffice/SmsCodeRelay](https://github.com/ThomasOffice/SmsCodeRelay)

---

*写于 2026 年 8 月，记录一个"简单"工具的不简单诞生过程。*
