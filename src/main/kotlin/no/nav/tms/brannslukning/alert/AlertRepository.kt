package no.nav.tms.brannslukning.alert

import kotliquery.queryOf
import no.nav.tms.brannslukning.common.database.Database
import no.nav.tms.brannslukning.common.database.defaultObjectMapper
import no.nav.tms.brannslukning.common.database.json
import java.time.ZoneId
import java.time.ZonedDateTime

class AlertRepository(private val database: Database) {

    private val objectMapper = defaultObjectMapper()

    fun activeAlerts() = alerts(aktiv = true)

    fun inactiveAlerts() = alerts(aktiv = false)

    private fun alerts(aktiv: Boolean): List<Alert> {
        return database.list {
            queryOf(
                "select * from alert_header where aktiv = :aktiv",
                    mapOf("aktiv" to aktiv)
                ).map {
                    Alert(
                        referenceId = it.string("referenceId"),
                        tekster = it.json("tekster"),
                        opprettet = it.zonedDateTime("opprettet"),
                        opprettetAv = it.json("opprettetAv", objectMapper),
                        aktiv = it.boolean("akiv")
                    )
                }.asList
        }
    }

    fun createAlert(createAlert: OpprettAlert) {
        database.update {
            queryOf(
                "insert into alert_header(referenceId, tekster, opprettet, opprettetAv) values(:referenceId, :tekster, :opprettet, :opprettetAv)",
                mapOf(
                    "referenceId" to createAlert.referenceId,
                    "tekster" to createAlert.tekster,
                    "opprettetAv" to createAlert.opprettetAv,
                    "opprettet" to nowAtUtcZ()
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
                    "opprettet" to nowAtUtcZ()
                )
            }
        )
    }

    fun nextInVarselQueue(antall: Int): List<VarselRequest> {
        return database.list {
            queryOf(
                """
                    select 
                        avq.*, ah.tekster->'beskjed' as beskjed, ah.tekster->'eksternTekst' as eksternTekst 
                    from 
                        alert_varsel_queue as avq
                        join alert_header as ah on avq.alert_ref = ah.referenceId
                    where not sendt order by opprettet limit :antall""".trimIndent(),
                mapOf("antall" to antall)
            ).map {
                VarselRequest(
                    referenceId = it.string("referenceId"),
                    ident = it.string("ident"),
                    beskjed = it.json("beskjed", objectMapper),
                    eksternTekst = it.json("eksternTekst", objectMapper),
                )
            }.asList
        }
    }

    fun markAsSent(referenceId: String, ident: String) {
        database.update {
            queryOf(
                "update alert_varsel_queue set sendt = true, ferdigstilt = :ferdigstilt where alert_ref = :referenceId, ident = :ident",
                mapOf(
                    "ident" to ident,
                    "referenceId" to referenceId,
                    "ferdigstilt" to nowAtUtcZ()
                )
            )
        }
    }
}

private fun nowAtUtcZ() = ZonedDateTime.now(ZoneId.of("Z"))
