package ai.devcodex.agents.data.model

enum class ProviderType(val displayName: String) {
    OPENAI_COMPATIBLE("OpenAI 兼容 API"),
    ANTHROPIC("Anthropic Claude"),
    OLLAMA("Ollama 本地");

    fun defaultBaseUrl(): String = when (this) {
        OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
        ANTHROPIC -> "https://api.anthropic.com"
        OLLAMA -> "http://10.0.2.2:11434"
    }

    fun defaultModel(): String = when (this) {
        OPENAI_COMPATIBLE -> "gpt-4o-mini"
        ANTHROPIC -> "claude-sonnet-4-5"
        OLLAMA -> "llama3.2"
    }

    companion object {
        fun byName(name: String): ProviderType =
            entries.firstOrNull { it.name == name } ?: OPENAI_COMPATIBLE
    }
}

enum class ThemeMode(val displayName: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun byName(name: String): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class AccentColor(val displayName: String) {
    PURPLE("默认紫"),
    BLUE("活力蓝"),
    GREEN("清新绿"),
    ORANGE("暖橙"),
    PINK("樱花粉"),
    TEAL("青碧");

    companion object {
        fun byName(name: String): AccentColor = entries.firstOrNull { it.name == name } ?: PURPLE
    }
}

enum class AppFont(val displayName: String) {
    DEFAULT("系统默认"),
    SANS("无衬线"),
    SERIF("衬线"),
    MONO("等宽"),
    CUSTOM("霞鹜文楷"),
    SMILEY("得意黑"),
    KAITI_GB2312("楷体GB2312"),
    FZ_HEITI_FANTI("方正黑体-繁体"),
    FZ_HEITI_JIANTI("方正黑体-简体"),
    FZ_HEITI_JZF("方正黑体-简转繁"),
    YIQI_XINSONGTI("义启新宋体"),
    XIAOMI_LANTING("小米兰亭Pro"),
    MSYH("微软雅黑"),
    PINGFANG_ZHONGHEI("苹方-中黑"),
    PINGFANG_ZHUN("苹方-准"),
    MAPLE_MONO("MapleMono"),
    MAPLE_MONO_ITALIC("MapleMono-斜体"),
    GOOGLE_SANS_CODE("GoogleSansCode");

    companion object {
        fun byName(name: String): AppFont = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

enum class UiStyle(val displayName: String) {
    MATERIAL("Material3"),
    MIUI("MIUI 风格");

    companion object {
        fun byName(name: String): UiStyle = entries.firstOrNull { it.name == name } ?: MATERIAL
    }
}

enum class ThemePreset(val displayName: String) {
    NONE("跟随明暗与强调色"),
    DARK("深色"),
    LIGHT("浅色"),
    COFFEE("咖啡"),
    VSCODE("VS Code"),
    GITHUB_DARK("GitHub深色"),
    GRUVBOX("Gruvbox"),
    HIGH_CONTRAST("高对比度");

    companion object {
        fun byName(name: String): ThemePreset = entries.firstOrNull { it.name == name } ?: NONE
    }
}

enum class FontWeightSetting(val displayName: String) {
    LIGHT("细体"),
    NORMAL("常规"),
    MEDIUM("中粗"),
    BOLD("粗体");

    companion object {
        fun byName(name: String): FontWeightSetting = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

data class SettingsData(
    val provider: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String = ProviderType.OPENAI_COMPATIBLE.defaultBaseUrl(),
    val apiKey: String = "",
    val model: String = ProviderType.OPENAI_COMPATIBLE.defaultModel(),
    val temperature: Float = 0.7f,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.PURPLE,
    val font: AppFont = AppFont.DEFAULT,
    val fontWeight: FontWeightSetting = FontWeightSetting.NORMAL,
    val fontSize: Float = 16f,
    val themePreset: ThemePreset = ThemePreset.NONE,
    val customPrimary: Long? = null,
    val customBackground: Long? = null,
    val uiStyle: UiStyle = UiStyle.MATERIAL
)

enum class MessageRole {
    SYSTEM, USER, ASSISTANT;

    companion object {
        fun byName(name: String): MessageRole =
            entries.firstOrNull { it.name == name } ?: USER
    }
}

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage> = emptyList()
)