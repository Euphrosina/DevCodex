# DevCodex v3 重构计划（Claude Code 风格 Android AI Agent）

## 状态：进行中（2026-10-19 启动）
进度看本文件底部「进度日志」。会话若中断，接续者从此处恢复。

## 需求（用户原话归纳）
1. 用 android/skills（已装 /home/z/my-project/skills/）指导构建
2. 主题/字体 移植 rikkahub（7 预设 + Google Sans Flex 变量字体 + JetBrains Mono）
3. UI 框架 MD3（MaterialExpressiveTheme），删除 miuix
4. Agent 形态对标 Claude Code / Codex / ZCode / DSH：聊天流+工具卡片+权限确认
5. 插件扩展（JSON tool pack：声明式 HTTP 工具包，文件/URL 安装）
6. 手机本地文件读写：App 沙箱 + SAF 树 + Shizuku + Root 四级访问链
7. Shizuku / Root / 无障碍 权限授权管理
8. 核心：真实工具调用（agent loop）+ API 提供商配置
9. 模型自动识别（GET /models）+ 模型测试（延迟/流式/工具支持三项，对标 rikkahub）
10. GitHub Actions 云端构建 + 签名 Release 出包
11. 仅 arm64（用户测试机 Android 11 arm64，拒绝 universal）

## 技术决策（锁定）
- 目录：/home/z/my-project/DevCodex-v3 → 推 Moirania/DevCodex（远端旧 main 备份到 legacy-v2 分支后强推）
- 包名 ai.devcodex.agent（保持升级兼容）versionName 3.0.0 versionCode 5
- 构建：AGP 9.4.0 / Gradle 9.6.0 / Kotlin 2.4.10 内置（无 kotlin-android 插件，kotlin-compose + serialization 插件照抄 v2 已验证配置）
- compileSdk 37（新 DSL release(37)）/ minSdk 26 / targetSdk 37
- Compose BOM 2026.09.00 / material3（MaterialExpressiveTheme + MotionScheme）
- 依赖：okhttp 4.12.0 / kotlinx-serialization-json 1.11.0 / coroutines 1.10.2 / datastore-preferences 1.1.7 / shizuku api+provider 13.1.5
- 持久化：无 Room（规避 AGP9 KSP 风险）——settings 用 DataStore+JSON，会话 filesDir/sessions/*.json
- edge-to-edge：ComponentActivity.enableEdgeToEdge()（skill 规范：不需手动状态栏图标，Scaffold contentWindowInsets=WindowInsets.safeDrawing，输入栏 imePadding，底部栏 SDK29+ isNavigationBarContrastEnforced=false）
- 字体：res/font/google_sans_flex.ttf（变量字体 weight/ROND/grade/opsz）+ jetbrains_mono.ttf（代码/工具卡），Typography 用 flexFont(weight,size) 辅助
- 主题：7 预设脚本化移植（sakura/ocean/spring/autumn/black/minimal/claude）+ 动态取色(SDK31+，用户 Android 11 无)+ AMOLED + SYSTEM/LIGHT/DARK
- 提供商：OpenAI 兼容 / Anthropic / Ollama 三类（v2 已有逻辑，重写精炼）；模型识别 GET {base}/models（Anthropic 带 x-api-key+anthropic-version）
- 工具（内置）：fs_read/fs_write/fs_list/fs_delete、shell_exec、http_request、memory_save/memory_read、app_list、device_info、ui_snapshot/ui_tap/ui_back/ui_home/ui_scroll（无障碍）、notify
- 权限门：shell_exec 每次确认（可记住会话）；沙箱外写入确认；审批在聊天内以卡片呈现（允许一次/本会话/拒绝）
- Shizuku：bindUserService + IShellService.aidl（exec/read/write/list）；Root：su -c；无障碍：AccessibilityService（窗口文本快照+点击+全局动作）
- 插件：JSON manifest（id/name/version/tools[]，tool=JSON schema 参数 + HTTP 模板执行 {{param}}），存 filesDir/plugins/*.json，启用/禁用/删除，从文件或 URL 或粘贴安装
- CI：.github/workflows/build.yml（JDK21 temurin + gradle wrapper 9.6.0 + setup-gradle 缓存；push=assembleDebug；tag v*=assembleRelease+签名(base64 keystore secret,pynacl 上传)+gh release 发布+sha256）
- 签名：新 keystore（deliverables/devcodex-v3.keystore，alias devcodex），secret 名 ANDROID_KEYSTORE_BASE64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD

## 界面（5 屏 + 导航）
底部 NavigationBar 4 tab：对话 / 提供商 / 插件 / 设置
- 对话：TopBar（会话名+抽屉入口）、消息流（用户气泡右/助手全宽、工具卡片可展开、审批卡、流式光标）、输入栏（模型 chip+附件+发送）、空态快捷入口；会话列表=ModalNavigationDrawer
- 提供商：卡片列表+新增（类型三选）、详情（字段+拉取模型+模型多选+逐模型测试对话框：延迟/流式/工具三状态）
- 插件：已装列表（开关/工具数/删除）、安装（文件/URL/粘贴）
- 设置：主题（预设色板网格/模式/AMOLED/动态取色）、字体（应用/对话等宽切换）、工具与权限（内置工具开关、SAF 根管理、Shizuku/Root/无障碍状态卡）、关于
- MainActivity 单 Activity + NavHost

## 文件清单（~45 kt）
app/src/main/kotlin/ai/devcodex/agent/
  DevCodexApp.kt MainActivity.kt
  theme/ DcTheme.kt PresetThemes.kt Typography.kt presets/(7个).kt
  data/ Models.kt SettingsRepo.kt SessionRepo.kt PluginRepo.kt
  provider/ Provider.kt OpenAiProvider.kt AnthropicProvider.kt OllamaProvider.kt Sse.kt
  agent/ AgentEngine.kt ToolRegistry.kt Tools.kt PermissionGate.kt
  tools/ FsTools.kt ShellTool.kt HttpTool.kt MemoryTools.kt DeviceTools.kt UiTools.kt
  shell/ ShellService.aidl ShizukuShell.kt RootShell.kt
  service/ DcAccessibilityService.kt
  plugin/ PluginManager.kt PluginExecutor.kt
  ui/ Nav.kt components/(MessageList.kt ToolCard.kt ApprovalCard.kt InputBar.kt ModelChip.kt)
  ui/screens/ ChatScreen.kt SessionsDrawer.kt ProvidersScreen.kt PluginsScreen.kt SettingsScreen.kt ThemePage.kt FontPage.kt ToolsPermPage.kt
res/font/(2 ttf) res/xml/(accessibility_service_config.xml,file_provider_paths.xml) 其余抄 v2

## 进度日志
- [x] 2026-10-19 调研完成：rikkahub 7 主题预设结构/PresetTheme 色值全量拿到/GoogleSans.kt 变量字体方案/ProviderConnectionTester 三项测试逻辑/OpenAI listModels 实现/远端 ci 分支旧 workflow（JDK17+SDK36 已过时，重写）
- [x] 骨架/主题/数据/API/工具/UI 全部代码完成（~30 文件）
- [x] 本地构建 5 轮修复通过：app-arm64-v8a-debug.apk 22.8MB（AIDL 开关、Compose1.10 TextStyle API、MotionScheme internal、Flow collectAsState initial、字体 spanStyle、endpoint 路径、Ollama 函数名、ic_launcher 资源漏拷）
- [x] 远端 main 已备份 legacy-v2（48e45f69），v3 强推 main（94bd38b）
- [x] 4 个签名 secrets 已上传（keystore: deliverables/devcodex-v3.keystore, alias devcodex, pass devcodex2026）
- [x] tag v3.0.0 已推送，Actions run 39242137973 构建中（JDK21+签名 Release+gh release 发布）
- [x] CI 全绿（run 39242137973），Release v3.0.0 已发布：app-arm64-v8a-release.apk 4.2MB + sha256
- [x] 终检通过：CN=DevCodex 正式证书签名、arm64 单 ABI、versionCode 5 / 3.0.0
- [x] APK 已 send_file 交付（deliverables/DevCodex-v3.0.0-arm64-release.apk）
- [x] **v3.0.0 交付完成（2026-10-19 19:11）**。后续升级：改代码 → push main（自动 debug CI）→ tag v3.x.0（自动签名 Release），keystore 同 deliverables/devcodex-v3.keystore 可覆盖安装
关键注意：本地构建用 Corretto21 + ANDROID_HOME=/home/z/tools/android-sdk + /home/z/tools/gradle-9.6.0/bin/gradle --no-daemon --console=plain，日志 build.log（曾因双进程写坏，勿并发跑两个构建）

---

# v3.1 重构方案（2026-10-19 晚，用户反馈"不像 Agent + bug 多"）

## 参考研究结论（Operit 8819★ 源码 + 搜索）
- **Operit 关键设计**：
  1. ChatStyle.CURSOR 模式：全宽交替行、无气泡（Claude/ChatGPT 网页风），与 BUBBLE 并存可切换
  2. 工具调用内联渲染：DetailedToolDisplay = 图标+工具名+参数摘要（含字节数标签）紧凑行，点击弹窗看详情——工具直接嵌在 AI 消息流里，不是独立卡片
  3. 权限系统：每工具 ALLOW/ASK/FORBID 三级持久化 + 主开关 + 60s 超时队列（ToolPermissionSystem.kt）
  4. 消息级 token/耗时/模型统计
  5. 思考块折叠展示
- 用户参考系 ZCode/Claude Code/Codex/DSH 全是终端形态 → v3 的微信气泡方向错了

## v3.1 改造清单
A. **终端风 Agent UI**（核心）：
   1. Cursor 式布局：用户消息全宽 "> " 前缀等宽；助手全宽流式 + ▍光标
   2. 工具行内联：⏺ name(args) + ⎿ 结果摘要，点击展开（Claude Code 符号语言）
   3. Reasoning 事件接通 → ✻ 思考折叠块
   4. 输入栏 ❯ 前缀 + imePadding 修复键盘遮挡
B. **Bug 修复**（代码审查定位）：
   1. Drawer 内 LazyColumn 无界高度崩溃 → weight(1f,fill=false)
   2. Shizuku bindUserService 竞态 → 挂起等待绑定(3s超时)
   3. appFont 设置无效 → Typography 按 appFont/chatFont 双轨生效
   4. Anthropic testModel streamOk 恒真
   5. 流式滚动风暴 → 仅贴底时自动滚
C. **权限升级**（学 Operit）：审批卡三键（拒绝/允许一次/总是允许→持久化 ALLOW），设置页可改
D. **全局崩溃捕获**：UncaughtExceptionHandler → filesDir/crash/*.txt，下次启动弹卡片可复制上报

## 进度（v3.1）
- [ ] 调研完成（本节）
- [ ] 代码改造
- [ ] 构建验证
- [ ] tag v3.1.0 发布
