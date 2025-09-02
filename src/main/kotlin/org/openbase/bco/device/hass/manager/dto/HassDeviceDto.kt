package org.openbase.bco.device.hass.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.device.hass.type.InputDtoProvider
import org.openbase.bco.device.hass.type.Mergeable

data class HassDeviceDto(
    override val id: String,
    override val name: String,
    @SerializedName("area_id")
    val areaId: String? = null,
    val model: String? = null,
    val icon: String? = null,
    val manufacturer: String? = null,
    val labels: List<String>,
    val entryType: String? = null,
): HassDto, Mergeable<HassDeviceInputDto, HassDeviceDto>, InputDtoProvider<HassDeviceInputDto> {

    override fun merge(input: HassDeviceInputDto): HassDeviceDto = copy(
        id = input.id ?: id,
        name = input.name ?: name,
        areaId = input.areaId ?: areaId,
        model  = input.model ?: model,
        icon = input.icon ?: icon,
        manufacturer = input.manufacturer ?: manufacturer,
        labels = input.labels ?: labels,
        entryType = input.entryType ?: entryType,
    )

    override fun toInputDto(): HassDeviceInputDto = HassDeviceInputDto(
        id = id,
        name = name,
        areaId = areaId,
        model = model,
        icon = icon,
        manufacturer = manufacturer,
        labels = labels,
        entryType = entryType,
    )
}
