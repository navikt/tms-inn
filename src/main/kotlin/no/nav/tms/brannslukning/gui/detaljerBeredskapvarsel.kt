package no.nav.tms.brannslukning.gui

import VarselStatus
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.tms.brannslukning.alert.AlertRepository

fun Route.detaljerBeredskapvarsel(alertRepository: AlertRepository) {
    route("hendelse") {
        get("{id}") {

            val id = call.parameters["id"] ?: throw IllegalArgumentException("hendelseid må være tilstede i path")

            val beredskapsvarsel = alertRepository.fetchHendelse(id)
                ?: throw IllegalArgumentException("Fant ikke hendelse med gitt id")
            val varselstatus = alertRepository.varselStatus(id)

            call.respondHtmlContent("${beredskapsvarsel.title} – detaljer", true) {
                beredskapsvarselStatusLinje(varselstatus)

                hendelseDl(beredskapsvarsel, classesOverride = "")
                form(classes = "black-and-white-form") {
                    action = "/hendelse/${beredskapsvarsel.id}"
                    method = FormMethod.post
                    button {
                        type = ButtonType.submit
                        onClick = "return confirm('Vil du avslutte hendelsen?')"
                        +"Avslutt hendelse"
                    }
                }

                a(classes = "btnlink neutral") {
                    href = "/"
                    +"Tilbake til forsiden"
                }
            }

        }

        post("{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("hendelseid må være tilstede i path")
            val hendelse =
                alertRepository.fetchHendelse(id) ?: throw IllegalArgumentException("Fant ikke hendelse med id $id")
            alertRepository.endAlert(hendelse.id, call.user)
            BeredskapvarselCache.tmpClose(hendelse.id)

            call.respondHtmlContent("Hendelse avsluttet", false) {
                h1 { +"Hendelse avsluttet" }
                hendelseDl(hendelse, "", avsluttetAv = call.user.username)

                a(classes = "btnlink neutral") {
                    href = "/"
                    +"Tilbake til forsiden"
                }
            }
        }
    }
}

private fun MAIN.beredskapsvarselStatusLinje(varselStatus: VarselStatus) {
    dl {
        div {
            id = "status-line-header"
            this@dl.dt {
                +"Status"
            }
            this@dl.dd { +varselStatus.eksterneVarslerStatus.eksterneVarslerStatusTekst }
        }
        statusBoks("Eksterne varsler sendt", varselStatus.eksterneVarslerStatus.antallSendt.toString())
        statusBoks("Eksterne varsler feilet", varselStatus.eksterneVarslerStatus.antallFeilet.toString())
        statusBoks("Beskjed åpnet", "${varselStatus.lestProsent} %")

    }
}

fun DL.statusBoks(label: String, value: String) {
    div(classes = "status-dl-boks") {
        this@statusBoks.dt { +label }
        this@statusBoks.dd { +value }
    }

}
