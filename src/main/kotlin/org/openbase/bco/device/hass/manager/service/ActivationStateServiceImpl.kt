package org.openbase.bco.device.hass.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.ActivationStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.device.hass.type.HassServiceType
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.state.ActivationStateType.ActivationState
import java.util.concurrent.Future

class ActivationStateServiceImpl<ST>(
    unit: ST,
) : HassService<ST>(unit),
    ActivationStateOperationService where ST : ActivationStateOperationService, ST : Unit<*> {
    override fun setActivationState(activationState: ActivationState): Future<ActionDescription> =
        callService(
            hassServiceType =
                when (activationState.value) {
                    ActivationState.State.ACTIVE -> HassServiceType.TURN_ON
                    ActivationState.State.INACTIVE -> HassServiceType.TURN_OFF
                    else -> HassServiceType.UNKNOWN
                },
            state = activationState,
        )

    override fun getActivationState(): ActivationState = unit.activationState
}
