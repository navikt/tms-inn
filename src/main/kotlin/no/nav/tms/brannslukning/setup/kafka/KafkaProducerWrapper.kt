package no.nav.personbruker.dittnav.varselbestiller.common.kafka

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.personbruker.dittnav.varselbestiller.common.RetriableKafkaException
import no.nav.personbruker.dittnav.varselbestiller.common.UnretriableKafkaException
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException

class KafkaProducerWrapper<K, V>(
    private val topicName: String,
    private val kafkaProducer: Producer<K, V>
) {

    private val log = KotlinLogging.logger {  }

    fun sendEventsAndLeaveTransactionOpen(event: RecordKeyValueWrapper<K, V>) {
        try {
            kafkaProducer.beginTransaction()
            sendSingleEvent(event)
        } catch (e: KafkaException) {
            kafkaProducer.abortTransaction()
            throw RetriableKafkaException("Et eller flere eventer feilet med en periodisk feil ved sending til kafka", e)
        } catch (e: Exception) {
            kafkaProducer.close()
            throw UnretriableKafkaException("Fant en uventet feil ved sending av eventer til kafka", e)
        }
    }

    fun abortCurrentTransaction() {
        try {
            kafkaProducer.abortTransaction()
        } catch (e: Exception) {
            /*
                KafkaProducer lar oss ikke sjekke state på transaction før vi faktisk kaller abortTransaction her.
                Fordi det kan skje at vi kaller denne etter vi allerede har kalt abortTransaction() eller close(),
                må vi gardere oss for at det kan feile her, men vi trenger ikke videre behandling.
            */
        }
    }

    fun commitCurrentTransaction() {
        kafkaProducer.commitTransaction()
    }

    private fun sendSingleEvent(event: RecordKeyValueWrapper<K, V>) {
        val producerRecord = ProducerRecord(topicName, event.key, event.value)
        kafkaProducer.send(producerRecord)
    }

    fun flushAndClose() {
        try {
            kafkaProducer.flush()
            kafkaProducer.close()
            log.info { "Produsent for kafka-eventer er flushet og lukket." }
        } catch (e: Exception) {
            log.warn { "Klarte ikke å flushe og lukke produsent. Det kan være eventer som ikke ble produsert." }
        }
    }
}
