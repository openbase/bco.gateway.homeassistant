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
import com.google.protobuf.Message
import org.openbase.bco.dal.lib.layer.service.Service
import org.openbase.bco.dal.lib.layer.service.ServiceProvider
import org.openbase.bco.dal.lib.layer.service.ServiceStateProcessor
import org.openbase.bco.dal.lib.layer.unit.Unit
import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.manager.HassDeviceManager
import org.openbase.bco.device.hass.manager.dto.HassServiceDto
import org.openbase.bco.device.hass.type.HassServiceType
import org.openbase.bco.device.hass.util.get
import org.openbase.bco.device.hass.util.isNull
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
            this.entityId = unit.config.metaConfig[HassDeviceManager.ALIAS_KEY_HASS_ENTITY_ID]
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
        type: HassServiceType,
        entityId: String,
        state: Message?,
    ): Future<ActionDescription> =
        HassCommunicator.instance
            .callService(
                HassServiceDto(
                    service = type,
                    entityId = entityId,
                ),
            ).thenApply { response ->
                if (state.isNull()) {
                    throw CouldNotPerformException("State not set!")
                }

                if (response?.asJsonObject?.get("success")?.asBoolean == true) {
                    ServiceStateProcessor.getResponsibleAction(state) {
                        ActionDescription.getDefaultInstance()
                    }
                } else {
                    val errorMessage = response?.asJsonObject?.get("errorMessage")?.asString ?: "Unknown Error"
                    throw CouldNotPerformException("Service call failed: $errorMessage")
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
