package org.openbase.bco.gateway.homeassistant.util

import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlin.reflect.KClass

object JsonUtils {
    val gson: Gson = Gson()
}

infix fun <T: Any> JsonElement.parse(klass: KClass<T>): T = Gson().fromJson(this, klass.java)
