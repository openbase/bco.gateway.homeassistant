package org.openbase.bco.gateway.homeassistant.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.PowerStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateDto
import org.openbase.bco.gateway.homeassistant.type.HassServiceType
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.state.PowerStateType.PowerState
import java.util.concurrent.Future

/*-
* #%L
* BCO Hass Device Manager
* %%
* Copyright (C) 2015 - 2021 openbase.org
* %%
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public
* License along with this program.  If not, see
* <http://www.gnu.org/licenses/gpl-3.0.html>.
* #L%
*/

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
