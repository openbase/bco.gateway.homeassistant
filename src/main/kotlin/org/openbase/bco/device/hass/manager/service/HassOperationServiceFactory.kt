package org.openbase.bco.device.hass.manager.service

import org.openbase.bco.dal.lib.layer.service.OperationServiceFactory
import org.openbase.bco.dal.lib.layer.service.Services
import org.openbase.bco.dal.lib.layer.service.operation.OperationService
import org.openbase.bco.dal.lib.layer.unit.UnitController
import org.openbase.jul.processing.StringProcessor
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate
import java.lang.reflect.InvocationTargetException

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

class HassOperationServiceFactory : OperationServiceFactory {
    @Throws(org.openbase.jul.exception.InstantiationException::class)
    override fun <UNIT : UnitController<*, *>> newInstance(
        operationServiceType: ServiceTemplate.ServiceType,
        unit: UNIT
    ): OperationService {
        val serviceImplClassName =
            HassService::class.java.getPackage().name + "." + StringProcessor.transformUpperCaseToPascalCase(
                operationServiceType.name
            ) + "Impl"
        try {
            val serviceImplClass = Class.forName(serviceImplClassName)
            return serviceImplClass.getConstructor(Services.loadOperationServiceClass(operationServiceType))
                .newInstance(unit) as OperationService
        } catch (ex: ClassNotFoundException) {
            throw org.openbase.jul.exception.InstantiationException(HassService::class.java, ex)
        } catch (ex: NoSuchMethodException) {
            throw org.openbase.jul.exception.InstantiationException(HassService::class.java, ex)
        } catch (ex: IllegalAccessException) {
            throw org.openbase.jul.exception.InstantiationException(HassService::class.java, ex)
        } catch (ex: InstantiationException) {
            throw org.openbase.jul.exception.InstantiationException(HassService::class.java, ex)
        } catch (ex: InvocationTargetException) {
            throw org.openbase.jul.exception.InstantiationException(HassService::class.java, ex)
        }
    }

    companion object {
        val instance: OperationServiceFactory = HassOperationServiceFactory()
    }
}
