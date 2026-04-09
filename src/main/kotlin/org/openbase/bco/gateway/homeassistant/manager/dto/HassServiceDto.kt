package org.openbase.bco.gateway.homeassistant.manager.dto

import com.google.gson.annotations.SerializedName
import org.openbase.bco.gateway.homeassistant.manager.dto.service.ServiceDto
import org.openbase.bco.gateway.homeassistant.type.HassDomainType
import org.openbase.bco.gateway.homeassistant.type.HassServiceType
import org.openbase.bco.gateway.homeassistant.type.toHassDomainType

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
    @SerializedName(HassDto.SERVICE_DATA)
    val serviceData: ServiceDto? = null,
    val target: Target? = null,
    @SerializedName(HassDto.RETURN_RESPONSE)
    val returnResponse: Boolean = false,
){
    constructor(
        hassServiceType: HassServiceType = HassServiceType.TURN_ON,
        entityId: String,
        domain: HassDomainType = entityId.toHassDomainType(),
        serviceData: ServiceDto? = null,
        returnResponse: Boolean = false,
    ): this(
        domain = domain.id,
        service = hassServiceType.id,
        serviceData = serviceData,
        target = entityId.toTarget(),
        returnResponse = returnResponse,
    )

    data class Target(
        @SerializedName(HassDto.ENTITY_ID)
        val entityId: String,
    )

    companion object {
        fun String.toTarget(): Target = Target(this)
    }
}
