package no.nav.tms.brannslukning.common.gui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.tms.brannslukning.alert.AlertRepository
import no.nav.tms.brannslukning.common.gui.FormInputField.Companion.getFormFieldValue

private val log = KotlinLogging.logger { }

private const val teksterEndpoint = "tekster"
private const val bakgrunnEndpoint = "bakgrunn"
private const val oppsumeringEndpoint = "oppsummering"
private const val sendEndpoint = "send"
private const val kvitteringEndpoint = "kvittering"
fun Route.opprettBeredskapvarsel(alertRepository: AlertRepository) {

    route("varsel") {
        route(bakgrunnEndpoint) {
            get {
                call.respondHtmlContent("Lag varsel – Bakgrunn", true) {
                    bakgrunnForm(tmpHendelse = call.tmpHendelseOrNull(), postEndpoint = bakgrunnEndpoint)
                }
            }

            post {
                val parameters = call.receiveParameters()
                val hendelse = call.tmpHendelseOrNull() ?: TmpBeredskapsvarsel(
                    title = parameters.getFormFieldValue(FormInputField.TITLE),
                    description = parameters.getFormFieldValue(FormInputField.DESCRIPTION),
                    initatedBy = call.user
                )
                BeredskapvarselCache.putHendelse(hendelse)
                call.respondSeeOther("varsel/${hendelse.id}/$teksterEndpoint")
            }
        }

        route("{varselId}") {
            route(teksterEndpoint) {
                get {
                    val hendelse = call.tmpHendelse()
                    call.respondHtmlContent("Lag varsel – tekster", true) {
                        h1 { +"Lag varsel" }
                        varselForm(
                            tmpHendelse = hendelse,
                            postEndpoint = "/varsel/${hendelse.id}/$teksterEndpoint"
                        )
                    }
                }

                post {
                    val hendelse = call.tmpHendelse()
                    val multipartData = call.receiveMultipart()
                    var content = byteArrayOf()

                    multipartData.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                when (part.name) {
                                    FormInputField.MIN_SIDE_TEXT.htmlName -> hendelse.varseltekst = part.value
                                    FormInputField.LINK.htmlName -> hendelse.url = part.value
                                    FormInputField.SMS_EPOST_TEKST.htmlName -> hendelse.eksternTekst = part.value
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

                    hendelse.affectedUsers = content.parseAndVerify()
                    BeredskapvarselCache.putHendelse(hendelse)
                    call.respondSeeOther("varsel/${hendelse.id}/$oppsumeringEndpoint")

                }
            }

            route(oppsumeringEndpoint) {
                get {
                    val hendelse = call.tmpHendelse()
                    call.respondHtmlContent("Lag varsel – Oppsummering", true) {
                        h1 { +"Oppsummering" }
                        hendelseDl(hendelse, "composite-box-top")
                        form(classes = "composite-box-bottom") {
                            action = "/varsel/${hendelse.id}/$sendEndpoint"
                            method = FormMethod.post
                            button {
                                onClick =
                                    "return confirm('Vil du opprette ${hendelse.title} og sende varsel til ${hendelse.affectedUsers.size} personer?')"
                                type = ButtonType.submit
                                text("Send varsel")
                            }
                        }
                        cancelAndGoBackButtons(teksterEndpoint)
                    }
                }

            }
            post(sendEndpoint) {
                val hendelse = call.tmpHendelse()
                alertRepository.createAlert(hendelse.toOpprettAlert())
                BeredskapvarselCache.invalidateHendelse(hendelse.id)
                call.respondSeeOther("varsel/${hendelse.id}/$kvitteringEndpoint")
            }

            get(kvitteringEndpoint) {
                val hendelse = call. parameters["varselId"]?.let {
                    alertRepository.fetchHendelse(it)
                }?: throw IllegalArgumentException("Hendelse finnes ikke")

                call.respondHtmlContent("Hendelse opprettet", true) {
                    h1 { +"Kvittering" }
                    div {
                        id = "kvittering"
                        div {
                            h2 { +"Hendelse opprettet" }
                            p { +"Du er ferdig!" }
                            p { +"Takk for at du er med å slukke branner" }
                        }
                        img {
                            id = "Brannmannkatt"
                            src = "/static/brannkatt.svg"
                            alt = "Brannmannkatt loves you!"
                            title = "Brannmannkatt loves you!"
                        }

                    }
                    h2 { +"Detaljer" }
                    hendelseDl(
                        hendelse, ""
                    )
                    a(classes = "btnlink neutral") {
                        id = "kvittering-back-btn"
                        href = "/"
                        +"Til forsiden"
                    }
                }
            }
        }

    }
}

private fun ByteArray.parseAndVerify(): List<String> =
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
        } ?: throw BadFileContent("Liste av identer er tom")

class BadFileContent(override val message: String) : IllegalArgumentException()
