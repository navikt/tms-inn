package no.nav.tms.brannslukning.common.gui

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import kotlinx.html.*

fun BODY.hendelseDl(tmpHendelse: TmpHendelse, avsluttetAv: String? = null, uploadExists: Boolean = true) {
    dl(classes = "hendelsedl") {
        dt { +"Opprettet av" }
        dd { +tmpHendelse.initatedBy.preferredUsername }
        if (uploadExists) {
            dt { +"Antall personer som mottar sms/epost og varsler på min side" }
            dd { +"${tmpHendelse.affectedUsers.size}" }
        }
        dt { +"Tekst i beskjed på min side" }
        dd { +tmpHendelse.varseltekst }
        dt { +"Link til mer informasjon i beskjed på min side" }
        dd {
            a {
                target = "_blank"
                href = tmpHendelse.url
                +tmpHendelse.url
            }
        }
        dt { +"Tekst i epost/SMS" }
        dd { +tmpHendelse.eksternTekst }
        avsluttetAv?.let {
            dt { +"Avsluttet av" }
            dd { +avsluttetAv }
        }
    }
}

fun FORM.cancelAndGoBackButtons(previousUrl: String? = null) {
    if (previousUrl != null) {
        a(classes = "btnlink") {
            href = previousUrl
            +"Forrige"
        }
    }
    a(classes = "btnlink") {
        href = "/"
        +"Avbryt"
    }
}

suspend fun ApplicationCall.respondSeeOther(endpoint: String) {
    response.headers.append(HttpHeaders.Location, "${request.headers["Origin"]}/$endpoint")
    respond(HttpStatusCode.SeeOther)
}

suspend fun ApplicationCall.respondHtmlContent(title: String, builder: HTML.() -> Unit) {
    this.respondHtml {
        head {
            lang = "nb"
            title(title)
            link {
                rel = "stylesheet"
                href = "/static/style.css"
            }
        }
        builder()
    }
}

fun ApplicationCall.hendelse(): TmpHendelse =
    hendelseOrNull() ?: throw IllegalArgumentException("queryparameter hendelse mangler")

fun ApplicationCall.hendelseOrNull(): TmpHendelse? = request.queryParameters["hendelse"]?.let {
    HendelseChache.getHendelse(it)
}

internal suspend fun ApplicationCall.respondUploadFileForm(tmpHendelse: TmpHendelse) {
    respondHtmlContent("Opprett ny hendelse – personer som er rammet") {
        body {
            h1 { +"Legg til personer som skal varsles" }
            hendelseDl(tmpHendelse = tmpHendelse, uploadExists = hendelse().affectedUsers.size > 0)
            form {
                action = "/opprett/personer?hendelse=${tmpHendelse.id}"
                method = FormMethod.post
                encType = FormEncType.multipartFormData
                fieldSet {
                    legend { +"Fødselsunmmer" }
                    label {
                        htmlFor = "ident-file"
                        +"Last opp csv-fil"
                    }
                    input {
                        id = "ident-file"
                        name = "ident"
                        accept = ".csv"
                        type = InputType.file
                        required = hendelse().affectedUsers.isEmpty()
                    }
                }
                button {
                    type = ButtonType.submit
                    text("Neste")
                }
                cancelAndGoBackButtons("/opprett?hendelse=${tmpHendelse.id}")
            }
        }
    }
}
