package no.nav.tms.brannslukning.alert

import io.kotest.matchers.shouldBe
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.tms.brannslukning.alert.setup.database.LocalPostgresDatabase
import no.nav.tms.brannslukning.alert.setup.database.VarselData
import no.nav.tms.brannslukning.alert.setup.database.setupTestAltert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VarselHendelseSinksTest {
    private val database = LocalPostgresDatabase.cleanDb()
    private val alertRepository = AlertRepository(database)
    private val testRapid = TestRapid()

    @BeforeAll
    fun startRapidListeners() {
        EksterntVarselStatusSink(testRapid, alertRepository)
        VarselInaktivertSink(testRapid, alertRepository)
    }


    @Test
    fun `plukker opp lest-hendelse`() {
        var (testAlertRef, varsler) = setupTestAltert(alertRepository, database)
        testRapid.sendVarselInaktivert(varsler[0], varsler[1])

        alertRepository.alertStatus(testAlertRef.referenceId).apply {
            antallFerdigstilteVarsler shouldBe 4
            antallLesteVarsler shouldBe 2
        }
    }

    @Test
    fun `plukker opp endring i status for eksternt varsel`() {
        val (testAlertRef, varsler) = setupTestAltert(alertRepository, database)

        testRapid.sendEksterntVarselEndret("bestilt", varsler[0], varsler[1], varsler[2], varsler[3])
        testRapid.sendEksterntVarselEndret("feilet", varsler[2])
        testRapid.sendEksterntVarselEndret("sendt", varsler[0], varsler[1])
        testRapid.sendVarselInaktivert(varsler[0], varsler[1])


        alertRepository.alertStatus(testAlertRef.referenceId).apply {
            antallFerdigstilteVarsler shouldBe 4
            antallLesteVarsler shouldBe 2
            eksterneVarslerStatus.antallFeilet shouldBe 1
            eksterneVarslerStatus.antallSendt shouldBe 2
            eksterneVarslerStatus.antallBestilt shouldBe 4
        }
    }

    @AfterEach
    fun cleanVarselData() {
        database.clearTables()
    }


}

private fun TestRapid.sendVarselInaktivert(vararg varselData: VarselData) {
    varselData.forEach {
        require(it.varselId != null)
        sendTestMessage(
            """ 
            {
                "@event_name": "inaktivert",
                "varselType": "beskjed",
                "varselId": "${it.varselId}",
                "namespace": "min-side",
                "appnavn": "tms-brannslukning"
            }
            """.trimIndent()
        )
    }

    sendTestMessage(
        """ 
            {
                "@event_name": "inaktivert",
                "varselType": "beskjed",
                "eventId": "${UUID.randomUUID()}",
                "namespace": "ikke-oss",
                "appnavn": "ikke-brannslukning"
            }
            """.trimIndent()
    )
}

private fun TestRapid.sendEksterntVarselEndret(status: String, vararg varselData: VarselData) {
    varselData.forEach {
        require(it.varselId != null)
        sendTestMessage(
            """ 
            {
                "@event_name": "eksternStatusOppdatert",
                "varselType": "beskjed",
                "status": "$status",               
                "varselId": "${it.varselId}",
                "namespace": "min-side",
                "appnavn": "tms-brannslukning"
            }
            """.trimIndent()
        )
    }

    sendTestMessage(
        """ 
            {
                "@event_name": "eksternStatusOppdatert",
                "varselType": "beskjed",
                "eventId": "${UUID.randomUUID()}",
                "namespace": "ikke-oss",
                "appnavn": "ikke-brannslukning"
            }
            """.trimIndent()
    )
}
