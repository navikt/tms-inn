package no.nav.tms.brannslukning.common.gui

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import kotlinx.html.*

fun BODY.hendelseDl(tmpHendelse: TmpHendelse, avsluttetAv: String? = null, showAffectedUsers: Boolean = true) {
    dl(classes = "hendelsedl") {
        dt { +"Tittel" }
        dd { +tmpHendelse.title }
        tmpHendelse.description.takeIf { it.isNotEmpty() }?.also {
            dt { +"Beskrivelse" }
            dd { +it }
        }
        dt { +"Opprettet av" }
        dd { +tmpHendelse.initatedBy.username }
        if (showAffectedUsers) {
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

suspend fun ApplicationCall.respondHtmlContent(title: String, builder: BODY.() -> Unit) {
    this.respondHtml {
        head {
            lang = "nb"
            title(title)
            link {
                rel = "stylesheet"
                href = "/static/style.css"
            }
            link {
                rel = "stylesheet"
                href = "/static/forms.css"
            }
        }
        body {
            builder()
        }

    }
}

fun ApplicationCall.hendelse(): TmpHendelse =
    hendelseOrNull() ?: throw IllegalArgumentException("queryparameter hendelse mangler")

fun ApplicationCall.hendelseOrNull(): TmpHendelse? = request.queryParameters["hendelse"]?.let {
    HendelseCache.getHendelse(it)
}

internal suspend fun ApplicationCall.respondUploadFileForm(tmpHendelse: TmpHendelse) {
    respondHtmlContent("Opprett ny hendelse – personer som er rammet") {

        h1 { +"Legg til personer som skal varsles" }
        hendelseDl(tmpHendelse = tmpHendelse, showAffectedUsers = hendelse().affectedUsers.size > 0)
        form {
            action = "/opprett/personer?hendelse=${tmpHendelse.id}"
            method = FormMethod.post
            encType = FormEncType.multipartFormData
            fieldSet {
                input {
                    type=InputType.text
                    name="teksttest"
                }
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


