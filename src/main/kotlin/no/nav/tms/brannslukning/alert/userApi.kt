package no.nav.tms.brannslukning.alert

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userApi(alertRepository: AlertRepository) {
    get("/user/alerts") {
        alertRepository.webAlertsForUser(call.ident())
            .let { matchingDomains(it, call.domene()) }
            .map { it.tekstOrDefault(call.spraakkode()) }
            .map { WebAlert(spraakkode = it.spraakkode, tekst = it.tekst) }
            .let { call.respond(it) }
    }
}

private fun matchingDomains(alerts: List<WebTekst>, location: String) = alerts.filter {
    it.domener.any { domene ->
        location.startsWith(domene)
    }
}

private data class WebAlert(
    val spraakkode: String,
    val tekst: String
)

private fun ApplicationCall.ident() = request.headers["ident"] ?: throw IllegalArgumentException("Mangler ident")
private fun ApplicationCall.domene() = request.headers["domene"] ?: throw IllegalArgumentException("Mangler domene")
private fun ApplicationCall.spraakkode() = request.headers["spraakkode"]
