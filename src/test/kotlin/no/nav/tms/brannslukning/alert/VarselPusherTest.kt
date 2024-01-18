package no.nav.tms.brannslukning.alert

import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotliquery.queryOf
import no.nav.tms.brannslukning.alert.setup.database.LocalPostgresDatabase
import no.nav.tms.brannslukning.setup.PodLeaderElection
import no.nav.tms.brannslukning.setup.database.Database
import no.nav.tms.brannslukning.setup.database.defaultObjectMapper
import no.nav.tms.brannslukning.setup.database.toJsonb
import no.nav.tms.varsel.action.Produsent
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID

class VarselPusherTest {

    private val database = LocalPostgresDatabase.cleanDb()
    private val alertRepository = AlertRepository(database)
    private val leaderElection: PodLeaderElection = mockk()

    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val objectMapper = defaultObjectMapper()

    @AfterEach
    fun cleanUp() {
        clearMocks(leaderElection)
        mockProducer.clear()
        database.clearTables()
    }

    @Test
    fun `behandler varsler fra backlog og sender til kafka`() {

        val alertMedMottakere = AlertWithRecipients(
            alertEntry = AlertEntry(
                referenceId = UUID.randomUUID().toString(),
                tekster = Tekster(
                    beskrivelse = "Alert for test",
                    beskjed = WebTekst(
                        spraakkode = "nb",
                        tekst = "Alerttekst for beskjed",
                        link = "https://test"
                    ),
                    eksternTekst = EksternTekst(
                        tittel = "Tittel for epost",
                        tekst = "Tekst i ekstern kanal"
                    )
                ),
                opprettetAv = Actor("TEST", "test")
            ),
            recipients = listOf(
                "11111111111",
                "22222222222",
                "33333333333",
            )
        )

        database.insertRequests(alertMedMottakere)

        coEvery { leaderElection.isLeader() } returns true

        val varselPusher = initVarselPusher(thisApp = Produsent("dev", "min-side", "tms-brannslukning"))

        runBlocking {
            varselPusher.start()
            delayUntilVarslerSendt()
            varselPusher.stop()
        }

        mockProducer.history().size shouldBe 3

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .first()
            .let {
                it["type"].asText() shouldBe "beskjed"
                it["link"].asText() shouldBe "https://test"
                it["tekster"].first()["spraakkode"].asText() shouldBe "nb"
                it["tekster"].first()["tekst"].asText() shouldBe "Alerttekst for beskjed"
                it["eksternVarsling"]["prefererteKanaler"].first().asText() shouldBe "SMS"
                it["eksternVarsling"]["smsVarslingstekst"].asText() shouldBe "Tekst i ekstern kanal"
                it["eksternVarsling"]["epostVarslingstekst"].asText() shouldBe "Tekst i ekstern kanal"
                it["eksternVarsling"]["epostVarslingstittel"].asText() shouldBe "Tittel for epost"
                it["produsent"]["cluster"].asText() shouldBe "dev"
                it["produsent"]["namespace"].asText() shouldBe "min-side"
                it["produsent"]["appnavn"].asText() shouldBe "tms-brannslukning"
            }
    }

    @Test
    fun `does nothing when not leader`() {
        val alertMedMottakere = AlertWithRecipients(
            alertEntry = AlertEntry(
                referenceId = UUID.randomUUID().toString(),
                tekster = Tekster(
                    beskrivelse = "Alert for test",
                    beskjed = WebTekst(
                        spraakkode = "nb",
                        tekst = "Alerttekst for beskjed",
                        link = "https://test"
                    ),
                    eksternTekst = EksternTekst(
                        tittel = "Tittel for epost",
                        tekst = "Tekst i ekstern kanal"
                    )
                ),
                opprettetAv = Actor("TEST", "test")
            ),
            recipients = listOf(
                "11111111111",
                "22222222222",
            )
        )

        database.insertRequests(alertMedMottakere)

        coEvery { leaderElection.isLeader() } returns false

        val pusher = initVarselPusher()

        runBlocking {
            pusher.start()
            delay(2000)
            pusher.stop()
        }

        backlogSize() shouldBe 2
        mockProducer.history().size shouldBe 0
    }

    private fun initVarselPusher(thisApp: Produsent = Produsent("cluster", "namespace", "app")) = VarselPusher(
        alertRepository = alertRepository,
        interval = Duration.ofMinutes(10),
        leaderElection = leaderElection,
        kafkaProducer = mockProducer,
        varselTopic = "mockTopic",
        produsentOverride = thisApp
    )

    private suspend fun delayUntilVarslerSendt(remainingInQueue: Int = 0) {
        withTimeout(5000) {
            while (backlogSize() > remainingInQueue) {
                delay(100)
            }
        }
    }

    private fun backlogSize(): Int {
        return database.singleOrNull {
            queryOf("select count(*) as antall from alert_varsel_queue where not sendt")
                .map { it.int("antall") }
                .asSingle
        }?: 0
    }
}

private fun Database.insertRequests(requests: AlertWithRecipients) {
    update {
        queryOf("""
                insert into alert_header(referenceId, tekster, opprettet, opprettetAv, aktiv)
                values (:referenceId, :tekster, :opprettet, :opprettetAv, :aktiv)
            """,
            mapOf(
                "referenceId" to requests.alertEntry.referenceId,
                "tekster" to requests.alertEntry.tekster.toJsonb(),
                "opprettet" to nowAtUtcZ(),
                "opprettetAv" to requests.alertEntry.opprettetAv.toJsonb(),
                "aktiv" to true
            )
        )
    }

    batch(
    """
            insert into alert_varsel_queue(alert_ref, ident, sendt, opprettet)
            values(:alertRef, :ident, :sendt, :opprettet)
        """,
        requests.recipients.map {
            mapOf(
                "alertRef" to requests.alertEntry.referenceId,
                "ident" to it,
                "sendt" to false,
                "opprettet" to nowAtUtcZ(),
            )
        }
    )
}

private data class AlertEntry(
    val referenceId: String,
    val tekster: Tekster,
    val opprettetAv: Actor,
    val aktiv: Boolean = true,
    val opprettet: ZonedDateTime = ZonedDateTime.now(),
    val avsluttet: ZonedDateTime? = null
)

private data class AlertWithRecipients(
    val alertEntry: AlertEntry,
    val recipients: List<String>
)

