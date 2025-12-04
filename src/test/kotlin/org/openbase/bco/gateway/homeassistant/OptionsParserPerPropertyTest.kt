package org.openbase.bco.gateway.homeassistant

import org.openbase.bco.gateway.homeassistant.option.OptionsParser
import kotlin.test.Test
import kotlin.test.assertNull
import org.amshove.kluent.shouldBeEqualTo

class OptionsParserPerPropertyTest {

    @Test
    fun `parseToContext returns null for blank or malformed`() {
        assertNull(OptionsParser.parseToContext(""))
    }

    @Test
    fun `parseHost and parsePort and admin fields work from context`() {
        val json = """
            {
              "BCO_MIDDLEWARE_HOST": "host.example",
              "BCO_MIDDLEWARE_PORT": "4242",
              "BCO_ADMIN": "adminUser",
              "BCO_ADMIN_PASSWORD": "pw"
            }
        """.trimIndent()

        val ctx = OptionsParser.parseToContext(json)!!
        OptionsParser.parseHost(ctx) shouldBeEqualTo "host.example"
        OptionsParser.parsePort(ctx) shouldBeEqualTo 4242
        OptionsParser.parseAdmin(ctx) shouldBeEqualTo "adminUser"
        OptionsParser.parseAdminPassword(ctx) shouldBeEqualTo "pw"
    }

    @Test
    fun `parsePort returns null for non-numeric string`() {
        val json = "{ \"BCO_MIDDLEWARE_PORT\": \"notanumber\" }"
        val ctx = OptionsParser.parseToContext(json)!!
        assertNull(OptionsParser.parsePort(ctx))
    }

    @Test
    fun `parseAdmin returns null when missing`() {
        val json = "{ \"OTHER\": \"x\" }"
        val ctx = OptionsParser.parseToContext(json)!!
        assertNull(OptionsParser.parseAdmin(ctx))
        assertNull(OptionsParser.parseAdminPassword(ctx))
    }
}
