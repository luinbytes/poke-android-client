package dev.luinbytes.pokeclient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PokeApiClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val endpoint: String = "https://poke.com/api/v1/inbound/api-message"
) {
    suspend fun send(apiKey: String, text: String): SendResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("message", text).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            SendResult(
                ok = response.isSuccessful,
                message = if (response.isSuccessful) null else "Poke API returned HTTP ${response.code}"
            )
        }
    }
}
