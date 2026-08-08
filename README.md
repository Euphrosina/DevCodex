# DevCodex

DevCodex 是一款运行在 Android 手机上的 AI 编码助手,采用 Jetpack Compose + Material 3 构建。在手机上随时随地与 AI 对话,获取编程帮助。

## 功能特性

- **多 AI 提供商**:支持 OpenAI 兼容 API、Anthropic Claude、Ollama 本地模型
- **流式输出**:实时流式生成回复,打字机效果
- **Markdown 渲染**:支持 Markdown 语法渲染与代码语法高亮
- **会话管理**:多会话切换,历史记录本地持久化
- **文件附加上下文**:选择项目文件,把代码内容作为上下文发送给 AI
- **主题定制**:浅色 / 深色 / 跟随系统,多种强调色可选
- **内置字体**:内置霞鹜文楷、得意黑、思源黑体等十余种中文字体

## 技术栈

- Kotlin + Jetpack Compose(Material 3)
- OkHttp(网络请求与流式响应)
- ViewModel + StateFlow(状态管理)
- Gradle Kotlin DSL

## 环境要求

- compileSdk 36,minSdk 21,targetSdk 34
- JDK 17
- 支持 Android Studio 或 AndroidIDE 构建

## 构建

```bash
./gradlew assembleDebug
```

## 项目结构

```
app/src/main/kotlin/ai/devcodex/agents/
├── MainActivity.kt              # 入口
├── data/
│   ├── model/                   # 数据模型与枚举
│   ├── remote/                  # AI 提供商实现(OpenAI / Anthropic / Ollama)
│   └── repo/                    # 会话、设置、项目文件仓库
└── ui/
    ├── AppViewModel.kt          # 全局状态管理
    ├── markdown/                # Markdown 渲染与代码高亮
    ├── screens/
    │   ├── chat/                # 聊天界面与消息气泡
    │   ├── sessions/            # 会话列表
    │   ├── settings/            # 设置界面
    │   └── about/               # 关于界面
    └── theme/                   # 主题、颜色、字体
```

## 使用

1. 打开 App,进入"设置"配置 API 提供商、Base URL、模型与 API Key
2. 在聊天界面输入问题开始对话
3. 可通过"+"选择项目文件作为上下文附加给 AI

> 注意:API Key 仅保存在本地,不会上传到任何服务器。
