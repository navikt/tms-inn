package no.nav.tms.brannslukning.common.gui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.tms.brannslukning.alert.AlertRepository

private val log = KotlinLogging.logger { }
fun Route.redigerHendelse(alertRepository: AlertRepository) {
    route("hendelse") {
        get("{id}") {

            val id = call.parameters["id"] ?: throw IllegalArgumentException("hendelseid må være tilstede i path")

            val hendelse = HendelseCache.getHendelse(id) ?: alertRepository.fetchHendelse(id)
            ?: throw IllegalArgumentException("Fant ikke hendelse med gitt id")

            call.respondHtmlContent("Hendelse detaljer", true) {
                h1 {
                    +"Hendelsedetaljer"
                }
                hendelseDl(hendelse)
                form {
                    action = "/hendelse/${hendelse.id}"
                    method = FormMethod.post
                    button {
                        type = ButtonType.submit
                        onClick = "return confirm('Vil du avslutte hendelsen?')"
                        +"Avslutt hendelse"
                    }
                }

                a {
                    href = "/"
                    +"Tilbake til forsiden"
                }
            }

        }
        //TODO fiks url
        post("{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("hendelseid må være tilstede i path")
            val hendelse =
                alertRepository.fetchHendelse(id) ?: throw IllegalArgumentException("Fant ikke hendelse med id $id")
            alertRepository.endAlert(hendelse.id, call.user)
            HendelseCache.tmpClose(hendelse.id)

            call.respondHtmlContent("Hendelse avsluttet", false) {
                h1 { +"Hendelse avsluttet" }
                hendelseDl(hendelse, avsluttetAv = call.user.username)

                a {
                    href = "/"
                    +"Tilbake til forsiden"
                }
            }
        }
    }

}
