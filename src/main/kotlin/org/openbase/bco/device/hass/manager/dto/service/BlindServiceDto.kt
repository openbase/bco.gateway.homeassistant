package org.openbase.bco.device.hass.manager.dto.service

import com.google.gson.annotations.SerializedName

data class BlindServiceDto(
    @SerializedName("entity_id")
    val entityId: String,
    /**
     * The position of the blind. 0 is open, 100 is closed.
     */
    val position: Double,
) : ServiceDto