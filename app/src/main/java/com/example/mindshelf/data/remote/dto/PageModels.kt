package com.example.mindshelf.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CustomPageDto(
    val id: String,
    val name: String,
    @SerializedName("schema_json") val schemaJson: Map<String, Any?> = emptyMap(),
    @SerializedName("data_bindings") val dataBindings: Map<String, Any?> = emptyMap(),
    val pinned: Boolean = false,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("deleted_at") val deletedAt: Long? = null,
)

data class CreatePageRequest(
    val id: String? = null,
    val name: String? = null,
    @SerializedName("schema_json") val schemaJson: Map<String, Any?>? = null,
    @SerializedName("data_bindings") val dataBindings: Map<String, Any?>? = null,
    val pinned: Boolean? = null,
)

data class UpdatePageRequest(
    val name: String? = null,
    @SerializedName("schema_json") val schemaJson: Map<String, Any?>? = null,
    @SerializedName("data_bindings") val dataBindings: Map<String, Any?>? = null,
    val pinned: Boolean? = null,
)

data class SyncPagePushItem(
    val id: String,
    val name: String,
    @SerializedName("schema_json") val schemaJson: Map<String, Any?>,
    @SerializedName("data_bindings") val dataBindings: Map<String, Any?>,
    val pinned: Boolean = false,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("deleted_at") val deletedAt: Long? = null,
)
