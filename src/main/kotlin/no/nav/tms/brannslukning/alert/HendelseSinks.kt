package no.nav.tms.brannslukning.alert

import no.nav.helse.rapids_rivers.*

class EksterntVarselStatusSink(
    rapidsConnection: RapidsConnection,
    private val alertRepository: AlertRepository,
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "eksternStatusOppdatert")
                it.requireValue("appnavn", "tms-brannslukning")
                it.requireKey("varselId", "status")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        //sendt trumfer bestilt og feilet
        alertRepository.updateEksternStatus(packet["varselId"].asText(), packet["status"].asText())
    }
}

class VarselInaktivertSink(
    rapidsConnection: RapidsConnection,
    private val alertRepository: AlertRepository,
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "inaktivert")
                it.requireValue("appnavn", "tms-brannslukning")
                it.requireKey("varselId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        alertRepository.setVarselLest(packet["varselId"].asText())
    }
}
