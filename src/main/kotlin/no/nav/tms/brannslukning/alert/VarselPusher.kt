package no.nav.tms.brannslukning.alert

import no.nav.tms.brannslukning.setup.PeriodicJob
import no.nav.tms.brannslukning.setup.PodLeaderElection
import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.action.Tekst
import no.nav.tms.varsel.action.Varseltype.Beskjed
import no.nav.tms.varsel.builder.VarselActionBuilder
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration
import java.util.UUID

class VarselPusher(
    private val alertRepository: AlertRepository,
    private val leaderElection: PodLeaderElection,
    private val kafkaProducer: Producer<String, String>,
    private val varselTopic: String,
    private val batchSize: Int = 500,
    private val produsentOverride: Produsent? = null,
    interval: Duration = Duration.ofSeconds(1)
) : PeriodicJob(interval = interval) {

    override val job = initializeJob {
        if (leaderElection.isLeader()) {
            alertRepository.nextInVarselQueue(batchSize)
                .forEach { request ->
                    sendBeskjed(request)
                    alertRepository.markAsSent(referenceId = request.referenceId, ident = request.ident)
                }
        }
    }

    private fun sendBeskjed(varselRequest: VarselRequest) {
        val beskjedId = UUID.randomUUID().toString()

        VarselActionBuilder.opprett {
            type = Beskjed
            varselId = beskjedId
            ident = varselRequest.ident
            link = varselRequest.beskjed.link
            varselRequest.beskjed.tekster.forEach {
                tekster += Tekst(
                    spraakkode = it.spraakkode,
                    tekst = it.tekst,
                    default = it.default
                )
            }
            eksternVarsling = EksternVarslingBestilling(
                prefererteKanaler = listOf(EksternKanal.SMS),
                smsVarslingstekst = varselRequest.eksternTekst.tekst,
                epostVarslingstekst = varselRequest.eksternTekst.tekst,
                epostVarslingstittel = varselRequest.eksternTekst.tittel
            )
            sensitivitet = Sensitivitet.Substantial
            produsentOverride?.let {
                produsent = it
            }
        }.let { beskjed ->
            kafkaProducer.send(ProducerRecord(varselTopic, beskjedId, beskjed))
        }
    }
}
