package org.openbase.bco.device.hass.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.ColorStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.state.ColorStateType.ColorState
import org.openbase.type.vision.ColorType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

class ColorStateServiceImpl<ST>(unit: ST) : HassService<ST>(unit),
    ColorStateOperationService where ST : ColorStateOperationService, ST : Unit<*> {
    @Throws(NotAvailableException::class)
    override fun getColorState(): ColorState {
        return unit.colorState
    }

    override fun setColorState(colorState: ColorState): Future<ActionDescription> {
        return setState(colorState)
    }

    @Throws(NotAvailableException::class)
    override fun getNeutralWhiteColor(): ColorType.Color {
        return unit.neutralWhiteColor
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ColorStateServiceImpl::class.java)
    }
}
