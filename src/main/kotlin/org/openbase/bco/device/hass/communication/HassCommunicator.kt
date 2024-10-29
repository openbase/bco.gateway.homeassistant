package org.openbase.bco.device.hass.communication

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import jakarta.ws.rs.core.MediaType
import org.openbase.bco.device.hass.manager.dto.HassEntityDto
import org.example.org.openbase.bco.device.hass.manager.dto.ServiceAction
import org.openbase.bco.device.hass.manager.dto.HassDeviceDto
import org.openbase.bco.device.hass.manager.dto.HassAreaDto
import org.openbase.bco.device.hass.manager.dto.HassFloorDto
import org.openbase.bco.device.hass.manager.dto.HassStateDto
import org.openbase.bco.device.hass.utils.get
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.InitializationException
import org.openbase.jul.exception.NotAvailableException
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.jul.iface.Shutdownable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

class HassCommunicator private constructor() : HassConnection() {

    // ==========================================================================================================================================
    // ITEMS
    // ==========================================================================================================================================
    @Throws(CouldNotPerformException::class)
    fun registerEntity(entity: HassEntityDto): HassEntityDto {
        val entityList: MutableList<HassEntityDto> = ArrayList()
        entityList.add(entity)
        return registerEntities(entityList)[0]
    }

    @Throws(CouldNotPerformException::class)
    fun registerEntities(entityList: List<HassEntityDto>?): List<HassEntityDto> {
        return jsonElementToTypedList(JsonParser.parseString(putJson(STATES_WS_REQUEST, entityList)), HassEntityDto::class.java)
    }

    @Throws(CouldNotPerformException::class)
    fun updateEntity(entity: HassEntityDto): HassEntityDto {
        return jsonToClass(
            JsonParser.parseString(putJson(STATES_WS_REQUEST + SEPARATOR + entity.entityId, entity)),
            HassEntityDto::class.java
        )
    }

    @Throws(CouldNotPerformException::class)
    fun deleteEntity(entity: HassEntityDto): HassEntityDto = deleteEntity(entity.entityId)

    @Throws(CouldNotPerformException::class)
    fun deleteEntity(entityId: String): HassEntityDto {
        LOGGER.warn("Delete item {}", entityId)
        return jsonToClass(JsonParser.parseString(delete(STATES_WS_REQUEST + SEPARATOR + entityId)), HassEntityDto::class.java)
    }

    @Throws(NotAvailableException::class)
    fun getEntity(entityId: String): HassEntityDto =
        try {
            jsonToClass(
                JsonParser.parseString(get(STATES_WS_REQUEST + SEPARATOR + entityId)),
                HassEntityDto::class.java
            )
        } catch (ex: CouldNotPerformException) {
            throw NotAvailableException("Entity with name[$entityId]")
        }

    fun hasEntity(entityId: String): Boolean {
        try {
            getEntity(entityId)
            return true
        } catch (ex: NotAvailableException) {
            return false
        }
    }

    @Throws(CouldNotPerformException::class)
    fun postServiceAction(entityId: String, serviceAction: ServiceAction) {
        postServiceAction(entityId, serviceAction.toString())
    }

    @Throws(CouldNotPerformException::class)
    fun postServiceAction(entityId: String, serviceAction: String?) {
        post(STATES_WS_REQUEST + SEPARATOR + entityId, serviceAction!!, MediaType.TEXT_PLAIN_TYPE)
    }

//    // ==========================================================================================================================================
//    // ITEM_CHANNEL_LINK
//    // ==========================================================================================================================================
//    @Throws(CouldNotPerformException::class)
//    fun registerEntityChannelLink(entityId: String?, channelUID: String?) {
//        registerEntityChannelLink(EntityChannelLinkDTO(entityId, channelUID, HashMap()))
//    }
//
//    @Throws(CouldNotPerformException::class)
//    fun registerEntityChannelLink(itemChannelLinkDTO: EntityChannelLinkDTO) {
//        putJson(
//            LINKS_TARGET + SEPARATOR + itemChannelLinkDTO.entityId + SEPARATOR + itemChannelLinkDTO.channelUID,
//            itemChannelLinkDTO
//        )
//    }
//
//    @Throws(CouldNotPerformException::class)
//    fun deleteEntityChannelLink(itemChannelLinkDTO: EntityChannelLinkDTO) {
//        deleteEntityChannelLink(itemChannelLinkDTO.entityId, itemChannelLinkDTO.channelUID)
//    }
//
//    @Throws(CouldNotPerformException::class)
//    fun deleteEntityChannelLink(entityId: String, channelUID: String) {
//        delete(LINKS_TARGET + SEPARATOR + entityId + SEPARATOR + channelUID)
//    }
//
//    @get:Throws(CouldNotPerformException::class)
//    val itemChannelLinks: List<EntityChannelLinkDTO>
//        get() = jsonElementToTypedList(JsonParser.parseString(get(LINKS_TARGET)), EntityChannelLinkDTO::class.java)

    // ==========================================================================================================================================
    // DISCOVERY
    // ==========================================================================================================================================
//    /**
//     * @param bindingId
//     *
//     * @return the discovery timeout in seconds
//     *
//     * @throws CouldNotPerformException
//     */
//    @Throws(CouldNotPerformException::class)
//    fun startDiscovery(bindingId: String): Int {
//        val response = post(
//            DISCOVERY_TARGET + SEPARATOR + BINDINGS_TARGET + SEPARATOR + bindingId + SCAN_TARGET,
//            "",
//            MediaType.APPLICATION_JSON_TYPE
//        )
//        val discoveryTimeout = response.toInt()
//
//        if (discoveryTimeout <= 0) {
//            throw CouldNotPerformException("Invalid discovery timeout. Maybe binding $bindingId is not available")
//        }
//
//        return discoveryTimeout
//    }
//
//    @Throws(CouldNotPerformException::class)
//    fun approve(entityUID: String, label: String?) {
//        post(INBOX_TARGET + SEPARATOR + entityUID + SEPARATOR + APPROVE_TARGET, label!!, MediaType.TEXT_PLAIN_TYPE)
//    }
//
//    @get:Throws(CouldNotPerformException::class)
//    val discoveryResults: List<DiscoveryResultDTO>
//        get() = jsonElementToTypedList(JsonParser.parseString(get(INBOX_TARGET)), DiscoveryResultDTO::class.java)

//    // ==========================================================================================================================================
//    // Extensions
//    // ==========================================================================================================================================
//    @Throws(CouldNotPerformException::class)
//    fun installBinding(bindingId: String) {
//        LOGGER.debug("Install Binding[$bindingId]")
//        post(
//            ADDONS_TARGET + SEPARATOR + ADDONS_BINDING_PREFIX + bindingId + SEPARATOR + INSTALL_TARGET,
//            "",
//            MediaType.APPLICATION_JSON_TYPE
//        )
//    }
//
//    fun isBindingInstalled(bindingId: String): Boolean {
//        try {
//            get(BINDINGS_TARGET + SEPARATOR + bindingId + SEPARATOR + CONFIG_TARGET)
//            LOGGER.debug("Binding[$bindingId] currently not installed!")
//            return true
//        } catch (ex: CouldNotPerformException) {
//            LOGGER.debug("Binding[$bindingId] is already installed.")
//            return false
//        }
//    }
//
//    @Throws(CouldNotPerformException::class)
//    fun uninstallBindings(bindingId: String) {
//        post(ADDONS_TARGET + SEPARATOR + bindingId + SEPARATOR + UNINSTALL_TARGET, "", MediaType.APPLICATION_JSON_TYPE)
//    }

    // ==========================================================================================================================================
    // Devices
    // ==========================================================================================================================================

    fun getDevices(): List<HassDeviceDto> =
        JsonParser.parseString(
            sendWSCommand(DEVICE_WS_REQUEST).get(Duration.ofSeconds(5))
        )
            .asJsonArray
            .map { gson.fromJson(it, HassDeviceDto::class.java) }

    fun getEntities(): List<HassEntityDto> =
        JsonParser.parseString(
            sendWSCommand(ENTITIES_WS_REQUEST).get(Duration.ofSeconds(5))
        )
            .asJsonArray
            .map { gson.fromJson(it, HassEntityDto::class.java) }

    // ==========================================================================================================================================
    // States
    // ==========================================================================================================================================
    fun getStates(): List<HassStateDto> =
        JsonParser.parseString(
            sendWSCommand(STATES_WS_REQUEST).get(Duration.ofSeconds(5))
        )
            .asJsonArray
            .map { gson.fromJson(it, HassStateDto::class.java) }

    // ==========================================================================================================================================
    // Areas
    // ==========================================================================================================================================
    fun getAreas(): List<HassAreaDto> =
        JsonParser.parseString(
            sendWSCommand(AREA_WS_REQUEST).get(Duration.ofSeconds(5))
        )
            .asJsonArray
            .map { gson.fromJson(it, HassAreaDto::class.java) }

    // ==========================================================================================================================================
    // Floors
    // ==========================================================================================================================================
    fun getFloors(): List<HassFloorDto> =
        JsonParser.parseString(
            sendWSCommand(FLOOR_WS_REQUEST).get(Duration.ofSeconds(5))
        )
            .asJsonArray
            .map { gson.fromJson(it, HassFloorDto::class.java) }

    // ==========================================================================================================================================
    // UTIL
    // ==========================================================================================================================================
    @Throws(CouldNotPerformException::class)
    private fun <T> jsonElementToTypedList(jsonElement: JsonElement, clazz: Class<T>): List<T> {
        if (jsonElement.isJsonArray) {
            return jsonArrayToTypedList(jsonElement.asJsonArray, clazz)
        } else {
            throw CouldNotPerformException("JsonElement is not a JsonArray and thus cannot be converted to a list")
        }
    }

    @Throws(CouldNotPerformException::class)
    private fun <T> jsonArrayToTypedList(jsonArray: JsonArray, clazz: Class<T>): List<T> {
        val result: MutableList<T> = ArrayList()

        for (jsonElement in jsonArray) {
            result.add(jsonToClass(jsonElement, clazz))
        }

        return result
    }

    @Throws(CouldNotPerformException::class)
    private fun <T> jsonToClass(jsonElement: JsonElement, clazz: Class<T>): T {
        try {
            return gson.fromJson(jsonElement, clazz)
        } catch (ex: JsonSyntaxException) {
            throw CouldNotPerformException(
                "Could not parse jsonElement into object of class[" + clazz.simpleName + "]",
                ex
            )
        }
    }

    @Throws(CouldNotPerformException::class)
    override fun testConnection() {
        get(API_HEALTH, true)
    }

    companion object {
        const val API_HEALTH: String = "/"
        const val STATES_WS_REQUEST: String = "get_states"
        const val DEVICE_WS_REQUEST: String = "config/device_registry/list"
        const val AREA_WS_REQUEST: String = "config/area_registry/list"
        const val FLOOR_WS_REQUEST: String = "config/floor_registry/list"
        const val ENTITIES_WS_REQUEST: String = "config/entity_registry/list"
        const val LINKS_TARGET: String = "links"
        const val INBOX_TARGET: String = "inbox"
        const val DISCOVERY_TARGET: String = "discovery"
        const val ADDONS_TARGET: String = "addons"
        const val ADDONS_BINDING_PREFIX: String = "binding-"
        const val INSTALL_TARGET: String = "install"
        const val UNINSTALL_TARGET: String = "uninstall"
        const val BINDINGS_TARGET: String = "bindings"
        const val CONFIG_TARGET: String = "config"
        const val SCAN_TARGET: String = "scan"

        private val LOGGER: Logger = LoggerFactory.getLogger(HassCommunicator::class.java)

        @get:Synchronized
        var instance: HassCommunicator = HassCommunicator().also {
            try {
                    Shutdownable.registerShutdownHook(it)
            } catch (ex: InitializationException) {
                ExceptionPrinter.printHistory("Could not create HassCommunicator", ex, LOGGER)
            } catch (ex: CouldNotPerformException) {
                // only thrown if instance would be null
            }
        }
    }
}
