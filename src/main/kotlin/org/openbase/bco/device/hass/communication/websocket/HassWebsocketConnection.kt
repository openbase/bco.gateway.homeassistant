package org.openbase.bco.device.hass.communication.websocket

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.openbase.bco.device.hass.jp.JPHassHost
import org.openbase.bco.device.hass.jp.JpHassPort
import org.example.util.toRequest
import org.openbase.bco.device.hass.communication.TokenProvider
import org.openbase.bco.device.hass.communication.websocket.command.ResultCommand
import org.openbase.bco.device.hass.utils.JsonUtils
import org.openbase.jps.core.JPService
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.iface.Activatable
import org.openbase.type.domotic.state.ConnectionStateType.ConnectionState
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class HassWebsocketConnection(
    private val tokenProvider: TokenProvider
) : Activatable {
    private val logger = LoggerFactory.getLogger(HassWebsocketConnection::class.java)

    @Volatile
    private var commandCounter: Long = 0
    private val client = OkHttpClient()
    private var active = false
    private var ws: WebSocket? = null
    val requestMap: MutableMap<Long, CompletableFuture<String>> = mutableMapOf()
    val requestMapLock: ReentrantReadWriteLock = ReentrantReadWriteLock()

    @Volatile
    var connectionState: ConnectionState.State = ConnectionState.State.UNKNOWN

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
        val listener = EchoWebSocketListener()
        ws = client.newWebSocket(request, listener)
    }

    override fun deactivate() {
        active = false
        client.dispatcher.executorService.shutdown()
    }

    override fun isActive(): Boolean = active

    fun ping() = sendCommand(COMMAND_PING)

    fun sendCommand(command: String): Future<String?> {

        if(connectionState != ConnectionState.State.CONNECTED) {
            return CompletableFuture.failedFuture(CouldNotPerformException("Connection is not established."))
        }

        // prepare request
        val commandId: Long
        val resultFuture: CompletableFuture<String>
        requestMapLock.write {
            commandId = ++commandCounter
            resultFuture = CompletableFuture<String>()
            requestMap[commandId] = resultFuture
        }

        // send message
        val result = ws?.send(mapOf("id" to commandId, "type" to command).toRequest())

        // error handling
        if (result == null || result == false) {
            requestMapLock.write {
                requestMap.remove(commandId)
            }
            resultFuture.completeExceptionally(Exception("Could not send command."))
        }

        return resultFuture
    }

    inner class EchoWebSocketListener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            logger.info("Connection to websocket server established.")
            connectionState = ConnectionState.State.CONNECTING
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
                else -> {
                    // continue with command result handling
                }
            }

            val result = try {
                 JsonUtils.gson.fromJson(jsonResult, ResultCommand::class.java)
            } catch (ex: Exception) {
                logger.error("Could not process result: ${jsonResult}")
                throw  ex
            }

            requestMapLock.write {
                if (result.success != false) {
                    requestMap.remove(result.id)?.complete(result.result.toString())
                } else {
                    requestMap.remove(result.id)?.completeExceptionally(CouldNotPerformException("Canceled by target"))
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            logger.info("Receiving bytes : " + bytes.hex())
            TODO()
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            connectionState = ConnectionState.State.DISCONNECTED
            requestMapLock.write {
                requestMap.values.forEach {
                    it.completeExceptionally(CouldNotPerformException("Connection closed: $code / $reason"))
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            t.printStackTrace()
        }
    }

    companion object {
        const val PROTOCOL_TYPE = "ws"
        const val ENDPOINT = "/api/websocket"
        const val COMMAND_PING = "ping"
    }
}
