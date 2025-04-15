package org.openbase.bco.device.hass.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.UserTransitStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.device.hass.manager.dto.HassStateDto
import org.openbase.type.domotic.state.UserTransitStateType.UserTransitState

class UserTransitStateService<ST>(unit: ST) : HassService<ST>(unit),
  UserTransitStateOperationService where ST: UserTransitStateOperationService, ST: Unit<*> {
    override fun getUserTransitState(): UserTransitState = unit.userTransitState
}

fun HassStateDto.toUserTransitState(): UserTransitState.Builder =
    UserTransitState.newBuilder().apply {
        value = when (state) {
            HassStateDto.STATE_HOME -> UserTransitState.State.LONG_TERM_PRESENT
            else -> UserTransitState.State.LONG_TERM_ABSENT
        }
    }