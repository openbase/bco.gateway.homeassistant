package org.openbase.bco.gateway.homeassistant

import org.openbase.bco.gateway.homeassistant.option.OptionsParser
import kotlin.test.Test
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBe

class OptionsParserPerPropertyTest {

    @Test
    fun `parseToContext returns null for blank or malformed`() {
        OptionsParser.parseToContext("") shouldBe null
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
        OptionsParser.parsePort(ctx) shouldBe null
    }

    @Test
    fun `parseAdmin returns null when missing`() {
        val json = "{ \"OTHER\": \"x\" }"
        val ctx = OptionsParser.parseToContext(json)!!
        OptionsParser.parseAdmin(ctx) shouldBe null
        OptionsParser.parseAdminPassword(ctx) shouldBe null
    }
}
