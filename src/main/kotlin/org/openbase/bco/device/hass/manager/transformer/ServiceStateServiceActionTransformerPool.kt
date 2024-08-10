package org.openbase.bco.device.hass.manager.transformer

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
import org.example.org.openbase.bco.device.hass.manager.dto.ServiceAction
import org.openbase.bco.registry.remote.Registries
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.NotAvailableException
import org.openbase.jul.processing.StringProcessor
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate
import java.lang.reflect.InvocationTargetException

/**
 * @author [Tamino Huxohl](mailto:pleminoq@openbase.org)
 */
class ServiceStateServiceActionTransformerPool private constructor() {
    private val pool: MutableMap<String, ServiceStateServiceActionTransformer<Message, ServiceAction>> = HashMap()

    @Throws(NotAvailableException::class)
    fun <TRANSFORMER : ServiceStateServiceActionTransformer<Message, ServiceAction>> getTransformer(transformerClass: Class<TRANSFORMER>): TRANSFORMER {
        return getTransformer(transformerClass.simpleName) as TRANSFORMER
    }

    @Throws(NotAvailableException::class)
    fun <S : Message?, C : ServiceAction> getTransformer(
        serviceStateClass: Class<S>,
        commandClass: Class<C>
    ): ServiceStateServiceActionTransformer<Message, ServiceAction> {
        return getTransformer(serviceStateClass.simpleName, commandClass)
    }

    @Throws(CouldNotPerformException::class)
    fun <C : ServiceAction> getTransformer(
        serviceType: ServiceTemplate.ServiceType,
        serviceActionClass: Class<C>
    ): ServiceStateServiceActionTransformer<Message, ServiceAction> {
        val serviceStateName: String = StringProcessor.transformToPascalCase(
            Registries.getTemplateRegistry().getServiceTemplateByType(serviceType).communicationType.name
        )
        return getTransformer(serviceStateName, serviceActionClass)
    }

    @Throws(NotAvailableException::class)
    private fun <C : ServiceAction> getTransformer(
        serviceStateName: String,
        serviceActionClass: Class<C>
    ): ServiceStateServiceActionTransformer<Message, ServiceAction> {
        val simpleClassName = serviceStateName + serviceActionClass.simpleName + TRANSFORMER_CLASSNAME_POSTFIX
        return getTransformer(simpleClassName)
    }

    @Throws(NotAvailableException::class)
    private fun getTransformer(simpleClassName: String): ServiceStateServiceActionTransformer<Message, ServiceAction> {
        if (!pool.containsKey(simpleClassName)) {
            pool[simpleClassName] = loadTransformer(simpleClassName)
        }

        return pool[simpleClassName] ?: throw NotAvailableException(simpleClassName)
    }

    @Throws(NotAvailableException::class)
    private fun loadTransformer(simpleClassName: String): ServiceStateServiceActionTransformer<Message, ServiceAction> {
        val className = javaClass.getPackage().name + "." + simpleClassName
        try {
            return javaClass.classLoader.loadClass(className).getConstructor()
                .newInstance() as ServiceStateServiceActionTransformer<Message, ServiceAction>
        } catch (ex: InstantiationException) {
            throw NotAvailableException(simpleClassName, ex)
        } catch (ex: ClassNotFoundException) {
            throw NotAvailableException(simpleClassName, ex)
        } catch (ex: NoSuchMethodException) {
            throw NotAvailableException(simpleClassName, ex)
        } catch (ex: InvocationTargetException) {
            throw NotAvailableException(simpleClassName, ex)
        } catch (ex: IllegalAccessException) {
            throw NotAvailableException(simpleClassName, ex)
        } catch (ex: ClassCastException) {
            throw NotAvailableException(simpleClassName, ex)
        }
    }

    companion object {
        private const val TRANSFORMER_CLASSNAME_POSTFIX = "Transformer"

        @get:Synchronized
        val instance: ServiceStateServiceActionTransformerPool = ServiceStateServiceActionTransformerPool()
    }
}
