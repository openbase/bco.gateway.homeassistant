package org.openbase.bco.gateway.homeassistant.manager.service


import org.openbase.bco.dal.lib.layer.service.operation.DiscoveryStateOperationService
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.state.ActivationStateType.ActivationState
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Future

class DiscoveryStateServiceImpl<ST>(unit: ST) : HassService<ST>(unit),
    DiscoveryStateOperationService where ST : DiscoveryStateOperationService, ST : Unit<*> {
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
