package no.nav.tms.brannslukning.alert

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
import no.nav.tms.brannslukning.setup.database.defaultObjectMapper
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Varseltype
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZonedDateTime

class AlertRepositoryTest {

    private val database = LocalPostgresDatabase.cleanDb()
    private val archiveRepository = AlertRepository(database)
    private val leaderElection: PodLeaderElection = mockk()

    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val objectMapper = defaultObjectMapper()

    @BeforeEach
    fun setup() {
        createVarsel(gammelBeskjed, nyBeskjed)
    }

    @AfterEach
    fun cleanUp() {
        clearMocks(leaderElection)
        mockProducer.clear()
        database.update { queryOf("delete from varsel") }
        database.update { queryOf("delete from varsel_arkiv") }
    }


    fun createVarsel(vararg varsler: DatabaseVarsel) {
        val varselRepository = WriteVarselRepository(database)

        varsler.forEach { varselRepository.insertVarsel(it) }
    }

    @Test
    fun `arkiverer alle gamle varsler`() {

        coEvery { leaderElection.isLeader() } returns true

        runArchiverUntilNRemains(1)

        val arkiverteVarsler = testRepository.getAllArchivedVarsel()
        arkiverteVarsler.size shouldBe 1
        arkiverteVarsler.first().apply {
            type shouldBe Varseltype.Beskjed
            varselId shouldBe gammelBeskjed.varselId
        }

        mockProducer.history().size shouldBe 1

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .first()
            .let {
                it["varselId"].asText() shouldBe gammelBeskjed.varselId
                it["varseltype"].asText() shouldBe gammelBeskjed.type.name.lowercase()
                it["produsent"]["cluster"]?.asText() shouldBe gammelBeskjed.produsent.cluster
                it["produsent"]["namespace"].asText() shouldBe gammelBeskjed.produsent.namespace
                it["produsent"]["appnavn"].asText() shouldBe gammelBeskjed.produsent.appnavn
                it["opprettet"].asZonedDateTime().toEpochSecond() shouldBe gammelBeskjed.opprettet.toEpochSecond()
            }
    }

    @Test
    fun `arkiverer beskjed-data`() = runBlocking<Unit> {

        coEvery { leaderElection.isLeader() } returns true

        runArchiverUntilNRemains(1)

        val arkiverteVarsler = testRepository.getAllArchivedVarsel()

        arkiverteVarsler.size shouldBe 1
        arkiverteVarsler.first().apply {
            varselId shouldBe gammelBeskjed.varselId
            innhold.tekst shouldBe gammelBeskjed.innhold.tekst
            innhold.link shouldBe gammelBeskjed.innhold.link
            sensitivitet shouldBe gammelBeskjed.sensitivitet
            aktiv shouldBe gammelBeskjed.aktiv
            produsent.appnavn shouldBe gammelBeskjed.produsent.appnavn
            produsent.namespace shouldBe gammelBeskjed.produsent.namespace
            eksternVarslingStatus?.kanaler shouldBe gammelBeskjed.eksternVarslingStatus?.kanaler
            eksternVarslingStatus?.sendt shouldBe gammelBeskjed.eksternVarslingStatus?.sendt
            opprettet shouldBe gammelBeskjed.opprettet
        }
    }

    @Test
    fun `does nothing when not leader`() = runBlocking {
        coEvery { leaderElection.isLeader() } returns false

        val archiver = PeriodicVarselArchiver(
            varselArchivingRepository = archiveRepository,
            ageThresholdDays = 10,
            interval = Duration.ofMinutes(10),
            leaderElection = leaderElection,
            varselArkivertProducer = arkivertProducer
        )

        archiver.start()
        delay(2000)
        archiver.stop()


        varselInDbCount() shouldBe 2
        testRepository.getAllArchivedVarsel().size shouldBe 0
        mockProducer.history().size shouldBe 0
    }

    private fun runArchiverUntilNRemains(remainingVarsler: Int = 0) = runBlocking {
        val archiver = PeriodicVarselArchiver(
            varselArchivingRepository = archiveRepository,
            ageThresholdDays = 10,
            interval = Duration.ofMinutes(10),
            leaderElection = leaderElection,
            varselArkivertProducer = arkivertProducer
        )

        archiver.start()
        delayUntilVarslerDeleted(remainingVarsler)
        archiver.stop()
    }

    private suspend fun delayUntilVarslerDeleted(remainingVarsler: Int = 0) {
        withTimeout(5000) {
            while (varselInDbCount() > remainingVarsler) {
                delay(100)
            }
        }
    }

    private fun varselInDbCount(): Int {
        return database.singleOrNull {
            queryOf("select count(*) as antall from varsel")
                .map { it.int("antall") }
                .asSingle
        }?: 0
    }


}

private data class AlertEntry(
    val referenceId: String =
    val tekster: String =
    val opprettet: String =
    val opprettetAv: String =
    val aktiv: String =
    val avsluttet: String =
)

private data class VarselBacklogEntry(

)



