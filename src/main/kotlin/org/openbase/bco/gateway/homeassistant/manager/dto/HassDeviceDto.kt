package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.gateway.homeassistant.type.InputDtoProvider
import org.openbase.bco.gateway.homeassistant.type.Mergeable

data class HassDeviceDto(
    override val id: String,
    override val name: String,
    @SerializedName("area_id")
    val areaId: String? = null,
    @SerializedName("model_id")
    val modelId: String? = null,
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
        modelId = input.modelId ?: modelId,
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
        modelId = modelId,
        model = model,
        icon = icon,
        manufacturer = manufacturer,
        labels = labels,
        entryType = entryType,
    )
}
