package no.nav.tms.brannslukning.alert

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.tms.brannslukning.alert.setup.database.LocalPostgresDatabase
import no.nav.tms.brannslukning.alert.setup.database.defaultTestAlert
import no.nav.tms.brannslukning.alert.setup.database.setupTestAltert
import org.junit.After
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlertRepositoryTest {

    private val database = LocalPostgresDatabase.cleanDb()
    private val alertRepository = AlertRepository(database)

    @AfterEach
    fun cleandb() {
        database.clearTables()
    }

    @Test
    fun `oppdaterer leststatus`() {
        var varsler = setupTestAltert(alertRepository, database)

        varsler.forEach {
            it.varselId shouldNotBe null
            it.lest shouldBe false
            it.eksternStatus shouldBe null
        }

        val lestVarsel = varsler.first()
        require(lestVarsel.varselId != null)


        alertRepository.setVarselLest(lestVarsel.varselId)
        varsler = database.getVarselForAlert(defaultTestAlert.referenceId)
        varsler.find { it.lest }?.varselId shouldBe lestVarsel.varselId
        varsler.count { !it.lest } shouldBe 3

        alertRepository.setVarselLest(varsler[1].varselId!!)

        alertRepository.alertStatus(defaultTestAlert.referenceId).apply {
            antallFerdigstilteVarsler shouldBe 4
            antallLesteVarsler shouldBe 2
            eksterneVarslerStatus.antallFeilet shouldBe 0
            eksterneVarslerStatus.antallBestilt shouldBe 0
            eksterneVarslerStatus.antallSendt shouldBe 0
        }
    }

    @Test
    fun `oppdaterer ekstern status`() {

        val varsler = setupTestAltert(alertRepository, database)

        alertRepository.updateEksternStatus(varsler[0].varselId!!, "bestilt")
        alertRepository.updateEksternStatus(varsler[1].varselId!!, "sendt")
        alertRepository.updateEksternStatus(varsler[2].varselId!!, "sendt")
        alertRepository.updateEksternStatus(varsler[3].varselId!!, "feilet")


        alertRepository.alertStatus(defaultTestAlert.referenceId).apply {
            antallFerdigstilteVarsler shouldBe 4
            eksterneVarslerStatus.antallFeilet shouldBe 1
            eksterneVarslerStatus.antallBestilt shouldBe 4
            eksterneVarslerStatus.antallSendt shouldBe 2
        }

    }
}
