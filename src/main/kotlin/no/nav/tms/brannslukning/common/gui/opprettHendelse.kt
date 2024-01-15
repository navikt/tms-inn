package no.nav.tms.brannslukning.common.gui

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*

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
                        action = "send/upload"
                        method = FormMethod.post
                        label {
                            htmlFor = "ident-file"
                            +"Last opp identer for brukere som er påvirket"
                        }
                        input {
                            id = "ident-file"
                            type = InputType.file
                        }
                        button {
                            type = ButtonType.submit
                            text("Opprett hendelse")
                        }
                    }
                }

            }

            //TODO Legg til i db
        }
    }

    route("send"){
        post("upload") {
            val multipartData = call.receiveMultipart()
            multipartData.forEachPart { part ->
                println(part)
            }
            call.respond("OK")
        }
    }
}
