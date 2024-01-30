package no.nav.tms.brannslukning.common.gui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.tms.brannslukning.alert.AlertRepository

private val log = KotlinLogging.logger { }

fun Route.opprettHendelse(alertRepository: AlertRepository) {

    route("opprett") {
        get {
            call.respondHtmlContent("Opprett varsel – tekster", true) {
                h1 { +"Lag varsel" }
                hendelseForm(tmpHendelse = call.hendelseOrNull(), postEndpoint = "/opprett")
            }
        }

        post {
            val hendelse = call.hendelseOrNull()
            var beskjedTekst = ""
            var url = ""
            var eksternTekst = ""
            var title = ""
            var description = ""

            val multipartData = call.receiveMultipart()
            var content = byteArrayOf()

            multipartData.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        when (part.name) {
                            FormInputField.TITLE.htmlName -> title = part.value
                            FormInputField.DESCRIPTION.htmlName -> description = part.value
                            FormInputField.MIN_SIDE_TEXT.htmlName -> beskjedTekst = part.value
                            FormInputField.LINK.htmlName -> url = part.value
                            FormInputField.SMS_EPOST_TEKST.htmlName -> eksternTekst = part.value

                        }

                    }

                    is PartData.FileItem -> {
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
                        url = url,
                        affectedUsers = content.parseAndVerify() ?: emptyList()
                    )
            HendelseCache.putHendelse(tmpHendelse)
            call.respondSeeOther("/opprett/confirm?hendelse=${tmpHendelse.id}")
        }

        route("confirm") {
            get {
                val hendelse = call.hendelse()
                call.respondHtmlContent("Opprett hendelse – oppsummering", true) {

                    h1 { +"Oppsummering" }
                    hendelseDl(hendelse,"composite-box-top")
                    form(classes = "composite-box-bottom") {
                        action = "/send/confirm?hendelse=${hendelse.id}"
                        method = FormMethod.post
                        button {
                            onClick =
                                "return confirm('Vil du opprette ${hendelse.title} og sende varsel til ${hendelse.affectedUsers.size} personer?')"
                            type = ButtonType.submit
                            text("Send varsel")
                        }
                    }
                    cancelAndGoBackButtons("/opprett?hendelse=${hendelse.id}")
                }
            }
        }

    }

    route("send") {
        post("confirm") {
            val hendelse = call.hendelse()
            alertRepository.createAlert(hendelse.toOpprettAlert())
            HendelseCache.invalidateHendelse(hendelse.id)

            call.respondHtmlContent("Hendelse opprettet", true) {
                h1 { +"Kvittering" }
                div {
                    id = "kvittering"
                    div {
                        h2 {+"Hendelse opprettet"}
                        p { +"Du er ferdig!" }
                        p { +"Takk for at du er med å slukke branner"}
                    }
                    img {
                        id = "Brannmannkatt"
                        src = "/static/brannkatt.svg"
                        alt = "Brannmannkatt loves you!"
                        title = "Brannmannkatt loves you!"
                    }

                }
                h2 { +"Detaljer" }
                hendelseDl(hendelse,"")
                a(classes="btnlink neutral") {
                    id="kvittering-back-btn"
                    href = "/"
                    +"Til forsiden"
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

