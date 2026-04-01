package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.gateway.homeassistant.type.InputDtoProvider
import org.openbase.bco.gateway.homeassistant.type.Mergeable

data class HassEntityDto(
    @SerializedName("unique_id")
    override val id: String,
    @SerializedName("entity_id")
    val entityId: String,
    @SerializedName("area_id")
    val areaId: String,
    val platform: String,
    @SerializedName("device_id")
    val deviceId: String,
): HassDto, Mergeable<HassEntityInputDto, HassEntityDto>, InputDtoProvider<HassEntityInputDto> {
    val type get() = entityId.split(".").first()
    override val name get() = entityId.split(".").last()

    override fun merge(input: HassEntityInputDto): HassEntityDto = copy(
        id = id,
        entityId = input.entityId ?: entityId,
        areaId = input.areaId ?: areaId,
        deviceId = input.deviceId ?: deviceId,
    )

    override fun toInputDto(): HassEntityInputDto = HassEntityInputDto(
        entityId = entityId,
        name = name,
        areaId = areaId,
        deviceId = deviceId,
    )
}
