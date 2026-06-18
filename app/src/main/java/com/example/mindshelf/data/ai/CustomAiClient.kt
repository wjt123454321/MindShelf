package com.example.mindshelf.data.ai

import com.example.mindshelf.data.repository.AiProvider
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

    fun completeRound(
        provider: AiProvider,
        apiKey: String,
        messages: JsonArray,
        model: String,
        tools: JsonArray?,
    ): Flow<LlmStreamDelta> = flow {
        val body = JsonObject().apply {
            addProperty("model", model)
            add("messages", messages)
            addProperty("stream", true)
            if (tools != null && tools.size() > 0) add("tools", tools)
        }
        val base = provider.baseUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$base/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(LlmStreamDelta.Error("自定义 API 请求失败: ${response.code}"))
                    return@flow
                }
                val source = response.body?.source() ?: run {
                    emit(LlmStreamDelta.Error("空响应"))
                    return@flow
                }
                val toolCalls = mutableMapOf<Int, ToolCallBuilder>()
                readSseLines(source).forEach { line ->
                    if (!line.startsWith("data: ")) return@forEach
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") return@forEach
                    val chunk = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull()
                        ?: return@forEach
                    chunk.parseStreamError()?.let {
                        emit(LlmStreamDelta.Error(it))
                        return@flow
                    }
                    val choice = chunk.get("choices")?.asJsonArray?.firstOrNull()?.asJsonObject
                    val delta = choice?.get("delta")?.asJsonObject
                    if (delta != null) {
                        delta.optionalString("reasoning_content")?.let {
                            emit(LlmStreamDelta.Reasoning(it))
                        }
                        delta.optionalString("content")?.let {
                            emit(LlmStreamDelta.Content(it))
                        }
                        delta.getAsJsonArray("tool_calls")?.forEach { tcEl ->
                            val tc = tcEl.asJsonObject
                            val idx = tc.get("index")?.asInt ?: 0
                            val builder = toolCalls.getOrPut(idx) { ToolCallBuilder() }
                            tc.optionalString("id")?.let { builder.id = it }
                            tc.get("function")?.asJsonObject?.let { fn ->
                                fn.optionalString("name")?.let { builder.name = it }
                                fn.optionalString("arguments")?.let { builder.arguments += it }
                            }
                        }
                    }
                }
                emit(LlmStreamDelta.RoundComplete(toolCalls.toSortedMap().values.mapNotNull { it.build() }))
            }
        } catch (e: IOException) {
            emit(LlmStreamDelta.Error(e.message ?: "连接已断开"))
        }
    }.flowOn(Dispatchers.IO)

    private class ToolCallBuilder {
        var id: String = ""
        var name: String = ""
        var arguments: String = ""
        fun build(): LlmToolCall? {
            if (name.isBlank()) return null
            return LlmToolCall(id.ifBlank { "call_$name" }, name, arguments.ifBlank { "{}" })
        }
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
