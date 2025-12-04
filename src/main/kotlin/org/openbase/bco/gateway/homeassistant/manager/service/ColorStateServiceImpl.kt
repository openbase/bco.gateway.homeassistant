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
