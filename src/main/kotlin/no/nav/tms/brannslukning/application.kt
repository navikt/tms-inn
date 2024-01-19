package no.nav.tms.brannslukning

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import no.nav.tms.brannslukning.alert.AlertRepository
import no.nav.tms.brannslukning.alert.VarselPusher
import no.nav.tms.brannslukning.common.gui.gui
import no.nav.tms.brannslukning.setup.PodLeaderElection
import no.nav.tms.brannslukning.setup.database.Database
import no.nav.tms.brannslukning.setup.database.Flyway
import no.nav.tms.brannslukning.setup.database.PostgresDatabase
import no.nav.tms.common.util.config.IntEnvVar.getEnvVarAsInt
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

fun main() {

    val environment = Environment()
    val database: Database = PostgresDatabase(environment)

    val alertRepository = AlertRepository(database)
    val kafkaProducer = initializeRapidKafkaProducer(environment)

    val leaderElection = PodLeaderElection()

    val varselPusher = VarselPusher(
        alertRepository,
        leaderElection,
        kafkaProducer,
        environment.varselTopic
    )

    embeddedServer(
        Netty,
        port = getEnvVarAsInt("PORT", 8081),
        module = {
            gui(alertRepository)
            configureStartupHook(environment, varselPusher)
            configureShutdownHook(varselPusher, kafkaProducer)
        }
    ).start(wait = true)
}

private fun Application.configureStartupHook(env: Environment, varselPusher: VarselPusher) {
    environment.monitor.subscribe(ApplicationStarted) {
        Flyway.runFlywayMigrations(env)
        varselPusher.start()
    }
}

private fun Application.configureShutdownHook(varselPusher: VarselPusher, kafkaProducer: KafkaProducer<*, *>) {
    environment.monitor.subscribe(ApplicationStopPreparing) {
        runBlocking {
            varselPusher.stop()
            kafkaProducer.flush()
            kafkaProducer.close()
        }
    }
}


private fun initializeRapidKafkaProducer(environment: Environment) = KafkaProducer<String, String>(
    Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.kafkaBrokers)
        put(
            ProducerConfig.CLIENT_ID_CONFIG,
            "tms-brannslukning"
        )
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 40000)
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
        put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
        put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
        put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, environment.kafkaTruststorePath)
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, environment.kafkaCredstorePassword)
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, environment.kafkaKeystorePath)
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, environment.kafkaCredstorePassword)
        put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, environment.kafkaCredstorePassword)
        put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
    }
)
