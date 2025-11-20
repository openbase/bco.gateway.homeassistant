package org.openbase.bco.gateway.homeassistant.jp

import org.openbase.jps.core.AbstractJavaProperty

class JPHassRestEndpoint: AbstractJavaProperty<String>(COMMAND_IDENTIFIERS) {
    override fun generateArgumentIdentifiers(): Array<String> {
        return ARGUMENT_IDENTIFIERS
    }

    override fun getPropertyDefaultValue(): String {
        return DEFAULT_HOST
    }

    @Throws(Exception::class)
    override fun parse(list: List<String>): String {
        return oneArgumentResult
    }

    override fun getDescription(): String {
        return "Define the websocket endpoint of the Homeassistant instance this app should connect to."
    }

    companion object {
        private val ARGUMENT_IDENTIFIERS = arrayOf("WEBSOCKET_ENDPOINT")
        private val COMMAND_IDENTIFIERS = arrayOf("--homeassistant-rest-endpoint", "--hass-rest")

        private const val DEFAULT_HOST = "/api/"
    }
}
