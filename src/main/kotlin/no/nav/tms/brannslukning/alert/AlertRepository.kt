package no.nav.tms.brannslukning.alert

import kotliquery.queryOf
import no.nav.tms.brannslukning.common.database.Database
import java.time.ZoneId
import java.time.ZonedDateTime

class AlertRepository(private val database: Database) {
    fun createAlert(createAlert: OpprettAlert) {
        database.update {
            queryOf(
                "insert into alert_header(referenceId, tekster, opprettet, opprettetAv) values(:referenceId, :tekster, opprettet, opprettetAv)",
                mapOf(
                    "referenceId" to createAlert.referenceId,
                    "tekster" to createAlert.tekster,
                    "opprettetAv" to createAlert.opprettetAv,
                    "opprettet" to ZonedDateTime.now(ZoneId.of("Z"))
                )
            )
        }
    }

    fun addToVarselQueue(varselQueue: AppendVarselQueue) {
        database.batch(
            "insert into alert_varsel_queue(alert_ref, ident, opprettet), values(:referenceId, :ident, :opprettet) on conflict do nothing",
            varselQueue.identer.map {
                mapOf(
                    "referenceId" to varselQueue.referenceId,
                    "ident" to it,
                    "opprettet" to ZonedDateTime.now(ZoneId.of("Z"))
                )
            }
        )
    }

    fun nextInVarselQueue(antall: Int): List<VarselRequest> {
        return database.list {
            queryOf(
                """
                    select 
                        avq.*, ah.tekster->'beskjed', ah.tekster->'eksternTekst' 
                    from 
                        alert_varsel_queue as avq
                        join alert_header as ah on avq.alert_ref = ah.referenceId
                    where not sendt order by opprettet limit :antall""".trimIndent(),
                mapOf("antall" to antall)
            ).map {
                VarselRequest(
                    referenceId = it.string("referenceId"),
                    ident = it.string("ident"),
                    beskjed = ,
                    eksternTekst = it.,
                )
            }.asList
        }
    }
}
