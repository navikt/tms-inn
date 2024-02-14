package no.nav.tms.brannslukning.alert.setup.database

import no.nav.tms.brannslukning.alert.*
import no.nav.tms.brannslukning.gui.User
import java.util.UUID.randomUUID


private val defaultTestAlert = OpprettAlert(
    referenceId = randomUUID().toString(),
    tekster = Tekster(
        tittel = "Larisa",
        beskrivelse = "Shain",
        beskjed = WebTekst(link = "Kerstin", spraakkode = "Brittan", tekst = "Hazel"),
        eksternTekst = EksternTekst(tittel = "Lucretia", tekst = "Laquana")
    ),
    opprettetAv = User("Testuser", "test-user"),
    mottakere = listOf("12345", "678910", "111213", "98764")
)

fun setupTestAltert(alertRepository: AlertRepository, database: LocalPostgresDatabase): Pair<OpprettAlert, List<VarselData>> {
    alertRepository.createAlert(defaultTestAlert)
    database.getVarselForAlert(defaultTestAlert.referenceId).forEach {
        alertRepository.markAsSent(
            defaultTestAlert.referenceId,
            it.ident,
            randomUUID().toString()
        )
    }
    return Pair(defaultTestAlert,database.getVarselForAlert(defaultTestAlert.referenceId))
}
