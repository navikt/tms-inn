package no.nav.tms.brannslukning.alert

import java.time.ZonedDateTime

data class Alert(
    val referenceId: String,
    val tekster: Tekster,
    val opprettet: ZonedDateTime,
    val opprettetAv: Actor,
    val aktiv: Boolean
)

data class OpprettAlert(
    val referenceId: String,
    val tekster: Tekster,
    val opprettetAv: Actor
)

data class Tekster(
    val beskjed: WebTekst,
    val eksternTekst: EksternTekst
)

data class WebTekst(
    val spraakkode: String,
    val tekst: String
)

data class EksternTekst(
    val tittel: String,
    val innhold: String
)

data class Actor(
    val navIdent: String
)

data class AppendVarselQueue(
    val referenceId: String,
    val identer: List<String>
)

data class VarselRequest(
    val referenceId: String,
    val ident: String,
    val beskjed: WebTekst,
    val eksternTekst: EksternTekst
)
