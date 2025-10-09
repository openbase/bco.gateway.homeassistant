package org.openbase.bco.gateway.homeassistant.manager.unit

import org.openbase.bco.dal.control.layer.unit.gateway.GenericGatewayController
import org.openbase.bco.dal.lib.layer.unit.device.DeviceControllerFactory

class HassGatewayController(deviceControllerFactory: DeviceControllerFactory) :
    GenericGatewayController(deviceControllerFactory)
