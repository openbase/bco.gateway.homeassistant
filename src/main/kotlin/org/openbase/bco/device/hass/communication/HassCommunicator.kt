package org.openbase.bco.device.hass.communication

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import org.openbase.bco.device.hass.communication.websocket.WSSubscription
import org.openbase.bco.device.hass.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.device.hass.manager.dto.*
import org.openbase.bco.device.hass.util.await
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.exception.InitializationException
import org.openbase.jul.exception.NotAvailableException
import org.openbase.jul.exception.printer.ExceptionPrinter
import org.openbase.jul.iface.Shutdownable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class HassCommunicator private constructor() : HassConnection() {

    // ==========================================================================================================================================
    // Entities
    // ==========================================================================================================================================
    @Throws(CouldNotPerformException::class)
    fun registerEntity(entity: HassEntityDto): HassEntityDto {
        val entityList: MutableList<HassEntityDto> = ArrayList()
        entityList.add(entity)
        return registerEntities(entityList)[0]
    }

    @Throws(CouldNotPerformException::class)
    fun registerEntities(entityList: List<HassEntityDto>?): List<HassEntityDto> {
        return jsonElementToTypedList(
            JsonParser.parseString(putJson(STATES_WS_REQUEST, entityList)),
            HassEntityDto::class.java
        )
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
        return jsonToClass(
            JsonParser.parseString(delete(STATES_WS_REQUEST + SEPARATOR + entityId)),
            HassEntityDto::class.java
        )
    }

    @Throws(NotAvailableException::class)
    fun getEntity(entityId: String): HassEntityDto = jsonToClass(
        JsonParser.parseString(get(STATES_WS_REQUEST + SEPARATOR + entityId)),
        HassEntityDto::class.java
    )

    // ==========================================================================================================================================
    // Services
    // ==========================================================================================================================================
    fun callService(service: HassServiceDto): CompletableFuture<JsonElement?> =
        sendWSCommand(CALL_SERVICE_WS_REQUEST, gson.toJsonTree(service).asJsonObject)

    // ==========================================================================================================================================
    // Devices
    // ==========================================================================================================================================
    fun getDevices(): List<HassDeviceDto> =
        sendWSCommand(DEVICE_WS_REQUEST).await()
            ?.asJsonArray
            ?.map { gson.fromJson(it, HassDeviceDto::class.java) }
            .orEmpty()

    fun getEntities(): List<HassEntityDto> =
        sendWSCommand(ENTITIES_WS_REQUEST).await()
            ?.asJsonArray
            ?.map { gson.fromJson(it, HassEntityDto::class.java) }
            .orEmpty()

    // ==========================================================================================================================================
    // States
    // ==========================================================================================================================================
    fun getStates(): List<HassStateDto> =
        sendWSCommand(STATES_WS_REQUEST).await()
            ?.asJsonArray
            ?.map { gson.fromJson(it, HassStateDto::class.java) }
            .orEmpty()

    // ==========================================================================================================================================
    // Areas
    // ==========================================================================================================================================
    fun getAreas(): List<HassAreaDto> =
        sendWSCommand(AREA_WS_REQUEST).await()
            ?.asJsonArray
            ?.map { gson.fromJson(it, HassAreaDto::class.java) }
            .orEmpty()

    fun saveAreas(areas: List<HassAreaInputDto>): List<HassAreaDto> = TODO()

    // ==========================================================================================================================================
    // Floors
    // ==========================================================================================================================================
    fun getFloors(): List<HassFloorDto> =
        sendWSCommand(FLOOR_WS_REQUEST).await()
            ?.asJsonArray
            ?.map { gson.fromJson(it, HassFloorDto::class.java) }
            .orEmpty()
    fun saveFloors(floors: List<HassFloorInputDto>): List<HassFloorDto> = TODO()

    @Throws(CouldNotPerformException::class)
    override fun testConnection() {
        get(API_HEALTH, true)
    }

    fun subscribe(
        commandType: String,
        eventType: HassEventType? = null,
        eventProcessor: (event: SubscriptionEvent.Event) -> Any,
    ): WSSubscription = WSSubscription(
        commandType = commandType,
        eventType = eventType,
        eventProcessor = eventProcessor,
    )
        .also { subscribe(it) }

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

    enum class HassEventType(val eventTypeName: String) {
        CONFIG_UPDATE("config_updated"),
        FLOOR_UPDATE("floor_registry_updated"),
        AREA_UPDATE("area_registry_updated"),
        STATE_UPDATE("state_changed")
    }

    companion object {
        const val API_HEALTH: String = "/"
        const val STATES_WS_REQUEST: String = "get_states"
        const val EVENT_WS_SUBSCRIPTION: String = "subscribe_events"
        const val DEVICE_WS_REQUEST: String = "config/device_registry/list"
        const val AREA_WS_REQUEST: String = "config/area_registry/list"
        const val FLOOR_WS_REQUEST: String = "config/floor_registry/list"
        const val ENTITIES_WS_REQUEST: String = "config/entity_registry/list"
        const val CALL_SERVICE_WS_REQUEST = "call_service"

        private val LOGGER: Logger = LoggerFactory.getLogger(HassCommunicator::class.java)

        @get:Synchronized
        var instance: HassCommunicator = HassCommunicator().also {
            try {
                Shutdownable.registerShutdownHook(it)
            } catch (ex: InitializationException) {
                ExceptionPrinter.printHistory("Could not create HassCommunicator", ex, LOGGER)
            }
        }
    }
}
