package com.example.mindshelf.data.ai

/**
 * 供 LLM 使用的自定义页面工具说明与 few-shot。
 * 与 [com.example.mindshelf.data.page.PageSchemaValidator] 校验规则保持一致。
 */
object CustomPageToolGuide {

    /** 写入 system prompt 的完整说明（含 few-shot）。 */
    fun systemPromptSection(): String = buildString {
        appendLine()
        appendLine("=== mutate_custom_page 专用规范（必须严格遵守，否则校验失败） ===")
        appendLine()
        append(rules())
        appendLine()
        appendLine("--- 正确示例 1：待办页面（最常用） ---")
        appendLine(EXAMPLE_TODO)
        appendLine()
        appendLine("--- 正确示例 2：标题 + 表格 ---")
        appendLine(EXAMPLE_TABLE)
        appendLine()
        appendLine("--- 正确示例 3：嵌入已有笔记（note_id 须来自 search_notes） ---")
        appendLine(EXAMPLE_NOTE_EMBED)
        appendLine()
        appendLine("--- 正确示例 4：更新已有页面 ---")
        appendLine(EXAMPLE_UPDATE)
        appendLine()
        appendLine("--- 常见错误（禁止） ---")
        append(commonMistakes())
    }

    /** 工具 JSON Schema 中的 function.description（摘要 + 指向规范）。 */
    fun toolDescription(): String = """
        创建/更新/删除 Schema 驱动的自定义页面（需用户确认）。
        硬性要求：schema_json 必须有 version=1 与 root；root.type 只能是 Column；
        子组件 type 只能是 Column|TextBlock|TodoList|Checklist|SimpleTable|NoteEmbed（禁止 div/span/p 等 HTML 标签名）；
        TextBlock 的 props 必须含 text 或 binding；TodoList/Checklist/SimpleTable/NoteEmbed 的 props.binding 必须在 data_bindings 中有同名键。
        调用前请对照 system 提示中的 few-shot 示例构造 JSON。
    """.trimIndent().replace('\n', ' ')

    private fun rules(): String = """
        【工具调用】mutate_custom_page
        - action: create | update | delete
        - create 必填：name、schema_json、data_bindings（可为空 {}，但有 TodoList 等组件时必须填对应 binding）
        - update 必填：page_id；只传要改的字段；修改前先用 search_custom_pages(page_id=...) 读取现状
        - delete 必填：page_id

        【schema_json 结构】必须是对象，且恰好两层：version + root
        {
          "version": 1,
          "root": { "type": "Column", "children": [ ... ] }
        }
        - version 必须是数字 1（不是 "1" 字符串）
        - 必须有 root，不能把 Column 直接放在顶层
        - root.type 必须是 "Column"（不是 div、container、Page、Box）
        - 子节点数组字段名必须是 children（不是 childeren、childs、items）

        【允许的组件 type】仅以下 6 种，大小写敏感：
        | type | props | 说明 |
        | Column | children[] | 唯一布局容器，root 必须是 Column |
        | TextBlock | binding（推荐）或 text | 正文在 data_bindings.{kind:text}；props.text 仅兼容旧页 |
        | TodoList | binding | 可勾选待办，数据在 data_bindings[binding] |
        | Checklist | binding | 同 TodoList |
        | SimpleTable | binding | 表格，数据在 data_bindings[binding] |
        | NoteEmbed | binding | 只读嵌入笔记 |

        【禁止】使用 HTML/Web 组件名：div、span、p、h1、section、View、Container 等一律无效。

        【TextBlock 写法】
        推荐：schema { "type": "TextBlock", "props": { "binding": "intro" } }
        data_bindings: "intro": { "kind": "text", "text": "今日待办" }
        兼容（会自动迁移）：props.text 静态字符串

        【TodoList / Checklist 写法】
        schema 中：{ "type": "TodoList", "props": { "binding": "todos" } }
        data_bindings 中必须有同名键 todos：
        "todos": { "kind": "checklist", "items": [ { "id": "1", "text": "任务", "done": false } ] }
        - items 每项必须有 id（字符串）、text、done（布尔）
        - id 在同一 checklist 内唯一

        【SimpleTable 写法】
        "stats": { "kind": "table", "headers": ["列1","列2"], "rows": [["a","b"]] }

        【NoteEmbed 写法】
        先用 search_notes 拿到 note_id，再：
        schema: { "type": "NoteEmbed", "props": { "binding": "notes_ref" } }
        data_bindings: "notes_ref": { "kind": "note", "note_id": "<真实 uuid>" }

        【pinned】可选；true 时固定到底栏（全局最多 1 个）。用户未要求时不要擅自 pinned=true。

        【客户端自动修正】以下写法会被自动转换为合法结构，但仍请尽量按规范输出：
        - TextBlock 的 text 写在节点顶层 → 移入 props.text
        - TodoList 内联 children/items（含 title/checked）→ 提取到 data_bindings.checklist
        - div/container → Column
        - 缺少 data_bindings → 从内联待办自动生成 todos 键
    """.trimIndent()

    private fun commonMistakes(): String = """
        ✗ schema_json 缺少 root → 必须 { "version": 1, "root": { "type": "Column", "children": [] } }
        ✗ root.type 为 div → 改为 Column
        ✗ 字段 childeren → 改为 children
        ✗ TextBlock 无 props.text → 加上 "props": { "text": "标题" }
        ✗ TodoList 有 binding 但 data_bindings 无对应键 → 两处 binding 名必须一致
        ✗ 把 checklist items 写在 schema 里 → items 只放在 data_bindings，schema 只写 binding 名
        ✗ data_bindings 缺 kind → 必须 checklist | note | table | text
        ✗ TextBlock 把长文写在 props.text → 应使用 props.binding + data_bindings.{kind:text}
    """.trimIndent()

    private val EXAMPLE_TODO = """
        {
          "action": "create",
          "name": "今日待办",
          "schema_json": {
            "version": 1,
            "root": {
              "type": "Column",
              "children": [
                { "type": "TextBlock", "props": { "binding": "intro" } },
                { "type": "TodoList", "props": { "binding": "todos" } }
              ]
            }
          },
          "data_bindings": {
            "intro": { "kind": "text", "text": "今日待办" },
            "todos": {
              "kind": "checklist",
              "items": [
                { "id": "1", "text": "写报告", "done": false },
                { "id": "2", "text": "回复邮件", "done": false }
              ]
            }
          },
          "pinned": false
        }
    """.trimIndent()

    private val EXAMPLE_TABLE = """
        {
          "action": "create",
          "name": "学习进度",
          "schema_json": {
            "version": 1,
            "root": {
              "type": "Column",
              "children": [
                { "type": "TextBlock", "props": { "binding": "intro" } },
                { "type": "SimpleTable", "props": { "binding": "progress" } }
              ]
            }
          },
          "data_bindings": {
            "intro": { "kind": "text", "text": "本周进度" },
            "progress": {
              "kind": "table",
              "headers": ["科目", "进度"],
              "rows": [
                ["Kotlin", "80%"],
                ["Flask", "60%"]
              ]
            }
          }
        }
    """.trimIndent()

    private val EXAMPLE_NOTE_EMBED = """
        {
          "action": "create",
          "name": "读书笔记",
          "schema_json": {
            "version": 1,
            "root": {
              "type": "Column",
              "children": [
                { "type": "TextBlock", "props": { "binding": "intro" } },
                { "type": "NoteEmbed", "props": { "binding": "notes_ref" } }
              ]
            }
          },
          "data_bindings": {
            "intro": { "kind": "text", "text": "摘录" },
            "notes_ref": {
              "kind": "note",
              "note_id": "00000000-0000-0000-0000-000000000001"
            }
          }
        }
    """.trimIndent()

    private val EXAMPLE_UPDATE = """
        {
          "action": "update",
          "page_id": "<已有页面 uuid>",
          "data_bindings": {
            "todos": {
              "kind": "checklist",
              "items": [
                { "id": "1", "text": "写报告", "done": true },
                { "id": "2", "text": "回复邮件", "done": false },
                { "id": "3", "text": "整理笔记", "done": false }
              ]
            }
          }
        }
    """.trimIndent()

    /** 校验失败时附加可操作的修复提示，便于模型在下一轮 tool 调用中改正。 */
    fun enrichValidationError(message: String): String {
        val hint = when {
            message.contains("缺少 root") ->
                "schema_json 必须是 { \"version\": 1, \"root\": { \"type\": \"Column\", \"children\": [...] } }，不能把 Column 放在顶层。"
            message.contains(".type 未知") ->
                "type 只能是 Column、TextBlock、TodoList、Checklist、SimpleTable、NoteEmbed；不要用 div/span/p/h1/View 等 HTML 名。"
            message.contains("TextBlock 需要 text 或 binding") ->
                "TextBlock 示例：{ \"type\": \"TextBlock\", \"props\": { \"text\": \"标题\" } }，不能省略 props。"
            message.contains("需要 props.binding") ->
                "TodoList/Checklist/SimpleTable/NoteEmbed 必须写 \"props\": { \"binding\": \"键名\" }，并在 data_bindings 中定义同名键。"
            message.contains("kind 无效") ->
                "data_bindings 每项必须有 kind：text（含 text）、checklist（含 items[]）、note（含 note_id）、table（含 headers[] 与 rows[][]）。"
            message.contains("TextBlock 需要 text 或 binding") ->
                "TextBlock 推荐 props.binding + data_bindings.{kind:text,text:...}，见 few-shot 示例。"
            message.contains("缺少 note_id") ->
                "NoteEmbed 的 binding 对应 data_bindings 项须为 { \"kind\": \"note\", \"note_id\": \"<search_notes 返回的 id>\" }。"
            else -> "请对照 few-shot 示例重新构造 schema_json 与 data_bindings。"
        }
        return "$message。修复：$hint"
    }
}
