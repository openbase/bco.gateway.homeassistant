package org.openbase.bco.gateway.homeassistant.type

import org.openbase.bco.gateway.homeassistant.manager.dto.HassDto
import org.openbase.bco.gateway.homeassistant.manager.dto.HassInputDto

interface Mergeable<INPUT: HassInputDto, OUTPUT: HassDto> {
    fun merge(input: INPUT): OUTPUT
}
