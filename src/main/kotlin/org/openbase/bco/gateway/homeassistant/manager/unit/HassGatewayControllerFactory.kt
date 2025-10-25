package org.openbase.bco.gateway.homeassistant.manager.unit

import org.openbase.bco.dal.control.layer.unit.device.DeviceControllerFactoryImpl
import org.openbase.bco.dal.control.layer.unit.gateway.GenericGatewayController
import org.openbase.bco.dal.lib.layer.unit.device.DeviceControllerFactory
import org.openbase.bco.dal.lib.layer.unit.gateway.Gateway
import org.openbase.bco.dal.lib.layer.unit.gateway.GatewayController
import org.openbase.bco.dal.lib.layer.unit.gateway.GatewayControllerFactory
import org.openbase.bco.gateway.homeassistant.manager.service.HassOperationServiceFactory
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.jul.exception.InstantiationException

class HassGatewayControllerFactory : GatewayControllerFactory {
    private val deviceControllerFactory: DeviceControllerFactory =
        DeviceControllerFactoryImpl(HassOperationServiceFactory(), null)

    @Throws(InstantiationException::class, InterruptedException::class)
    override fun newInstance(gatewayUnitConfig: UnitConfig): GatewayController {
        try {
            if (!gatewayUnitConfig.hasId()) {
                throw NotAvailableException("gatewayConfig.id")
            }

            if (!gatewayUnitConfig.hasLabel()) {
                throw NotAvailableException("gatewayConfig.label")
            }

            if (!gatewayUnitConfig.hasPlacementConfig()) {
                throw NotAvailableException("gatewayConfig.placement")
            }

            if (!gatewayUnitConfig.placementConfig.hasLocationId()) {
                throw NotAvailableException("gatewayConfig.placement.locationId")
            }

            val genericGatewayController: GenericGatewayController =
                HassGatewayController(getDeviceControllerFactory())
            genericGatewayController.init(gatewayUnitConfig)

            return genericGatewayController
        } catch (ex: CouldNotPerformException) {
            throw InstantiationException(Gateway::class.java, gatewayUnitConfig.id, ex)
        }
    }

    override fun getDeviceControllerFactory(): DeviceControllerFactory {
        return deviceControllerFactory
    }
}
