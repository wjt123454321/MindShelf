package com.example.mindshelf.data.ai

import com.example.mindshelf.BuildConfig
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
import javax.inject.Inject
import javax.inject.Singleton

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class LlmRoundResult(
    val content: String,
    val reasoning: String,
    val toolCalls: List<LlmToolCall>,
)

/** 经 Flask JWT 代理调用内置 LLM（无服务端工具循环）。 */
@Singleton
class BuiltinLlmClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val gson = Gson()

    fun completeRound(
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
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}ai/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(LlmStreamDelta.Error("AI 请求失败: ${response.code}"))
                    return@flow
                }
                val source = response.body?.source() ?: run {
                    emit(LlmStreamDelta.Error("空响应"))
                    return@flow
                }
                val toolCalls = mutableMapOf<Int, LlmToolCallBuilder>()
                while (true) {
                    val line = try {
                        if (source.exhausted()) break
                        source.readUtf8Line()
                    } catch (_: EOFException) {
                        break
                    } ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    val chunk = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull()
                        ?: continue
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
                            val builder = toolCalls.getOrPut(idx) { LlmToolCallBuilder() }
                            tc.optionalString("id")?.let { builder.id = it }
                            tc.get("function")?.asJsonObject?.let { fn ->
                                fn.optionalString("name")?.let { builder.name = it }
                                fn.optionalString("arguments")?.let { builder.arguments += it }
                            }
                        }
                    }
                }
                val calls = toolCalls.toSortedMap().values.mapNotNull { it.build() }
                emit(LlmStreamDelta.RoundComplete(calls))
            }
        } catch (e: IOException) {
            emit(LlmStreamDelta.Error(e.message ?: "连接已断开"))
        }
    }.flowOn(Dispatchers.IO)

    private class LlmToolCallBuilder {
        var id: String = ""
        var name: String = ""
        var arguments: String = ""
        fun build(): LlmToolCall? {
            if (name.isBlank()) return null
            return LlmToolCall(id.ifBlank { "call_${name}" }, name, arguments.ifBlank { "{}" })
        }
    }
}

sealed class LlmStreamDelta {
    data class Reasoning(val text: String) : LlmStreamDelta()
    data class Content(val text: String) : LlmStreamDelta()
    data class RoundComplete(val toolCalls: List<LlmToolCall>) : LlmStreamDelta()
    data class Error(val message: String) : LlmStreamDelta()
}
