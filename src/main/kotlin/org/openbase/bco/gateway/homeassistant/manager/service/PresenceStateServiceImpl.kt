package org.openbase.bco.gateway.homeassistant.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.PresenceStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.gateway.homeassistant.type.HassServiceType
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.action.ActionDescriptionType
import org.openbase.type.domotic.state.PresenceStateType.PresenceState
import java.util.concurrent.Future

class PresenceStateServiceImpl<ST>(
    unit: ST,
) : HassService<ST>(unit),
    PresenceStateOperationService where ST : PresenceStateOperationService, ST : Unit<*> {
    override fun setPresenceState(presence: PresenceState): Future<ActionDescriptionType.ActionDescription> =
        callService(
            hassServiceType =
                when (presence.value) {
                    PresenceState.State.PRESENT -> HassServiceType.TURN_ON
                    PresenceState.State.ABSENT -> HassServiceType.TURN_OFF
                    else -> HassServiceType.UNKNOWN
                },
            state = presence,
        )

    @Throws(NotAvailableException::class)
    override fun getPresenceState(): PresenceState = unit.presenceState
}
