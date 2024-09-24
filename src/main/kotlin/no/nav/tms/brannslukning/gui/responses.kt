package no.nav.tms.brannslukning.gui

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import kotlinx.html.*

fun MAIN.hendelseDl(
    tmpHendelse: TmpBeredskapsvarsel,
    classesOverride: String? = null,
    avsluttetAv: String? = null,
    showAffectedUsers: Boolean = true,
) {
    dl(classes = classesOverride ?: "hendelsedl") {
        dt { +"Tittel" }
        dd { +tmpHendelse.title }
        tmpHendelse.description.takeIf { it.isNotEmpty() }?.also {
            dt { +"Beskrivelse" }
            dd { +it }
        }
        dt { +"Tekst i epost/SMS" }
        dd { +tmpHendelse.eksternTekst!! }
        dt { +"Varselet er opprettet av" }
        dd { +tmpHendelse.initatedBy.username }
        dt { +"Tekst i beskjed på min side" }
        dd { +tmpHendelse.varseltekst!! }
        dt { +"Lenke i beskjed på min side/varselbjella" }
        dd {
            if (tmpHendelse.nonBlankLinkOrNull() != null) {
                a {
                    target = "_blank"
                    href = tmpHendelse.link!!
                    +tmpHendelse.link!!
                }
            } else {
                +"Ingen"
            }
        }
        avsluttetAv?.let {
            dt { +"Avsluttet av" }
            dd { +avsluttetAv }
        }
        if (showAffectedUsers) {
            dt { +"Antall personer som mottar sms/epost og varsel på min side" }
            dd { +"${tmpHendelse.affectedCount}" }
        }
        if (tmpHendelse.duplicates != 0) {
            dt { +"Duplikate identer i fil (kun ett varsel sendes per bruker)" }
            dd { +"${tmpHendelse.duplicates}" }
        }
        if (tmpHendelse.parseStatus != IdentParseResult.Status.Success) {
            displayErrors(tmpHendelse.parseStatus, tmpHendelse.errors)
        }

    }
}

private fun DL.displayErrors(status: IdentParseResult.Status, errors: List<IdentParseResult.Error>) {
    dt(classes = "error_text") { +"Feil ved lesing av identer fra fil" }

    if (status == IdentParseResult.Status.Empty) {
        dd(classes = "error_text") { +"Gitt fil er tom." }
    } else if (errors.size > 5) {
        dd(classes = "error_text") { +"Fant ${errors.size} feil. Pass på at det kun er én ident per linje, og at det ikke brukes uventede tegn." }
    } else {
        errors.forEach {
            val tekst = when(it.cause) {
                IdentParseResult.Cause.Length -> "feil antall sifre"
                IdentParseResult.Cause.Characters -> "uventede tegn i ident"
            }
            dd(classes = "error_text") { +"Linje ${it.line}: $tekst" }
        }
    }

    if (status == IdentParseResult.Status.Error) {
        dd(classes = "error_text") { +"Klarte ikke lese ut noen gyldige identer fra gitt fil." }
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

suspend fun ApplicationCall.respondHtmlContent(
    title: String,
    fireIsActive: Boolean,
    wide: Boolean = false,
    builder: MAIN.() -> Unit,
) {

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
            main(classes = if (wide) "wide" else "") {
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



