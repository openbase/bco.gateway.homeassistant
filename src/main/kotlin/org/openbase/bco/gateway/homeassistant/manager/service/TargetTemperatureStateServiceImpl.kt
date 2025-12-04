package org.openbase.bco.gateway.homeassistant.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.TargetTemperatureStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateDto
import org.openbase.bco.gateway.homeassistant.manager.dto.service.TargetTemperatureStateDto
import org.openbase.bco.gateway.homeassistant.manager.dto.temperature
import org.openbase.bco.gateway.homeassistant.type.HassServiceType
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.state.TemperatureStateType.TemperatureState
import java.util.concurrent.Future


class TargetTemperatureStateServiceImpl<ST>(unit: ST) : HassService<ST>(unit),
    TargetTemperatureStateOperationService where ST : TargetTemperatureStateOperationService, ST : Unit<*> {
    override fun setTargetTemperatureState(temperatureState: TemperatureState): Future<ActionDescription> =
        callService(
            hassServiceType = HassServiceType.SET_TEMPERATURE,

            state = temperatureState,
            serviceData = TargetTemperatureStateDto(
                temperature = temperatureState.temperature
            )
        )

    @Throws(NotAvailableException::class)
    override fun getTargetTemperatureState(): TemperatureState = unit.targetTemperatureState
}

fun HassStateDto.toTargetTemperatureState(): TemperatureState.Builder {
    return  TemperatureState.newBuilder().apply {
        temperature = attributes.temperature()
    }
}
