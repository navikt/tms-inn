package no.nav.tms.brannslukning.gui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.tms.brannslukning.alert.AlertRepository
import no.nav.tms.token.support.azure.validation.AzurePrincipal
import no.nav.tms.token.support.azure.validation.azure

fun Application.gui(alertRepository: AlertRepository) {

    val log = KotlinLogging.logger { }

    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondText(text = "404: Page Not Found", status = status)
        }
        exception<Throwable> { call, cause ->
            log.error(cause) { "Ukjent feil" }
            when (cause) {
                is BadFileContent ->
                    call.respondHtmlContent("Feil i identfil", true) {
                        p {
                            +cause.message
                        }
                        a {
                            href = "/"
                            +"Tilbake"
                        }
                    }

                is BadInputException ->
                    call.respondHtmlContent("Feil i tekster", true) {
                        p {
                            +cause.message
                            cause.explanation.forEach { explanation ->
                                +explanation
                            }
                        }
                        a {
                            href = "/"
                            +"Tilbake"
                        }
                    }


                is HendelseNotFoundException ->
                    call.respondHtmlContent("Hendelse ikke funnet", true) {
                        p {
                            +"Hendelsen du leter etter finnes ikke"
                        }
                        a {
                            href = "/"
                            +"Tilbake"
                        }
                    }

                else ->
                    call.respondHtmlContent("Feil", true) {
                        p { +"Oups..Nå ble det noe feil" }
                        p { +"${cause.message}" }
                        img {
                            id = "500-katt"
                            src = "/static/500-katt.svg"
                            alt = "500-cat loves you!"
                            title = "500-cat loves you!"
                        }
                    }
            }
        }
    }

    authentication {
        azure {
            setAsDefault = true
        }
    }

    routing {
        meta()
        authenticate {
            startPage(alertRepository)
            opprettBeredskapvarsel(alertRepository)
            detaljerBeredskapvarsel(alertRepository)
        }
        staticResources("/static", "static") {
            preCompressed(CompressedFileType.GZIP)
        }
    }
}

fun Routing.meta() {
    get("isalive") {
        call.respond(HttpStatusCode.OK)
    }
    get("isready") {
        call.respond(HttpStatusCode.OK)
    }
}


val ApplicationCall.user
    get() = principal<AzurePrincipal>()?.let {
        User(
            oid = it.decodedJWT.getClaim("oid").toString(),
            username = it.decodedJWT.getClaim("preferred_username").toString().removeSurrounding("\"")
        )
    } ?: throw IllegalStateException("Må være innlogget")

