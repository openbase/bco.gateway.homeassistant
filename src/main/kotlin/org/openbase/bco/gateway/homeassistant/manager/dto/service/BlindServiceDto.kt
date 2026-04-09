package org.openbase.bco.gateway.homeassistant.manager.dto.service

import com.google.gson.annotations.SerializedName
import org.openbase.bco.gateway.homeassistant.manager.dto.HassDto

data class BlindServiceDto(
    @SerializedName(HassDto.ENTITY_ID)
    val entityId: String,
    /**
     * The position of the blind. 0 is open, 100 is closed.
     */
    val position: Double,
) : ServiceDto
