package ai.devcodex.agents.ui

import ai.devcodex.agents.data.model.ChatMessage
import ai.devcodex.agents.data.model.Conversation
import ai.devcodex.agents.data.model.MessageRole
import ai.devcodex.agents.data.model.SettingsData
import ai.devcodex.agents.data.remote.ProviderFactory
import ai.devcodex.agents.data.repo.AttachedFile
import ai.devcodex.agents.data.repo.ConversationRepository
import ai.devcodex.agents.data.repo.ProjectFile
import ai.devcodex.agents.data.repo.ProjectFilesRepository
import ai.devcodex.agents.data.repo.SettingsRepository
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val settings: SettingsData = SettingsData(),
    val conversations: List<Conversation> = emptyList(),
    val selectedId: String? = null,
    val streaming: Boolean = false,
    val attachedFiles: List<AttachedFile> = emptyList(),
    val testing: Boolean = false,
    val testResult: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val conversationRepo = ConversationRepository(app)
    val projectFilesRepo = ProjectFilesRepository(app)

    private val _uiState = MutableStateFlow(AppUiState(settings = settingsRepo.load()))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    init {
        refreshConversations()
    }

    fun refreshConversations() {
        viewModelScope.launch {
            val list = conversationRepo.loadAll()
            _uiState.update { it.copy(conversations = list) }
        }
    }

    fun selectConversation(id: String?) {
        streamJob?.cancel()
        _uiState.update { it.copy(selectedId = id) }
    }

    private fun currentConversation(): Conversation? =
        _uiState.value.conversations.firstOrNull { it.id == _uiState.value.selectedId }

    fun newConversation() {
        val conv = Conversation(
            id = conversationRepo.newId(),
            title = "新会话",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            conversationRepo.save(conv)
            _uiState.update {
                it.copy(conversations = listOf(conv) + it.conversations, selectedId = conv.id)
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationRepo.delete(id)
            _uiState.update {
                it.copy(
                    conversations = it.conversations.filterNot { c -> c.id == id },
                    selectedId = if (it.selectedId == id) null else it.selectedId
                )
            }
        }
    }

    fun clearConversations() {
        viewModelScope.launch {
            _uiState.value.conversations.forEach { conversationRepo.delete(it.id) }
            _uiState.update { it.copy(conversations = emptyList(), selectedId = null) }
        }
    }

    fun updateSettings(settings: SettingsData) {
        settingsRepo.save(settings)
        _uiState.update { it.copy(settings = settings) }
    }

    fun attachFiles(files: List<ProjectFile>) {
        viewModelScope.launch {
            val loaded = files.map { file ->
                runCatching { projectFilesRepo.readFile(file.uriString) }
                    .getOrElse {
                        AttachedFile(
                            name = file.name,
                            path = file.path,
                            content = "",
                            truncated = false,
                            error = it.message
                        )
                    }
            }
            _uiState.update { state ->
                state.copy(attachedFiles = (state.attachedFiles + loaded).distinctBy { it.path })
            }
        }
    }

    fun removeAttached(path: String) {
        _uiState.update {
            it.copy(attachedFiles = it.attachedFiles.filterNot { af -> af.path == path })
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(testing = true, testResult = null) }
            val result = runCatching { ProviderFactory.create(_uiState.value.settings).testConnection() }
                .getOrElse { "连接失败：${it.message}" }
            _uiState.update { it.copy(testing = false, testResult = result) }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.streaming) return

        val files = _uiState.value.attachedFiles
        var conversation = currentConversation()
        val isNew = conversation == null
        if (conversation == null) {
            conversation = Conversation(
                id = conversationRepo.newId(),
                title = trimmed.take(24),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        val userMessage = ChatMessage(MessageRole.USER, trimmed)
        val updated = conversation.copy(
            title = if (isNew) trimmed.take(24) else conversation.title,
            updatedAt = System.currentTimeMillis(),
            messages = conversation.messages + userMessage + ChatMessage(MessageRole.ASSISTANT, "")
        )

        _uiState.update { state ->
            state.copy(
                conversations = if (isNew) {
                    listOf(updated) + state.conversations.filterNot { it.id == updated.id }
                } else {
                    state.conversations.map { if (it.id == updated.id) updated else it }
                },
                selectedId = updated.id,
                streaming = true,
                attachedFiles = emptyList()
            )
        }
        if (isNew) viewModelScope.launch { conversationRepo.save(updated) }

        streamJob = viewModelScope.launch {
            var streamed = ""
            var errorText: String? = null
            try {
                val provider = ProviderFactory.create(_uiState.value.settings)

                val history = buildList {
                    if (files.isNotEmpty()) {
                        add(
                            ChatMessage(
                                MessageRole.SYSTEM,
                                "以下是用户提供的项目文件内容，请基于这些文件回答：\n" +
                                    files.joinToString("\n\n") { f ->
                                        val head = "【文件】${f.path}"
                                        val body = if (f.error != null) {
                                            "（读取失败：${f.error}）"
                                        } else {
                                            f.content + if (f.truncated) "\n（内容过长已截断）" else ""
                                        }
                                        "$head\n$body"
                                    }
                            )
                        )
                    }
                    addAll(updated.messages.dropLast(1).takeLast(40))
                }

                provider.streamChat(history) { delta ->
                    streamed += delta
                    if (_uiState.value.streaming) {
                        _uiState.update { state ->
                            state.copy(
                                conversations = state.conversations.map { conv ->
                                    if (conv.id == updated.id) {
                                        val msgs = conv.messages.toMutableList()
                                        msgs[msgs.size - 1] = msgs.last().copy(content = streamed)
                                        conv.copy(messages = msgs)
                                    } else conv
                                }
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorText = e.message ?: "未知错误"
            } finally {
                if (_uiState.value.streaming) {
                    val finalMessage = ChatMessage(
                        MessageRole.ASSISTANT,
                        if (errorText != null) "请求失败：$errorText" else streamed,
                        isError = errorText != null
                    )
                    viewModelScope.launch {
                        if (_uiState.value.conversations.any { it.id == updated.id }) {
                            conversationRepo.save(
                                updated.copy(
                                    title = if (isNew) trimmed.take(24) else updated.title,
                                    updatedAt = System.currentTimeMillis(),
                                    messages = updated.messages.dropLast(1) + finalMessage
                                )
                            )
                        }
                    }
                    _uiState.update { state ->
                        state.copy(
                            streaming = false,
                            conversations = state.conversations.map { conv ->
                                if (conv.id == updated.id) {
                                    val msgs = conv.messages.toMutableList()
                                    if (msgs.isNotEmpty()) {
                                        msgs[msgs.size - 1] = finalMessage
                                    }
                                    conv.copy(messages = msgs)
                                } else conv
                            }
                        )
                    }
                }
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
    }
}