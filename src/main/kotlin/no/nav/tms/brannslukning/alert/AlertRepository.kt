package no.nav.tms.brannslukning.alert

import EksterneVarslerStatus
import VarselStatus
import kotliquery.queryOf
import no.nav.tms.brannslukning.gui.TmpBeredskapsvarsel
import no.nav.tms.brannslukning.gui.User
import no.nav.tms.brannslukning.gui.VarslerNotFoundException
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
    fun fetchHendelse(referenceId: String): TmpBeredskapsvarsel? {
        return database.singleOrNull {
            queryOf(
                //language=PostgreSQL
                """
                    select ah.*, count(aq.ident)
                    from alert_header as ah
                    left join alert_beskjed_queue as aq on ah.referenceid = aq.alert_ref
                    where ah.referenceId = :referenceId
                    group by referenceid
                """,
                mapOf("referenceId" to referenceId)
            ).map {
                val tekster: Tekster = it.json("tekster", objectMapper)

                TmpBeredskapsvarsel(
                    id = it.string("referenceId"),
                    initatedBy = it.json("opprettetAv", objectMapper),
                    title = tekster.tittel,
                    description = tekster.beskrivelse
                ).apply {
                    varseltekst = tekster.beskjed.tekst
                    eksternTekst = tekster.eksternTekst.tekst
                    link = tekster.beskjed.link
                    affectedCount = it.int("count")
                }
            }.asSingle
        }
    }

    private fun alerts(aktiv: Boolean): List<AlertInfo> {
        return database.list {
            queryOf(
                //language=PostgreSQL
                """
                    select 
                        ah.*,
                        count(*) as mottakere,
                        count(*) filter ( where ferdigstilt is not null) as ferdigstilte_varsler,
                        count(*) filter ( where varsel_lest is true) as leste_varsler,
                        count(*) filter ( where status_ekstern is not null) as bestilte_varsler,
                        count(*) filter ( where status_ekstern='sendt') as sendte_varsler,
                        count(*) filter ( where status_ekstern='feilet') as feilende_varsler
                    from alert_header as ah
                        left join alert_beskjed_queue as abq on ah.referenceId = abq.alert_ref
                    where ah.aktiv = :aktiv
                    group by ah.referenceId
                    order by opprettet desc
                """,
                mapOf("aktiv" to aktiv)
            ).map {row ->
                AlertInfo(
                    referenceId = row.string("referenceId"),
                    tekster = row.json("tekster"),
                    opprettet = row.zonedDateTime("opprettet"),
                    opprettetAv = row.json("opprettetAv", objectMapper),
                    aktiv = row.boolean("aktiv"),
                    mottakere = row.int("mottakere"),
                    avsluttet = row.zonedDateTimeOrNull("avsluttet"),
                    avsluttetAv = if (aktiv) null else row.json("avsluttetAv", objectMapper),
                    varselStatus = VarselStatus(
                        antallLesteVarsler = row.int("leste_varsler"),
                        antallFerdigstilteVarsler = row.int("ferdigstilte_varsler"),
                        eksterneVarslerStatus = EksterneVarslerStatus(
                            antallBestilt = row.int("bestilte_varsler"),
                            antallSendt = row.int("sendte_varsler"),
                            antallFeilet = row.int("feilende_varsler")
                        )
                    )
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
            "insert into alert_beskjed_queue(alert_ref, ident, opprettet) values(:referenceId, :ident, :opprettet) on conflict do nothing",
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
                        abq.alert_ref, abq.ident, ah.tekster->'beskjed' as beskjed, ah.tekster->'eksternTekst' as eksternTekst, ah.tekster ->> 'tittel' as tittel
                    from 
                        alert_beskjed_queue as abq
                        join alert_header as ah on abq.alert_ref = ah.referenceId
                    where not behandlet order by abq.opprettet limit :antall""".trimIndent(),
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
                "update alert_beskjed_queue set behandlet = true, ferdigstilt = :ferdigstilt, varselId = :varselId, status = :status where alert_ref = :referenceId and ident = :ident",
                mapOf(
                    "ident" to ident,
                    "referenceId" to referenceId,
                    "ferdigstilt" to nowAtUtcZ(),
                    "varselId" to varselId,
                    "status" to BeskjedStatus.Sendt.dbName
                )
            )
        }
    }

    fun markAsFailed(referenceId: String, ident: String, feilkilde: Feilkilde) {
        database.update {
            queryOf(
                "update alert_beskjed_queue set behandlet = true, ferdigstilt = :ferdigstilt, status = :status, feilkilde = :feilkilde where alert_ref = :referenceId and ident = :ident",
                mapOf(
                    "ident" to ident,
                    "referenceId" to referenceId,
                    "ferdigstilt" to nowAtUtcZ(),
                    "status" to BeskjedStatus.Feilet.dbName,
                    "feilkilde" to feilkilde.toJsonb(objectMapper)
                )
            )
        }
    }

    fun setVarselLest(varselId: String) {
        database.update {
            queryOf(
                //language=PostgreSQL
                """update alert_beskjed_queue 
                    set varsel_lest = true 
                    where varselId= :varselId""".trimIndent(),
                mapOf("varselId" to varselId)
            )
        }
    }

    fun updateEksternStatus(varselId: String, status: String) {
        val oldStatus = database.singleOrNull {
            queryOf(
                //language=PostgreSQL
                """select status_ekstern from alert_beskjed_queue 
                    where varselId= :varselId""".trimIndent(),
                mapOf("varselId" to varselId)
            ).map {
                it.stringOrNull("status_ekstern")
            }.asSingle
        }

        database.update {
            queryOf(
                //language=PostgreSQL
                """update alert_beskjed_queue 
                    set status_ekstern = :status 
                    where varselId= :varselId""".trimIndent(),
                mapOf("varselId" to varselId, "status" to EksternStatus.resolve(oldStatus, status))
            )
        }
    }

    fun varselStatus(alertRefId: String): VarselStatus =
        database.singleOrNull {
            queryOf( //language=PostgreSQL
                """
                |select count(1) filter ( where ferdigstilt is not null) as ferdigstilte_varsler,
                | count(1) filter ( where varsel_lest is true) as leste_varsler,
                | count(1) filter ( where status_ekstern is not null) as bestilte_varsler,
                | count(1) filter ( where status_ekstern='sendt') as sendte_varsler,
                | count(1) filter ( where status_ekstern='feilet') as feilende_varsler
                | from alert_beskjed_queue
                | where alert_ref = :alertRef """.trimMargin(),
                mapOf("alertRef" to alertRefId)
            ).map { row ->
                VarselStatus(
                    antallLesteVarsler = row.int("leste_varsler"),
                    antallFerdigstilteVarsler = row.int("ferdigstilte_varsler"),
                    eksterneVarslerStatus = EksterneVarslerStatus(
                        antallBestilt = row.int("bestilte_varsler"),
                        antallSendt = row.int("sendte_varsler"),
                        antallFeilet = row.int("feilende_varsler")
                    )
                )
            }.asSingle
        } ?: throw VarslerNotFoundException(alertRefId)
}

fun nowAtUtcZ() = ZonedDateTime.now(ZoneId.of("Z"))
