# Requirements Document

## Introduction

为 DevCodex 增加多语言支持。首批语言为简体中文、繁体中文（台湾）、繁体中文（香港）、English、日本語、한국어。覆盖范围包括界面文案、无障碍服务文案、工具返回信息、通知文案和默认系统提示词。用户可以在设置页选择语言，默认跟随系统，切换后立即生效。

## Glossary

- **System**: DevCodex Android 应用
- **Supported Locale**: zh-CN、zh-TW、zh-HK、en、ja、ko 六种语言之一
- **Language Setting**: 保存在 AppSettings 中的语言选项，取值为 SYSTEM、zh-CN、zh-TW、zh-HK、en、ja、ko
- **Effective Locale**: 根据 Language Setting 与系统语言解析出的、当前生效的 Supported Locale
- **UI Text**: Compose 界面、对话框、Toast、通知中展示给用户的文字
- **Tool Message**: 工具执行结果中面向用户或模型展示的状态、错误与提示文字
- **Default System Prompt**: AgentEngine 中的 DEFAULT_SYSTEM_PROMPT 及其拼装的附加段落
- **Custom System Prompt**: 用户在自定义系统提示词页填写的内容

## Requirements

### Requirement 1 语言选项与存储

**User Story:** AS 用户, I want 在设置页选择应用语言, so that 应用以我熟悉的语言显示

#### Acceptance Criteria

1. THE System SHALL 在设置页提供语言选项：跟随系统、简体中文、繁體中文（台灣）、繁體中文（香港）、English、日本語、한국어。
2. THE System SHALL 以各语言的本语言名称显示选项，不随当前语言翻译。
3. THE System SHALL 将 Language Setting 持久化到 AppSettings，默认值为 SYSTEM。
4. WHEN 旧版本升级后首次启动, THE System SHALL 将缺失的 Language Setting 视为 SYSTEM。

### Requirement 2 跟随系统

**User Story:** AS 用户, I want 应用默认跟随系统语言, so that 无需额外设置即可使用

#### Acceptance Criteria

1. WHILE Language Setting 为 SYSTEM, THE System SHALL 按系统语言列表的首个 Supported Locale 解析 Effective Locale。
2. WHEN 系统语言为繁体中文且地区为台湾, THE System SHALL 解析为 zh-TW。
3. WHEN 系统语言为繁体中文且地区为香港或澳门, THE System SHALL 解析为 zh-HK。
4. WHEN 系统语言为简体中文, THE System SHALL 解析为 zh-CN。
5. WHEN 系统语言列表中没有 Supported Locale, THE System SHALL 解析为 en。

### Requirement 3 即时切换

**User Story:** AS 用户, I want 切换语言后立即看到效果, so that 不需要重启应用

#### Acceptance Criteria

1. WHEN 用户选择新的语言选项, THE System SHALL 在同一界面内于 1 秒内以新的 Effective Locale 重新渲染所有可见 UI Text。
2. WHEN 语言切换发生, THE System SHALL 保留当前页面、会话、输入框草稿和滚动位置。
3. WHEN 语言切换发生, THE System SHALL 保留正在进行的 Agent 任务和后台终端。
4. WHEN 系统语言在应用运行中发生变化且 Language Setting 为 SYSTEM, THE System SHALL 重新解析 Effective Locale 并刷新界面。

### Requirement 4 界面文案外置

**User Story:** AS 翻译维护者, I want 所有界面文案集中在资源文件中, so that 新增或修改翻译无需改动业务代码

#### Acceptance Criteria

1. THE System SHALL 将 UI Text 存放于 Android 字符串资源，并提供 values-zh-rCN、values-zh-rTW、values-zh-rHK、values-en、values-ja、values-ko 六套资源。
2. THE System SHALL 对含变量的文案使用带位置序号的格式参数（如 %1$s、%2$d）。
3. THE System SHALL 对随数量变化的文案使用 plurals 资源。
4. THE System SHALL 保持各语言资源的 key 集合一致。
5. IF 某语言缺少某个 key, THEN THE System SHALL 回退显示 en 文案。
6. THE System SHALL 在构建中将 MissingTranslation 检查设为错误级别。

### Requirement 5 系统级文案

**User Story:** AS 用户, I want 系统设置里看到的应用信息也使用我的语言, so that 体验一致

#### Acceptance Criteria

1. THE System SHALL 为 app_name、a11y_service_label、a11y_service_desc 提供六种语言版本。
2. THE System SHALL 提供 locales_config 声明六种 Supported Locale，使 Android 13 及以上的系统「应用语言」设置可用。
3. WHERE 系统版本为 Android 13 及以上, THE System SHALL 将系统「应用语言」设置与应用内 Language Setting 保持同步。

### Requirement 6 工具返回与通知

**User Story:** AS 用户, I want 工具执行提示和通知也使用所选语言, so that 整个使用过程语言一致

#### Acceptance Criteria

1. THE System SHALL 以 Effective Locale 生成 Tool Message 中的状态描述与错误提示。
2. THE System SHALL 以 Effective Locale 生成前台服务通知、Toast 和权限询问对话框的文字。
3. THE System SHALL 保持 Tool Message 中的错误前缀 ERROR 以及工具名、参数名、命令输出原文不被翻译。

### Requirement 7 系统提示词

**User Story:** AS 用户, I want AI 默认用我选择的语言回复, so that 无需每次在对话里说明

#### Acceptance Criteria

1. THE System SHALL 为六种 Supported Locale 各提供一份 Default System Prompt。
2. WHEN 会话发起请求, THE System SHALL 使用 Effective Locale 对应的 Default System Prompt，并在其中声明回复语言。
3. THE System SHALL 保持行为准则的条目含义在六种语言版本中一致。
4. THE System SHALL 将 Custom System Prompt 原文追加在 Default System Prompt 之后，不做翻译。
5. WHEN 用户消息明确要求使用其他语言回复, THE System SHALL 允许模型以该语言回复。

### Requirement 8 本地化格式

**User Story:** AS 用户, I want 日期、数字和单位符合所选语言习惯, so that 统计页等内容易读

#### Acceptance Criteria

1. THE System SHALL 以 Effective Locale 格式化界面中的日期、时间、月份名称和数字。
2. THE System SHALL 保持调试日志、崩溃日志、文件名时间戳使用固定的 Locale.US 格式。

### Requirement 9 用户数据保真

**User Story:** AS 用户, I want 切换语言不改变我的历史数据, so that 对话与设置不丢失

#### Acceptance Criteria

1. WHEN 语言切换发生, THE System SHALL 保持已保存的消息、记忆、技能、插件、提供商配置内容不变。
2. THE System SHALL 将内置技能与内置插件的名称与描述按 Effective Locale 展示，用户自行编辑过的内容保持原文。
3. THE System SHALL 对用户输入内容、模型输出内容和文件内容按原文显示。

### Requirement 10 繁体中文地区用词

**User Story:** AS 台湾或香港用户, I want 译文符合本地用词, so that 阅读自然

#### Acceptance Criteria

1. THE System SHALL 为 zh-TW 与 zh-HK 分别维护独立资源，两者不互为简单转换。
2. THE System SHALL 在 zh-TW 使用台湾通行用词（如「軟體」「檔案」「設定」「網路」）。
3. THE System SHALL 在 zh-HK 使用香港通行用词（如「軟件」「檔案」「設定」「網絡」）。
4. THE System SHALL 在翻译词汇表中记录各语言的统一术语（如 Agent、工作区、沙盒、提供商、插件、技能）。

### Requirement 11 验证

**User Story:** AS 开发者, I want 有自动化手段发现遗漏与错误, so that 发布前能保证翻译完整

#### Acceptance Criteria

1. WHEN 执行 lint, THE System SHALL 报告缺失的翻译 key 与格式参数不一致的字符串。
2. THE System SHALL 提供单元测试，校验六种语言资源的 key 集合与格式参数个数一致。
3. THE System SHALL 提供单元测试，校验 Effective Locale 的解析规则（Requirement 2 全部情形）。
4. WHEN 云端构建完成, THE System SHALL 生成可安装的 APK 供真机验证六种语言的界面截图。

### Requirement 12 平台约束与版本

**User Story:** AS 用户, I want 多语言版本继续支持现有设备并保持沙盒可用, so that 升级后原有功能不受影响

#### Acceptance Criteria

1. THE System SHALL 保持 minSdk 为 26。
2. THE System SHALL 保持 targetSdk 为 28，使 files 目录下的 proot、Termux 与 Ubuntu 沙盒二进制继续可执行。
3. THE System SHALL 将 versionName 设为 0.4.0，versionCode 设为 86。
4. WHERE 系统版本低于 Android 13, THE System SHALL 仅使用应用内语言选项切换语言。
