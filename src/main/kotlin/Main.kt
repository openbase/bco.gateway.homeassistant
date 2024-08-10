package org.example

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking

fun main() {
    val client = HttpClient(Java) {
        engine {
            pipelining = true
            protocolVersion = java.net.http.HttpClient.Version.HTTP_2
        }
    }

     runBlocking {
         val response = client.get("http://scummbar:8123/api/config") {
            bearerAuth("")
            headers {
                append("Content-Type", "application/json")
            }
        }

        println(response.body<String>())
    }
}
