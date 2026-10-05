package com.magrilink.web

import com.magrilink.web.config.SystemConfig
import com.magrilink.web.db.DatabaseFactory
import com.magrilink.web.routes.marketAnalyticsRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        )
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respondText(
                text = "Internal server error",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    install(CallLogging) {
        level = Level.INFO
    }

    routing {
        get("/") {
            call.respondText(SystemConfig.ENGINE_BANNER)
        }

        get("/health") {
            call.respondText("OK")
        }

        marketAnalyticsRoutes()
    }
}
