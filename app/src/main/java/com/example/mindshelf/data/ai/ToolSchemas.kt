package com.example.mindshelf.data.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject

object ToolSchemas {
    private val WRITE_TOOLS = setOf("mutate_knowledge_base", "mutate_note")

    fun isWriteTool(name: String): Boolean = name in WRITE_TOOLS

    fun schemas(includeWebSearch: Boolean): JsonArray {
        val list = JsonArray()
        list.add(searchKbSchema())
        list.add(searchNotesSchema())
        list.add(mutateKbSchema())
        list.add(mutateNoteSchema())
        if (includeWebSearch) list.add(webSearchSchema())
        return list
    }

    private fun searchKbSchema(): JsonObject = functionSchema(
        "search_knowledge_bases",
        "搜索或列出用户在本应用内保存的知识库（不含互联网内容）",
        JsonObject().apply {
            add("type", gsonPrimitive("object"))
            add("properties", JsonObject().apply {
                add("query", prop("string", "名称关键词，留空则列出全部"))
            })
        },
    )

    private fun searchNotesSchema(): JsonObject = functionSchema(
        "search_notes",
        "搜索或读取用户在本应用内保存的笔记（不含新闻或网页）",
        JsonObject().apply {
            add("type", gsonPrimitive("object"))
            add("properties", JsonObject().apply {
                add("query", prop("string", "标题或正文关键词"))
                add("note_id", prop("string", "指定笔记 ID 时返回全文"))
                add("kb_id", prop("string", "限定在某个知识库内搜索"))
            })
        },
    )

    private fun mutateKbSchema(): JsonObject = functionSchema(
        "mutate_knowledge_base",
        "创建、更新或删除知识库（需用户确认后执行）",
        JsonObject().apply {
            add("type", gsonPrimitive("object"))
            add("properties", JsonObject().apply {
                add("action", prop("string", "create/update/delete"))
                add("kb_id", prop("string", ""))
                add("name", prop("string", ""))
                add("description", prop("string", ""))
            })
            add("required", JsonArray().apply { add("action") })
        },
    )

    private fun mutateNoteSchema(): JsonObject = functionSchema(
        "mutate_note",
        "创建、更新或删除笔记（需用户确认后执行）",
        JsonObject().apply {
            add("type", gsonPrimitive("object"))
            add("properties", JsonObject().apply {
                add("action", prop("string", "create/update/delete"))
                add("note_id", prop("string", ""))
                add("title", prop("string", ""))
                add("content", prop("string", ""))
                add("knowledge_base_ids", JsonObject().apply {
                    add("type", gsonPrimitive("array"))
                    add("items", JsonObject().apply { add("type", gsonPrimitive("string")) })
                })
            })
            add("required", JsonArray().apply { add("action") })
        },
    )

    private fun webSearchSchema(): JsonObject = functionSchema(
        "web_search",
        "搜索互联网获取新闻、时事、百科等外部信息",
        JsonObject().apply {
            add("type", gsonPrimitive("object"))
            add("properties", JsonObject().apply {
                add("query", prop("string", "搜索关键词"))
            })
            add("required", JsonArray().apply { add("query") })
        },
    )

    private fun functionSchema(name: String, description: String, parameters: JsonObject): JsonObject =
        JsonObject().apply {
            add("type", gsonPrimitive("function"))
            add("function", JsonObject().apply {
                add("name", gsonPrimitive(name))
                add("description", gsonPrimitive(description))
                add("parameters", parameters)
            })
        }

    private fun prop(type: String, description: String): JsonObject =
        JsonObject().apply {
            add("type", gsonPrimitive(type))
            add("description", gsonPrimitive(description))
        }

    private fun gsonPrimitive(value: String) = com.google.gson.JsonPrimitive(value)
}
