package no.nav.tms.brannslukning

import no.nav.tms.common.util.config.StringEnvVar.getEnvVar

data class Environment(
    val kafkaBrokers: String = getEnvVar("KAFKA_BROKERS"),
    val kafkaSchemaRegistry: String = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
    val kafkaTruststorePath: String = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
    val kafkaKeystorePath: String = getEnvVar("KAFKA_KEYSTORE_PATH"),
    val kafkaCredstorePassword: String = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
    val kafkaSchemaRegistryUser: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
    val kafkaSchemaRegistryPassword: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
    val varselTopic: String = "min-side.aapen-brukervarsel-v1",
) {
    val rapidConfig = mapOf(
        "KAFKA_BROKERS" to kafkaBrokers,
        "KAFKA_CONSUMER_GROUP_ID" to "brannslukning-01",
        "KAFKA_RAPID_TOPIC" to "min-side.aapen-varsel-hendelse-v1",
        "KAFKA_KEYSTORE_PATH" to kafkaKeystorePath,
        "KAFKA_CREDSTORE_PASSWORD" to kafkaCredstorePassword,
        "KAFKA_TRUSTSTORE_PATH" to kafkaTruststorePath,
        "KAFKA_RESET_POLICY" to "earliest",
        "HTTP_PORT" to "8080"
    )

    companion object {
        val isDevMode: Boolean = System.getenv("DEV_MODE").toBoolean()
    }
}

