package org.openbase.bco.device.hass.jp

import org.openbase.jps.core.AbstractJavaProperty

class JPHassHost: AbstractJavaProperty<String>(COMMAND_IDENTIFIERS) {
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
        return "Define the host of the Homeassistant instance this app should connect to."
    }

    companion object {
        private val ARGUMENT_IDENTIFIERS = arrayOf("HOST")
        private val COMMAND_IDENTIFIERS = arrayOf("--homeassistant-host", "--hass-host")

        private const val DEFAULT_HOST = "localhost"
    }
}
