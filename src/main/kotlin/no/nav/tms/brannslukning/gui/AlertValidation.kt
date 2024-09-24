package no.nav.tms.brannslukning.gui

import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.builder.VarselActionBuilder
import java.util.*

object AlertValidation {
    fun validerBeskjed(beredskapsvarsel: TmpBeredskapsvarsel) {
        try {
            VarselActionBuilder.opprett {
                type = Varseltype.Beskjed
                varselId = UUID.randomUUID().toString()
                ident = "00000000000"
                link = beredskapsvarsel.nonBlankLinkOrNull()
                tekst = Tekst(
                    spraakkode = "nb",
                    tekst = beredskapsvarsel.varseltekst!!,
                    default = true
                )
                eksternVarsling = EksternVarslingBestilling(
                    prefererteKanaler = listOf(EksternKanal.SMS),
                    smsVarslingstekst = beredskapsvarsel.eksternTekst!!,
                    epostVarslingstekst = beredskapsvarsel.eksternTekst!!,
                )
                sensitivitet = Sensitivitet.Substantial
                produsent = Produsent("c", "n", "a")

            }
        } catch (e: NullPointerException) {
            throw BadInputException("varselTekst og eksternTekst må være satt")
        } catch (e: VarselValidationException) {
            throw BadInputException("gitt informasjon ikke godkjent som varsel", e.explanation)
        }
    }
}

class BadInputException(override val message: String, val explanation: List<String> = emptyList()): IllegalArgumentException()
