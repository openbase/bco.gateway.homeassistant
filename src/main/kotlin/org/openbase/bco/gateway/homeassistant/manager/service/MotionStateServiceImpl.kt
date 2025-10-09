package org.openbase.bco.gateway.homeassistant.manager.service

import org.openbase.bco.gateway.homeassistant.manager.dto.HassDeviceClass
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateAttributes
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateDto
import org.openbase.type.domotic.state.MotionStateType.MotionState

fun HassStateDto.isMotionSensor(): Boolean =
    attributes[HassStateAttributes.DEVICE_CLASS.id] == HassDeviceClass.MOTION.id

fun HassStateDto.toMotionState(): MotionState.Builder {
    return MotionState.newBuilder().apply {
        value = when (state) {
            HassStateDto.STATE_ON -> MotionState.State.MOTION
            HassStateDto.STATE_OFF -> MotionState.State.NO_MOTION
            else -> MotionState.State.UNKNOWN
        }
    }
}
