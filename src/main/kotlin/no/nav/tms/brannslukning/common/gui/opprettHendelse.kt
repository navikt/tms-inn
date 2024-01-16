package no.nav.tms.brannslukning.common.gui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.html.*
import kotlin.math.min

private val log = KotlinLogging.logger { }

fun Routing.opprettHendelse() {

    route("opprett") {
        get {
            call.respondHtmlContent("Opprett ny hendelse") {
                body {
                    h1 { +"Opprett ny hendelse" }
                    form {
                        action = "/opprett"
                        method = FormMethod.post
                        fieldSet {
                            legend {
                                +"Beskjed på min side"
                            }
                            label {
                                htmlFor = "beskjed-input"
                                +"Tekst"
                            }
                            input {
                                id = "beskjed-input"
                                name = "beskjed-text"
                                type = InputType.text
                                required = true
                                maxLength="150"
                                minLength="50"
                            }
                        }
                        fieldSet {
                            legend {
                                +"Varsel på sms/epost"
                            }
                            label {
                                htmlFor = "ekstern-tekst-input"
                                +"Tekst"
                            }
                            input {
                                id = "ekstern-tekst-input"
                                name = "ekstern-text"
                                type = InputType.text
                                required = true
                                maxLength="150"
                                minLength="50"
                            }
                        }
                        button {
                            type = ButtonType.submit
                            text("Neste")
                        }
                    }
                }
            }
        }
        post {
            val params = call.receiveParameters()
            val beskjedTekst =
                params["beskjed-text"] ?: throw IllegalArgumentException("Tekst for beskjed må være satt")
            val eksternTekst =
                params["ekstern-text"] ?: throw IllegalArgumentException("Tekst for sms/epost må være satt")
            val tmpHendelse =
                TmpHendelse(varseltekst = beskjedTekst, eksternTekst = eksternTekst, initatedBy = call.user)
            HendelseChache.putHendelse(tmpHendelse)
            call.respondHtmlContent("Opprett ny hendelse – personer som er rammet") {
                body {
                    h1 { +"Personer som skal motta varsel" }
                    hendelseDl(tmpHendelse)
                    form {
                        action = "send/upload?hendelse=${tmpHendelse.id}"
                        method = FormMethod.post
                        encType = FormEncType.multipartFormData
                        label {
                            htmlFor = "ident-file"
                            +"Last opp identer for personer som skal motta varsel om hendelsen"
                        }
                        input {
                            id = "ident-file"
                            name = "ident"
                            accept = ".csv"
                            type = InputType.file
                            required=true
                        }
                        button {
                            type = ButtonType.submit
                            text("Neste")
                        }
                    }
                }

            }
        }
    }

    route("send") {
        post("upload") {
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
            val idents = content.parseAndVerify()
            val hendelse = call.hendelse().addAffectedUsers(idents)
            HendelseChache.putHendelse(hendelse)

            call.respondHtmlContent("Opprett hendelse – bekreft") {
                body {
                    h1 { +"Hendelsesoppsummering" }
                    hendelseDl(hendelse)
                    form {
                        action = "/send/confirm?hendelse=${hendelse.id}"
                        method = FormMethod.post
                        button {
                            type = ButtonType.submit
                            text("Opprett hendelse")
                        }
                    }
                }
            }
        }
        post("confirm") {
            val hendelse = call.hendelse()
            //TODO database og kafka og fest
            log.info { "TODO: Lagre i database og send varsel på kafka" }
            HendelseChache.invalidateHendelse(hendelse.id)

            call.respondHtmlContent("Hendelse opprettet") {
                body {
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
}

private fun ApplicationCall.hendelse(): TmpHendelse = request.queryParameters["hendelse"]?.let {
    HendelseChache.getHendelse(it)
} ?: throw IllegalArgumentException("queryparameter hendelse mangler")

private fun ByteArray.parseAndVerify(): List<String> =
    String(this).lines()
        .filter { it.isNotEmpty() }
        .apply {
            if (this.any { identStr -> identStr.toDoubleOrNull() == null }) {
                throw BadFileContent("Liste av identer inneholder ugyldige tegn")
            }
            if (this.any { identStr -> identStr.length != 11 }) {
                throw BadFileContent("Liste av identer inneholder identer med feil antall siffer")
            }
        }

class BadFileContent(override val message: String) : IllegalArgumentException()

fun BODY.hendelseDl(tmpHendelse: TmpHendelse, avsluttetAv: String?=null) {
    dl {
        dt { +"Opprettet av" }
        dd { +tmpHendelse.initatedBy.preferredUsername }
        if (tmpHendelse.affectedUsers.isNotEmpty()) {
            dt { +"Antall personer som mottar sms/epost og varsler på min side" }
            dd { +"${tmpHendelse.affectedUsers.size}" }
        }
        dt { +"Tekst i beskjed på min side" }
        dd { +tmpHendelse.varseltekst }
        dt { +"Tekst i epost/SMS" }
        dd { +tmpHendelse.eksternTekst }
        avsluttetAv?.let {
            dt { +"Avsluttet av" }
            dd { +avsluttetAv }
        }
    }
}