package org.openbase.bco.gateway.homeassistant.manager.service

import org.openbase.bco.dal.lib.layer.service.operation.BlindStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.gateway.homeassistant.manager.dto.HassStateDto
import org.openbase.bco.gateway.homeassistant.type.HassServiceType
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.state.BlindStateType.BlindState
import java.util.concurrent.Future


class BlindStateServiceImpl<ST>(unit: ST) : HassService<ST>(unit),
    BlindStateOperationService where ST : BlindStateOperationService, ST : Unit<*> {
    override fun setBlindState(blindState: BlindState): Future<ActionDescription> =
        callService(
            hassServiceType =
                when (blindState.value) {
                    BlindState.State.UP -> HassServiceType.OPEN_COVER
                    BlindState.State.DOWN -> HassServiceType.CLOSE_COVER
                    BlindState.State.STOP -> HassServiceType.STOP_COVER
                    else -> HassServiceType.UNKNOWN
                },
            state = blindState,
        )

    @Throws(NotAvailableException::class)
    override fun getBlindState(): BlindState = unit.blindState
}

fun HassStateDto.toBlindState(): BlindState.Builder {
    return  BlindState.newBuilder().apply {
        value = when (state) {
            HassStateDto.STATE_OPENING,
            HassStateDto.STATE_CLOSING,
            HassStateDto.STATE_CLOSED -> BlindState.State.DOWN
            HassStateDto.STATE_STOPPED -> BlindState.State.STOP
            HassStateDto.STATE_OPEN -> BlindState.State.UP
            else -> BlindState.State.UNKNOWN
        }
        openingRatio = 1.0 - (position?.let { it / 100.0 }
            ?: throw NotAvailableException("Position not available for entity[$entityId]."))
    }
}
