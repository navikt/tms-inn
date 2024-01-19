package no.nav.tms.brannslukning.common.gui

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
                    call.respondHtmlContent("Feil i identfil") {
                        p {
                            +cause.message
                        }
                        a {
                            href = "/"
                            +"Tilbake"
                        }
                    }

                is HendelseNotFoundException ->
                    call.respondHtmlContent("Hendelse ikke funnet") {
                        p {
                            +"Hendelsen du leter etter finnes ikke"
                        }
                        a {
                            href = "/"
                            +"Tilbake"
                        }
                    }

                else ->
                    call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
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
            opprettHendelse(alertRepository)
            redigerHendelse(alertRepository)
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

fun Route.startPage(repository: AlertRepository) {
    get {
        val aktiveHendelser = repository.activeAlerts()
        call.respondHtmlContent("Min side brannslukning – Start") {
            h1 { +"Hendelsesvarsling" }
            img {
                id = "500-katt"
                src = "/static/500-katt.svg"
                alt = "500-cat loves you!"
                title = "500-cat loves you!"
            }
            h2 { +"Aktive hendelser" }
            if (aktiveHendelser.isEmpty())
                p { +"Ingen aktive hendelser" }
            else
                ul {
                    aktiveHendelser.forEach {
                        li {
                            a {
                                href = "hendelse/${it.referenceId}"
                                +"${it.tekster.beskrivelse} --sett inn dato og klokkeslett--"
                            }
                        }
                    }
                }

            a {
                href = "opprett"
                +"Opprett ny hendelse"
            }

        }
    }
}


val ApplicationCall.user
    get() = principal<AzurePrincipal>()?.let {
        User(it.decodedJWT.getClaim("oid").toString(), it.decodedJWT.getClaim("preferred_username").toString())
    } ?: throw IllegalStateException("Må være innlogget")

