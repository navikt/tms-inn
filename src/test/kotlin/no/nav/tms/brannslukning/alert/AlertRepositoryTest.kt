package no.nav.tms.brannslukning.alert

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.tms.brannslukning.alert.setup.database.LocalPostgresDatabase
import no.nav.tms.brannslukning.common.gui.User
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.BeforeEach
import java.util.UUID

class AlertRepositoryTest {

    private val database = LocalPostgresDatabase.cleanDb()
    private val alertRepository = AlertRepository(database)

    private val testAlert = OpprettAlert(
        referenceId = UUID.randomUUID().toString(),
        tekster = Tekster(
            tittel = "Larisa",
            beskrivelse = "Shain",
            beskjed = WebTekst(link = "Kerstin", spraakkode = "Brittan", tekst = "Hazel"),
            eksternTekst = EksternTekst(tittel = "Lucretia", tekst = "Laquana")
        ),
        opprettetAv = User("Testuser", "test-user"),
        mottakere = listOf("12345", "678910", "111213", "98764")
    )

    @BeforeEach
    fun cleandb() {
        database.clearTables()
    }

    @Test
    fun `oppdaterer leststatus`() {
        alertRepository.createAlert(testAlert)
        var varsler = database.getVarselForAlert(testAlert.referenceId)
        varsler.forEach { alertRepository.markAsSent(testAlert.referenceId, it.ident, UUID.randomUUID().toString()) }

        varsler = database.getVarselForAlert(testAlert.referenceId)
        varsler.forEach {
            it.varselId shouldNotBe null
            it.lest shouldBe false
            it.eksternStatus shouldBe null
        }

        val lestVarsel = varsler.first()
        require(lestVarsel.varselId != null)


        alertRepository.setVarselLest(lestVarsel.varselId)
        varsler = database.getVarselForAlert(testAlert.referenceId)
        varsler.find { it.lest }?.varselId shouldBe lestVarsel.varselId
        varsler.count { !it.lest } shouldBe 3

        alertRepository.setVarselLest(varsler[1].varselId!!)

        alertRepository.alertStatus(testAlert.referenceId).apply {
            antallFerdigstilteVarsler shouldBe 4
            antallLesteVarsler shouldBe 2
            eksterneVarslerStatus.antallFeilet shouldBe 0
            eksterneVarslerStatus.antallBestilt shouldBe 0
            eksterneVarslerStatus.antallSendt shouldBe 0
        }
    }

    @Test
    fun `oppdaterer ekstern status`() {
        alertRepository.createAlert(testAlert)
        var varsler = database.getVarselForAlert(testAlert.referenceId)
        varsler.forEach { alertRepository.markAsSent(testAlert.referenceId, it.ident, UUID.randomUUID().toString()) }

        varsler = database.getVarselForAlert(testAlert.referenceId)
        varsler.forEach {
            it.varselId shouldNotBe null
            it.lest shouldBe false
            it.eksternStatus shouldBe null
        }

        val lestVarsel = varsler.first()
        require(lestVarsel.varselId != null)


        alertRepository.setVarselLest(lestVarsel.varselId)
        varsler = database.getVarselForAlert(testAlert.referenceId)
        varsler.find { it.lest }?.varselId shouldBe lestVarsel.varselId
        varsler.count { !it.lest } shouldBe 3

        alertRepository.updateEksternStatus(lestVarsel.varselId, "bestilt")
        varsler = database.getVarselForAlert(testAlert.referenceId)
        varsler.find { it.varselId == lestVarsel.varselId }.let {
            require(it != null)
            it.eksternStatus shouldBe "bestilt"
            it.lest shouldBe true
        }

        alertRepository.alertStatus(testAlert.referenceId).apply {
            antallFerdigstilteVarsler shouldBe 4
            eksterneVarslerStatus.antallFeilet shouldBe 1
            eksterneVarslerStatus.antallBestilt shouldBe 4
            eksterneVarslerStatus.antallSendt shouldBe 3
        }

    }
}

/*
* ident text not null,
    sendt boolean not null default false,
    opprettet timestamp with time zone not null,
    ferdigstilt timestamp with time zone,
* */