package org.openbase.bco.gateway.homeassistant.type

import org.openbase.bco.gateway.homeassistant.manager.dto.HassInputDto

interface InputDtoProvider<INPUT: HassInputDto> {
    fun toInputDto(): INPUT
}
