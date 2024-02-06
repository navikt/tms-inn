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
                //language=PostgreSQL
                """
                    select ah.*, count(aq.ident)
                    from alert_header as ah
                    left join alert_varsel_queue as aq on ah.referenceid = aq.alert_ref
                    where ah.referenceId = :referenceId
                    group by referenceid, tekster, aktiv, ah.opprettet, opprettetav, avsluttet, avsluttetav
                """,
                mapOf("referenceId" to referenceId)
            ).map {
                val tekster: Tekster = it.json("tekster", objectMapper)

                TmpHendelse(
                    id = it.string("referenceId"),
                    initatedBy = it.json("opprettetAv", objectMapper),
                    title = tekster.tittel,
                    description = tekster.beskrivelse
                ).apply {
                    varseltekst = tekster.beskjed.tekst
                    eksternTekst = tekster.eksternTekst.tekst
                    url = tekster.beskjed.link
                    affectedCount = it.int("count")
                }
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
            "insert into alert_varsel_queue(alert_ref, ident, opprettet) values(:referenceId, :ident, :opprettet) on conflict do nothing",
            createAlert.mottakere.map {
                mapOf(
                    "referenceId" to createAlert.referenceId,
                    "ident" to it,
                    "opprettet" to nowAtUtcZ()
                )
            }
        )
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
    }

    fun nextInVarselQueue(antall: Int): List<VarselRequest> {
        return database.list {
            queryOf(
                """
                    select 
                        avq.*, ah.tekster->'beskjed' as beskjed, ah.tekster->'eksternTekst' as eksternTekst, ah.tekster -> 'tittel' as tittel
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
                    tittel = it.string("tittel")
                )
            }.asList
        }
    }

    fun markAsSent(referenceId: String, ident: String, varselId: String) {
        database.update {
            queryOf(
                "update alert_varsel_queue set sendt = true, ferdigstilt = :ferdigstilt, varselId = :varselId where alert_ref = :referenceId and ident = :ident",
                mapOf(
                    "ident" to ident,
                    "referenceId" to referenceId,
                    "ferdigstilt" to nowAtUtcZ(),
                    "varselId" to varselId
                )
            )
        }
    }

    fun setVarselLest(varselId: String) {
        database.update {
            queryOf(
                //language=PostgreSQL
                """update alert_varsel_queue 
                    set varsel_lest = true 
                    where varselId= :varselId""".trimIndent(),
                mapOf("varselId" to varselId)
            )
        }
    }

    fun updateEksternStatus(varselId: String, status: String) {
        database.update {
            queryOf(
                //language=PostgreSQL
                """update alert_varsel_queue 
                    set status_ekstern = :status 
                    where varselId= :varselId""".trimIndent(),
                mapOf("varselId" to varselId, "status" to status)
            )
        }
    }

    fun alertStatus(alertRefId: String): AlertStatus =
        database.singleOrNull {
            queryOf( //language=PostgreSQL
                """
                |select sum(case when ferdigstilt is not null then 1 else 0 end) as ferdigstilte_varsler,
                | sum(case when varsel_lest is true then 1 else 0 end ) as leste_varsler,
                | sum(case when status_ekstern is not null then 1 else 0 end) as bestilte_varsler,
                |sum(case when status_ekstern='sendt' then 1 else 0 end) as sendte_varsler,
                | sum(case when status_ekstern='feilet' then 1 else 0 end) as feilende_varsler
                | from alert_varsel_queue
                | where alert_ref = :alertRef """.trimMargin(),
                mapOf("alertRef" to alertRefId)
            ).map { row ->
                AlertStatus(
                    antallLesteVarsler = row.int("leste_varsler"),
                    antallFerdigstilteVarsler = row.int("ferdigstilte_varsler"),
                    eksterneVarslerStatus = EksterneVarslerStatus(
                        antallBestilt = row.int("bestilte_varsler"),
                        antallSendt = row.int("sendte_varsler"),
                        antallFeilet = row.int("feilende_varsler")
                    )
                )
            }.asSingle
        } ?: AlertStatus.empty()
}

fun nowAtUtcZ() = ZonedDateTime.now(ZoneId.of("Z"))

class AlertStatus(
    val antallFerdigstilteVarsler: Int,
    val antallLesteVarsler: Int,
    val eksterneVarslerStatus: EksterneVarslerStatus
) {
    companion object {
        fun empty(): AlertStatus = AlertStatus(
            0, 0, EksterneVarslerStatus(
                0, 0, 0
            )
        )
    }
}

class EksterneVarslerStatus(
    val antallBestilt: Int,
    val antallSendt: Int,
    val antallFeilet: Int
)