package org.openbase.bco.gateway.homeassistant.jp

import org.openbase.jps.core.AbstractJavaProperty

class JPBcoAdminPassword: AbstractJavaProperty<String>(COMMAND_IDENTIFIERS) {
    override fun generateArgumentIdentifiers(): Array<String> {
        return ARGUMENT_IDENTIFIERS
    }

    override fun getPropertyDefaultValue(): String {
        return DEFAULT_ADMIN_PASSWORD
    }

    @Throws(Exception::class)
    override fun parse(list: List<String>): String {
        return oneArgumentResult
    }

    override fun getDescription(): String {
        return "The password of the admin user needed for the initial setup to create a dedicated bco user for Home Assistant."
    }

    companion object {
        private val ARGUMENT_IDENTIFIERS = arrayOf("BCO_ADMIN_PASSWORD")
        private val COMMAND_IDENTIFIERS = arrayOf("--bco-admin-password")

        private const val DEFAULT_ADMIN_PASSWORD = "admin"
    }
}
