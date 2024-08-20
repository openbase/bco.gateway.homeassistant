package org.openbase.bco.device.hass.communication.websocket

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.example.org.openbase.bco.device.homeassistant.jp.JPHassHost
import org.example.org.openbase.bco.device.homeassistant.jp.JpHassPort
import org.example.util.toRequest
import org.openbase.jps.core.JPService
import org.openbase.jul.iface.Activatable

class HassWebsocketConnection: Activatable {

    private val client = OkHttpClient()
    private var active = false
    private var ws: WebSocket? = null

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

        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiIzODAwZTZiZGNlMDk0NTU4ODAxNWY2YmFhNmZmMjgwYyIsImlhdCI6MTcyNDE3ODE1OSwiZXhwIjoyMDM5NTM4MTU5fQ.dPtFSBneq8xP9pHgdHdICmdif3XoAbY5RfpN_68d850"
        sendMessage(mapOf("type" to "auth", "access_token" to token).toRequest())
        sendMessage(mapOf("id" to 1, "type" to "ping").toRequest())
    }

    override fun deactivate() {
        active = false
        client.dispatcher.executorService.shutdown()
    }

    override fun isActive(): Boolean = active


    fun sendMessage(message: String): Boolean {
        return ws?.send(message) ?: false
    }

    private class EchoWebSocketListener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            // Do nothing
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            println("Receiving : $text")
            // Hier kannst du die Nachrichten verarbeiten, die die Entities und Services enthalten
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            println("Receiving bytes : " + bytes.hex())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            println("Closing : $code / $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            t.printStackTrace()
        }
    }

    companion object {
        const val PROTOCOL_TYPE = "ws"
        const val ENDPOINT = "/api/websocket"
    }
}
