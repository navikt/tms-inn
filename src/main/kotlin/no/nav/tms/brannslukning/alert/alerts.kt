package no.nav.tms.brannslukning.alert

import no.nav.tms.brannslukning.common.gui.User
import java.time.ZonedDateTime

data class AlertInfo(
    val referenceId: String,
    val tekster: Tekster,
    val aktiv: Boolean,
    val opprettet: ZonedDateTime,
    val opprettetAv: User,
    val avsluttet: ZonedDateTime?,
    val avsluttetAv: User?,
    val mottakere: Int
)

data class OpprettAlert(
    val referenceId: String,
    val tekster: Tekster,
    val opprettetAv: User,
    val mottakere: List<String>
)

data class Tekster(
    val tittel: String,
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

data class VarselRequest(
    val referenceId: String,
    val ident: String,
    val beskjed: WebTekst,
    val eksternTekst: EksternTekst,
    val tittel: String
)
