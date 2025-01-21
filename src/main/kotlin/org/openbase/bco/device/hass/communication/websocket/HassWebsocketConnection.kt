package org.openbase.bco.device.hass.communication.websocket

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.openbase.bco.device.hass.jp.JPHassHost
import org.openbase.bco.device.hass.jp.JpHassPort
import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.communication.TokenProvider
import org.openbase.bco.device.hass.communication.websocket.command.CommandResult
import org.openbase.bco.device.hass.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.device.hass.util.toRequest
import org.openbase.bco.device.hass.util.JsonUtils
import org.openbase.bco.device.hass.util.await
import org.openbase.jps.core.JPService
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.iface.Activatable
import org.openbase.jul.schedule.GlobalCachedExecutorService
import org.openbase.type.domotic.state.ConnectionStateType.ConnectionState
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

data class Subscription (
    val commandType: String = HassCommunicator.EVENT_WS_SUBSCRIPTION,
    val eventType: String? = null,
    val eventProcessor: (event: SubscriptionEvent.Event) -> Any,
)

class HassWebsocketConnection(
    private val tokenProvider: TokenProvider
) : Activatable {
    private val logger = LoggerFactory.getLogger(HassWebsocketConnection::class.java)

    @Volatile
    private var commandCounter: Long = 0
    private val client = OkHttpClient()
    private var active = false
    private var ws: WebSocket? = null
    val requestMap: MutableMap<Long, CompletableFuture<JsonElement?>> = mutableMapOf()
    val requestMapLock: ReentrantReadWriteLock = ReentrantReadWriteLock()
    private val subscriptions: MutableList<Subscription> = emptyList<Subscription>().toMutableList()

    @Volatile
    var connectionState: ConnectionState.State = ConnectionState.State.UNKNOWN
        set(value) {
            field = value

            logger.trace("connection state updated to {}", value)

            if (value == ConnectionState.State.CONNECTED) {
                GlobalCachedExecutorService.execute {
                    subscriptions.forEach { subscription ->
                        val json = JsonObject()
                        json.addProperty("event_type", subscription.eventType.toString())
                        sendCommand(subscription.commandType,json)
                            .await()
                            .also {
                                logger.info("subscribe on: $subscription")
                            }
                    }
                }
            }
        }

    /**
     * Subscribe has to be called before the client is activated.
     */
    fun subscribe(
        subscription: Subscription,
    ) {
        if(active) {
            error("Can not register a subscription on an already active client.")
        }

        subscriptions
            .find { it == subscription}
            ?.let { logger.error("Already subscribed to ${subscription.commandType}") }
            ?: subscriptions.add(subscription)
    }

    override fun activate() {
        active = true
        val request = Request.Builder()
            .url(
                PROTOCOL_TYPE +
                        "://" +
                        "${JPService.getValue(JPHassHost::class.java)}:" +
                        "${JPService.getValue(JpHassPort::class.java)}" +
                        ENDPOINT
            )
            .build()
        val listener = EventProcessor()
        ws = client.newWebSocket(request, listener)
    }

    override fun deactivate() {
        active = false
        client.dispatcher.executorService.shutdown()
    }

    override fun isActive(): Boolean = active

    fun ping() = sendCommand(COMMAND_PING)

    fun sendCommand(
        commandType: String,
        payload: JsonObject = JsonObject(),
    ): CompletableFuture<JsonElement?> {

        if(connectionState != ConnectionState.State.CONNECTED) {
            return CompletableFuture.failedFuture(CouldNotPerformException("Connection is not established."))
        }

        // prepare request
        val commandId: Long
        val resultFuture: CompletableFuture<JsonElement?>
        requestMapLock.write {
            commandId = ++commandCounter
            resultFuture = CompletableFuture<JsonElement?>()
            requestMap[commandId] = resultFuture
        }

        // generate payload
        payload.addProperty("id", commandId)
        payload.addProperty("type", commandType)

        // send message
        val success = ws?.send(payload.toString())

        // error handling
        if (success == null || success == false) {
            requestMapLock.write {
                requestMap.remove(commandId)
            }
            resultFuture.completeExceptionally(Exception("Could not send command."))
        }

        return resultFuture
    }

    inner class EventProcessor : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            logger.info("Connection to websocket server established.")
            connectionState = ConnectionState.State.CONNECTING
            super.onOpen(webSocket, response)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {

            val jsonResult = JsonParser.parseString(text)

            when (jsonResult.asJsonObject.getAsJsonPrimitive("type").asString) {
                "auth_required" -> {
                    logger.debug("Websocket authentication required.")
                    webSocket.send(mapOf("type" to "auth", "access_token" to tokenProvider.token).toRequest())
                    return
                }
                "auth_ok" -> {
                    logger.info("Websocket authentication successful.")
                    connectionState = ConnectionState.State.CONNECTED
                    return
                }
                "auth_invalid" -> {
                    logger.error("Websocket could not be authenticated.")
                    return
                }
                "event" -> {
                    JsonUtils.gson.fromJson(jsonResult, SubscriptionEvent::class.java).also { result ->
                        if (!result.event.data.entityId.contains("light")) return

                        subscriptions.find { it.eventType == result.event.eventType }?.eventProcessor?.invoke(result.event)
                    }
                    return
                }
                else -> {
                    // continue with command result handling
                }
            }

            val result = try {
                 JsonUtils.gson.fromJson(jsonResult, CommandResult::class.java)
            } catch (ex: Exception) {
                logger.error("Could not process result: $jsonResult")
                throw  ex
            }

            requestMapLock.write {
                if (result.success != false) {
                    requestMap.remove(result.id)?.complete(result.result) ?: also {
                        logger.info("Unknown Event: $jsonResult")
                    }
                } else {
                    requestMap.remove(result.id)?.completeExceptionally(CouldNotPerformException("Canceled by target"))
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            logger.info("Receiving bytes : " + bytes.hex())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            connectionState = ConnectionState.State.DISCONNECTED
            requestMapLock.write {
                requestMap.values.forEach {
                    it.completeExceptionally(CouldNotPerformException("Connection closed: $code / $reason"))
                }
            }
            super.onClosing(webSocket, code, reason)
        }

        override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: okhttp3.Response?) {
            logger.error("Connection failure!", throwable)
            super.onFailure(webSocket, throwable, response)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logger.error("Connection closed: $code - $reason")
            super.onClosed(webSocket, code, reason)
        }
    }

    companion object {
        const val PROTOCOL_TYPE = "ws"
        const val ENDPOINT = "/api/websocket"
        const val COMMAND_PING = "ping"
    }
}
