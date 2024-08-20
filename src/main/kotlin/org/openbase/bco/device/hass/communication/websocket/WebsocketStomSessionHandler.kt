package org.openbase.bco.device.hass.websocket

import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandler
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import java.lang.reflect.Type

class WebsocketStompSessionHandler : StompSessionHandlerAdapter() {

    private val logger = LoggerFactory.getLogger(StompSessionHandler::class.java)

    override fun afterConnected(session: StompSession, connectedHeaders: StompHeaders) {
        logger.info("Connected to WebSocket server")
        session.subscribe("/topic/messages", this)
        session.send("/app/chat", "Hello, WebSocket!")
    }

    override fun handleFrame(headers: StompHeaders, payload: Any?) {
        logger.info("Received: $payload")
    }

    override fun handleException(session: StompSession, command: StompCommand?, headers: StompHeaders, payload: ByteArray, exception: Throwable) {
        logger.error("Error in WebSocket session", exception)
    }

    override fun getPayloadType(headers: StompHeaders): Type {
        return String::class.java
    }
}
