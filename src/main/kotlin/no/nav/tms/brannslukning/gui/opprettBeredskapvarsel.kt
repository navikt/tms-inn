package no.nav.tms.brannslukning.gui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.tms.brannslukning.alert.AlertRepository
import no.nav.tms.brannslukning.gui.FormInputField.Companion.getFormFieldValue
import kotlin.text.String

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
                                    FormInputField.LINK.htmlName -> hendelse.link = part.value
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

                    hendelse.parsedFile = parseIdentList(content)
                    AlertValidation.validerBeskjed(hendelse)
                    BeredskapvarselCache.putHendelse(hendelse)
                    call.respondSeeOther("varsel/${hendelse.id}/$oppsumeringEndpoint")
                }
            }

            route(oppsumeringEndpoint) {
                get {
                    val hendelse = call.tmpHendelse()
                    call.respondHtmlContent("Lag varsel – Oppsummering", true) {
                        h1 { +"Oppsummering" }
                        hendelseDl(hendelse, "hendelsedl composite-box-top")
                        form(classes = "composite-box-bottom") {
                            action = "/varsel/${hendelse.id}/$sendEndpoint"
                            method = FormMethod.post
                            button {
                                onClick =
                                    "return confirm('Vil du opprette ${hendelse.title} og sende varsel til ${hendelse.affectedCount} personer?')"
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
                val hendelse = call.parameters["varselId"]?.let {
                    alertRepository.fetchHendelse(it)
                } ?: throw IllegalArgumentException("Hendelse finnes ikke")

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

fun parseIdentList(datastream: ByteArray): IdentParseResult {

    val lines = String(datastream.stripBom())
        .lines()
        .filter { it.isNotBlank() }
        .takeIf { it.isNotEmpty() }

    if (lines == null) {
        return IdentParseResult.empty()
    }

    log.info { "Lines: ${lines.size}" }

    val valid = mutableListOf<String>()
    val errors = mutableListOf<IdentParseResult.Error>()

    lines.forEachIndexed { i, line ->
        if (IdentPattern.matches(line)) {
            valid.add(line)
        } else {
            if (line.length != 11) {
                errors.add(IdentParseResult.Error(i, IdentParseResult.Cause.Length))
            } else {
                errors.add(IdentParseResult.Error(i, IdentParseResult.Cause.Characters))
            }
        }
    }

    log.info { "Errors: ${errors.size}" }

    return if (errors.isEmpty()) {
        IdentParseResult.complete(valid)
    } else if (valid.isEmpty()) {
        IdentParseResult.error(errors)
    } else {
        IdentParseResult.partial(valid, errors)
    }
}

private val IdentPattern = "[0-9]{11}".toRegex()

fun ByteArray.stripBom(): ByteArray {
    val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    val utf16BEBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    val utf16LEBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())

    return if (size >= 3 && utf8Bom.contentEquals(sliceArray(0..2))) {
        log.info { "Removed UTF-8 BOM from start of file" }
        sliceArray(3..<size)
    } else if (size >= 2 && utf16BEBom.contentEquals(sliceArray(0..1))) {
        log.info { "Removed UTF-16 BE BOM from start of file" }
        sliceArray(2..<size)
    } else if (size >= 2 && utf16LEBom.contentEquals(sliceArray(0..1))) {
        log.info { "Removed UTF-16 LE BOM from start of file" }
        sliceArray(2..<size)
    } else {
        this
    }
}

class BadFileContent(override val message: String) : IllegalArgumentException()

data class IdentParseResult(
    val status: Status,
    val valid: List<String>,
    val errors: List<Error>
) {
    data class Error(
        val line: Int,
        val cause: Cause
    )

    enum class Status {
        Complete, // All lines parsed successfully
        Empty, // File contains no data
        Partial, // Some lines were erroneous
        Error // All lines were erroneous
    }

    enum class Cause {
        Length, Characters
    }

    companion object {
        fun complete(lines: List<String>) = IdentParseResult(Status.Complete, valid = lines, errors = emptyList())
        fun empty() = IdentParseResult(Status.Empty, valid = emptyList(), errors = emptyList())
        fun partial(lines: List<String>, errors: List<Error>) = IdentParseResult(Status.Partial, valid = lines, errors = errors)
        fun error(errors: List<Error>) = IdentParseResult(Status.Error, valid = emptyList(), errors = errors)
    }
}
