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

suspend fun ApplicationCall.respondHtmlContent(title: String, fireIsActive: Boolean, builder: BODY.() -> Unit) {
    this.respondHtml {
        head {
            lang = "nb"
            title(title)
            //    <link rel="preload" href="https://cdn.nav.no/aksel/@navikt/ds-css/5.7.3/index.min.css" as="style" />

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
            div(classes = "brannslukning-logo ${if (fireIsActive) "active-fire" else "no-active-fire"}") {
                img {
                    id = "brannslukning"
                }
                p {
                    +"Brannslukning"
                }
            }
            builder()
        }
    }
}

fun ApplicationCall.hendelse(): TmpHendelse =
    hendelseOrNull() ?: throw IllegalArgumentException("queryparameter hendelse mangler")

fun ApplicationCall.hendelseOrNull(): TmpHendelse? = request.queryParameters["hendelse"]?.let {
    HendelseCache.getHendelse(it)
}


