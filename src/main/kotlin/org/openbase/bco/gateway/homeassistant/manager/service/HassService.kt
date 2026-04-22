package org.openbase.bco.gateway.homeassistant.manager.service

import com.google.protobuf.Message
import org.openbase.bco.dal.lib.layer.service.Service
import org.openbase.bco.dal.lib.layer.service.ServiceProvider
import org.openbase.bco.dal.lib.layer.service.ServiceStateProcessor
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.gateway.homeassistant.communication.HassCommunicator
import org.openbase.bco.gateway.homeassistant.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_ENTITY_ID
import org.openbase.bco.gateway.homeassistant.manager.dto.HassServiceDto
import org.openbase.bco.gateway.homeassistant.manager.dto.service.ServiceDto
import org.openbase.bco.gateway.homeassistant.type.HassServiceType
import org.openbase.bco.gateway.homeassistant.util.get
import org.openbase.bco.gateway.homeassistant.util.isNotNull
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.InstantiationException
import org.openbase.jul.exception.NotSupportedException
import org.openbase.jul.processing.StringProcessor
import org.openbase.type.domotic.action.ActionDescriptionType.ActionDescription
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Future

abstract class HassService<ST>(
    unit: ST,
) : Service where ST : Service, ST : Unit<*> {
    var unit: ST
    var entityId: String
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private var serviceType: ServiceTemplate.ServiceType = detectServiceType()

    init {
        try {
            this.unit = unit
            this.entityId = unit.config.metaConfig[ALIAS_KEY_HASS_ENTITY_ID]
                ?: error("Could not solve entity Id! For unit $unit")
            loadServiceConfig()
        } catch (ex: CouldNotPerformException) {
            throw InstantiationException(this, ex)
        }
    }

    @Throws(CouldNotPerformException::class)
    private fun loadServiceConfig() {
        for (serviceConfig in unit.config.serviceConfigList) {
            if (serviceConfig.serviceDescription.serviceType == serviceType) {
                return
            }
        }
        throw CouldNotPerformException(
            "Could not detect service config! Service[${serviceType.name}] is not configured in Unit[${(unit as Unit<*>).id}]!",
        )
    }

    @Throws(NotSupportedException::class)
    fun detectServiceType(): ServiceTemplate.ServiceType =
        ServiceTemplate.ServiceType.valueOf(
            StringProcessor.transformToUpperCase(
                javaClass.simpleName.replaceFirst(
                    "Impl".toRegex(),
                    "",
                ),
            ),
        )

    fun callService(
        hassServiceType: HassServiceType,
        state: Message,
        serviceData: ServiceDto? = null,
    ): Future<ActionDescription> =
        HassCommunicator.instance
            .callService(
                HassServiceDto(
                    hassServiceType = hassServiceType,
                    entityId = entityId,
                    serviceData = serviceData,
                ),
            ).thenApply { response ->
                if (response.isNotNull()) {
                    ServiceStateProcessor.getResponsibleAction(state) {
                        ActionDescription.getDefaultInstance()
                    }
                } else {
                    run {
                        response?.asJsonObject?.get("errorMessage")?.asString ?: "Unknown Error"
                    }.let { errorMessage ->
                        throw CouldNotPerformException("Service call failed: $errorMessage")
                    }
                }
            }

    fun setState(serviceState: Message): Future<ActionDescription> {
        TODO()
//        try {
//            var success = false
//            var exceptionStack: ExceptionStack? = null
//            for (serviceActionClass in ServiceTypeServiceActionMapping.getServiceActionClasses(serviceType)) {
//                val serviceAction: ServiceAction = ServiceStateServiceActionTransformerPool.instance.getTransformer(serviceState.javaClass, serviceActionClass).transform(serviceState = serviceState)
//                try {
//                    HassCommunicator.instance.postServiceAction(entityId, serviceAction.toString())
//                    success = true
//                } catch (ex: CouldNotPerformException) {
//                    if (ex.cause is NotAvailableException) {
//                        throw CouldNotPerformException("Entity may not be configured or hass not reachable", ex)
//                    }
//                    exceptionStack = MultiException.push(
//                        "Could not apply state via " + serviceActionClass.simpleName + "!",
//                        ex,
//                        exceptionStack
//                    )
//                }
//            }
//
//            if (!success) {
//                MultiException.checkAndThrow(Callable<String> { "Could not apply state!" }, exceptionStack)
//            } else {
//                // if at least one command reached its target, then we just print a warning
//                try {
//                    MultiException.checkAndThrow(
//                        Callable<String> { "Some command classes could not be used to apply the state:" },
//                        exceptionStack
//                    )
//                } catch (ex: Exception) {
//                    ExceptionPrinter.printHistory<Exception>(ex, logger, LogLevel.WARN)
//                }
//            }
//
//            return FutureProcessor.completedFuture(
//                ServiceStateProcessor.getResponsibleAction(serviceState,
//                     { ActionDescription.getDefaultInstance() })
//            )
//        } catch (ex: CouldNotPerformException) {
//            return FutureProcessor.canceledFuture(ActionDescription::class.java, ex)
//        }
    }

    override fun getServiceProvider(): ServiceProvider<*>? = unit
}
