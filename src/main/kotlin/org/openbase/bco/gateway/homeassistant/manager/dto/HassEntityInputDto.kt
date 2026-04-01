package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName

data class HassEntityInputDto(
    @SerializedName("entity_id")
    val entityId: String? = null,
    override val name: String? = null,
    @SerializedName("area_id")
    val areaId: String? = null,
    @SerializedName("device_id")
    val deviceId: String? = null,
): HassInputDto

