package no.nav.tms.brannslukning.alert

import io.kotest.matchers.shouldBe
import no.nav.tms.brannslukning.alert.setup.database.LocalPostgresDatabase
import no.nav.tms.brannslukning.alert.setup.database.VarselData
import no.nav.tms.brannslukning.alert.setup.database.setupTestAltert
import no.nav.tms.kafka.application.MessageBroadcaster
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VarselHendelseSubscriberTest {
    private val database = LocalPostgresDatabase.cleanDb()
    private val alertRepository = AlertRepository(database)
    private val broadcaster = MessageBroadcaster(
        listOf(
            EksterntVarselStatusSubscriber(alertRepository),
            VarselInaktivertSubscriber(alertRepository)
        )
    )

    @BeforeAll
    fun startRapidListeners() {

    }


    @Test
    fun `plukker opp lest-hendelse`() {
        var (testAlertRef, varsler) = setupTestAltert(alertRepository, database)
        broadcaster.sendVarselInaktivert(varsler[0], varsler[1])

        alertRepository.varselStatus(testAlertRef.referenceId).apply {
            antallFerdigstilteVarsler shouldBe 4
            antallLesteVarsler shouldBe 2
        }
    }

    @Test
    fun `plukker opp endring i status for eksternt varsel`() {
        val (testAlertRef, varsler) = setupTestAltert(alertRepository, database)

        broadcaster.sendEksterntVarselEndret("bestilt", varsler[0], varsler[1], varsler[2], varsler[3])
        broadcaster.sendEksterntVarselEndret("feilet", varsler[2])
        broadcaster.sendEksterntVarselEndret("sendt", varsler[0], varsler[1])
        broadcaster.sendVarselInaktivert(varsler[0], varsler[1])


        alertRepository.varselStatus(testAlertRef.referenceId).apply {
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

private fun MessageBroadcaster.sendVarselInaktivert(vararg varselData: VarselData) {
    varselData.forEach {
        require(it.varselId != null)
        broadcastJson(
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

    broadcastJson(
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

private fun MessageBroadcaster.sendEksterntVarselEndret(status: String, vararg varselData: VarselData) {
    varselData.forEach {
        require(it.varselId != null)
        broadcastJson(
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

    broadcastJson(
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
