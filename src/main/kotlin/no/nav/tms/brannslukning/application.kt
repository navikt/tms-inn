package no.nav.tms.brannslukning

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.tms.brannslukning.alert.AlertRepository
import no.nav.tms.brannslukning.alert.EksterntVarselStatusSink
import no.nav.tms.brannslukning.alert.VarselInaktivertSink
import no.nav.tms.brannslukning.alert.VarselPusher
import no.nav.tms.brannslukning.gui.gui
import no.nav.tms.brannslukning.setup.PodLeaderElection
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
    val alertRepository = AlertRepository(PostgresDatabase())

    if (Environment.isDevMode)
        startDevServer(alertRepository)
    else
        startRapidApplication(alertRepository)

}

private fun startDevServer(alertRepository: AlertRepository) {
    embeddedServer(
        Netty,
        port = getEnvVarAsInt("PORT", 8081),
        module = {
            gui(alertRepository)
            environment.monitor.subscribe(ApplicationStarted) {
                Flyway.runFlywayMigrations()
            }
        }
    ).start(wait = true)
}

private fun startRapidApplication(alertRepository: AlertRepository) {
    val environment = Environment()

    val kafkaProducer = initializeRapidKafkaProducer(environment)
    val varselPusher = VarselPusher(
        alertRepository = alertRepository,
        leaderElection = PodLeaderElection(),
        kafkaProducer = initializeRapidKafkaProducer(environment),
        varselTopic = environment.varselTopic
    )

    RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(environment.rapidConfig))
        .withKtorModule {
            gui(
                alertRepository
            )
        }.build().apply {
            VarselInaktivertSink(this, alertRepository)
            EksterntVarselStatusSink(this, alertRepository)

        }.apply {
            register(object : RapidsConnection.StatusListener {
                override fun onStartup(rapidsConnection: RapidsConnection) {
                    Flyway.runFlywayMigrations()
                    varselPusher.start()
                }

                override fun onShutdown(rapidsConnection: RapidsConnection) {
                    runBlocking {
                        varselPusher.stop()
                        kafkaProducer.flush()
                        kafkaProducer.close()
                    }
                }
            })
        }.start()
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
