package org.openbase.bco.gateway.homeassistant.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.PowerStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateDto
import org.openbase.bco.gateway.homeassistant.type.HassServiceType
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.state.PowerStateType.PowerState
import java.util.concurrent.Future


class PowerStateServiceImpl<ST>(
    unit: ST,
) : HassService<ST>(unit),
    PowerStateOperationService where ST : PowerStateOperationService, ST : Unit<*> {
    override fun setPowerState(powerState: PowerState): Future<ActionDescription> =
        callService(
            hassServiceType =
                when (powerState.value) {
                    PowerState.State.ON -> HassServiceType.TURN_ON
                    PowerState.State.OFF -> HassServiceType.TURN_OFF
                    else -> HassServiceType.UNKNOWN
                },
            state = powerState,
        )

    @Throws(NotAvailableException::class)
    override fun getPowerState(): PowerState = unit.powerState
}

fun HassStateDto.toPowerState(): PowerState.Builder =
    PowerState.newBuilder().apply {
        value = when (state) {
            HassStateDto.STATE_ON -> PowerState.State.ON
            HassStateDto.STATE_OFF -> PowerState.State.OFF
            else -> PowerState.State.UNKNOWN
        }
    }
