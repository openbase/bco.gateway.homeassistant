package org.openbase.bco.device.hass.manager.dto

import org.openbase.bco.device.hass.type.InputDtoProvider

data class HassFloorInputDto(
    val id: String? = null,
    val name: String? = null,
    val icon: String? = null,
    val aliases: List<String>? = null,
): HassInputDto
