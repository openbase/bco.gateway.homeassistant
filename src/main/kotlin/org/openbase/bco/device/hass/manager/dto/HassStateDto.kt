package org.openbase.bco.device.hass.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.device.hass.type.HassDomainType
import org.openbase.bco.device.hass.type.toHassDomainType

data class HassStateDto(
    @SerializedName("entity_id")
    val entityId: String,
    @SerializedName("last_changed")
    val lastChanged: String,
    val state: String,
    val attributes: Map<String, Any>,
    val context: Map<String, String?>,
) {
    val type
        get() : HassDomainType =
            entityId.split(".").first().toHassDomainType()
    val name get() = entityId.split(".").last()

    private val hsColor: Pair<Double, Double>?
        get() =
            attributes["hs_color"]
                ?.toString()
                ?.replace("[", "")
                ?.replace("]", "")
                ?.split(",")
                ?.let { it[0].toDouble() to it[1].toDouble() }

    val hue: Double?
        get() =
            hsColor?.first

    val saturation: Double?
        get() =
            hsColor?.second

    val brightness: Double?
        get() =
            (attributes["brightness"] as? Double)

    companion object {
        const val STATE_ON: String = "on"
        const val STATE_OFF: String = "off"

        const val BUTTON_INITIAL_PRESS = "initial_press"
        const val BUTTON_REPEAT = "repeat"
        const val BUTTON_SHORT_RELEASE = "short_release"
        const val BUTTON_LONG_PRESS = "long_press"
        const val BUTTON_LONG_RELEASE = "long_release"
    }
}
