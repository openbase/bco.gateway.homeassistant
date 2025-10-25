package org.openbase.bco.gateway.homeassistant.jp

import org.openbase.jps.core.AbstractJavaProperty

class JPHassToken: AbstractJavaProperty<String?>(COMMAND_IDENTIFIERS) {
    override fun generateArgumentIdentifiers(): Array<String> {
        return ARGUMENT_IDENTIFIERS
    }

    override fun getPropertyDefaultValue(): String? {
        return DEFAULT_HASS_TOKEN
    }

    @Throws(Exception::class)
    override fun parse(list: List<String>): String {
        return oneArgumentResult
    }

    override fun getDescription(): String {
        return "Define the supervisor api token of the Homeassistant instance this app should connect to."
    }

    companion object {
        private val ARGUMENT_IDENTIFIERS = arrayOf("TOKEN")
        private val COMMAND_IDENTIFIERS = arrayOf("--homeassistant-token", "--hass-token")

        private val DEFAULT_HASS_TOKEN: String? = System.getenv("SUPERVISOR_TOKEN")
    }
}
