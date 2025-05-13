package org.openbase.bco.device.hass.communication.websocket

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.communication.HassCommunicator.HassEventType
import org.openbase.bco.device.hass.communication.TokenProvider
import org.openbase.bco.device.hass.communication.websocket.command.CommandResult
import org.openbase.bco.device.hass.communication.websocket.command.SubscriptionEvent
import org.openbase.bco.device.hass.jp.JPHassHost
import org.openbase.bco.device.hass.jp.JpHassPort
import org.openbase.bco.device.hass.util.JsonUtils
import org.openbase.bco.device.hass.util.await
import org.openbase.bco.device.hass.util.isNull
import org.openbase.bco.device.hass.util.toRequest
import org.openbase.jps.core.JPService
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.iface.Activatable
import org.openbase.jul.schedule.GlobalCachedExecutorService
import org.openbase.type.domotic.state.ConnectionStateType.ConnectionState
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class WSSubscription (
    val commandType: String = HassCommunicator.EVENT_WS_SUBSCRIPTION,
    val eventType: HassEventType? = null,
    val eventProcessor: (event: SubscriptionEvent.Event) -> Any,
) {
    val payload = JsonObject().also { jsonObject ->
        eventType?.also { jsonObject.addProperty("event_type", eventType.eventTypeName) }
    }
}

class HassWebsocketConnection(
    private val tokenProvider: TokenProvider
) : Activatable {
    @Volatile
    private var commandCounter: Long = 0
    private val client = OkHttpClient()
    private var active = false
    private var ws: WebSocket? = null
    private val requestMap: MutableMap<Long, CompletableFuture<JsonElement?>> = mutableMapOf()
    private val requestMapLock: ReentrantReadWriteLock = ReentrantReadWriteLock()
    private val connectionStateLock: ReentrantReadWriteLock = ReentrantReadWriteLock()
    private val connectionStateCondition: Condition = connectionStateLock.writeLock().newCondition()
    private val subscriptionsLock = ReentrantReadWriteLock()
    private val subscriptions: MutableList<WSSubscription> = emptyList<WSSubscription>().toMutableList()

    @Volatile
    var connectionState: ConnectionState.State = ConnectionState.State.UNKNOWN
        set(value) {
            connectionStateLock.write {
                field = value
                connectionStateCondition.signalAll()
            }

            connectionStateLock.read {
                LOGGER.trace("connection state updated to {}", value)


                if (value == ConnectionState.State.CONNECTED) {
                    GlobalCachedExecutorService.execute {
                        subscriptionsLock.read {
                            subscriptions.forEach { subscription ->
                                sendSubscriptionRequest(subscription)
                                    .await()
                                    .also { LOGGER.info("subscribe on: $subscription") }
                            }
                        }
                    }
                }
            }
        }
        get() = connectionStateLock.read { field }

    @Throws(InterruptedException::class)
    fun waitForConnectionState(connectionState: ConnectionState.State) {
        connectionStateLock.write {
            while (this.connectionState != connectionState) {
                connectionStateCondition.await()
            }
        }
    }

    /**
     * Subscribe has to be called before the client is activated.
     */
    fun subscribe(
        subscription: WSSubscription,
    ) {
        subscriptionsLock.write {
            subscriptions
                .find { it == subscription}
                ?.let { LOGGER.error("Already subscribed to ${subscription.commandType}") }
                ?: subscriptions.add(subscription).also {
                    connectionStateLock.read {
                        if (connectionState == ConnectionState.State.CONNECTED) {
                            sendSubscriptionRequest(subscription)
                                .await()
                                .also { LOGGER.info("subscribe on: $subscription") }
                        }
                    }
                }
        }
    }

    fun unsubscribe(
        subscription: WSSubscription,
    ) {
        subscriptionsLock.write {
            subscriptions
                .find { it == subscription}
                ?.let { subscriptions.remove(it) }
        }
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

    private fun sendSubscriptionRequest(subscription: WSSubscription): CompletableFuture<JsonElement?> =
        sendCommand(subscription.commandType, subscription.payload)

    fun sendCommand(
        commandType: String,
        payload: JsonObject = JsonObject(),
    ): CompletableFuture<JsonElement?> {

        connectionStateLock.read {
            if (connectionState != ConnectionState.State.CONNECTED) {
                return CompletableFuture.failedFuture(CouldNotPerformException("Connection is not established."))
            }
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
        if (success.isNull() || success == false) {
            requestMapLock.write {
                requestMap.remove(commandId)
            }
            resultFuture.completeExceptionally(Exception("Could not send command."))
        }

        return resultFuture
    }

    inner class EventProcessor : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            LOGGER.info("Connection to websocket server established.")
            connectionState = ConnectionState.State.CONNECTING
            super.onOpen(webSocket, response)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {

            val jsonResult = JsonParser.parseString(text)

            when (jsonResult.asJsonObject.getAsJsonPrimitive("type").asString) {
                "auth_required" -> {
                    LOGGER.debug("Websocket authentication required.")
                    webSocket.send(mapOf("type" to "auth", "access_token" to tokenProvider.token).toRequest())
                    return
                }
                "auth_ok" -> {
                    LOGGER.info("Websocket authentication successful.")
                    connectionState = ConnectionState.State.CONNECTED
                    return
                }
                "auth_invalid" -> {
                    LOGGER.error("Websocket could not be authenticated.")
                    return
                }
                "event" -> {
                    JsonUtils.gson.fromJson(jsonResult, SubscriptionEvent::class.java).also { result ->
                        subscriptionsLock.read {
                            subscriptions.find { it.eventType?.eventTypeName == result.event.eventType }?.eventProcessor?.invoke(
                                result.event
                            )
                        }
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
                LOGGER.error("Could not process result: $jsonResult")
                throw  ex
            }

            requestMapLock.write {
                if (result.success != false) {
                    requestMap.remove(result.id)?.complete(result.result) ?: also {
                        LOGGER.info("Unknown Event: $jsonResult")
                    }
                } else {
                    requestMap
                        .remove(result.id)
                        ?.completeExceptionally(CouldNotPerformException("Request[$jsonResult] canceled by home assistant possibly because of an invalid request!"))
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            LOGGER.info("Receiving bytes : " + bytes.hex())
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
            LOGGER.error("Connection failure!", throwable)
            super.onFailure(webSocket, throwable, response)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            LOGGER.error("Connection closed: $code - $reason")
            super.onClosed(webSocket, code, reason)
        }
    }

    companion object {
        const val PROTOCOL_TYPE = "ws"
        const val ENDPOINT = "/api/websocket"
        const val COMMAND_PING = "ping"

        private val LOGGER = LoggerFactory.getLogger(HassWebsocketConnection::class.java)
    }
}
