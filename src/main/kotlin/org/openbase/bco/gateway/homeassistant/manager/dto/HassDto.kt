package org.openbase.bco.gateway.homeassistant.manager.dto

import org.openbase.bco.gateway.homeassistant.util.IdProvider
import org.openbase.bco.gateway.homeassistant.util.NameProvider

interface HassDto: IdProvider<String>, NameProvider<String>
