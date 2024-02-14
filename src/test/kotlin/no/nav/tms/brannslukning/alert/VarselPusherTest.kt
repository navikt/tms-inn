package no.nav.tms.brannslukning.alert

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotliquery.queryOf
import no.nav.tms.brannslukning.alert.setup.database.LocalPostgresDatabase
import no.nav.tms.brannslukning.gui.User
import no.nav.tms.brannslukning.setup.PodLeaderElection
import no.nav.tms.brannslukning.setup.database.*
import no.nav.tms.varsel.action.Produsent
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
                    tittel = "Alert for test",
                    beskrivelse = "Alert for test med beskrivelse",
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
                opprettetAv = User("TEST", "test")
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
                    tittel = "Alert for test",
                    beskrivelse = "Alert for test med beskrivelse",
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
                opprettetAv = User("TEST", "test")
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

    @Test
    fun `hopper over elementer som gir ugyldige beskjeder`() {

        val gyldigeAlerts = AlertWithRecipients(
            alertEntry = AlertEntry(
                referenceId = UUID.randomUUID().toString(),
                tekster = Tekster(
                    tittel = "Alert for test",
                    beskrivelse = "Alert for test med beskrivelse",
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
                opprettetAv = User("TEST", "test")
            ),
            recipients = listOf(
                "11111111111",
                "33333333333",
            )
        )

        val ugyldigAlert = AlertWithRecipients(
            alertEntry = AlertEntry(
                referenceId = UUID.randomUUID().toString(),
                tekster = Tekster(
                    tittel = "Alert for test",
                    beskrivelse = "Alert for test med beskrivelse",
                    beskjed = WebTekst(
                        spraakkode = "nb",
                        tekst = "Alerttekst for beskjed, men den er aaalt for lang".repeat(10),
                        link = "https://test"
                    ),
                    eksternTekst = EksternTekst(
                        tittel = "Tittel for epost",
                        tekst = "Tekst i ekstern kanal"
                    )
                ),
                opprettetAv = User("TEST", "test")
            ),
            recipients = listOf(
                "22222222222",
            )
        )

        database.insertRequests(gyldigeAlerts)
        database.insertRequests(ugyldigAlert)

        coEvery { leaderElection.isLeader() } returns true

        val varselPusher = initVarselPusher(thisApp = Produsent("dev", "min-side", "tms-brannslukning"))

        runBlocking {
            varselPusher.start()
            delay(2000)
            varselPusher.stop()
        }

        mockProducer.history().size shouldBe 2

        val mottakere = mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .map { it["ident"].asText() }

        mottakere shouldContainAll listOf("11111111111", "33333333333")

        val alerts = database.getQueueEntries()

        alerts.all { it.behandlet } shouldBe true
        alerts.all { it.ferdigstilt != null } shouldBe true
        alerts.none { it.status == BeskjedStatus.Venter } shouldBe true
        alerts.filter { it.status == BeskjedStatus.Sendt }.none { it.feilkilde != null } shouldBe true

        alerts.filter { it.status == BeskjedStatus.Sendt }
            .map { it.ident } shouldContainAll listOf("11111111111", "33333333333")

        alerts.filter { it.status == BeskjedStatus.Feilet }
            .map { it.ident } shouldContainAll listOf("22222222222")


        alerts.first { it.status == BeskjedStatus.Feilet }.let {
            it.feilkilde.shouldNotBeNull()
        }
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
            queryOf("select count(*) as antall from alert_beskjed_queue where not behandlet")
                .map { it.int("antall") }
                .asSingle
        } ?: 0
    }
}

private fun Database.insertRequests(requests: AlertWithRecipients) {
    update {
        queryOf(
            """
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
            insert into alert_beskjed_queue(alert_ref, ident, behandlet, opprettet)
            values(:alertRef, :ident, :behandlet, :opprettet)
        """,
        requests.recipients.map {
            mapOf(
                "alertRef" to requests.alertEntry.referenceId,
                "ident" to it,
                "behandlet" to false,
                "opprettet" to nowAtUtcZ(),
            )
        }
    )
}

private fun Database.getQueueEntries(): List<BeskjedQueueEntry> {
    return list {
        queryOf(
            "select * from alert_beskjed_queue"
        ).map {
            BeskjedQueueEntry(
                alertRef = it.string("alert_ref"),
                ident = it.string("ident"),
                behandlet = it.boolean("behandlet"),
                status = BeskjedStatus.parse(it.string("status")),
                varselId = it.stringOrNull("varselId"),
                ferdigstilt = it.zonedDateTimeOrNull("ferdigstilt"),
                feilkilde = it.jsonOrNull("feilkilde"),
            )
        }.asList
    }
}

private data class BeskjedQueueEntry(
    val alertRef: String,
    val ident: String,
    val behandlet: Boolean,
    val status: BeskjedStatus,
    val varselId: String?,
    val ferdigstilt: ZonedDateTime?,
    val feilkilde: Feilkilde?,
)

private data class AlertEntry(
    val referenceId: String,
    val tekster: Tekster,
    val opprettetAv: User,
    val aktiv: Boolean = true,
    val opprettet: ZonedDateTime = ZonedDateTime.now(),
    val avsluttet: ZonedDateTime? = null
)

private data class AlertWithRecipients(
    val alertEntry: AlertEntry,
    val recipients: List<String>
)

