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

import org.openbase.bco.device.hass.manager.service.ServiceAction
import org.openbase.bco.registry.remote.Registries
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.NotAvailableException
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.jul.exception.printer.LogLevel
import org.openbase.jul.extension.type.processing.MetaConfigPool
import org.openbase.jul.extension.type.processing.MetaConfigVariableProvider
import org.openbase.jul.pattern.Observer
import org.openbase.jul.pattern.provider.DataProvider
import org.openbase.jul.schedule.SyncObject
import org.openbase.type.configuration.MetaConfigType.MetaConfig
import org.openbase.type.domotic.registry.TemplateRegistryDataType.TemplateRegistryData
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Helper class building a mapping from service types to serviceAction classes from the service template registry.
 * The mapping will be build on the first call and then register an observer on the registry to update automatically
 * on changes.
 *
 *
 * Note: if you register an observer on the template registry and in this need to resolve the serviceAction classes
 * you should make to sure to call [.getServiceActionClasses] before because only after this call
 * this helper registers its observer which will then be executed before yours.
 *
 * @author [Tamino Huxohl](mailto:pleminoq@openbase.org)
 */
object ServiceTypeServiceActionMapping {
    const val SERVICEACTION_CLASSES_KEY: String = "SERVICEACTION_CLASSES"

    private val LOGGER: Logger = LoggerFactory.getLogger(ServiceTypeServiceActionMapping::class.java)

    private val UPDATE_LOCK: SyncObject = SyncObject("ServiceTypeServiceActionMappingLock")
    private val MAP: MutableMap<ServiceTemplate.ServiceType, MutableSet<Class<out ServiceAction>>> =
        HashMap<ServiceTemplate.ServiceType, MutableSet<Class<out ServiceAction?>>>()

    /**
     * Get a set of serviceAction classes which can be handled by a service type.
     *
     * @param serviceType the service type for which the serviceAction classes are resolved.
     * @return a set of serviceAction classes that can be handled by the service type.
     * @throws NotAvailableException if no serviceAction classes are available for the service type.
     */
    @Throws(NotAvailableException::class)
    fun getServiceActionClasses(serviceType: ServiceTemplate.ServiceType): Set<Class<out ServiceAction>> {
        synchronized(UPDATE_LOCK) {
            if (MAP.isEmpty()) {
                Registries.getTemplateRegistry()
                    .addDataObserver(Observer<DataProvider<TemplateRegistryData?>, TemplateRegistryData> { provider: DataProvider<TemplateRegistryData?>?, data: TemplateRegistryData? -> buildMap() })

                try {
                    if (!Registries.getTemplateRegistry().isDataAvailable) {
                        Registries.getTemplateRegistry().waitForData()
                    }
                    buildMap()
                } catch (ex: CouldNotPerformException) {
                    throw NotAvailableException("mapping from service types to serviceAction classes")
                } catch (ex: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw NotAvailableException("mapping from service types to serviceAction classes")
                }
            }
            if (!MAP.containsKey(serviceType) || MAP[serviceType]!!.isEmpty()) {
                throw NotAvailableException("serviceAction classes for service type[" + serviceType.name + "]")
            }
            return MAP[serviceType]!!
        }
    }

    @Throws(CouldNotPerformException::class)
    private fun buildMap() {
        synchronized(UPDATE_LOCK) {
            for (serviceTemplate in Registries.getTemplateRegistry().serviceTemplates) {
                MAP[serviceTemplate.serviceType] =
                    HashSet<Class<out ServiceAction?>>()
                val metaConfigPool: MetaConfigPool = MetaConfigPool()
                metaConfigPool.register(
                    MetaConfigVariableProvider(
                        serviceTemplate.serviceType.name + MetaConfig::class.java.getSimpleName(),
                        serviceTemplate.metaConfig
                    )
                )

                var serviceActionTypeClasses: String
                try {
                    serviceActionTypeClasses = metaConfigPool.getValue(SERVICEACTION_CLASSES_KEY)
                } catch (e: NotAvailableException) {
                    continue
                }

                for (serviceActionTypeClass in serviceActionTypeClasses.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()) {
                    try {
                        MAP[serviceTemplate.serviceType]!!.add(generateServiceActionClass(serviceActionTypeClass.trim { it <= ' ' }))
                    } catch (ex: CouldNotPerformException) {
                        ExceptionPrinter.printHistory<CouldNotPerformException>(
                            "ServiceAction class[" + serviceActionTypeClass + "] for service type[" + serviceTemplate.serviceType.name + "] not available",
                            ex,
                            LOGGER,
                            LogLevel.WARN
                        )
                    }
                }
            }
        }
    }

    val SERVICE_ACTION_TYPE_PACKAGE: String = ServiceAction::class.java.getPackage().getName()

    @Throws(CouldNotPerformException::class)
    fun generateServiceActionClass(simpleClassName: String): Class<out ServiceAction?> {
        try {
            val className = "$SERVICE_ACTION_TYPE_PACKAGE.$simpleClassName"
            return ServiceTypeServiceActionMapping::class.java.classLoader.loadClass(className) as Class<ServiceAction?>
        } catch (ex: ClassNotFoundException) {
            throw CouldNotPerformException(
                "Could not generate serviceAction class based on class name [" + simpleClassName + "Type]",
                ex
            )
        }
    }

    @Throws(CouldNotPerformException::class)
    fun lookupServiceActionClass(stateType: String): Class<out ServiceAction> {
        TODO("map update of state to action classes")
        // is color value set then color
        // is state off then power etc.
//        return when (stateType.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]) {
//            "Color" -> HSBType::class.java
//            "Number" -> DecimalType::class.java
//            "Switch" -> OnOffType::class.java
//            "Contact" -> OpenClosedType::class.java
//            "Dimmer" -> PercentType::class.java
//            else -> try {
//                generateServiceActionClass(stateType + "Type")
//            } catch (ex: CouldNotPerformException) {
//                throw CouldNotPerformException(
//                    "Could not lookup serviceAction class based on communication type [$stateType]",
//                    ex
//                )
//            }
//        }
    }
}
