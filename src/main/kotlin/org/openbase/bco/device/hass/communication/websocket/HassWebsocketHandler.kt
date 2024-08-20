package org.openbase.bco.device.hass.communication.websocket

import org.slf4j.LoggerFactory
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.atomic.AtomicReference

class HassWebsocketHandler : WebSocketHandler {
    private val log = LoggerFactory.getLogger(HassWebsocketHandler::class.java)

    private val sessionRef = AtomicReference<WebSocketSession?>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        log.debug("Connection to org.openbase.bco.device.hass.communication.websocket established!")
        sessionRef.set(session)
    }

    override fun handleMessage(session: WebSocketSession, message: WebSocketMessage<*>) {
        println("Received message: ${message.payload}")
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        throw exception
    }

    override fun afterConnectionClosed(session: WebSocketSession, closeStatus: CloseStatus) {
        sessionRef.set(null)
        log.debug("Connection to org.openbase.bco.device.hass.communication.websocket closed!")
    }

    override fun supportsPartialMessages(): Boolean = false

    fun sendMessage(message: String) {
        sessionRef.get()?.sendMessage(TextMessage(message))
    }
}