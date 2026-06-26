package dev.luinbytes.pokeclient

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PokeViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)
    private val backend = BackendClient()
    private val chat = ChatRepository()
    private var streamJob: Job? = null
    private var sessionSettings: AppSettings? = null

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )
    val messages: StateFlow<List<ChatMessage>> = chat.messages

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        viewModelScope.launch {
            settingsStore.load()
        }
    }

    fun saveSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            settingsStore.save(newSettings)
            connect(newSettings, saved = true)
        }
    }

    fun useLocalQaSettings() {
        viewModelScope.launch {
            connect(
                AppSettings(
                    backendBaseUrl = "http://127.0.0.1:8787",
                    pokeUserId = "qa-samsung"
                ),
                saved = false
            )
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val message = chat.outboundDraft(trimmed)
        sendExisting(message.id, trimmed)
    }

    fun retry(messageId: String) {
        val message = chat.find(messageId) ?: return
        if (message.direction != MessageDirection.Outbound) return
        sendExisting(message.id, message.text)
    }

    private fun sendExisting(messageId: String, text: String) {
        chat.replace(messageId) { it.copy(status = MessageStatus.Sending) }
        _uiState.update { it.copy(sending = true, status = "Sending...") }

        viewModelScope.launch {
            val current = sessionSettings ?: settings.value
            val result = runCatching {
                when {
                    current.backendBaseUrl.isNotBlank() && current.pokeUserId.isNotBlank() ->
                        backend.sendViaBackend(current, text)
                    else ->
                        SendResult(false, "Add a backend URL and Poke user ID before sending")
                }
            }.getOrElse { SendResult(false, it.message ?: "Send failed") }
            val status = if (result.ok) MessageStatus.Sent else MessageStatus.Failed
            val eventId = result.eventId
            if (eventId.isNullOrBlank()) {
                chat.replace(messageId) { it.copy(status = status) }
            } else {
                chat.replaceId(messageId, eventId) { it.copy(status = status) }
            }
            _uiState.update { it.copy(sending = false, status = result.message ?: "Sent") }
        }
    }

    fun observeBackend(newSettings: AppSettings = sessionSettings ?: settings.value) {
        if (newSettings.backendBaseUrl.isBlank() || newSettings.pokeUserId.isBlank()) return
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _uiState.update { it.copy(status = "Listening for Poke events...") }
            runCatching {
                backend.streamEvents(newSettings).collect(chat::add)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                val detail = error.message?.takeIf { message -> message.isNotBlank() } ?: "unknown error"
                _uiState.update { it.copy(status = "Event stream disconnected: $detail") }
            }
        }
    }

    fun performAction(messageId: String, action: RichAction) {
        viewModelScope.launch {
            val result = runCatching {
                backend.completeAction(sessionSettings ?: settings.value, messageId, action)
            }.getOrElse { SendResult(false, it.message ?: "Action failed") }
            _uiState.update { it.copy(status = result.message ?: "Action sent: ${action.label}") }
            if (result.ok) {
                chat.add(
                    ChatMessage(
                        id = "action-$messageId-${action.id}-${System.currentTimeMillis()}",
                        text = "Action: ${action.label}",
                        direction = MessageDirection.Outbound,
                        status = MessageStatus.Sent,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun clearConversation() {
        chat.clear()
        _uiState.update { it.copy(status = "Conversation cleared") }
        sessionSettings?.let(::observeBackend)
    }

    private suspend fun connect(newSettings: AppSettings, saved: Boolean) {
        sessionSettings = newSettings
        _uiState.update {
            it.copy(
                status = if (saved) "Settings saved" else "Using local QA backend",
                setupSaved = saved
            )
        }

        val registered = runCatching { backend.registerDevice(newSettings, android.os.Build.MODEL) }.getOrDefault(false)
        val healthy = runCatching { backend.health(newSettings.backendBaseUrl) }.getOrDefault(false)
        val status = when {
            newSettings.backendBaseUrl.isBlank() -> "Direct-send mode ready"
            registered && healthy -> "Backend connected"
            healthy -> "Backend reachable, device registration failed"
            else -> "Backend not reachable"
        }
        _uiState.update {
            it.copy(
                backendConnected = registered && healthy,
                status = status
            )
        }
        observeBackend(newSettings)
    }
}
