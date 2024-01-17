package no.nav.personbruker.dittnav.varselbestiller.common.kafka

data class RecordKeyValueWrapper <K, V> (
    val key: K,
    val value: V
)
