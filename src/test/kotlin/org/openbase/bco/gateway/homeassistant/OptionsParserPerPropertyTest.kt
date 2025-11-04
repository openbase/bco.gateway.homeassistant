package org.openbase.bco.gateway.homeassistant

import org.openbase.bco.gateway.homeassistant.option.OptionsParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        assertEquals("host.example", OptionsParser.parseHost(ctx))
        assertEquals(4242, OptionsParser.parsePort(ctx))
        assertEquals("adminUser", OptionsParser.parseAdmin(ctx))
        assertEquals("pw", OptionsParser.parseAdminPassword(ctx))
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
