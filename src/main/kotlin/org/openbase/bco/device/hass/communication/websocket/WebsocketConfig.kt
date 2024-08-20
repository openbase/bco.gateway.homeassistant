package org.openbase.bco.device.hass.communication.websocket

import org.openbase.bco.device.hass.websocket.WebsocketStompSessionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.stomp.StompSessionHandler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.socket.client.WebSocketClient
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient

@Configuration
class WebsocketConfig {
    @Bean
    fun webSocketClient(): WebSocketClient {
        return StandardWebSocketClient()
    }

    @Bean
    fun webSocketStompClient(webSocketClient: WebSocketClient): WebSocketStompClient {
        val stompClient = WebSocketStompClient(webSocketClient)
        stompClient.taskScheduler = ThreadPoolTaskScheduler().apply { initialize() }
        return stompClient
    }

    @Bean
    fun websocketHandler(): HassWebsocketHandler {
        return HassWebsocketHandler()
    }

    @Bean
    fun websocketService(websocketHandler: HassWebsocketHandler): WebsocketService {
        return WebsocketService(websocketHandler);
    }

    @Bean
    fun stompSessionHandler(): StompSessionHandler {
        return WebsocketStompSessionHandler()
    }
}