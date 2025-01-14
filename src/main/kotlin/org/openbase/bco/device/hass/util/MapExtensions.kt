package org.openbase.bco.device.hass.util

import com.google.gson.JsonObject

fun Map<String, Any>.toRequest(): String {
    val jsonObject = JsonObject()
    forEach { (key, value) ->
        when (value) {
            is String -> jsonObject.addProperty(key, value)
            is Int -> jsonObject.addProperty(key, value)
            is Double -> jsonObject.addProperty(key, value)
            is Float -> jsonObject.addProperty(key, value)
            is Long -> jsonObject.addProperty(key, value)
            is Boolean -> jsonObject.addProperty(key, value)
            else -> jsonObject.addProperty(key, value.toString())
        }
    }
    return jsonObject.toString()
}
