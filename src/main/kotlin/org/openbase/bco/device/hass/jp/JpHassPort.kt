package org.openbase.bco.device.hass.jp

import org.openbase.jps.core.AbstractJavaProperty

class JpHassPort: AbstractJavaProperty<Int>(COMMAND_IDENTIFIERS) {
    override fun generateArgumentIdentifiers(): Array<String> {
        return ARGUMENT_IDENTIFIERS
    }

    override fun getPropertyDefaultValue(): Int {
        return DEFAULT_PORT
    }

    @Throws(Exception::class)
    override fun parse(list: List<String>): Int {
        return oneArgumentResult.toInt()
    }

    override fun getDescription(): String {
        return "Define the port of the Homeassistant instance this app should connect to."
    }

    companion object {
        private val ARGUMENT_IDENTIFIERS = arrayOf("PORT")
        private val COMMAND_IDENTIFIERS = arrayOf("--homeassistant-port", "--hass-port")

        private const val DEFAULT_PORT = 8123
    }
}
