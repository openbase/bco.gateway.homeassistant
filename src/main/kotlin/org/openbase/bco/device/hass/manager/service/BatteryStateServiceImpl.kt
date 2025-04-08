package org.openbase.bco.device.hass.manager.service

import org.openbase.bco.dal.lib.layer.service.provider.BatteryStateProviderService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.device.hass.manager.dto.HassDeviceClass
import org.openbase.bco.device.hass.manager.dto.HassStateAttributes
import org.openbase.bco.device.hass.manager.dto.HassStateDto
import org.openbase.type.domotic.state.BatteryStateType.BatteryState

class BatteryStateServiceImpl<ST>(unit: ST) : HassService<ST>(unit),
    BatteryStateProviderService where ST : BatteryStateProviderService, ST : Unit<*> {
    override fun getBatteryState(): BatteryState = unit.batteryState
}

fun HassStateDto.toBatteryState(): BatteryState.Builder {
    return BatteryState.newBuilder().apply {
        value = when(state.toInt()) {
            in 0..20 -> BatteryState.State.CRITICAL
            in 21..50 -> BatteryState.State.LOW
            in 51..100 -> BatteryState.State.OK
            else -> BatteryState.State.UNKNOWN
        }
    }
}

fun HassStateDto.isBatterySensor(): Boolean =
    attributes[HassStateAttributes.DEVICE_CLASS.id] == HassDeviceClass.BATTERY.id