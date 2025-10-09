package org.openbase.bco.gateway.homeassistant.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.ColorStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateDto
import org.openbase.bco.gateway.homeassistant.manager.dto.service.ColorServiceDto
import org.openbase.bco.gateway.homeassistant.type.HassServiceType
import org.openbase.bco.gateway.homeassistant.util.*
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.state.ColorStateType.ColorState
import org.openbase.type.vision.ColorType
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
    override fun getColorState(): ColorState = unit.colorState

    override fun setColorState(colorState: ColorState): Future<ActionDescription> = callService(
        hassServiceType = when (colorState.color.hsbColor.brightness) {
            0.0 -> HassServiceType.TURN_OFF
            else -> HassServiceType.TURN_ON
        },
        state = colorState,
        serviceData = ColorServiceDto(
            entityId = entityId,
            hsColor = listOf(
                colorState.color.hsbColor.hue.toHassHue(),
                colorState.color.hsbColor.saturation.toHassSaturation(),
            ),
            brightness = colorState.color.hsbColor.brightness.toHassBrightness(),
        )
    )

    @Throws(NotAvailableException::class)
    override fun getNeutralWhiteColor(): ColorType.Color = unit.neutralWhiteColor
}

fun HassStateDto.isColorableLight() = hue.isNotNull() && saturation.isNotNull() && brightness.isNotNull()

fun HassStateDto.toColorState(): ColorState.Builder = ColorState.newBuilder().apply {
    setColor(
        colorBuilder.apply {
            setHsbColor(
                hsbColorBuilder.apply {
                    hue = this@toColorState.hue?.toBCOHue()
                        ?: throw NotAvailableException("Hue is not available for entity[$entityId].")
                    saturation = this@toColorState.saturation?.toBCOSaturation()
                        ?: throw NotAvailableException("Saturation is not available for entity[$entityId].")
                    brightness = this@toColorState.brightness?.toBCOBrightness()
                        ?: throw NotAvailableException("Brightness is not available for entity[$entityId].")
                }
            )
        }
    )
}
