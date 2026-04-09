package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.gateway.homeassistant.type.InputDtoProvider
import org.openbase.bco.gateway.homeassistant.type.Mergeable

data class HassEntityDto(
    @SerializedName(HassDto.UNIQUE_ID)
    override val id: String,
    @SerializedName(HassDto.ENTITY_ID)
    val entityId: String,
    @SerializedName(HassDto.AREA_ID)
    val areaId: String?,
    val platform: String?,
    @SerializedName(HassDto.DEVICE_ID)
    val deviceId: String?,
    val icon: String? = null,
): HassDto, Mergeable<HassEntityInputDto, HassEntityDto>, InputDtoProvider<HassEntityInputDto> {
    val type get() = entityId.split(".").first()
    override val name get() = entityId.split(".").last()

    override fun merge(input: HassEntityInputDto): HassEntityDto = copy(
        id = id,
        entityId = input.entityId ?: entityId,
        areaId = input.areaId ?: areaId,
        deviceId = input.deviceId ?: deviceId,
        icon = input.icon ?: icon,
    )

    override fun toInputDto(): HassEntityInputDto = HassEntityInputDto(
        entityId = entityId,
        name = name,
        areaId = areaId,
        deviceId = deviceId,
        icon = icon,
    )
}
