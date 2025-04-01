package org.openbase.bco.device.hass.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.TargetTemperatureStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.device.hass.manager.dto.HassStateDto
import org.openbase.bco.device.hass.manager.dto.service.TargetTemperatureStateDto
import org.openbase.bco.device.hass.type.HassServiceType
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.state.TemperatureStateType.TemperatureState
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
    override fun getTargetTemperatureState(): TemperatureState {
        return unit.targetTemperatureState
    }
}

fun HassStateDto.toTargetTemperatureState(): TemperatureState.Builder {
    return  TemperatureState.newBuilder().apply {
        temperature = state.toDouble()
    }
}

// TODO:
// 1. Unit type mapping in unit template registry
// 2. Register tado temperature controller -> device class registry
