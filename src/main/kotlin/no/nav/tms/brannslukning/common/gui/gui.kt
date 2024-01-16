package no.nav.tms.brannslukning.common.gui

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*

fun Application.gui() {
    //TODO: Auth

    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondText(text = "404: Page Not Found", status = status)
        }
        exception<Throwable> { call, cause ->
            when (cause) {
                is BadFileContent ->
                    call.respondHtmlContent("Feil i identfil") {
                        body {
                            p {
                                +cause.message
                            }
                            a {
                                href = "/"
                                +"Tilbake"
                            }
                        }
                    }

                is HendelseNotFoundException ->
                    call.respondHtmlContent("Hendelse ikke funnet") {
                        body {
                            p {
                                +"Hendelsen du leter etter finnes ikke"
                            }
                            a {
                                href = "/"
                                +"Tilbake"
                            }
                        }
                    }

                else ->
                    call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
            }
        }
    }

    val registeredRoutes = mutableListOf<Route>()
    routing {
        startPage()
        meta()
        opprettHendelse()
        redigerHendelse()
        staticResources("/static", "static") {
            preCompressed(CompressedFileType.GZIP)
        }

        registeredRoutes.addAll(children)

    }

    log.info(registeredRoutes.joinToString("        "))

}

fun Routing.meta() {
    get("isalive") {
        call.respond(HttpStatusCode.OK)
    }
    get("isready") {
        call.respond(HttpStatusCode.OK)
    }
}

fun Routing.startPage() {
    get {
        val aktiveHendelser = HendelseChache.getAllHendelser()
        call.respondHtmlContent("Min side brannslukning – Start") {
            body {
                h1 { +"Hendelsesvarsling" }
                img {
                    id = "admin-katt"
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
                                    href = "hendelse/${it.id}"
                                    +"Opprettet av ${it.initatedBy.preferredUsername} --sett inn dato og klokkeslett--"
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
}


suspend fun ApplicationCall.respondHtmlContent(title: String, builder: HTML.() -> Unit) {
    this.respondHtml {
        head {
            lang = "nb"
            title(title)
            link {
                rel = "stylesheet"
                href = "/static/style.css"
            }
        }
        builder()
    }
}

val ApplicationCall.user
    get() = User("Placeholder", "Placeholder")

