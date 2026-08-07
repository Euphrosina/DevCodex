package ai.devcodex.agents.data.remote

import ai.devcodex.agents.data.model.ChatMessage
import ai.devcodex.agents.data.model.SettingsData

interface AiProvider {
    val displayName: String

    suspend fun streamChat(
        messages: List<ChatMessage>,
        onDelta: suspend (String) -> Unit
    )

    suspend fun testConnection(): String
}

object ProviderFactory {
    fun create(settings: SettingsData): AiProvider = when (settings.provider) {
        ai.devcodex.agents.data.model.ProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(settings)
        ai.devcodex.agents.data.model.ProviderType.ANTHROPIC -> AnthropicProvider(settings)
        ai.devcodex.agents.data.model.ProviderType.OLLAMA -> OllamaProvider(settings)
    }
}