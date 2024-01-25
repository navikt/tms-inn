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
                    from alert_header as ah
                    where ah.referenceId = :referenceId
                """,
                mapOf("referenceId" to referenceId)
            ).map {
                val tekster: Tekster = it.json("tekster", objectMapper)

                TmpHendelse(
                    id = it.string("referenceId"),
                    initatedBy = it.json("opprettetAv", objectMapper),
                    varseltekst = tekster.beskjed.defaultTekst().tekst,
                    eksternTekst = tekster.beskjed.eksternTekst.tekst,
                    url = tekster.beskjed.link,
                    title = tekster.tittel,
                    description = tekster.beskrivelse
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
                    aktiv = it.boolean("aktiv"),
                    mottakere = it.int("mottakere"),
                    avsluttet = it.zonedDateTimeOrNull("avsluttet"),
                    avsluttetAv = if (aktiv) null else it.json("avsluttetAv", objectMapper)
                )
            }.asList
        }
    }

    fun createAlert(createAlert: OpprettAlert) {
        database.update {
            queryOf(
                "insert into alert_header(referenceId, tekster, aktivFremTil, opprettet, opprettetAv) values(:referenceId, :tekster, :aktivFremTil, :opprettet, :opprettetAv)",
                mapOf(
                    "referenceId" to createAlert.referenceId,
                    "tekster" to createAlert.tekster.toJsonb(objectMapper),
                    "aktivFremTil" to createAlert.aktivFremTil,
                    "opprettet" to nowAtUtcZ(),
                    "opprettetAv" to createAlert.opprettetAv.toJsonb(objectMapper)
                )
            )
        }

        database.batch(
            "insert into alert_varsel_queue(alert_ref, ident, opprettet) values(:referenceId, :ident, :opprettet) on conflict do nothing",
            createAlert.mottakere.map {
                mapOf(
                    "referenceId" to createAlert.referenceId,
                    "ident" to it,
                    "opprettet" to nowAtUtcZ()
                )
            }
        )

        database.update {
            queryOf(
                "insert into web_alert_mottakere(alert_ref, mottakere, opprettet) values(:refereneId, :mottakere, :opprettet)",
                mapOf(
                    "referenceId" to createAlert.referenceId,
                    "mottakere" to createAlert.mottakere,
                    "opprettet" to nowAtUtcZ()
                )
            )
        }
    }

    fun endAlert(referenceId: String, actor: User) {
        database.update {
            queryOf(
                "update alert_header set aktiv = false, avsluttet = :avsluttet, avsluttetAv = :avsluttetAv where referenceId = :referenceId",
                mapOf(
                    "referenceId" to referenceId,
                    "avsluttet" to nowAtUtcZ(),
                    "avsluttetAv" to actor.toJsonb(objectMapper)
                )
            )
        }

        database.update {
            queryOf(
                "delete from alert_varsel_queue where alert_ref = :referenceId",
                mapOf("referenceId" to referenceId)
            )
        }

        database.update {
            queryOf(
                "delete from web_alert_mottakere where alert_ref = :referenceId",
                mapOf("referenceId" to referenceId)
            )
        }
    }

    fun webAlertsForUser(ident: String): List<WebTekst> {
        return database.list {
            queryOf(
                """
                    select ah.tekster->'webTekst' as webTekst from alert_header as ah
                        join web_alert_mottakere as wam on ah.referenceId = wam.alert_ref
                    where wam.mottakere @> :ident and ah.tekster->'webTekst' is not null
                """,
                mapOf(
                    "ident" to ident.toJsonb(objectMapper)
                )
            ).map {
                it.json<WebTekst>("webTekst", objectMapper)
            }.asList
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
