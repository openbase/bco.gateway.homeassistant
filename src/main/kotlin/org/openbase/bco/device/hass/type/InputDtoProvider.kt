package org.openbase.bco.device.hass.type

import org.openbase.bco.device.hass.manager.dto.HassInputDto

interface InputDtoProvider<INPUT: HassInputDto> {
    fun toInputDto(): INPUT
}
