package org.openbase.bco.device.hass.type

import org.openbase.bco.device.hass.manager.dto.HassDto
import org.openbase.bco.device.hass.manager.dto.HassInputDto

interface Mergeable<INPUT: HassInputDto, OUTPUT: HassDto> {
    fun merge(input: INPUT): OUTPUT
}
