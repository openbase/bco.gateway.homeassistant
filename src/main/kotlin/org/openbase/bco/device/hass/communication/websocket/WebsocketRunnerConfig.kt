package org.openbase.bco.device.hass.communication.websocket

import org.example.org.openbase.bco.device.homeassistant.jp.JPHassHost
import org.example.org.openbase.bco.device.homeassistant.jp.JpHassPort
import org.openbase.jps.core.JPService
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.stomp.StompSessionHandler
import org.springframework.web.socket.messaging.WebSocketStompClient

@Configuration
class WebSocketRunnerConfig {

    @Bean
    fun webSocketRunner(webSocketStompClient: WebSocketStompClient, stompSessionHandler: StompSessionHandler): ApplicationRunner {
        return ApplicationRunner {
            val url = "ws://${JPService.getValue(JPHassHost::class.java)}:${JPService.getValue(JpHassPort::class.java)}/api/websocket"
            webSocketStompClient.connect(url, stompSessionHandler)
        }
    }
}
