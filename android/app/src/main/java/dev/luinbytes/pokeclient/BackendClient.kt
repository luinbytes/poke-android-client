package dev.luinbytes.pokeclient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.util.UUID

class BackendClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun registerDevice(settings: AppSettings, deviceName: String): Boolean = withContext(Dispatchers.IO) {
        if (settings.backendBaseUrl.isBlank() || settings.pokeUserId.isBlank()) return@withContext false
        val body = JSONObject()
            .put("pokeUserId", settings.pokeUserId)
            .put("deviceName", deviceName)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${settings.backendBaseUrl.trimEnd('/')}/api/devices/register")
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { it.isSuccessful }
    }

    suspend fun sendViaBackend(settings: AppSettings, text: String): SendResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("pokeUserId", settings.pokeUserId)
            .put("text", text)
            .put("correlationId", UUID.randomUUID().toString())
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${settings.backendBaseUrl.trimEnd('/')}/api/messages/send")
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            SendResult(response.isSuccessful, if (response.isSuccessful) null else "Backend returned HTTP ${response.code}")
        }
    }

    fun streamEvents(settings: AppSettings): Flow<ChatMessage> = callbackFlow {
        if (settings.backendBaseUrl.isBlank() || settings.pokeUserId.isBlank()) {
            close()
            return@callbackFlow
        }
        val request = Request.Builder()
            .url("${settings.backendBaseUrl.trimEnd('/')}/api/events/stream?pokeUserId=${settings.pokeUserId}")
            .addHeader("Accept", "text/event-stream")
            .build()
        val call = httpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful || it.body == null) {
                        close(IOException("SSE HTTP ${it.code}"))
                        return
                    }
                    readSse(it.body!!.charStream().buffered(), ::trySend)
                }
            }
        })
        awaitClose { call.cancel() }
    }

    private fun readSse(reader: BufferedReader, emit: (ChatMessage) -> Unit) {
        val data = StringBuilder()
        reader.useLines { lines ->
            for (line in lines) {
                if (line.startsWith("data:")) {
                    data.append(line.removePrefix("data:").trim())
                } else if (line.isBlank() && data.isNotEmpty()) {
                    parseMessage(data.toString())?.let(emit)
                    data.clear()
                }
            }
        }
    }

    private fun parseMessage(raw: String): ChatMessage? {
        val obj = JSONObject(raw)
        val payload = obj.optJSONObject("payload") ?: obj
        val text = payload.optString("text", payload.optString("summary", raw))
        return ChatMessage(
            id = obj.optString("eventId", UUID.randomUUID().toString()),
            text = text,
            direction = MessageDirection.Inbound,
            status = MessageStatus.Received,
            createdAt = System.currentTimeMillis()
        )
    }
}
