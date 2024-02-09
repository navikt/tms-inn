package no.nav.tms.brannslukning.common.gui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.tms.brannslukning.alert.AlertInfo
import no.nav.tms.brannslukning.alert.AlertRepository
import statusColumns
import statusHeaders

private val log = KotlinLogging.logger { }
fun Route.startPage(repository: AlertRepository) {
    get {
        val aktiveHendelser: List<AlertInfo> =
            try {
                repository.activeAlerts()
            } catch (e: Exception) {
                log.error(e) { "Noe gikk feil med henting fra db" }
                //fix:bedre errorhandling
                emptyList()
            }

        call.respondHtmlContent(
            "Min side brannslukning – Start",
            aktiveHendelser.isNotEmpty(),
            wide = true
        ) {
            h1 { +"Direktevarsling til berørte brukere" }
            div {
                id = "startpage-ingress-div"
                p {
                    id = "startpage-ingress"
                    +"""Hvis du vet hvem som er berørt av hendelsen, skal disse brukerne få beskjed på Min side og direktevarsling per SMS og/eller e-post.""".trimMargin()
                }
                p {
                    +"I SMS-en/e-posten skal brukeren få beskjed om å logge på nav.no for mer informasjon"
                }
            }

            if (aktiveHendelser.isEmpty()) {
                h2 { +"Aktive varsler" }
                p { +"Ingen aktive  varsler" }
            } else
                table(classes = "aktive-hendelser-list") {
                    caption { +"Aktive varsler" }
                    tr(classes = "aktive-hendlelser-header") {
                        columnTh(
                            "Varsel",
                            "Status"
                        )
                        statusHeaders()
                    }
                    aktiveHendelser.forEach { alertInfo ->
                        tr {
                            th {
                                //funker ikke?
                                scope = ThScope.row
                                a {
                                    href = "hendelse/${alertInfo.referenceId}"
                                    p { +alertInfo.tekster.tittel }
                                    p { +alertInfo.tekster.beskrivelse }
                                }
                            }
                            td { +alertInfo.varselStatus.eksterneVarslerStatus.eksterneVarslerStatusTekst }
                            statusColumns(alertInfo.varselStatus)
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

fun TR.columnTh(vararg text: String) {
    text.forEach {
        th {
            scope = ThScope.col
            text(it)
        }
    }
}

