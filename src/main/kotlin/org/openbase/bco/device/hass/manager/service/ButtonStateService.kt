package org.openbase.bco.device.hass.manager.service

import org.openbase.bco.dal.lib.layer.service.provider.ButtonStateProviderService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.device.hass.manager.dto.HassDeviceClass
import org.openbase.bco.device.hass.manager.dto.HassStateAttributes
import org.openbase.bco.device.hass.manager.dto.HassStateDto
import org.openbase.bco.device.hass.manager.dto.HassStateDto.Companion.BUTTON_INITIAL_PRESS
import org.openbase.bco.device.hass.manager.dto.HassStateDto.Companion.BUTTON_LONG_PRESS
import org.openbase.bco.device.hass.manager.dto.HassStateDto.Companion.BUTTON_LONG_RELEASE
import org.openbase.bco.device.hass.manager.dto.HassStateDto.Companion.BUTTON_REPEAT
import org.openbase.bco.device.hass.manager.dto.HassStateDto.Companion.BUTTON_SHORT_RELEASE
import org.openbase.type.domotic.state.ButtonStateType.ButtonState

class ButtonStateService<ST>(unit: ST) : HassService<ST>(unit),
 ButtonStateProviderService where ST : ButtonStateProviderService, ST : Unit<*> {
    override fun getButtonState(): ButtonState = unit.buttonState
}

fun HassStateDto.toButtonState(): ButtonState.Builder {
    return ButtonState.newBuilder().apply {
        value = when (attributes["event_type"]) {
            BUTTON_INITIAL_PRESS -> ButtonState.State.PRESSED
            BUTTON_REPEAT -> ButtonState.State.DOUBLE_PRESSED
            BUTTON_SHORT_RELEASE -> ButtonState.State.RELEASED
            BUTTON_LONG_PRESS -> ButtonState.State.PRESSED
            BUTTON_LONG_RELEASE -> ButtonState.State.RELEASED
            else -> ButtonState.State.UNKNOWN
        }
    }
}

fun HassStateDto.isButtonState(): Boolean =
    attributes[HassStateAttributes.DEVICE_CLASS.id] == HassDeviceClass.BUTTON.id