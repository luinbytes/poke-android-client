package dev.luinbytes.pokeclient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

class BackendClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val streamClient = httpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun health(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext false
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/health")
            .get()
            .build()
        runCatching { httpClient.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

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
            val body = response.body?.string().orEmpty()
            val eventId = runCatching { JSONObject(body).optJSONObject("event")?.optString("eventId") }.getOrNull()
            SendResult(
                response.isSuccessful,
                if (response.isSuccessful) null else "Backend returned HTTP ${response.code}${body.takeIf { it.isNotBlank() }?.let { ": ${it.take(160)}" }.orEmpty()}",
                eventId
            )
        }
    }

    suspend fun completeAction(settings: AppSettings, messageId: String, action: RichAction): SendResult = withContext(Dispatchers.IO) {
        if (settings.backendBaseUrl.isBlank() || settings.pokeUserId.isBlank()) {
            return@withContext SendResult(false, "Backend URL and Poke user ID are required for actions")
        }
        val payload = JSONObject()
            .put("pokeUserId", settings.pokeUserId)
            .put("messageId", messageId)
            .put("actionId", action.id)
            .put("type", action.type)
            .put("label", action.label)
            .put("payload", JSONObject(action.payload))
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${settings.backendBaseUrl.trimEnd('/')}/api/actions/complete")
            .post(payload)
            .build()
        httpClient.newCall(request).execute().use { response ->
            SendResult(response.isSuccessful, if (response.isSuccessful) null else "Action failed with HTTP ${response.code}")
        }
    }

    fun streamEvents(settings: AppSettings): Flow<ChatMessage> = callbackFlow {
        if (settings.backendBaseUrl.isBlank() || settings.pokeUserId.isBlank()) {
            close()
            return@callbackFlow
        }
        val encodedUser = URLEncoder.encode(settings.pokeUserId, StandardCharsets.UTF_8.name())
        val request = Request.Builder()
            .url("${settings.backendBaseUrl.trimEnd('/')}/api/events/stream?pokeUserId=$encodedUser")
            .addHeader("Accept", "text/event-stream")
            .build()
        val call = streamClient.newCall(request)
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
        val type = obj.optString("eventType", "message")
        val direction = payload.optString("direction")
        val deliveryState = obj.optString("deliveryState")
        val actions = mutableListOf<RichAction>()
        payload.optJSONArray("actions")?.let { array ->
            for (index in 0 until array.length()) {
                val action = array.optJSONObject(index) ?: continue
                val payloadObject = action.optJSONObject("payload")
                actions += RichAction(
                    id = action.optString("id", UUID.randomUUID().toString()),
                    type = action.optString("type", "quick_reply"),
                    label = action.optString("label", "Action"),
                    payload = payloadObject?.keys()?.asSequence()?.associateWith { key -> payloadObject.optString(key) }.orEmpty()
                )
            }
        }
        return ChatMessage(
            id = obj.optString("eventId", UUID.randomUUID().toString()),
            text = text,
            direction = when {
                direction == "outbound" -> MessageDirection.Outbound
                type == "log" || type == "progress" || type == "status" -> MessageDirection.System
                else -> MessageDirection.Inbound
            },
            status = when (deliveryState) {
                "queued" -> MessageStatus.Sending
                "delivered" -> if (direction == "outbound") MessageStatus.Sent else MessageStatus.Received
                "failed" -> MessageStatus.Failed
                else -> MessageStatus.Received
            },
            createdAt = runCatching { Instant.parse(obj.optString("createdAt")).toEpochMilli() }.getOrDefault(System.currentTimeMillis()),
            actions = actions
        )
    }
}
