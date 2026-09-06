# zero finish input

<p align="center">
  <img src="logo.jpg" width="360" alt="zero finish input 项目 logo" />
</p>

**离线中文与英文输入法，面向 Android 8.0 及以上设备。**

[下载 v0.1.0 测试版](https://github.com/yixunfei/zero-finish-input/releases/tag/v0.1.0) ·
[提交问题](https://github.com/yixunfei/zero-finish-input/issues) ·
[贡献指南](CONTRIBUTING.md) · [安全政策](SECURITY.md) · [Apache-2.0](LICENSE)

zero finish input（原工程名 ZeroInput）以隐私、安全、离线和可扩展性为核心。当前首发为
`v0.1.0` **预发布测试版**，中文提供全拼、简拼及可配置模糊拼音，同时提供离线英文候选、emoji 分类/搜索/最近使用、加密用户词组
以及需要系统身份认证的安全剪贴板。

An offline Android keyboard for Chinese Pinyin and English, with encrypted local
personalization and a private, authenticated snippet vault. Android 8.0+;
Apache-2.0 project code. The first release is a debug-signed testing prerelease.

项目展示及应用显示名称使用 `zero finish input`。Logo 保留所提供原图，图中文字为
`ZERO FISH INPUT`；Android 启动图标使用该图的主体裁切。内部包名和数据标识保留 `zeroinput`。

## 安装与启用

1. 从 [Release 页面](https://github.com/yixunfei/zero-finish-input/releases/tag/v0.1.0)
   下载 `zero-finish-input-0.1.0-debug-universal.apk`。它包含 `arm64-v8a`、`armeabi-v7a` 和
   `x86_64` 三种架构，要求 Android 8.0（API 26）或更新版本。
2. 对照同页 `SHA256SUMS.txt` 校验下载；Windows 可运行
   `Get-FileHash .\zero-finish-input-0.1.0-debug-universal.apk -Algorithm SHA256`。
3. 安装后打开 **zero finish input**，在系统输入法设置中启用，然后选择为当前键盘。
   Android 会显示针对所有第三方输入法的系统提醒；项目的隐私边界见下文。
4. 在普通输入框中输入 `nihao` 并选取“你好”，通过语言切换键切换英文。
   首次启动会在设备上准备 Rime 数据，无需联网。

本次 APK 使用 **Debug 签名**，包名为 `dev.zeroinput.ime.debug`，版本为 `0.1.0-debug`，
可调试，仅用于体验和反馈。尚未配置维护者正式发布签名，不建议用此测试包保存真实秘密。
Release 源码构建生成的包名为 `dev.zeroinput.ime`，两个包的本地数据彼此独立。
不同开发环境的 Debug 签名可能不同；遇到签名冲突不要直接卸载，以免丢失本地数据。

应用关闭云备份及设备迁移。卸载或清除应用数据会丢失加密词组、emoji 历史和安全片段。
只有用户词组支持主动导出，导出文件为明文；安全片段没有导出或恢复入口。

## 当前能力与边界

| 能力 | 当前实现 |
| --- | --- |
| 中文 | 真实 librime 1.13.1，全拼、简拼、简繁、模糊音、全键盘与九键 |
| 英文 | 内置离线候选，无云端补全 |
| 个性化 | 本地加密词组与词频，支持隐身模式和禁用学习 |
| Emoji | 分类、中英文关键词搜索、本地最近使用 |
| 安全剪贴板 | 默认关闭，用户主动添加，每次访问要求系统认证 |
| 语言包 | 只含数据的离线包，目前仅接受中文或英文 |
| 离线词典测试引擎 | 约 130 条参考词条，用于开发对照，不替代完整 Rime 词库 |

目前没有云同步、语音输入、手写识别或联网语言包市场。ARM 真机、厂商输入框以及真实系统
身份认证流程仍需扩大验收；模拟器测试通过不等同于所有设备均已验证。

## 隐私与安全边界

- 应用不声明 `INTERNET` 权限，不包含广告、统计或崩溃上报 SDK，离线可完整输入。
- 输入法服务不监听、不主动读取或写入 Android 系统剪贴板。设置页文本框仍支持 Android 在用户
  明确执行粘贴操作时提供的标准编辑行为；安全剪贴板不会与系统剪贴板同步。
- 安全剪贴板默认关闭，数据保存在应用私有目录中。未认证的输入面板只能读取不含标签和正文的
  加密索引；读取、新增和删除正文每次都需要生物识别或设备凭据，并使用一次性认证授权。
- 密码、PIN、可见密码以及目标应用要求禁用个性化的输入框，会强制关闭学习、个人候选和
  emoji 最近使用记录；邮箱/网址和隐身模式也不会读取或写入这些个性化数据。
- Android 云备份与设备迁移均关闭，所有存储目录也由备份规则显式排除。
- 用户词组、词频、emoji 最近使用记录和安全剪贴板使用 Android Keystore 中相互隔离的
  AES-256-GCM 密钥。密文位于应用 `noBackupFilesDir/encrypted/` 下；清除个性化数据时会同时
  删除用户词组和 emoji 历史的专用密钥。
- librime 自带用户词典已关闭；个人词频只经过 ZeroInput 的加密 `PersonalizationStore`。
  词库仅在允许个性化的会话首次需要时后台按需预热，学习写入串行移出输入主线程；会话或
  隐私策略改变后，旧上下文中尚未执行的学习任务会失效。
- 输入框打开时先显示轻量降级键盘，Rime 和语言包在后台预热完成后自动切换；切换应用、输入框
  或隐私状态时，旧预热结果会被丢弃，不会阻塞按键响应。
- 系统设置中的输入法名称为 `zero finish input`，提供 `中文`（`zh-CN`）和 `English`（`en-US`）
  两个子类型；切换后即使设备报告了硬件键盘配置，触摸键盘仍会创建并显示。

详细控制和限制见 [威胁模型](docs/threat-model.md)。
当前接手评估、首轮修复与后续优先级见 [项目推进记录](docs/project-progress.md)。

## 中文输入与操作

- 默认输出简体。键盘工具栏的“简/繁”和设置页均可切换文字，个人候选也使用当前文字形式。
- 简拼默认开启，支持 `zg`、`zhg`、`zhongg`、`zguo` 等全拼与首字母混输。
- 模糊拼音默认关闭，可分别启用 `z/zh`、`c/ch`、`s/sh`、`n/l`、`hu/fu`、`an/ang`、
  `en/eng`、`in/ing`。中文标点和每页候选数量（5、8、10）可在设置页调整。
- 初次使用或重建配置时，键盘显示准备状态；失败时可主动重试。准备期间提供基础输入，
  本次拼音组合结束后才切换引擎或应用普通设置，避免丢弃正在输入的内容。
- 拼音与候选分行显示，候选右侧箭头展开多行面板，面板底部支持翻页；展开不改变键盘高度。
- 长按退格清空当前拼音，且本次长按不会继续删除已上屏内容。没有拼音组合时连续删除，
  松手、滑出按键或切换输入框/面板即停止。emoji 搜索中长按退格清空搜索词。
- 已安装语言包后，可在设置页重新选择“使用内置中文引擎”。当前高级拼音配置作用于 Rime，
  其他中文引擎可通过统一配置与能力接口扩展。
- 设置页和键盘工具栏可切换中文九键/全键盘；英文保持全键盘。九键按 `64426` 可输入“你好”，
  左侧可选择具体拼音，紧接着按退格可撤回该次选择。符号页数字直接上屏，不作为九键拼音码。
- 按键触控区域连续覆盖整行，松手时立即派发输入，支持双指交替输入；滑出或取消手势不触发旧按键。
- 中文引擎仍默认 Rime。设置页新增“离线词典（测试）”：这是 ZeroInput 自有 Apache-2.0 开源
  参考实现，内置约 130 条常用词条，用于对比输入响应。支持全拼前缀候选、翻页、
  标点和提交；不支持整句生成、繁体转换、简拼、模糊音或九键，对应控件会禁用。

## 用户词组与数据传输

- 用户词组支持新增、删除和 JSON 导入/导出。手动新增及合并后容量限制为 20,000 条、5 MiB；
  达到上限会拒绝更新，不会通过截断词库造成重启后不可读。
- 导入前说明合并规则：相同语言、输入码和词组更新词频及使用时间；其他已有词组保留。
  文件只能包含约定的词组结构，非法嵌套、重复字段/条目、未知语言、损坏编码及超限文件会
  整次拒绝。错误提示不会显示文件内容，写入失败不会发布未保存的内存状态。
- 导出前确认明文风险。JSON 包含中英文词组、输入码、词频及使用时间，不包含 emoji 和安全
  剪贴板。选择已有文件会覆盖其内容；能访问保存位置的其他应用也可能读取该文件。
- 未知编辑器类别和变体按敏感输入处理，禁用候选引擎与个性化数据访问。

## 构建

已验证环境为 Windows + PowerShell。需要 Git、`tar`、JDK 17、Android SDK Platform 36、
Build Tools 36.0.0、NDK `28.2.13676358` 和 CMake（Android SDK 3.22.1 或本机可用版本）。
Gradle 8.14 由仓库 Wrapper 提供，无需单独安装 Gradle。首次获取构建工具、依赖和源码需要网络，
应用安装后的输入功能完全离线。

```powershell
git clone https://github.com/yixunfei/zero-finish-input.git
cd zero-finish-input
```

配置 `JAVA_HOME` 指向 JDK 17，`ANDROID_SDK_ROOT` 指向 Android SDK，或在本机
`local.properties` 中设置 `sdk.dir`。不要提交本机 SDK 路径或签名文件。
先获取固定版本及校验和的 librime 1.13.1 与依赖，然后用 Android Studio 打开仓库根目录：

```powershell
./tools/bootstrap-rime.ps1
```

开发验证可以只编译当前模拟器 ABI，避免重复构建 native 依赖：

```powershell
./gradlew.bat :app:assembleDebug testDebugUnitTest privacyCheck :app:lintDebug `
  "-Pandroid.injected.build.abi=x86_64" -PrequireRime=true
```

生成可直接安装到受支持真机的 Debug 签名通用测试包，并在打包前运行完整单元测试、隐私门禁和
Lint：

```powershell
./tools/package-test-apk.ps1
```

APK、第三方许可证压缩包及 SHA-256 清单位于 `app/build/outputs/test-apk/<时间戳>/`。
许可证和源码获取说明同时内置于 APK 的 `assets/licenses/`。需要更小的单 ABI 包时可传入
`-Abi arm64-v8a`、`-Abi armeabi-v7a` 或 `-Abi x86_64`；连接且授权一台 ADB 设备后，使用
`./tools/package-test-apk.ps1 -Install` 可在验证通过后直接覆盖安装 Debug 测试版。Debug 版使用
独立包名 `dev.zeroinput.ime.debug`，不会覆盖正式版。

正式构建会强制检查 native Rime，并生成三个独立 ABI 包：

```powershell
./gradlew.bat :app:assembleRelease -PrequireRime=true --no-parallel
```

产物位于 `app/build/outputs/apk/release/`：

- `app-arm64-v8a-release-unsigned.apk`
- `app-armeabi-v7a-release-unsigned.apk`
- `app-x86_64-release-unsigned.apk`

仓库不保存发布私钥，因此 Release APK 有意保持未签名；分发前必须使用项目维护者自己的发布密钥
签名。未执行 `bootstrap-rime.ps1` 时可构建带降级拼音引擎的开发包，但 `requireRime` 会阻止它被
误当成正式版本。

## 模块

- `app`：Android 入口、设置、生命周期编排及平台适配。
- `engine-api`：稳定的输入引擎和个性化端口。
- `engine-rime`：可替换的 librime/JNI 适配器、全拼数据和降级实现。
- `engine-english`：离线英文候选引擎。
- `engine-dictionary`：独立的离线有限词典中文测试引擎，默认不启用。
- `ime-core`：输入会话、编辑器交互和隐私策略。
- `ime-ui`：键盘、候选栏、安全剪贴板入口和 emoji 面板。
- `security`：Android Keystore、AES-GCM 与认证授权。
- `user-data`：用户词组、词频、emoji 历史和安全剪贴板。
- `language-pack`：只允许数据文件的语言包格式、校验和安装边界，并提供已安装包的发现、启用、
  删除及数据型引擎注册。

架构与替换边界见 [架构说明](docs/architecture.md) 和
[引擎边界 ADR](docs/adr/0001-engine-boundary.md)。第三方依赖及许可证见
[THIRD_PARTY.md](THIRD_PARTY.md)，安全问题提交方式见 [SECURITY.md](SECURITY.md)。

## 真机验收清单

连接 Android Studio 模拟器后，可运行实际 Rime 和输入面板的设备回归测试：

```powershell
./tools/test-input-experience.ps1
```

测试覆盖完整和分段选词、空格/回车提交、翻页/退格/重置，以及深浅主题、320/411/800 dp 宽度、
系统导航栏避让、面板返回和横屏 emoji 搜索。脚本按设备 ABI 构建并覆盖安装测试 APK，不清除
应用数据。如果当前键盘是 zero finish input，会临时切换到系统键盘以隔离全局 native 运行时，并在结束
或失败后恢复原键盘。多台设备时传入 `-Serial`。

新增设备测试覆盖简繁候选及个人文本转换、简拼开关与混输、各模糊音项、标点模式、候选页大小、
配置部署与活跃会话隔离、旧索引回收、展开候选高度、过期点击、连续删除取消、设置持久化与中英文布局。
`InputLatencyTest` 只测固定公开样例，输出耗时分位数；不能代表不同真机上的整体输入延迟。
`InputPipelineTest` 使用实际 IME 窗口与测试编辑框，分别测量触控派发、编辑器更新与下一帧，
并验证全键盘、九键及第二引擎的连续输入。触控坐标、双指交替和九键深浅主题另有设备测试覆盖。
输入性能数据不包含用户文本，模拟器结果不能替代厂商真机与目标应用验收。
本次结果及测量边界见 [输入体验验收记录](docs/input-experience-validation.md)。

九键验收时还应检查读音选择/撤回、分段选词、数字直输，以及输入中切换布局/引擎后设置延迟到
组合结束生效。第二引擎仅测试有限词典覆盖的词语，例如 `nihao`、`zhongguo`、`shurufa`。

1. 安装与设备 ABI 匹配的已签名 APK，启用 zero finish input，并确认中英文子类型可切换。
2. 在飞行模式下验证中文全拼、候选翻页、英文联想、退格、空格和回车动作。
   中文模式下输入 `nihao` 后，点击“你好”或按空格应一次上屏且不带多余空格；下一段输入不能
   覆盖已提交文本。切换语言、旋转屏幕和切换输入框后，确认选中的语言保持一致。
3. 在密码、PIN、邮件地址以及声明禁用个性化的输入框中验证学习和个人候选被关闭。
4. 验证用户词组新增、删除、JSON 导入导出及进程重启后的加密持久化。
5. 验证 emoji 分类、中文/英文关键词搜索、最近使用及敏感输入框不记录最近项。
   从安全剪贴板和 emoji 面板返回键盘，并检查深浅主题、横竖屏中的工具按钮和最底行按键；
   宽横屏的 emoji 搜索面板与键盘并排显示，搜索后切换面板再返回不能改变可用高度。
6. 验证安全剪贴板默认关闭；启用、查看、新增、删除和插入均触发系统认证，取消认证不泄露正文。
   在引擎准备尚未完成时发起插入；引擎完成接管后，先前认证必须失效且不能提交正文。再分别
   验证认证期间继续输入、切换输入框/面板、修改中文配置时均不提交旧请求。这些系统认证负向
   场景需真机手工验收，当前设备测试未自动化覆盖。
7. 验证安全剪贴板管理页禁止截屏、离开页面即清除可见元数据，旋转设备不会错误授权或重复提交。
8. 使用 `aapt2 dump permissions` 核对最终 APK 不含 `INTERNET`，并检查每个包只含对应 ABI。

已在 Android Studio 的 x86_64 模拟器上执行 Rime 和输入面板回归，并进行实际 IME 操作检查。
真机的系统身份认证、厂商输入框行为及 ARM 设备运行仍需按上述清单在发布前验收。

## 参与与许可

欢迎提交可复现的问题和范围明确的 Pull Request。请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)
和 [AGENTS.md](AGENTS.md)；涉及输入会话或安全边界时，还需阅读相应架构和 ADR。
报告问题请使用构造的公开输入样例，不要附带真实词库、安全片段、应用数据目录或密钥。

自有源码采用 [Apache-2.0](LICENSE)。Rime、词典数据和其他依赖保留各自许可证，详见
[THIRD_PARTY.md](THIRD_PARTY.md)、[NOTICE](NOTICE)、[LICENSES/](LICENSES) 和
[源码获取说明](SOURCES.md)。分发 APK 时请同时保留上述许可与来源说明。

下一阶段优先扩大 ARM 真机和系统认证验收、测量实际输入延迟，并持续改进输入正确性。
当前进展见 [项目推进记录](docs/project-progress.md)，首发说明见
[v0.1.0](docs/releases/v0.1.0.md)。
