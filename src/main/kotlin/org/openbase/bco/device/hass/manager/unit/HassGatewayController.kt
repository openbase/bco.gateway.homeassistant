package org.example.org.openbase.bco.device.hass.manager.unit

import org.openbase.bco.dal.control.layer.unit.gateway.GenericGatewayController
import org.openbase.bco.dal.lib.layer.unit.device.DeviceControllerFactory
import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.type.domotic.state.ConnectionStateType
import java.util.concurrent.TimeUnit

class HassGatewayController(deviceControllerFactory: DeviceControllerFactory) :
    GenericGatewayController(deviceControllerFactory) {
    @Throws(InterruptedException::class, CouldNotPerformException::class)
    override fun activate() {
        // check if installed

        try {
            // prepare variable pool
            val vars = generateVariablePool()
            val bindingId = vars.getValue(HASS_BINDING_ID_META_CONFIG_KEY)

            // wait for hass connection
            HassCommunicator.instance
                .waitForConnectionState(ConnectionStateType.ConnectionState.State.CONNECTED, 30, TimeUnit.SECONDS)

            // install corresponding binding if missing
            TODO()
//            if (!HassCommunicator.getInstance().isBindingInstalled(bindingId)) {
//                try {
//                    HassCommunicator.getInstance().installBinding(bindingId)
//                } catch (ex: CouldNotPerformException) {
//                    ExceptionPrinter.printHistory("Could not install hass binding: $bindingId", ex, logger)
//                }
//            }
        } catch (ex: CouldNotPerformException) {
            ExceptionPrinter.printHistory("Could not validate gateway binding of " + getLabel("?"), ex, logger)
        }
        super.activate()
    }

    companion object {
        const val HASS_BINDING_ID_META_CONFIG_KEY: String = "HASS_BINDING_ID"
    }
}
