package org.openbase.bco.device.hass.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.device.hass.type.HassDomainType
import org.openbase.bco.device.hass.type.toHassDomainType
import org.openbase.bco.device.hass.util.toBCOBrightness
import org.openbase.bco.device.hass.util.toBCOHue
import org.openbase.bco.device.hass.util.toBCOSaturation

data class HassStateDto(
    @SerializedName("entity_id")
    val entityId: String,
    @SerializedName("last_changed")
    val lastChanged: String,
    val state: String,
    val attributes: Map<String, Any>,
    val context: Map<String, String?>,
) {
    val type get() : HassDomainType =
        entityId.split(".").first().toHassDomainType()
    val name get() = entityId.split(".").last()

    private val hsColor: Pair<Double, Double>? get() =
        attributes["hs_color"]
            ?.toString()
            ?.replace("[", "")
            ?.replace("]", "")
            ?.split(",")
            ?.let { it[0].toDouble() to it[1].toDouble() }

    val hue: Double? get() =
        hsColor?.first?.toBCOHue()

    val saturation: Double? get() =
        hsColor?.second?.toBCOSaturation()

    val brightness: Double? get() =
       (attributes["brightness"] as? Double)?.toBCOBrightness()
}
