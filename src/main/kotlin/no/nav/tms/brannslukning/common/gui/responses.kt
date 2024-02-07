package no.nav.tms.brannslukning.common.gui

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import kotlinx.html.*

fun MAIN.hendelseDl(
    tmpHendelse: TmpBeredskapsvarsel,
    classes: String,
    avsluttetAv: String? = null,
    showAffectedUsers: Boolean = true,
) {
    dl(classes = "hendelsedl $classes") {
        dt { +"Tittel" }
        dd { +tmpHendelse.title }
        tmpHendelse.description.takeIf { it.isNotEmpty() }?.also {
            dt { +"Beskrivelse" }
            dd { +it }
        }
        dt { +"Varselet er opprettet av" }
        dd { +tmpHendelse.initatedBy.username }
        dt { +"Tekst i beskjed på min side" }
        dd { +tmpHendelse.varseltekst!! }
        dt { +"Lenke i beskjed på min side/varselbjella" }
        dd {
            a {
                target = "_blank"
                href = tmpHendelse.url!!
                +tmpHendelse.url!!
            }
        }
        dt { +"Tekst i epost/SMS" }
        dd { +tmpHendelse.eksternTekst!! }
        avsluttetAv?.let {
            dt { +"Avsluttet av" }
            dd { +avsluttetAv }
        }
        if (showAffectedUsers) {
            dt { +"Antall personer som mottar sms/epost og varsel på min side" }
            dd { +"${tmpHendelse.affectedCount}" }
        }
    }
}

fun MAIN.cancelAndGoBackButtons(previousUrl: String? = null) {
    if (previousUrl != null) {
        a(classes = "btnlink back-and-cancel edit") {
            href = previousUrl
            +"Rediger"
        }
    }
    a(classes = "btnlink back-and-cancel") {
        href = "/"
        +"Avbryt"
    }
}

suspend fun ApplicationCall.respondSeeOther(endpoint: String) {
    response.headers.append(HttpHeaders.Location, "${request.headers["Origin"]}/$endpoint")
    respond(HttpStatusCode.SeeOther)
}

suspend fun ApplicationCall.respondHtmlContent(title: String, fireIsActive: Boolean, builder: MAIN.() -> Unit) {
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
            div(classes = "brannslukning-logo ${if (fireIsActive) "active-fire" else "no-active-fire"}") {
                a {
                    href = "/"
                    img {
                        alt = "Til forsiden"
                        id = "brannslukning"
                    }
                    p {
                        +"Brannslukning"
                    }
                }

            }
            main {
                builder()
            }
        }
    }
}

fun ApplicationCall.tmpHendelse(): TmpBeredskapsvarsel = parameters["varselId"]?.let {
    BeredskapvarselCache.getHendelse(it)
} ?: throw IllegalArgumentException("Ukjent hendelse")

fun ApplicationCall.tmpHendelseOrNull(): TmpBeredskapsvarsel? = request.queryParameters["hendelse"]?.let {
    BeredskapvarselCache.getHendelse(it)
}



