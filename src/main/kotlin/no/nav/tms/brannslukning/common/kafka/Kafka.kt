package no.nav.tms.brannslukning.common.kafka

import no.nav.tms.brannslukning.Environment
import no.nav.tms.brannslukning.SecurityVars
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

object Kafka {

    fun producerProps(env: Environment): Properties {
        return Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env.aivenBrokers)
            put(ProducerConfig.CLIENT_ID_CONFIG, "tms-brannslukning")
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 40000)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            putAll(credentialPropsAiven(env.securityVars))
        }
    }

    private fun credentialPropsAiven(securityVars: SecurityVars): Properties {
        return Properties().apply {
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
            put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
            put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, securityVars.aivenTruststorePath)
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, securityVars.aivenCredstorePassword)
            put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, securityVars.aivenKeystorePath)
            put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, securityVars.aivenCredstorePassword)
            put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, securityVars.aivenCredstorePassword)
            put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        }
    }
}

enum class Eventtype(val eventtype: String) {
    DOKNOTIFIKASJON_STOPP("doknotifikasjon-stopp"), VARSEL(" varsel")
}
