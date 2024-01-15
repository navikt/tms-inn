package no.nav.tms.brannslukning

import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.tms.brannslukning.common.gui.gui

fun main() {
    //val environment = Environment()
    embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment {
            module { gui() }
            connector {
                port = 8080
            }
        }
    ).start(wait = true)
}
