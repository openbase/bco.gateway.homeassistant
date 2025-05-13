package org.openbase.bco.device.hass.manager.dto

import org.openbase.bco.device.hass.util.IdProvider
import org.openbase.bco.device.hass.util.NameProvider

interface HassDto: IdProvider<String>, NameProvider<String>
