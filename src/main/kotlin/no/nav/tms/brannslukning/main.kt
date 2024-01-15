package no.nav.tms.brannslukning

import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val environment = Environment()

    embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment {
            rootPath = "tms-varsel-api"
            module {
            }
            connector {
                port = 8080
            }
        }
    ).start(wait = true)
}
