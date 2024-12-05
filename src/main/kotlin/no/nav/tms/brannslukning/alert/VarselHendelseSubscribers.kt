package no.nav.tms.brannslukning.alert

import no.nav.tms.kafka.application.JsonMessage
import no.nav.tms.kafka.application.Subscriber
import no.nav.tms.kafka.application.Subscription

class EksterntVarselStatusSubscriber(
    private val alertRepository: AlertRepository,
) : Subscriber() {
    override fun subscribe(): Subscription = Subscription
        .forEvent("eksternStatusOppdatert")
        .withFields("varselId")
        .withAnyValue("status", "venter", "kansellert", "bestilt", "feilet", "sendt")
        .withValue("appnavn", "tms-brannslukning")

    override suspend fun receive(jsonMessage: JsonMessage) {
        alertRepository.updateEksternStatus(jsonMessage["varselId"].asText(), jsonMessage["status"].asText())
    }
}

class VarselInaktivertSubscriber(
    private val alertRepository: AlertRepository,
) : Subscriber() {

    override fun subscribe(): Subscription = Subscription
        .forEvent("inaktivert")
        .withValue("appnavn", "tms-brannslukning")
        .withFields("varselId")

    override suspend fun receive(jsonMessage: JsonMessage) {
        alertRepository.setVarselLest(jsonMessage["varselId"].asText())

    }
}
