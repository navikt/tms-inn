package no.nav.tms.brannslukning

import no.nav.doknotifikasjon.schemas.Doknotifikasjon
import no.nav.doknotifikasjon.schemas.DoknotifikasjonStopp
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.tms.brannslukning.common.database.Database
import no.nav.personbruker.dittnav.varselbestiller.common.kafka.KafkaProducerWrapper
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjon.DoknotifikasjonProducer
import no.nav.personbruker.dittnav.varselbestiller.doknotifikasjonStopp.DoknotifikasjonStoppProducer
import no.nav.personbruker.dittnav.varselbestiller.varsel.InaktivertSink
import no.nav.personbruker.dittnav.varselbestiller.varsel.VarselSink
import no.nav.personbruker.dittnav.varselbestiller.varselbestilling.VarselbestillingRepository
import org.apache.kafka.clients.producer.KafkaProducer

fun main() {
    val environment = Environment()

    val database: Database = PostgresDatabase(environment)
    val varselbestillingRepository = VarselbestillingRepository(database)

    val doknotifikasjonProducer = DoknotifikasjonProducer(
        producer = KafkaProducerWrapper(
            topicName = environment.doknotifikasjonTopicName,
            kafkaProducer = KafkaProducer<String, Doknotifikasjon>(
                Kafka.producerProps(environment, Eventtype.VARSEL)
            ).apply { initTransactions() }
        ),
        varselbestillingRepository = varselbestillingRepository
    )
    val doknotifikasjonStoppProducer = DoknotifikasjonStoppProducer(
        producer = KafkaProducerWrapper(
            topicName = environment.doknotifikasjonStopTopicName,
            kafkaProducer = KafkaProducer<String, DoknotifikasjonStopp>(
                Kafka.producerProps(environment, Eventtype.DOKNOTIFIKASJON_STOPP)
            ).apply { initTransactions() }
        ),
        varselbestillingRepository = varselbestillingRepository
    )

    startRapid(
        environment = environment,
        varselbestillingRepository = varselbestillingRepository,
        doknotifikasjonProducer = doknotifikasjonProducer,
        doknotifikasjonStoppProducer = doknotifikasjonStoppProducer
    )
}

private fun startRapid(
    environment: Environment,
    varselbestillingRepository: VarselbestillingRepository,
    doknotifikasjonProducer: DoknotifikasjonProducer,
    doknotifikasjonStoppProducer: DoknotifikasjonStoppProducer
) {
    RapidApplication.create(environment.rapidConfig()).apply {
        VarselSink(
            rapidsConnection = this,
            doknotifikasjonProducer = doknotifikasjonProducer,
            varselbestillingRepository = varselbestillingRepository
        )
        InaktivertSink(
            rapidsConnection = this,
            doknotifikasjonStoppProducer = doknotifikasjonStoppProducer,
            varselbestillingRepository = varselbestillingRepository
        )
    }.apply {
        register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                Flyway.runFlywayMigrations(environment)
            }

            override fun onShutdown(rapidsConnection: RapidsConnection) {
                doknotifikasjonProducer.flushAndClose()
                doknotifikasjonStoppProducer.flushAndClose()
            }
        })
    }.start()
}
