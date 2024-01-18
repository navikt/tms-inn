package no.nav.tms.brannslukning.alert

import kotliquery.queryOf
import no.nav.tms.brannslukning.common.gui.TmpHendelse
import no.nav.tms.brannslukning.common.gui.User
import no.nav.tms.brannslukning.setup.database.Database
import no.nav.tms.brannslukning.setup.database.defaultObjectMapper
import no.nav.tms.brannslukning.setup.database.json
import no.nav.tms.brannslukning.setup.database.toJsonb
import java.time.ZoneId
import java.time.ZonedDateTime

class AlertRepository(private val database: Database) {

    private val objectMapper = defaultObjectMapper()

    fun activeAlerts() = alerts(aktiv = true)

    fun inactiveAlerts() = alerts(aktiv = false)

    fun fetchHendelse(referenceId: String): TmpHendelse? {
        return database.singleOrNull {
            queryOf(
                """
                    select 
                        ah.*
                    from alert_header
                    where ah.referenceId = :referenceId
                """,
                mapOf("referenceId" to referenceId)
            ).map {
                TmpHendelse(
                    id = it.string("referenceId"),
                    initatedBy = it.json<Actor>("opprettetAv", objectMapper).let { oa -> User(oa.username, oa.oid) },
                    varseltekst = it.json<Tekster>("tekster").beskjed.tekst,
                    eksternTekst = it.json<Tekster>("tekster").eksternTekst.tekst,
                    url = it.json<Tekster>("tekster").beskjed.link
                )
            }.asSingle
        }
    }

    private fun alerts(aktiv: Boolean): List<AlertInfo> {
        return database.list {
            queryOf(
                """
                    select 
                        ah.*,
                        count(*) as mottakere
                    from alert_header as ah
                        left join alert_varsel_queue as avq on ah.referenceId = avq.alert_ref
                    where ah.aktiv = :aktiv
                    group by ah.referenceId
                """,
                    mapOf("aktiv" to aktiv)
                ).map {
                    AlertInfo(
                        referenceId = it.string("referenceId"),
                        tekster = it.json("tekster"),
                        opprettet = it.zonedDateTime("opprettet"),
                        opprettetAv = it.json("opprettetAv", objectMapper),
                        aktiv = it.boolean("akiv"),
                        mottakere = it.int("mottakere"),
                        avsluttet = it.zonedDateTime("avsluttet"),
                        avsluttetAv = it.json("avsluttetAv", objectMapper)
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
                    "tekster" to createAlert.tekster.toJsonb(objectMapper),
                    "opprettetAv" to createAlert.opprettetAv.toJsonb(objectMapper),
                    "opprettet" to nowAtUtcZ()
                )
            )
        }

        database.batch(
            "insert into alert_varsel_queue(alert_ref, ident, opprettet), values(:referenceId, :ident, :opprettet) on conflict do nothing",
            createAlert.mottakere.map {
                mapOf(
                    "referenceId" to createAlert.referenceId,
                    "ident" to it,
                    "opprettet" to nowAtUtcZ()
                )
            }
        )
    }

    fun endAlert(referenceId: String, actor: Actor) {
        database.update {
            queryOf(
                "update alert header set aktiv = false, avsluttet = :avsluttet, avsluttetAv = :avsluttetAv where referenceId = :referenceId returning *",
                mapOf(
                    "referenceId" to referenceId,
                    "avsluttet" to nowAtUtcZ(),
                    "avsluttetAv" to actor.toJsonb(objectMapper)
                )
            )
        }
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
                    referenceId = it.string("alert_ref"),
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
                "update alert_varsel_queue set sendt = true, ferdigstilt = :ferdigstilt where alert_ref = :referenceId and ident = :ident",
                mapOf(
                    "ident" to ident,
                    "referenceId" to referenceId,
                    "ferdigstilt" to nowAtUtcZ()
                )
            )
        }
    }
}

fun nowAtUtcZ() = ZonedDateTime.now(ZoneId.of("Z"))
