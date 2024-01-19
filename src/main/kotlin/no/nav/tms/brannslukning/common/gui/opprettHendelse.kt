package no.nav.tms.brannslukning.common.gui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.tms.brannslukning.alert.AlertRepository
import detailsForm

private val log = KotlinLogging.logger { }

fun Route.opprettHendelse(alertRepository: AlertRepository) {

    route("opprett") {
        get {
            call.respondHtmlContent("Opprett hendelse – tekster") {
                h1 { +"Opprett hendelse" }
                detailsForm(tmpHendelse = call.hendelseOrNull(), postEndpoint = "/opprett")
            }
        }

        post {
            val hendelse = call.hendelseOrNull()
            val params = call.receiveParameters()
            val beskjedTekst =
                params["beskjed-text"] ?: throw IllegalArgumentException("Tekst for beskjed må være satt")
            val url =
                params["url"] ?: throw IllegalArgumentException("Url for beskjed må være satt")
            val eksternTekst =
                params["ekstern-text"] ?: throw IllegalArgumentException("Tekst for sms/epost må være satt")
            val title =
                params["title"] ?: throw IllegalArgumentException("Tittel må være satt")
            val description =
                params["description"] ?: ""

            val tmpHendelse =
                hendelse?.withUpdatedText(
                    beskjedTekst = beskjedTekst,
                    url = url,
                    eksternTekst = eksternTekst,
                    description = description,
                    title = title
                )
                    ?: TmpHendelse(
                        description = description,
                        title = title,
                        varseltekst = beskjedTekst,
                        eksternTekst = eksternTekst,
                        initatedBy = call.user,
                        url = url
                    )
            HendelseCache.putHendelse(tmpHendelse)
            call.respondSeeOther("opprett/personer?hendelse=${tmpHendelse.id}")
        }

        route("personer") {
            get {
                call.respondUploadFileForm(call.hendelse())
            }
            post {
                val hendelse = call.hendelse()
                val multipartData = call.receiveMultipart()
                var fileDescription = ""
                var fileName = ""
                var content = byteArrayOf()

                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            fileDescription = part.value
                        }

                        is PartData.FileItem -> {
                            fileName = part.originalFileName as String
                            val fileBytes = part.streamProvider().readBytes()
                            content += fileBytes
                            log.info { content }
                        }

                        else -> {
                            throw IllegalArgumentException("Ukjent innholdt i opplastet fil")
                        }
                    }
                    part.dispose()
                }

                log.info { "$fileName opplastet\n$fileDescription" }
                val idents = content.parseAndVerify() ?: hendelse.affectedUsers
                HendelseCache.putHendelse(hendelse.withAffectedUsers(idents))
                call.respondSeeOther("/opprett/confirm?hendelse=${hendelse.id}")
            }
        }

        route("confirm") {
            get {
                val hendelse = call.hendelse()
                call.respondHtmlContent("Opprett hendelse – bekreft") {

                    h1 { +"Bekreft" }
                    hendelseDl(hendelse)
                    form {
                        action = "/send/confirm?hendelse=${hendelse.id}"
                        method = FormMethod.post
                        button {
                            type = ButtonType.submit
                            text("Opprett hendelse")
                        }
                        cancelAndGoBackButtons("/opprett/personer?hendelse=${hendelse.id}")
                    }
                }
            }
        }

    }

    route("send") {

        post("confirm") {
            val hendelse = call.hendelse()

            alertRepository.createAlert(hendelse.toOpprettAlert())

            HendelseCache.invalidateHendelse(hendelse.id)

            call.respondHtmlContent("Hendelse opprettet") {
                h1 { +"Hendelse opprettet" }
                hendelseDl(hendelse)
                a {
                    href = "/"
                    +"Tilbake til forsiden"
                }
            }
        }
    }
}

private fun ByteArray.parseAndVerify(): List<String>? =
    String(this).lines()
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.apply {
            if (this.any { identStr -> identStr.toDoubleOrNull() == null }) {
                throw BadFileContent("Liste av identer inneholder ugyldige tegn")
            }
            if (this.any { identStr -> identStr.length != 11 }) {
                throw BadFileContent("Liste av identer inneholder identer med feil antall siffer")
            }
        }

class BadFileContent(override val message: String) : IllegalArgumentException()

