package org.openbase.bco.device.hass.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.device.hass.manager.dto.service.ServiceDto
import org.openbase.bco.device.hass.type.HassDomainType
import org.openbase.bco.device.hass.type.HassServiceType
import org.openbase.bco.device.hass.type.toHassDomainType

//{
//    "id": 1,
//    "type": "call_service",
//    "domain": "light",
//    "service": "turn_on",
//    "service_data": {
//      "brightness": 128,
//      "rgb_color": [255, 100, 100]
//     },
//    "target": {
//        "entity_id": "light.dusche"
//    }
//}

data class HassServiceDto(
    val domain: String,
    val service: String,
    @SerializedName("service_data")
    val serviceData: ServiceDto? = null,
    val target: Target? = null,
    @SerializedName("return_response")
    val returnResponse: Boolean = false,
){
    constructor(
        service: HassServiceType = HassServiceType.TURN_ON,
        entityId: String,
        domain: HassDomainType = entityId.toHassDomainType(),
        serviceData: ServiceDto? = null,
        returnResponse: Boolean = false,
    ): this(
        domain = domain.id,
        service = service.id,
        serviceData = serviceData,
        target = entityId.toTarget(),
        returnResponse = returnResponse,
    )

    data class Target(
        @SerializedName("entity_id")
        val entityId: String,
    )

    companion object {
        fun String.toTarget(): Target = Target(this)
    }
}
