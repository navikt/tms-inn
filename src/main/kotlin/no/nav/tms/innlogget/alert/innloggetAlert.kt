package no.nav.tms.innlogget.alert

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.jackson.*
import no.nav.tms.token.support.azure.validation.azure

fun Application.innloggetAlert(
    httpClient: HttpClient,
    authInstaller: Application.() -> Unit = {
        authentication {
            azure {
                setAsDefault = true
            }
        }
    }
) {
    val securelog = KotlinLogging.logger("secureLog")

    authInstaller()

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            securelog.warn(cause) { "Kall til ${call.request.uri} feilet: ${cause.message}" }
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

    routing {
        metaRoutes()
        authenticate {
            varsel(varselConsumer)
        }
    }

    configureShutdownHook(httpClient)
}

private fun Application.configureShutdownHook(httpClient: HttpClient) {
    environment.monitor.subscribe(ApplicationStopping) {
        httpClient.close()
    }
}

fun ObjectMapper.jsonConfig(): ObjectMapper {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    return this
}
