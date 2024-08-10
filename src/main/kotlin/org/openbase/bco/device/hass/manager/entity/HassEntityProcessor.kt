package org.openbase.bco.device.hass.manager.entity

import org.openbase.bco.registry.lib.util.UnitConfigProcessor
import org.openbase.bco.registry.unit.core.consistency.UnitAliasGenerationConsistencyHandler
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.NotAvailableException
import org.openbase.jul.processing.StringProcessor
import org.openbase.type.domotic.service.ServiceTemplateType.ServiceTemplate
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig

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

object HassEntityProcessor {
    const val ENTITY_SUBSEGMENT_DELIMITER: String = "_"
    const val ENTITY_SEGMENT_DELIMITER: String = "__"

    const val HASS_COLOR_TYPE: String = "Color" // Color information (RGB);	OnOff, IncreaseDecrease, Percent, HSB
    const val HASS_CONTACT_TYPE: String = "Contact" // Status of contacts, e.g. door/window contacts;	OpenClose
    const val HASS_DATE_TIME_TYPE: String = "DateTime" // Stores date and time;
    const val HASS_DIMMER_TYPE: String = "Dimmer" // Percentage value for dimmers;	OnOff, IncreaseDecrease, Percent
    const val HASS_GROUP_TYPE: String = "Group" // Entity to nest other entitys / collect them in groups;
    const val HASS_IMAGER_TYPE: String = "Image" // 	Binary data of an image;
    const val HASS_LOCATION_TYPE: String = "Location" // GPS coordinates;	Point
    const val HASS_NUMBER_TYPE: String = "Number" // Values in number format;	Decimal
    const val HASS_PLAYER_TYPE: String =
        "Player" // Allows control of players (e.g. audio players);	PlayPause, NextPrevious, RewindFastforward
    const val HASS_ROLLERSHUTTER_TYPE: String =
        "Rollershutter" // Roller shutter Entity, typically used for blinds;	UpDown, StopMove, Percent
    const val HASS_STRING_TYPE: String = "String" // Stores texts;	String
    const val HASS_SWITCH_TYPE: String = "Switch" //Switch Entity, typically used for lights (on/off);	OnOff

    //    enum EntityStateType {
    //        COLOR("Color", HSBType.class),
    //        CONTACT("Contact",org.hass.core.library.types.RawType),
    //        DATE_TIME("DateTime"),
    //        DIMMER("Dimmer"),
    //        GROUP("Group"),
    //        IMAGER("Image"),
    //        LOCATION("Location"),
    //        NUMBER("Number"),
    //        PLAYER("Player"),
    //        ROLLERSHUTTER("Rollershutter"),
    //        STRING("String"),
    //        SWITCH("Switch");
    //
    //        public final String name;
    //        public final Class<? extends Command> commandClass;
    //
    //        EntityStateType(String name, Class<? extends Command> commandClass) {
    //            this.name = name;
    //            this.commandClass = commandClass;
    //        }
    //    }
    fun generateEntityId(unitConfig: UnitConfig?, serviceType: ServiceTemplate.ServiceType): String {
        return UnitConfigProcessor.getDefaultAlias(unitConfig, "?")
            .replace(UnitAliasGenerationConsistencyHandler.ALIAS_NUMBER_SEPARATOR, ENTITY_SUBSEGMENT_DELIMITER) +
                ENTITY_SEGMENT_DELIMITER +
                StringProcessor.transformUpperCaseToPascalCase(serviceType.name)
    }

    @Throws(CouldNotPerformException::class)
    fun getMetaData(entityId: String): HassEntityIdMetaData {
        return HassEntityIdMetaData(entityId)
    }

    @Throws(NotAvailableException::class)
    fun getEntityType(serviceType: ServiceTemplate.ServiceType): String {
        return when (serviceType) {
            ServiceTemplate.ServiceType.COLOR_STATE_SERVICE -> HASS_COLOR_TYPE
            ServiceTemplate.ServiceType.POWER_CONSUMPTION_STATE_SERVICE, ServiceTemplate.ServiceType.TEMPERATURE_STATE_SERVICE, ServiceTemplate.ServiceType.BATTERY_STATE_SERVICE, ServiceTemplate.ServiceType.TARGET_TEMPERATURE_STATE_SERVICE, ServiceTemplate.ServiceType.ILLUMINANCE_STATE_SERVICE, ServiceTemplate.ServiceType.USER_TRANSIT_STATE_SERVICE, ServiceTemplate.ServiceType.SMOKE_STATE_SERVICE -> HASS_NUMBER_TYPE
            ServiceTemplate.ServiceType.BLIND_STATE_SERVICE -> HASS_ROLLERSHUTTER_TYPE
            ServiceTemplate.ServiceType.TAMPER_STATE_SERVICE, ServiceTemplate.ServiceType.DISCOVERY_STATE_SERVICE, ServiceTemplate.ServiceType.STANDBY_STATE_SERVICE, ServiceTemplate.ServiceType.SWITCH_STATE_SERVICE, ServiceTemplate.ServiceType.AVAILABILITY_STATE_SERVICE, ServiceTemplate.ServiceType.MOTION_STATE_SERVICE, ServiceTemplate.ServiceType.PRESENCE_STATE_SERVICE, ServiceTemplate.ServiceType.POWER_STATE_SERVICE, ServiceTemplate.ServiceType.BUTTON_STATE_SERVICE, ServiceTemplate.ServiceType.ACTIVATION_STATE_SERVICE, ServiceTemplate.ServiceType.SMOKE_ALARM_STATE_SERVICE, ServiceTemplate.ServiceType.FIRE_ALARM_STATE_SERVICE, ServiceTemplate.ServiceType.EARTHQUAKE_ALARM_STATE_SERVICE, ServiceTemplate.ServiceType.INTRUSION_ALARM_STATE_SERVICE, ServiceTemplate.ServiceType.MEDICAL_EMERGENCY_ALARM_STATE_SERVICE, ServiceTemplate.ServiceType.TEMPEST_ALARM_STATE_SERVICE, ServiceTemplate.ServiceType.WATER_ALARM_STATE_SERVICE, ServiceTemplate.ServiceType.TEMPERATURE_ALARM_STATE_SERVICE -> HASS_SWITCH_TYPE

            ServiceTemplate.ServiceType.CONTACT_STATE_SERVICE, ServiceTemplate.ServiceType.WINDOW_STATE_SERVICE, ServiceTemplate.ServiceType.DOOR_STATE_SERVICE -> HASS_CONTACT_TYPE
            ServiceTemplate.ServiceType.HANDLE_STATE_SERVICE -> HASS_STRING_TYPE
            ServiceTemplate.ServiceType.BRIGHTNESS_STATE_SERVICE -> HASS_DIMMER_TYPE
            ServiceTemplate.ServiceType.GLOBAL_POSITION_STATE_SERVICE -> HASS_LOCATION_TYPE
            else -> throw NotAvailableException("Hass entity type for service[" + serviceType.name + "]")
        }
    }

    class HassEntityIdMetaData internal constructor(entityId: String) {
        var alias: String? = null
        private var serviceType: ServiceTemplate.ServiceType? = null

        init {
            try {
                val nameSegment =
                    entityId.split(ENTITY_SEGMENT_DELIMITER.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                try {
                    alias = nameSegment[0].replace(
                        ENTITY_SUBSEGMENT_DELIMITER,
                        UnitAliasGenerationConsistencyHandler.ALIAS_NUMBER_SEPARATOR
                    )
                } catch (ex: IndexOutOfBoundsException) {
                    throw CouldNotPerformException("Could not extract alias from entity name!", ex)
                } catch (ex: NullPointerException) {
                    throw CouldNotPerformException("Could not extract alias from entity name!", ex)
                }

                try {
                    serviceType =
                        ServiceTemplate.ServiceType.valueOf(StringProcessor.transformToUpperCase(nameSegment[1]))
                } catch (ex: IndexOutOfBoundsException) {
                    throw CouldNotPerformException("Could not extract service type from entity name!", ex)
                } catch (ex: IllegalArgumentException) {
                    throw CouldNotPerformException("Could not extract service type from entity name!", ex)
                } catch (ex: NullPointerException) {
                    throw CouldNotPerformException("Could not extract service type from entity name!", ex)
                }
            } catch (ex: CouldNotPerformException) {
                throw CouldNotPerformException("Could not extract meta data out of entity name[$entityId]", ex)
            }
        }

        fun getServiceType(): ServiceTemplate.ServiceType? {
            return serviceType
        }
    }
}
