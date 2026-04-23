package org.openbase.bco.gateway.homeassistant.util

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.reflect.KClass

object JsonUtils {
    val gson: Gson = Gson()
}

infix fun <T: Any> JsonElement.parse(klass: KClass<T>): T = Gson().fromJson(this, klass.java)

/**
 * Build a [JsonObject] from key-value pairs, automatically skipping null values.
 */
fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject().apply {
    for ((key, value) in pairs) {
        when (value) {
            null -> {}
            is String -> addProperty(key, value)
            is Number -> addProperty(key, value)
            is Boolean -> addProperty(key, value)
            is Char -> addProperty(key, value)
            else -> add(key, JsonUtils.gson.toJsonTree(value))
        }
    }
}

