package dev.luinbytes.pokeclient

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SettingsStore(private val context: Context) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val state = MutableStateFlow(AppSettings())
    val settings: Flow<AppSettings> = state

    suspend fun load() = withContext(Dispatchers.IO) {
        state.value = read()
    }

    suspend fun save(settings: AppSettings) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(BACKEND_BASE_URL, settings.backendBaseUrl)
                .putString(POKE_USER_ID, settings.pokeUserId)
                .commit()
            state.value = settings
        }
    }

    private fun read(): AppSettings {
        return AppSettings(
            backendBaseUrl = prefs.getString(BACKEND_BASE_URL, "").orEmpty(),
            pokeUserId = prefs.getString(POKE_USER_ID, "").orEmpty()
        )
    }

    companion object {
        private const val BACKEND_BASE_URL = "backend_base_url"
        private const val POKE_USER_ID = "poke_user_id"
    }
}
