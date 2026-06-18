package com.example.mindshelf.data.page

object PageSchemaValidator {
    private val allowedNodeTypes = setOf(
        "Column", "TextBlock", "TodoList", "Checklist", "SimpleTable", "NoteEmbed",
    )
    private val bindingComponents = setOf("TodoList", "Checklist", "SimpleTable", "NoteEmbed")
    private val allowedBindingKinds = setOf("checklist", "note", "table", "text")

    fun validateSchema(schema: Map<String, Any?>): String? {
        if (schema.isEmpty()) return null
        if ((schema["version"] as? Number)?.toInt() != 1) {
            return "schema_json.version 必须为 1"
        }
        val root = schema["root"] as? Map<*, *> ?: return "schema_json 缺少 root"
        return validateNode(root)
    }

    @Suppress("UNCHECKED_CAST")
    fun validateBindings(bindings: Map<String, Any?>): String? {
        for ((key, value) in bindings) {
            val binding = value as? Map<String, Any?> ?: return "data_bindings.$key 必须是对象"
            val kind = binding["kind"]?.toString()
            if (kind !in allowedBindingKinds) {
                return "data_bindings.$key.kind 无效"
            }
            when (kind) {
                "checklist" -> {
                    val items = binding["items"]
                    if (items != null && items !is List<*>) {
                        return "data_bindings.$key.items 必须是数组"
                    }
                }
                "note" -> {
                    if (binding["note_id"].toString().isBlank()) {
                        return "data_bindings.$key 缺少 note_id"
                    }
                }
                "table" -> {
                    val headers = binding["headers"]
                    val rows = binding["rows"]
                    if (headers != null && headers !is List<*>) {
                        return "data_bindings.$key.headers 必须是数组"
                    }
                    if (rows != null && rows !is List<*>) {
                        return "data_bindings.$key.rows 必须是数组"
                    }
                }
                "text" -> {
                    if (binding["text"] == null && binding["value"] == null) {
                        return "data_bindings.$key 缺少 text"
                    }
                }
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun validateNode(node: Map<*, *>, path: String = "root"): String? {
        val type = node["type"]?.toString()
        if (type !in allowedNodeTypes) return "$path.type 未知: $type"
        val props = node["props"] as? Map<*, *>

        if (type == "Column") {
            val children = node["children"] as? List<*> ?: return null
            children.forEachIndexed { index, child ->
                val childMap = child as? Map<*, *> ?: return "$path.children[$index] 必须是对象"
                validateNode(childMap, "$path.children[$index]")?.let { return it }
            }
            return null
        }

        if (type == "TextBlock") {
            val text = props?.get("text")?.toString().orEmpty()
            val binding = props?.get("binding")?.toString().orEmpty()
            if (text.isBlank() && binding.isBlank()) {
                return "$path TextBlock 需要 text 或 binding"
            }
            return null
        }

        if (type in bindingComponents) {
            val binding = props?.get("binding")?.toString().orEmpty()
            if (binding.isBlank()) return "$path $type 需要 props.binding"
        }
        return null
    }

    fun summarizeComponentTypes(schema: Map<String, Any?>): String {
        val types = mutableListOf<String>()
        collectTypes(schema["root"] as? Map<*, *>, types)
        return types.distinct().joinToString("、").ifBlank { "Column" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectTypes(node: Map<*, *>?, out: MutableList<String>) {
        if (node == null) return
        node["type"]?.toString()?.let { out.add(it) }
        if (node["type"] == "Column") {
            (node["children"] as? List<*>)?.forEach { child ->
                collectTypes(child as? Map<*, *>, out)
            }
        }
    }

    fun defaultSchema(): Map<String, Any?> = mapOf(
        "version" to 1,
        "root" to mapOf(
            "type" to "Column",
            "children" to listOf(
                mapOf("type" to "TextBlock", "props" to mapOf("text" to "新页面")),
            ),
        ),
    )
}
