package com.example.mindshelf.data.ai

import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.repository.AiProvider
import com.example.mindshelf.data.repository.ChatStreamEvent
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 客户端直连 OpenAI 兼容 API（Key 不出本机）。 */
@Singleton
class CustomAiClient @Inject constructor() {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun streamChat(
        provider: AiProvider,
        apiKey: String,
        history: List<MessageDto>,
        userContent: String,
    ): Flow<ChatStreamEvent> = flow {
        val messages = JsonArray().apply {
            add(jsonMessage("system", "你是 MindShelf 知识库助手，简洁准确地回答用户问题。"))
            history.filter { it.role == "user" || it.role == "assistant" }.forEach { msg ->
                add(jsonMessage(msg.role, msg.content.orEmpty()))
            }
            if (history.lastOrNull()?.role != "user" || history.lastOrNull()?.content != userContent) {
                add(jsonMessage("user", userContent))
            }
        }

        val body = JsonObject().apply {
            addProperty("model", provider.model)
            add("messages", messages)
            addProperty("stream", true)
        }

        val request = Request.Builder()
            .url("${provider.baseUrl}/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ChatStreamEvent.Error("自定义 API 请求失败: ${response.code}"))
                    return@flow
                }
                val source = response.body?.source() ?: run {
                    emit(ChatStreamEvent.Error("空响应"))
                    return@flow
                }
                readSseLines(source).forEach { line ->
                    if (!line.startsWith("data: ")) return@forEach
                    val data = line.removePrefix("data: ")
                    if (data.trim() == "[DONE]") return@forEach
                    val obj = runCatching { gson.fromJson(data, JsonObject::class.java) }.getOrNull()
                        ?: return@forEach
                    val delta = obj.get("choices")?.asJsonArray?.firstOrNull()?.asJsonObject
                        ?.get("delta")?.asJsonObject ?: return@forEach
                    val reasoning = delta.get("reasoning_content")?.asString.orEmpty()
                    val content = delta.get("content")?.asString.orEmpty()
                    if (reasoning.isNotEmpty()) emit(ChatStreamEvent.ReasoningDelta(reasoning))
                    if (content.isNotEmpty()) emit(ChatStreamEvent.Delta(content))
                }
            }
        } catch (e: IOException) {
            emit(ChatStreamEvent.Error(e.message ?: "连接已断开"))
        }
    }.flowOn(Dispatchers.IO)

    private fun jsonMessage(role: String, content: String): JsonObject =
        JsonObject().apply {
            addProperty("role", role)
            addProperty("content", content)
        }

    private fun readSseLines(source: BufferedSource): Sequence<String> = sequence {
        while (true) {
            val line = try {
                if (source.exhausted()) break
                source.readUtf8Line()
            } catch (_: EOFException) {
                break
            } ?: break
            yield(line)
        }
    }
}
