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
    val mottakere: List<String>,
    val aktivFremTil: ZonedDateTime?
)

data class Tekster(
    val tittel: String,
    val beskrivelse: String,
    val beskjed: Beskjed,
    val webTekst: WebTekst
)

data class Tekst(
    val spraakkode: String,
    val tekst: String,
    val default: Boolean
)

data class Beskjed(
    val link: String,
    val tekster: List<Tekst>,
    val eksternTekst: EksternTekst
) {
    fun defaultTekst() = when {
        tekster.size == 1 -> tekster.first()
        tekster.size > 1 -> tekster.first { it.default }
        else -> throw IllegalStateException("Beskjed må ha minst 1 tekst")
    }

    companion object {
        fun withTekst(link: String, spraakkode: String, internTekst: String, eksternTekst: String, eksternTittel: String) =
            Beskjed(
                link = link,
                tekster = listOf(
                    Tekst(
                        spraakkode = spraakkode,
                        tekst = internTekst,
                        default = true
                    )
                ),
                eksternTekst = EksternTekst(
                    tittel = eksternTittel,
                    tekst = eksternTekst
                )
            )
    }
}

data class WebTekst(
    val tekster: List<Tekst>,
    val domener: List<String>
) {
    fun tekstOrDefault(spraakkode: String?): Tekst {
        return if (tekster.size == 1) {
            tekster.first()
        } else if (spraakkode == null) {
            tekster.first { it.default }
        } else {
            tekster.find { it.spraakkode.lowercase() == spraakkode.lowercase() }
                ?: tekster.first { it.default }
        }
    }
}

data class EksternTekst(
    val tittel: String,
    val tekst: String
)

data class VarselRequest(
    val referenceId: String,
    val ident: String,
    val beskjed: Beskjed,
    val eksternTekst: EksternTekst
)
