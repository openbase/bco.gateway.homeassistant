package org.openbase.bco.gateway.homeassistant.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.StandbyStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.state.StandbyStateType.StandbyState
import java.util.concurrent.Future


class StandbyStateServiceImpl<ST>(unit: ST) : HassService<ST>(unit),
    StandbyStateOperationService where ST : StandbyStateOperationService, ST : Unit<*> {
    override fun setStandbyState(standbyState: StandbyState): Future<ActionDescription> {
        return setState(standbyState)
    }

    @Throws(NotAvailableException::class)
    override fun getStandbyState(): StandbyState {
        return unit.standbyState
    }
}
