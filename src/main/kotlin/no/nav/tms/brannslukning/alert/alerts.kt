package no.nav.tms.brannslukning.alert

import java.time.ZonedDateTime

data class AlertInfo(
    val referenceId: String,
    val tekster: Tekster,
    val aktiv: Boolean,
    val opprettet: ZonedDateTime,
    val opprettetAv: Actor,
    val avsluttet: ZonedDateTime,
    val avsluttetAv: Actor?,
    val mottakere: Int
)

data class OpprettAlert(
    val referenceId: String,
    val tekster: Tekster,
    val opprettetAv: Actor,
    val mottakere: List<String>
)

data class Tekster(
    val beskrivelse: String,
    val beskjed: WebTekst,
    val eksternTekst: EksternTekst
)

data class WebTekst(
    val link: String,
    val spraakkode: String,
    val tekst: String
)

data class EksternTekst(
    val tittel: String,
    val tekst: String
)

data class Actor(
    val username: String,
    val oid: String
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
