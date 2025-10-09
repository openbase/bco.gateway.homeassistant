package org.openbase.bco.gateway.homeassistant.manager.service
import org.openbase.bco.dal.lib.layer.service.provider.TemperatureStateProviderService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.gateway.homeassistant.manager.dto.HassDeviceClass
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateDto
import org.openbase.type.domotic.state.TemperatureStateType.TemperatureState

class TemperatureStateServiceImpl<ST>(unit: ST) : HassService<ST>(unit),
    TemperatureStateProviderService where ST : TemperatureStateProviderService, ST : Unit<*> {
    override fun getTemperatureState(): TemperatureState = unit.temperatureState
    }

fun HassStateDto.isTemperatureSensor() : Boolean =
    deviceClass == HassDeviceClass.TEMPERATURE.id


fun HassStateDto.toTemperatureState(): TemperatureState.Builder {
    return TemperatureState.newBuilder().apply {
        temperature = state.toDouble()
    }
}
