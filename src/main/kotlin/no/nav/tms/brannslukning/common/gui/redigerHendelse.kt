package no.nav.tms.brannslukning.common.gui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.html.*

private val log = KotlinLogging.logger { }
fun Routing.redigerHendelse() {
    route("hendelse") {
        get("{id}") {
            val hendelse = HendelseChache.getHendelse(
                call.parameters["id"] ?: throw IllegalArgumentException("hendelseid må være tilstede i path")
            ).also {
                log.info { "TODO: Hent fra database" }
            }

            call.respondHtmlContent("Hendelse detaljer") {
                body {
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
                }
            }

        }
        post("{id}") {
            val hendelse = HendelseChache.getHendelse(
                call.parameters["id"] ?: throw IllegalArgumentException("hendelseid må være tilstede i path")
            ).also {
                log.info { "TODO: Hent fra database" }
            }
            HendelseChache.tmpClose(hendelse.id)
            call.respondHtmlContent("Hendelse avsluttet") {
                body {
                    h1 { +"Hendelse avsluttet" }
                    hendelseDl(hendelse, avsluttetAv = call.user.preferredUsername)

                    a {
                        href = "/"
                        +"Tilbake til forsiden"
                    }
                }
            }
        }
    }

}
