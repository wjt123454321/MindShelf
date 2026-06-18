package com.example.mindshelf.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ToolContentSnapshot(
    val title: String? = null,
    val content: String? = null,
    val name: String? = null,
    val description: String? = null,
)

data class ToolPreview(
    val action: String = "",
    @SerializedName("note_id") val noteId: String? = null,
    @SerializedName("kb_id") val kbId: String? = null,
    val title: String? = null,
    val content: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerializedName("page_id") val pageId: String? = null,
    @SerializedName("schema_summary") val schemaSummary: String? = null,
    val pinned: Boolean? = null,
    val before: ToolContentSnapshot? = null,
    val after: ToolContentSnapshot? = null,
)
