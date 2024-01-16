package no.nav.tms.brannslukning.common.gui

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.html.*

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
                            }
                        }
                        button {
                            type = ButtonType.submit
                            text("Opprett hendelse")
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
            val hendelse = Hendelse(varseltekst = beskjedTekst, eksternTekst = eksternTekst)
            HendelseChache.putHendelse(hendelse)
            call.respondHtmlContent("Opprett varsel for hendelse") {
                body {
                    h1 { +"Opprett varsel for hendelse" }
                    dl {
                        dt { +"Tekst i beskjed på minside" }
                        dd {
                            +beskjedTekst
                        }
                        dt { +"Beskjed i epost/SMS" }
                        dd {
                            +eksternTekst
                        }
                    }
                    form {
                        action = "send/upload?hendelse=${hendelse.id}"
                        method = FormMethod.post
                        encType = FormEncType.multipartFormData
                        label {
                            htmlFor = "ident-file"
                            +"Last opp identer for brukere som er påvirket"
                        }
                        input {
                            id = "ident-file"
                            name = "ident"
                            accept = ".csv"
                            type = InputType.file
                        }
                        button {
                            type = ButtonType.submit
                            text("Opprett hendelse")
                        }
                    }
                }

            }
        }
    }

    route("send") {
        post("upload") {
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
                        throw IllegalArgumentException("Ukjent innholdt i opplastet file")
                    }
                }
                part.dispose()
            }

            log.info { "$fileName opplastet\n$fileDescription" }
            val idents = content.parseAndVerify()
            HendelseChache.putHendelse(hendelse.addAffectedUsers(idents))

            call.respondHtmlContent("Send varsling") {
                body {
                    h1 { +"Send varsel og sms/epost for hendelse" }
                    dl {
                        dt { +"Opprettet av" }
                        dd { +"TODO, call.user" }
                        dt { +"Antall personer" }
                        dd { +"${idents.size}" }
                        dt { +"Tekst i beskjed på minside" }
                        dd {
                            +hendelse.varseltekst
                        }
                        dt { +"Beskjed i epost/SMS" }
                        dd {
                            +hendelse.eksternTekst
                        }
                    }
                    form {
                        action = "/send/confirm?hendelse=${hendelse.id}"
                        method = FormMethod.post
                        input {
                            type = InputType.submit
                            +"Send varsling og sms/epost"
                        }
                    }
                }
            }
        }
        post("confirm") {
            val hendelse = call.hendelse()
            //TODO database og kafka og fest
            HendelseChache.invalidateHendelse(hendelse.id)

            call.respondHtmlContent("Hendelse opprettet") {
                body {
                    h1 { +"Hendelse opprettet" }
                    dl {
                        dt { +"Opprettet av" }
                        dd { +"TODO, call.user" }
                        dt { +"Antall personer som mottar sms/epost og varsler på min side" }
                        dd { +"${hendelse.affectedUsers.size}" }
                        dt { +"Tekst i beskjed på min side" }
                        dd { +hendelse.varseltekst }
                        dt { +"Tekst i epost/SMS" }
                        dd { +hendelse.eksternTekst }
                    }

                    a {
                        href = "/"
                        +"Tilbake til forsiden"
                    }

                }

            }

        }
    }
}

private fun ApplicationCall.hendelse(): Hendelse = request.queryParameters["hendelse"]?.let {
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