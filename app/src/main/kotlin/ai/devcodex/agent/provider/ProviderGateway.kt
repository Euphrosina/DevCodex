package ai.devcodex.agent.provider

import ai.devcodex.agent.data.ModelTestResult
import ai.devcodex.agent.data.ProviderConfig
import ai.devcodex.agent.data.ProviderType
import kotlinx.serialization.json.JsonObject

/** 提供商网关：统一入口，按类型路由 */
object ProviderGateway {

    suspend fun listModels(p: ProviderConfig): List<String> = when (p.type) {
        ProviderType.OPENAI_COMPAT -> OpenAiCompatProvider.listModels(p)
        ProviderType.ANTHROPIC -> AnthropicProvider.listModels(p)
        ProviderType.OLLAMA -> OllamaProvider.listModels(p)
    }

    suspend fun testModel(p: ProviderConfig, model: String): ModelTestResult = when (p.type) {
        ProviderType.OPENAI_COMPAT -> OpenAiCompatProvider.testModel(p, model)
        ProviderType.ANTHROPIC -> AnthropicProvider.testModel(p, model)
        ProviderType.OLLAMA -> OllamaProvider.testModel(p, model)
    }

    fun chatRequest(p: ProviderConfig, model: String, systemPrompt: String, turns: List<ChatTurn>, tools: List<JsonObject>, stream: Boolean, reasoningEffort: String = "") = when (p.type) {
        ProviderType.OPENAI_COMPAT -> OpenAiCompatProvider.chatRequest(p, model, turns, tools, stream, reasoningEffort)
        ProviderType.ANTHROPIC -> AnthropicProvider.chatRequest(p, model, systemPrompt, turns, tools, stream, reasoningEffort)
        ProviderType.OLLAMA -> OllamaProvider.chatRequest(p, model, turns, tools, stream)
    }

    fun parseSseLine(p: ProviderConfig, line: String): List<StreamEvent> = when (p.type) {
        ProviderType.OPENAI_COMPAT -> OpenAiCompatProvider.parseSseLine(line)
        ProviderType.ANTHROPIC -> AnthropicProvider.parseSseLine(line)
        ProviderType.OLLAMA -> OllamaProvider.parseLine(line)
    }
}
