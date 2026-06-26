package dev.luinbytes.pokeclient

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PokeViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)
    private val pokeApi = PokeApiClient()
    private val backend = BackendClient()
    private val chat = ChatRepository()

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )
    val messages: StateFlow<List<ChatMessage>> = chat.messages

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsStore.save(settings)
            backend.registerDevice(settings, android.os.Build.MODEL)
            observeBackend(settings)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val message = chat.outboundDraft(trimmed)
        viewModelScope.launch {
            val current = settings.value
            val result = if (current.backendBaseUrl.isNotBlank() && current.pokeUserId.isNotBlank()) {
                backend.sendViaBackend(current, trimmed)
            } else {
                pokeApi.send(current.pokeApiKey, trimmed)
            }
            chat.replace(message.id) {
                it.copy(status = if (result.ok) MessageStatus.Sent else MessageStatus.Failed)
            }
        }
    }

    fun observeBackend(settings: AppSettings = this.settings.value) {
        if (settings.backendBaseUrl.isBlank() || settings.pokeUserId.isBlank()) return
        viewModelScope.launch {
            backend.streamEvents(settings).collect(chat::add)
        }
    }
}
