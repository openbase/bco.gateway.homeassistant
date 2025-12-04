package org.openbase.bco.gateway.homeassistant.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.BrightnessStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateDto
import org.openbase.bco.gateway.homeassistant.manager.dto.service.BrightnessServiceDto
import org.openbase.bco.gateway.homeassistant.util.toHassBrightness
import org.openbase.bco.gateway.homeassistant.type.HassServiceType
import org.openbase.bco.gateway.homeassistant.util.isNotNull
import org.openbase.bco.gateway.homeassistant.util.isNull
import org.openbase.bco.gateway.homeassistant.util.toBCOBrightness
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.state.BrightnessStateType.BrightnessState
import java.util.concurrent.Future


class BrightnessStateServiceImpl<ST>(unit: ST) : HassService<ST>(unit),
    BrightnessStateOperationService where ST : BrightnessStateOperationService, ST : Unit<*> {
    override fun setBrightnessState(brightnessState: BrightnessState): Future<ActionDescription> = callService(
        hassServiceType = HassServiceType.SET_BRIGHTNESS,
        state = brightnessState,
        serviceData = BrightnessServiceDto(
            entityId = entityId,
            brightness = brightnessState.brightness.toHassBrightness(),
        )
    )

    @Throws(NotAvailableException::class)
    override fun getBrightnessState(): BrightnessState {
        return unit.brightnessState
    }
}

fun HassStateDto.isBrightnessState() = hue.isNull() && saturation.isNull() && brightness.isNotNull()

fun HassStateDto.toBrightnessState(): BrightnessState.Builder = BrightnessState.newBuilder().apply {
    brightness = this@toBrightnessState.brightness?.toBCOBrightness() ?:
        throw NotAvailableException("Brightness is not available for entity[$entityId].")
}
