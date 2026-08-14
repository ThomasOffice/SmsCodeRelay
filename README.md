# 验证码蓝牙中继工具

自动读取 Android 手机短信验证码，通过蓝牙实时推送到 PC 并写入剪贴板，`Ctrl+V` 即可粘贴。同时支持 **IMAP 轮询邮箱**，自动提取邮箱验证码并复用同一处理管道（剪贴板 + 通知 + 记录）。

![License](https://img.shields.io/badge/license-MIT-blue) ![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Windows-green) ![Language](https://img.shields.io/badge/language-Kotlin%20%7C%20Python-orange)

## 下载

从 [Releases](../../releases) 页面下载：
- **`SmsRelay.exe`** — PC 端程序（免安装，双击运行，16MB，无需装 Python）
- **`app-release.apk`** — Android App（安装到手机）

## 架构

```
Android 手机                            PC (Windows)
┌─────────────────────┐  蓝牙 RFCOMM  ┌──────────────────────┐
│ 辅助 App (Kotlin)    │ ────────────> │ Python 服务           │
│ · 轮询短信数据库      │  JSON 行      │ · Winsock2 直连监听    │
│ · 正则提取验证码      │              │ · token 校验去重       │
│ · 反射直连蓝牙端口    │              │ · 写剪贴板+通知        │
│ · 前台服务保活        │              │ · 系统托盘+轻量UI      │
│ · 验证码保护检测      │              │ · 一键生成Token        │
└─────────────────────┘              │ · IMAP 轮询邮箱验证码  │
                                     └──────────────────────┘
```

PC 端使用 **Winsock2 蓝牙 API 直接监听 RFCOMM 端口**，绕过 Windows COM 端口中间层，其他软件扫描/占用 COM 端口完全不影响连接。

## 功能特性

- **自动提取验证码** — 中英文验证码正则识别，规则可配置
- **蓝牙实时传输** — Winsock2 直连 RFCOMM 端口，绕过 COM 端口，不受其他软件干扰
- **邮箱验证码接收** — IMAP 轮询收件箱，自动提取邮件中的验证码，与短信共用同一处理管道（内置 QQ/163/Gmail 预设）
- **自动写入剪贴板** — 收到验证码后 1 秒内写入 PC 剪贴板，Ctrl+V 粘贴
- **Windows 通知** — 弹出通知显示验证码和发送方
- **系统托盘常驻** — 无终端窗口，双击托盘图标弹出轻量状态窗口
- **一键生成 Token** — UI 内生成随机 Token，自动复制到剪贴板
- **验证码安全保护检测** — 检测华为/荣耀等国产 ROM 的验证码保护功能并提醒
- **深色模式适配** — 日志区配色随系统主题切换
- **华为/荣耀后台保活** — 轮询短信数据库，绕过后台广播冻结

## 前置条件

- Windows 10/11 PC，带蓝牙
- Android 7.0+ 手机
- 手机与 PC 已蓝牙配对

## 快速开始

### 1. PC 端

**方式 A：下载 exe 直接运行（推荐）**

从 [Releases](../../releases) 下载 `SmsRelay.exe`，双击运行即可。配置自动存到 `%APPDATA%\SmsRelay\config.yaml`，无需手动编辑。

**方式 B：从源码运行/打包**

```powershell
cd desktop
pip install -r requirements.txt
python server.py          # 直接运行
# 或打包成 exe：
pwsh -ExecutionPolicy Bypass -File build_exe.ps1
```

启动后无终端窗口，自动隐藏到系统托盘。双击托盘图标弹出轻量状态窗口（显示连接状态、Token、最近验证码记录），关闭窗口则回到托盘后台运行。右键托盘图标可退出。

### 2. 安装 Android App

从 [Releases](../../releases) 下载 `app-release.apk` 传到手机安装。

或自行构建：
```powershell
cd android
powershell -ExecutionPolicy Bypass -File build_apk.ps1 -Release
adb install "app\build\outputs\apk\release\app-release.apk"
```

### 3. 配对与配置

1. **蓝牙配对**：手机与 PC 蓝牙配对（Windows 设置 → 蓝牙和其他设备 → 添加设备）
2. **启动 PC 端**：双击 `SmsRelay.exe`，状态显示"监听中，等待 Android 连接"
3. **配置 App**：
   - 打开手机上的"验证码中继"App
   - 授予短信和蓝牙权限
   - 选择已配对的 PC 蓝牙设备
   - 输入与 PC 端相同的 token（PC 端主窗口可见，点击可复制）
   - 点击"保存配置" → "启动监听"

### 4. 使用

手机收到含验证码的短信后，1 秒内 PC 剪贴板自动更新为验证码，同时弹出 Windows 通知。`Ctrl+V` 粘贴即可。

### 5. 接收邮箱验证码（可选）

1. 在 PC 端主窗口点击「邮箱设置」
2. 勾选「启用邮箱接收」，选择预设（QQ 邮箱/网易/Gmail 等，自动填充 IMAP 服务器），或手动填写
3. 填写邮箱账号与**授权码**：
   - **QQ 邮箱**：网页端 → 设置 → 账号 → 开启 IMAP/SMTP 服务 → 生成授权码（非登录密码）
   - **网易 163/126**：设置 → POP3/SMTP/IMAP → 开启并获取授权码
   - **Gmail**：开启两步验证后在「应用专用密码」中生成
4. 点击「测试连接」确认可登录，再点「保存」
5. 邮箱收到含验证码的邮件后，验证码会自动复制到剪贴板并弹出通知

> **严格匹配**（默认开启）：只处理主题/正文含「验证码、verification code、OTP」等关键词的邮件，避免营销邮件中的随机数字被误当作验证码复制到剪贴板。可在「邮箱设置」中关闭。

## 验证码识别规则

默认规则（可在 `config.yaml` 热更新，无需改代码）：

1. 中文：`验证码|动态码|校验码|认证码` 附近 4-8 位数字
2. 英文：`verification code|code|OTP|passcode` 附近 4-8 位数字
3. 兜底：任意 4-8 位数字

## 目录结构

```
CAPTCHA/
├── README.md                      本文档
├── protocol.md                    通信协议
├── LICENSE                        MIT
├── desktop/                       PC 端 Python 服务
│   ├── server.py                  主程序（UI+托盘+剪贴板+通知）
│   ├── bt_rfcomm.py               Winsock2 蓝牙 RFCOMM 服务端
│   ├── code_parser.py             验证码正则提取
│   ├── email_poller.py            IMAP 邮箱验证码轮询
│   ├── config.yaml                配置（内嵌进 exe）
│   ├── config.yaml.example        配置模板
│   ├── app_icon.ico / .png        应用图标
│   ├── build_exe.ps1              exe 打包脚本
│   └── requirements.txt           Python 依赖
└── android/                       Android App 工程
    ├── build_apk.ps1              APK 打包脚本
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle/ wrapper
    ├── gradlew.bat
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── res/                        布局/图标/主题/夜间配色
            └── kotlin/com/relay/sms/
                ├── MainActivity.kt          配置界面+日志+测试按钮
                ├── SmsPoller.kt             短信数据库轮询
                ├── SmsReceiver.kt           短信广播接收（备用）
                ├── CodeExtractor.kt         验证码正则提取
                ├── CodeProtector.kt         验证码安全保护检测
                ├── BtClient.kt              蓝牙反射直连客户端
                ├── KeepAliveService.kt      前台服务保活
                ├── SmsProcessor.kt          短信处理逻辑
                └── NotifHelper.kt           通知辅助
```

## 常见问题

### Q: 手机提示"转发失败: IOException"
A: 蓝牙连接失败，排查：
1. 确认手机与 PC 已蓝牙**配对**（不只是开启蓝牙）
2. 确认 PC 端 `SmsRelay.exe` 正在运行且状态显示"监听中"
3. 尝试在 App 内点击"测试蓝牙转发"验证连接
4. 若 PC 端曾运行过旧版（COM 端口模式），重启 PC 清除残留的 COM 端口状态
5. 确认没有其他蓝牙 SPP 程序占用端口

### Q: 手机收到短信但 App 无反应
A: 华为/荣耀等国产 ROM 会冻结后台广播，本工具已改用**轮询短信数据库**绕过此限制：
1. 确认 App 已"启动监听"（通知栏有"验证码中继运行中"）
2. 在系统设置中关闭该 App 的电池优化
3. 检查 App 日志区是否显示"轮询启动"
4. 若日志显示"验证码安全保护提醒"，按提示关闭该系统功能

### Q: 验证码被系统屏蔽为星号(*)
A: 华为/荣耀的「验证码安全保护」功能会把短信中的验证码数字替换为 `*`。关闭路径：设置 → 安全 → 更多安全设置 → 验证码安全保护。

### Q: 后台被系统杀死
A: 在手机系统设置中：关闭该 App 的电池优化 + 允许自启动 + 允许后台活动。前台服务通知需保持开启。

### Q: 验证码识别不准
A: 编辑 `%APPDATA%\SmsRelay\config.yaml` 的 `code_rules`，添加自定义正则规则。

### Q: 如何更换 Token
A: PC 端主窗口点击"生成新 Token"按钮，自动生成随机串并复制到剪贴板。然后在手机 App 中更新相同 Token。

### Q: 邮箱验证码收不到
A: 排查：
1. 确认已在网页端开启 IMAP 并使用**授权码**（非登录密码）
2. 点击「测试连接」确认能登录；若失败请检查服务器/端口（QQ 为 `imap.qq.com:993`）
3. 确认验证码邮件在收件箱（而非垃圾箱/文件夹），`since_days` 只处理最近 N 天的邮件
4. 查看状态栏「邮箱:」是否显示异常；日志位于 `%APPDATA%\SmsRelay\logs\server.log`

### Q: 邮箱授权码安全吗
A: 授权码以明文存于 `%APPDATA%\SmsRelay\config.yaml`（本机文件）。建议使用独立授权码，QQ 授权码仅限 IMAP/SMTP 用途，可随时在网页端撤销重置。

## 安全说明

- Token 为预共享密钥，防止蓝牙链路上的伪造消息
- 蓝牙配对本身提供链路层加密
- PC 端不暴露任何网络端口，仅监听蓝牙 RFCOMM
- 配置文件存储在 `%APPDATA%\SmsRelay\`，不暴露在 exe 同目录
- keystore 密码为示例值，正式使用请自行生成

## 技术栈

| 模块 | 技术 |
|------|------|
| Android | Kotlin + Gradle 8.7 + AGP 8.5.2 + compileSdk 34 |
| 蓝牙通信 | Winsock2 RFCOMM 直连（绕过 COM 端口和 SDP） |
| PC 端 | Python 3.13 + ctypes + tkinter + pystray + pyperclip + win11toast |
| 通信协议 | JSON over RFCOMM，`\n` 分行 |
| Android 短信 | SmsPoller 轮询 `content://sms/inbox`（绕过广播冻结） |
| Android 蓝牙 | 反射调用 `createRfcommSocket(port)` 直连（绕过 SDP 查询） |
| 邮箱验证码 | Python `imaplib` IMAP4_SSL 轮询收件箱（零第三方依赖） |

## 从源码构建

### 打包 Android APK

需要 JDK 17 + Android SDK（commandline-tools）：

```powershell
cd android
# 生成签名 keystore（首次）
keytool -genkeypair -v -keystore smsrelay.keystore -alias smsrelay -keyalg RSA -keysize 2048 -validity 36500
# 创建 keystore.properties（参考 build.gradle.kts 中的签名配置）
# 构建
powershell -ExecutionPolicy Bypass -File build_apk.ps1 -Release
```

### 打包 PC 端 exe

需要 Python 3.10+：

```powershell
cd desktop
pip install -r requirements.txt
pwsh -ExecutionPolicy Bypass -File build_exe.ps1
```

## License

[MIT](LICENSE)
