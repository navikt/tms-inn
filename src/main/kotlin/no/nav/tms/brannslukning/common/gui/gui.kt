package no.nav.tms.brannslukning.common.gui

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.tms.brannslukning.alert.AlertInfo
import no.nav.tms.brannslukning.alert.AlertRepository
import no.nav.tms.brannslukning.alert.userApi
import no.nav.tms.token.support.azure.validation.AzurePrincipal
import no.nav.tms.token.support.azure.validation.azure
import no.nav.tms.token.support.tokenx.validation.TokenXAuthenticator
import no.nav.tms.token.support.tokenx.validation.tokenX
import java.text.DateFormat
import java.time.format.DateTimeFormatter

fun Application.brannslukningApi(
    alertRepository: AlertRepository,
    authenticationConfig: Application.() -> Unit = {
        authentication {
            azure {
                setAsDefault = true
            }
            tokenX {
                setAsDefault = false
            }
        }
    }
) {

    val log = KotlinLogging.logger { }

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            dateFormat = DateFormat.getDateTimeInstance()
        }
    }

    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondText(text = "404: Page Not Found", status = status)
        }
        exception<Throwable> { call, cause ->
            log.error(cause) { "Ukjent feil" }
            when (cause) {
                is BadFileContent -> {
                    log.error(cause) { "Feil i fil" }
                    call.respondHtmlContent("Feil i identfil") {
                        p {
                            +cause.message
                        }
                        a {
                            href = "/"
                            +"Tilbake"
                        }
                    }
                }

                is HendelseNotFoundException -> {
                    log.info(cause) { "Fant ikke hendelse" }
                    call.respondHtmlContent("Hendelse ikke funnet") {
                        p {
                            +"Hendelsen du leter etter finnes ikke"
                        }
                        a {
                            href = "/"
                            +"Tilbake"
                        }
                    }
                }

                else -> {
                    log.error(cause) { "Ukjent feil" }
                    call.respondHtmlContent("Feil") {
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
    }

    authenticationConfig()

    routing {
        meta()
        authenticate {
            startPage(alertRepository)
            opprettHendelse(alertRepository)
            redigerHendelse(alertRepository)
        }
        authenticate(TokenXAuthenticator.name) {
            userApi(alertRepository)
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
        val aktiveHendelser : List<AlertInfo> =
            try {
                repository.activeAlerts()
            } catch (e: Exception) {
                println("Noe gikk feil med henting fra db")
                emptyList()
            }

        call.respondHtmlContent("Min side brannslukning – Start") {
            h1 { +"Hendelsesvarsling" }
            p {
                +"""Som en del av beredskapsplanen for nav.no kan du varsle brukere dersom det har skjedd en feil. 
                |Brukeren vil motta en SMS/e-post og får en beskjed på Min side. """.trimMargin()
            }
            h2 { +"Aktive hendelser" }
            if (aktiveHendelser.isEmpty())
                p { +"Ingen aktive hendelser" }
            else
                ul(classes = "aktive-hendelser-list") {
                    aktiveHendelser.forEach {
                        li {
                            a {
                                href = "hendelse/${it.referenceId}"
                                +"${it.opprettet.format(DateTimeFormatter.ofPattern(" dd.MM.yyyy"))}: ${it.tekster.tittel}"
                            }
                        }
                    }
                }

            a(classes = "btnlink") {
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

