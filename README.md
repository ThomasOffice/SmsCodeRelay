# 验证码蓝牙中继工具

自动读取 Android 手机短信验证码，通过蓝牙实时推送到 PC 并写入剪贴板，`Ctrl+V` 即可粘贴。

![License](https://img.shields.io/badge/license-MIT-blue) ![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Windows-green) ![Language](https://img.shields.io/badge/language-Kotlin%20%7C%20Python-orange)

## 下载

从 [Releases](../../releases) 页面下载：
- **`SmsRelay.exe`** — PC 端程序（免安装，双击运行，16MB）
- **`app-release.apk`** — Android App（安装到手机）

## 架构

```
Android 手机                          PC (Windows)
┌─────────────────┐   蓝牙 SPP    ┌──────────────────┐
│ 辅助 App (Kotlin) │ ──────────> │ Python 服务       │
│ · 监听短信       │   JSON 行     │ · 读 COM 端口     │
│ · 正则提取验证码  │              │ · token 校验去重   │
│ · 蓝牙发送       │              │ · 写剪贴板+通知    │
│ · 前台服务保活    │              │ · 系统托盘常驻     │
└─────────────────┘              └──────────────────┘
```

## 前置条件

- Windows 10/11 PC，带蓝牙
- Android 7.0+ 手机
- Python 3.10+（PC 端）
- Android 开发环境（仅打包 APK 时需要：JDK 17 + Android SDK）

## 快速开始

### 1. PC 端

**方式 A：下载 exe 直接运行（推荐）**

从 [Releases](../../releases) 下载 `SmsRelay.exe`，双击运行即可。配置自动存到 `%APPDATA%\SmsRelay\config.yaml`，无需手动编辑。

**方式 B：从源码运行/打包**

```powershell
cd desktop
pip install -r requirements.txt
# 从模板创建配置（首次）
Copy-Item config.yaml.example config.yaml
# 编辑 config.yaml 设置 token 等
python server.py          # 直接运行
# 或打包成 exe：
pwsh -ExecutionPolicy Bypass -File build_exe.ps1
```

生成的 `dist\SmsRelay.exe`（约 16MB），config.yaml 已内嵌，首次运行自动提取到 `%APPDATA%\SmsRelay\`。

启动后无终端窗口，自动隐藏到系统托盘。双击托盘图标弹出轻量状态窗口（显示连接状态、COM 端口、Token、最近验证码记录），关闭窗口则回到托盘后台运行。右键托盘图标可退出。

### 2. 打包并安装 Android App

```powershell
cd android
# 构建 Debug APK（可直接安装）
powershell -ExecutionPolicy Bypass -File build_apk.ps1

# 或构建签名 Release APK
powershell -ExecutionPolicy Bypass -File build_apk.ps1 -Release
```

安装到手机（需开启 USB 调试）：
```powershell
adb install "app\build\outputs\apk\debug\app-debug.apk"
```

### 3. 蓝牙配对与 COM 端口配置

1. **配对**：手机与 PC 蓝牙配对（Windows 设置 → 蓝牙和其他设备 → 添加设备）
2. **添加传入 COM 端口**：
   - Windows 设置 → 蓝牙和其他设备 → 更多蓝牙设置（或"设备和打印机"）
   - COM 端口选项卡 → 添加 → 传入 → 选择手机 → 确认
   - 记住分配的 COM 端口号（如 COM5）
3. **配置 PC 端**：将 `config.yaml` 的 `com_port` 设为该端口，或留空自动探测
4. **配置 App**：
   - 打开手机上的"验证码中继"App
   - 授予短信和蓝牙权限
   - 选择已配对的 PC 蓝牙设备
   - 输入与 PC 端相同的 token
   - 点击"保存配置" → "启动监听"

### 4. 使用

手机收到含验证码的短信后，1 秒内 PC 剪贴板自动更新为验证码，同时弹出 Windows 通知。`Ctrl+V` 粘贴即可。

## 验证码识别规则

默认规则（可在 `config.yaml` 热更新，无需改代码）：

1. 中文：`验证码|动态码|校验码|认证码` 附近 4-8 位数字
2. 英文：`verification code|code|OTP|passcode` 附近 4-8 位数字
3. 兜底：任意 4-8 位数字

## 目录结构

```
CAPTCHA/
├── README.md              本文档
├── protocol.md            通信协议
├── .gitignore
├── desktop/               PC 端 Python 服务
│   ├── server.py          主程序（COM 读取+剪贴板+通知+托盘）
│   ├── code_parser.py     验证码正则提取
│   ├── config.yaml        配置（端口/token/规则）
│   └── requirements.txt   Python 依赖
└── android/               Android App 工程
    ├── build_apk.ps1      一键打包脚本
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle/ wrapper
    ├── gradlew.bat
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── res/                布局/字符串/图标
            └── kotlin/com/relay/sms/
                ├── MainActivity.kt       配置界面
                ├── SmsReceiver.kt        短信广播接收
                ├── CodeExtractor.kt      验证码提取
                ├── BtClient.kt           蓝牙 SPP 客户端
                └── KeepAliveService.kt   前台服务保活
```

## 常见问题

### Q: PC 端提示"未自动探测到蓝牙 COM 端口"
A: 需在 Windows 蓝牙设置中手动添加"传入 COM 端口"（见上方步骤 3），然后将端口号填入 `config.yaml`。

### Q: 提示"打开 COM 端口失败: FileNotFoundError"
A: 探测到了蓝牙端口但它不可用。常见原因：
- **选到了传出端口/幽灵端口**：server 会自动区分传入/传出端口并实测打开，若仍失败，说明没有可用的传入端口。请在 Windows「蓝牙设置 → 更多蓝牙设置 → COM 端口」选项卡中添加一个**传入**端口（不是传出），再将该端口号手动填入 `config.yaml` 的 `com_port`。
- **端口被占用**：确认没有其他程序（如串口调试助手）占用该端口。
- 排查命令（列出所有 COM 端口及 hwid）：
  ```powershell
  python -c "import serial.tools.list_ports as lp; [print(p.device, '|', p.hwid) for p in lp.comports()]"
  ```
  hwid 含 `000000000000` 的才是传入端口；含 12 位真实设备地址的是传出端口（不可用于监听）。

### Q: App 提示"发送失败"
A: 检查：① 手机与 PC 已蓝牙配对 ② PC 端 server.py 正在运行且 COM 端口已打开 ③ App 中选择的设备正确 ④ token 一致。

### Q: 后台被系统杀死
A: 在手机系统设置中关闭该 App 的电池优化，前台服务通知需保持开启。

### Q: 验证码识别不准
A: 编辑 `config.yaml` 的 `code_rules`，添加自定义正则规则。

### Q: 换了手机或重装后 COM 端口变了
A: 重新配对并添加传入 COM 端口，更新 `config.yaml`。

## 安全说明

- token 为预共享密钥，防止局域网/蓝牙伪造
- 蓝牙配对本身提供链路层加密
- PC 端仅监听蓝牙 COM 端口，不暴露网络端口
- keystore 密码为示例值，正式使用请自行生成（`build_apk.ps1` 不含签名密钥管理，需手动 `keytool`）

## 技术栈

| 模块 | 技术 |
|------|------|
| Android | Kotlin + Gradle 8.7 + AGP 8.5.2 + compileSdk 34 |
| 蓝牙 | RFCOMM SPP，UUID `00001101-...` |
| PC 端 | Python 3.13 + pyserial + pyperclip + win11toast + pystray |
| 通信 | JSON over SPP，`\n` 分行 |

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
