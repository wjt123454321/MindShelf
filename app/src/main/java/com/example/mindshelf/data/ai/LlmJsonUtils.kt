package com.example.mindshelf.data.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun JsonObject.optionalString(key: String): String? {
    val element: JsonElement = get(key) ?: return null
    if (element.isJsonNull) return null
    return element.asString.takeIf { it.isNotEmpty() }
}

internal fun JsonObject.parseStreamError(): String? {
    val errObj = get("error")?.takeIf { it.isJsonObject }?.asJsonObject
    if (errObj != null) {
        return errObj.optionalString("message") ?: errObj.optionalString("code")
    }
    return optionalString("error") ?: optionalString("message")
}
