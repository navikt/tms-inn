package no.nav.personbruker.dittnav.varselbestiller.common.database

import java.sql.*
import java.time.LocalDateTime

fun ResultSet.getUtcDateTime(columnLabel: String): LocalDateTime = getTimestamp(columnLabel).toLocalDateTime()

fun ResultSet.getListFromSeparatedString(columnLabel: String, separator: String): List<String> {
    var stringValue = getString(columnLabel)
    return if(stringValue.isNullOrEmpty()) {
        emptyList()
    }
    else {
        stringValue.split(separator)
    }
}
