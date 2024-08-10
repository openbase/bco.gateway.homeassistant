package org.openbase.bco.device.hass.manager.service

/*-
* #%L
* BCO Hass Device Manager
* %%
* Copyright (C) 2015 - 2021 openbase.org
* %%
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public
* License along with this program.  If not, see
* <http://www.gnu.org/licenses/gpl-3.0.html>.
* #L%
*/

import org.example.org.openbase.bco.device.hass.manager.unit.HassGatewayController
import org.openbase.bco.dal.control.action.ActionImpl
import org.openbase.bco.dal.control.layer.unit.AbstractUnitController
import org.openbase.bco.dal.lib.layer.service.operation.DiscoveryStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.registry.remote.Registries
import org.openbase.jul.exception.NotAvailableException
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.service.ServiceTemplateType
import org.openbase.type.domotic.state.ActivationStateType.ActivationState
import org.openbase.type.domotic.unit.gateway.GatewayClassType.GatewayClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class DiscoveryStateServiceImpl<ST>(unit: ST) : HassService<ST>(unit),
    DiscoveryStateOperationService where ST : DiscoveryStateOperationService, ST : Unit<*>? {
//    private val DISCOVERY_STATE_SERVICE_LOCK = "DISCOVERY_SERVICE_LOCK"

    override fun getDiscoveryState(): ActivationState {
        return super.getDiscoveryState()
    }

    override fun setDiscoveryState(discoveryState: ActivationState): Future<ActionDescription> {
        TODO()
//        try {
//            val unitController: AbstractUnitController<*, *> = unit as AbstractUnitController<*, *>
//            unitController.applyServiceState<ActivationState>(
//                discoveryState,
//                ServiceTemplateType.ServiceTemplate.ServiceType.DISCOVERY_STATE_SERVICE
//            )
//
//            if (discoveryState.getValue() == ActivationState.State.ACTIVE) {
//                // trigger discovery for binding at hass
//                val gatewayClass: GatewayClass = Registries.getClassRegistry().getGatewayClassById(
//                    unit!!.config.getGatewayConfig().getGatewayClassId()
//                )
//                val bindingId: String =
//                    unit.generateVariablePool().getValue(HassGatewayController.HASS_BINDING_ID_META_CONFIG_KEY)
//                val discoveryTimeout: Int = HassCommunicator.instance.startDiscovery(bindingId)
//
//                // apply returned timeout to the action
//                val action = unitController.getActionById(
//                    discoveryState.getResponsibleAction().getActionId(),
//                    DISCOVERY_STATE_SERVICE_LOCK
//                ) as ActionImpl
//                action.setExecutionTimePeriod(discoveryTimeout.toLong(), TimeUnit.SECONDS)
//                unitController.reschedule()
//            }
//
//            return FutureProcessor.completedFuture<ActionDescription>(
//                ServiceStateProcessor.getResponsibleAction(discoveryState,
//                    Supplier<ActionDescription> { ActionDescription.getDefaultInstance() })
//            )
//        } catch (ex: CouldNotPerformException) {
//            return FutureProcessor.canceledFuture<ActionDescription>(ActionDescription::class.java, ex)
//        } catch (ex: InterruptedException) {
//            Thread.currentThread().interrupt()
//            return FutureProcessor.canceledFuture<ActionDescription>(ActionDescription::class.java, ex)
//        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DiscoveryStateServiceImpl::class.java)
    }
}
