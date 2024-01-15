package no.nav.tms.brannslukning

import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.tms.token.support.tokendings.exchange.TokendingsServiceBuilder
import no.nav.tms.varsel.api.varsel.VarselConsumer

fun main() {
    val environment = Environment()

    val httpClient = HttpClientBuilder.build()

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
