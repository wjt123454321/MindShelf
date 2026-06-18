package com.example.mindshelf.data.page

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 将 LLM 常见的非规范页面 JSON 修正为 [PageSchemaValidator] 可接受的 v1 结构。
 * 典型误输出：TextBlock 的 text 在顶层、TodoList 内联 children/items、缺 data_bindings。
 */
object PageSchemaNormalizer {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    data class Result(
        val schema: Map<String, Any?>,
        val bindings: Map<String, Any?>,
        val fixes: List<String> = emptyList(),
    )

    fun normalize(schemaInput: Any?, bindingsInput: Any?): Result {
        val fixes = mutableListOf<String>()
        var schema = parseMap(schemaInput) ?: PageSchemaValidator.defaultSchema()
        var bindings = parseMap(bindingsInput) ?: emptyMap()

        schema = wrapRootIfNeeded(schema, fixes)
        val extracted = mutableMapOf<String, Any?>()
        schema = normalizeSchemaMap(schema, extracted, fixes)
        bindings = bindings.toMutableMap().apply { putAll(extracted) }
        bindings = normalizeBindings(bindings, fixes)

        return Result(schema = schema, bindings = bindings, fixes = fixes)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMap(value: Any?): Map<String, Any?>? {
        return when (value) {
            null -> null
            is Map<*, *> -> deepStringKeyMap(value)
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isEmpty() || trimmed == "{}") {
                    emptyMap()
                } else {
                    runCatching { gson.fromJson<Map<String, Any?>>(trimmed, mapType) }.getOrNull()
                }
            }
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun deepStringKeyMap(raw: Map<*, *>): Map<String, Any?> =
        raw.entries.associate { (k, v) ->
            k.toString() to when (v) {
                is Map<*, *> -> deepStringKeyMap(v)
                is List<*> -> v.map { item ->
                    if (item is Map<*, *>) deepStringKeyMap(item) else item
                }
                else -> v
            }
        }

    private fun wrapRootIfNeeded(schema: Map<String, Any?>, fixes: MutableList<String>): Map<String, Any?> {
        if (schema["root"] != null) {
            val version = (schema["version"] as? Number)?.toInt()
            return if (version == 1) schema else schema.toMutableMap().apply { put("version", 1) }
        }
        fixes.add("补全 schema_json.root 与 version=1")
        val root = when {
            schema["type"] != null -> schema
            schema["children"] != null -> mapOf("type" to "Column", "children" to schema["children"])
            else -> PageSchemaValidator.defaultSchema()["root"]!!
        }
        return mapOf("version" to 1, "root" to root)
    }

    @Suppress("UNCHECKED_CAST")
    private fun normalizeSchemaMap(
        schema: Map<String, Any?>,
        extractedBindings: MutableMap<String, Any?>,
        fixes: MutableList<String>,
    ): Map<String, Any?> {
        val root = schema["root"] as? Map<String, Any?> ?: return schema
        val normalizedRoot = normalizeNode(root, "root", extractedBindings, fixes)?.let { deepStringKeyMap(it) }
            ?: root
        return mapOf("version" to 1, "root" to normalizedRoot)
    }

    @Suppress("UNCHECKED_CAST")
    private fun normalizeNode(
        raw: Map<*, *>,
        path: String,
        extractedBindings: MutableMap<String, Any?>,
        fixes: MutableList<String>,
    ): MutableMap<String, Any?>? {
        val node = deepStringKeyMap(raw).toMutableMap()
        node["type"] = normalizeType(node["type"]?.toString())

        // 统一 children 字段名
        listOf("childeren", "childs", "child", "elements").forEach { wrong ->
            if (node.containsKey(wrong) && !node.containsKey("children")) {
                node["children"] = node.remove(wrong)
                fixes.add("$path: $wrong → children")
            }
        }

        val type = node["type"]?.toString().orEmpty()
        val props = (node["props"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()

        when (type) {
            "TextBlock" -> {
                listOf("text", "title", "content", "label", "value").forEach { key ->
                    node[key]?.toString()?.takeIf { it.isNotBlank() }?.let { text ->
                        if (props["text"].toString().isBlank() && props["binding"].toString().isBlank()) {
                            props["text"] = text
                            fixes.add("$path TextBlock: 将顶层 $key 移入 props.text")
                        }
                        node.remove(key)
                    }
                }
                val staticText = props["text"]?.toString()?.takeIf { it.isNotBlank() }
                if (staticText != null && props["binding"].toString().isBlank()) {
                    val bindingKey = defaultTextBindingKey(path)
                    extractedBindings[bindingKey] = mapOf("kind" to "text", "text" to staticText)
                    props.remove("text")
                    props["binding"] = bindingKey
                    fixes.add("$path TextBlock: 静态 text → data_bindings.$bindingKey")
                }
                if (props.isNotEmpty()) node["props"] = props
            }
            "TodoList", "Checklist" -> {
                if (props["binding"].toString().isBlank()) {
                    val bindingKey = defaultBindingKey(path, "todos")
                    val items = extractChecklistItems(node)
                    if (items.isNotEmpty()) {
                        extractedBindings[bindingKey] = mapOf("kind" to "checklist", "items" to items)
                        props["binding"] = bindingKey
                        node.keys.retainAll(setOf("type", "props"))
                        node["props"] = props
                        fixes.add("$path $type: 内联待办 → data_bindings.$bindingKey")
                    } else {
                        props["binding"] = bindingKey
                        node["props"] = props
                        fixes.add("$path $type: 补全 props.binding=$bindingKey")
                    }
                } else {
                    node["props"] = props
                }
                stripInlineLists(node)
            }
            "SimpleTable" -> {
                if (props["binding"].toString().isBlank()) {
                    val bindingKey = defaultBindingKey(path, "table")
                    val table = extractTable(node)
                    if (table != null) {
                        extractedBindings[bindingKey] = mapOf(
                            "kind" to "table",
                            "headers" to table.first,
                            "rows" to table.second,
                        )
                        props["binding"] = bindingKey
                        node.keys.retainAll(setOf("type", "props"))
                        node["props"] = props
                        fixes.add("$path SimpleTable: 内联表格 → data_bindings.$bindingKey")
                    } else {
                        props["binding"] = bindingKey
                        node["props"] = props
                    }
                }
                stripInlineLists(node)
            }
            "NoteEmbed" -> {
                if (props["binding"].toString().isBlank()) {
                    val noteId = node["note_id"]?.toString()
                        ?: node["noteId"]?.toString()
                        ?: props["note_id"]?.toString()
                    val bindingKey = defaultBindingKey(path, "notes_ref")
                    if (!noteId.isNullOrBlank()) {
                        extractedBindings[bindingKey] = mapOf("kind" to "note", "note_id" to noteId)
                    }
                    props["binding"] = bindingKey
                    node.keys.retainAll(setOf("type", "props"))
                    node["props"] = props
                    fixes.add("$path NoteEmbed: 补全 props.binding")
                }
            }
            "Column" -> {
                val children = node["children"] as? List<*> ?: emptyList<Any>()
                val normalizedChildren = children.mapIndexedNotNull { index, child ->
                    val childMap = child as? Map<*, *> ?: return@mapIndexedNotNull null
                    normalizeNode(childMap, "$path.children[$index]", extractedBindings, fixes)
                }
                node["children"] = normalizedChildren
            }
        }

        // 非法 type 已映射；若仍为 Column 以外且带 children，降级为 Column
        if (type !in setOf("Column", "TextBlock", "TodoList", "Checklist", "SimpleTable", "NoteEmbed") &&
            node["children"] != null
        ) {
            node["type"] = "Column"
            fixes.add("$path: 未知布局 type → Column")
        }

        return node
    }

    private fun normalizeType(raw: String?): String {
        val t = raw?.trim().orEmpty()
        val lower = t.lowercase()
        return when (lower) {
            "div", "container", "box", "view", "page", "stack", "layout", "column", "vstack" -> "Column"
            "p", "h1", "h2", "h3", "h4", "span", "label", "text", "textblock", "heading", "title" -> "TextBlock"
            "todo", "todolist", "todo_list", "tasks", "tasklist" -> "TodoList"
            "checklist", "check_list", "checks" -> "Checklist"
            "table", "simpletable", "grid" -> "SimpleTable"
            "note", "noteembed", "note_embed", "markdown" -> "NoteEmbed"
            else -> when (t) {
                "Column", "TextBlock", "TodoList", "Checklist", "SimpleTable", "NoteEmbed" -> t
                else -> if (t.isBlank()) "Column" else t.replaceFirstChar { it.uppercase() }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractChecklistItems(node: Map<String, Any?>): List<Map<String, Any?>> {
        val raw = node["items"] ?: node["children"] ?: node["tasks"] ?: node["todos"] ?: return emptyList()
        val list = raw as? List<*> ?: return emptyList()
        return list.mapIndexedNotNull { index, item ->
            when (item) {
                is String -> mapOf("id" to "${index + 1}", "text" to item, "done" to false)
                is Map<*, *> -> {
                    val m = deepStringKeyMap(item)
                    val text = m["text"]?.toString()
                        ?: m["title"]?.toString()
                        ?: m["label"]?.toString()
                        ?: m["name"]?.toString()
                        ?: return@mapIndexedNotNull null
                    val done = m["done"] as? Boolean
                        ?: m["checked"] as? Boolean
                        ?: m["completed"] as? Boolean
                        ?: false
                    val id = m["id"]?.toString()?.takeIf { it.isNotBlank() } ?: "${index + 1}"
                    mapOf("id" to id, "text" to text, "done" to done)
                }
                else -> null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractTable(node: Map<String, Any?>): Pair<List<String>, List<List<String>>>? {
        val headersRaw = node["headers"] ?: node["columns"]
        val rowsRaw = node["rows"] ?: node["data"]
        if (headersRaw == null && rowsRaw == null) return null
        val headers = (headersRaw as? List<*>)?.map { it.toString() }.orEmpty()
        val rows = (rowsRaw as? List<*>)?.mapNotNull { row ->
            (row as? List<*>)?.map { it.toString() }
        }.orEmpty()
        if (headers.isEmpty() && rows.isEmpty()) return null
        return headers to rows
    }

    private fun stripInlineLists(node: MutableMap<String, Any?>) {
        node.remove("items")
        node.remove("children")
        node.remove("tasks")
        node.remove("todos")
        node.remove("headers")
        node.remove("rows")
        node.remove("columns")
        node.remove("data")
    }

    private fun defaultBindingKey(path: String, fallback: String): String {
        if (path.contains("todos")) return "todos"
        if (path.contains("table") || path.contains("progress")) return "progress"
        return fallback
    }

    private fun defaultTextBindingKey(path: String): String {
        if (path.contains("intro") || path.contains("title")) return "intro"
        if (path.contains("children[0]")) return "intro"
        return "text_${path.replace(".", "_").replace("[", "_").replace("]", "")}"
    }

    @Suppress("UNCHECKED_CAST")
    private fun normalizeBindings(bindings: Map<String, Any?>, fixes: MutableList<String>): Map<String, Any?> {
        val out = bindings.toMutableMap()
        for ((key, value) in bindings) {
            val binding = value as? Map<String, Any?> ?: continue
            val kind = binding["kind"]?.toString()?.lowercase()
            when (kind) {
                null, "" -> {
                    when {
                        binding.containsKey("items") -> {
                            out[key] = mapOf(
                                "kind" to "checklist",
                                "items" to normalizeChecklistItems(binding["items"]),
                            )
                            fixes.add("data_bindings.$key: 补全 kind=checklist")
                        }
                        binding.containsKey("note_id") || binding.containsKey("noteId") -> {
                            out[key] = mapOf(
                                "kind" to "note",
                                "note_id" to (binding["note_id"] ?: binding["noteId"]).toString(),
                            )
                        }
                        binding.containsKey("headers") || binding.containsKey("rows") -> {
                            out[key] = mapOf(
                                "kind" to "table",
                                "headers" to binding["headers"],
                                "rows" to binding["rows"],
                            )
                        }
                        binding.containsKey("text") || binding.containsKey("value") -> {
                            out[key] = mapOf(
                                "kind" to "text",
                                "text" to (binding["text"] ?: binding["value"]).toString(),
                            )
                            fixes.add("data_bindings.$key: 补全 kind=text")
                        }
                    }
                }
                "checklist" -> {
                    out[key] = mapOf(
                        "kind" to "checklist",
                        "items" to normalizeChecklistItems(binding["items"]),
                    )
                }
            }
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun normalizeChecklistItems(raw: Any?): List<Map<String, Any?>> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapIndexedNotNull { index, item ->
            when (item) {
                is String -> mapOf("id" to "${index + 1}", "text" to item, "done" to false)
                is Map<*, *> -> {
                    val m = deepStringKeyMap(item)
                    val text = m["text"]?.toString() ?: m["title"]?.toString() ?: return@mapIndexedNotNull null
                    mapOf(
                        "id" to (m["id"]?.toString()?.takeIf { it.isNotBlank() } ?: "${index + 1}"),
                        "text" to text,
                        "done" to (m["done"] as? Boolean ?: m["checked"] as? Boolean ?: false),
                    )
                }
                else -> null
            }
        }
    }
}
