package no.nav.tms.brannslukning.common.gui

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
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
                                +"$cause"
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
        registeredRoutes.addAll(children)
    }

    log.info(registeredRoutes.joinToString("\n"))

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
        call.respondHtmlContent("Min side brannslukning – Start") {
            body {
                h1 { +"Send innhold om en hendelse til min-side" }
                ul {
                    li {
                        a {
                            href = "opprett"
                            +"Opprett hendelse"
                        }
                    }
                    li {
                        a {
                            href = "rediger"
                            +"Rediger hendelse"
                        }
                    }
                    li {
                        a {
                            href = "avslutt"
                            +"Avslutt hendelse"
                        }
                    }
                }
            }
        }
    }
}

suspend fun ApplicationCall.respondHtmlContent(tile: String, builder: HTML.() -> Unit) {
    this.respondHtml {
        title = tile
        builder()
    }
}

