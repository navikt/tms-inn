package no.nav.tms.brannslukning.alert

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

class VarselHendelseReader(
    private val alertRepository: AlertRepository,
    private val kafkaConsumer: KafkaConsumer<String, String>,
) {
    private val objectmapper = jacksonObjectMapper()
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    init {
        kafkaConsumer.subscribe(Collections.singletonList("aapen-varsel-hendelse-v1"))
        scope.launch { consumeHendelseTopic() }
    }

    private tailrec fun <T> readVarselHendelse(block: () -> T?): T = block() ?: readVarselHendelse(block)

    private fun consumeHendelseTopic() {
        kafkaConsumer.use { consumer ->
            val message = readVarselHendelse {
                consumer.poll(400.milliseconds.toJavaDuration()).map { it.value() }.firstOrNull()
            }.let { msg ->
                objectmapper.readTree(msg)
            }

            when (message["@event"].toString()) {
                "eksternStatusOppdatert" ->
                    alertRepository.updateEksternStatus(message["varselId"].asText(), message["status"].asText())

                "inaktivert" ->
                    alertRepository.setVarselLest(message["varselId"].asText())
            }

        }
    }

    fun shutdown() {
        scope.cancel()
        kafkaConsumer.close()
    }
}