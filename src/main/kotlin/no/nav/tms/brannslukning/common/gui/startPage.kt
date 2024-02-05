package no.nav.tms.brannslukning.common.gui

import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.tms.brannslukning.alert.AlertInfo
import no.nav.tms.brannslukning.alert.AlertRepository
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun Route.startPage(repository: AlertRepository) {
    get {
        val aktiveHendelser: List<AlertInfo> =
            try {
                repository.activeAlerts()
            } catch (e: Exception) {
                println("Noe gikk feil med henting fra db")
                emptyList()
            }

        call.respondHtmlContent("Min side brannslukning – Start", aktiveHendelser.isNotEmpty()) {
            h1 { +"Direktevarsling til berørte brukere" }
            p {
                id = "startpage-ingress"
                +"""Hvis du vet hvem som er berørt av hendelsen, skal disse brukerne få beskjed på Min side og direktevarsling per SMS og/eller e-post.""".trimMargin()
            }
            p {
                +"I SMS-en/e-posten skal brukeren få beskjed om å logge på nav.no for mer informasjon"
            }
            h2 { +"Aktive varsler" }
            if (aktiveHendelser.isEmpty())
                p { +"Ingen aktive  varsler" }
            else
                ul(classes = "aktive-hendelser-list") {
                    aktiveHendelser.forEach {
                        li {
                            a {
                                href = "hendelse/${it.referenceId}"
                                p { +it.tekster.tittel }
                                p { +"${it.opprettet.dayMonthYear()} ${it.opprettetAv.username}" }
                            }
                        }
                    }
                }

            a(classes = "btnlink") {
                id = "opprett-ny-btn"
                href = "varsel/bakgrunn"
                +"Lag nytt varsel"
            }

        }
    }
}

private fun ZonedDateTime.dayMonthYear() = format(DateTimeFormatter.ofPattern(" dd.MM.yyyy"))