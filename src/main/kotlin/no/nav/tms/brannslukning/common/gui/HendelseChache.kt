package no.nav.tms.brannslukning.common.gui

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.*
import java.util.concurrent.TimeUnit

private val objectmapper = jacksonObjectMapper()
internal object HendelseChache {
    val cache: Cache<String, String> = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .maximumSize(100)
        .build()

    fun putHendelse(hendelse: Hendelse) {
        cache.put(hendelse.id, jacksonObjectMapper().writeValueAsString(hendelse))
    }

    fun getHendelse(hendelseId: String) = cache.getIfPresent(hendelseId)?.let {
        objectmapper.readValue(it, object : TypeReference<Hendelse>() {})
    } ?: throw HendelseNotFoundException()

    fun invalidateHendelse(hendelseId: String) = cache.invalidate(hendelseId)
}

class Hendelse(
    val id: String = UUID.randomUUID().toString(),
    val varseltekst: String,
    val eksternTekst: String,
    val affectedUsers: List<String> = emptyList()
) {
    fun addAffectedUsers(userIdents: List<String>) =
        Hendelse(
            id = id,
            varseltekst = varseltekst,
            eksternTekst = eksternTekst,
            affectedUsers = userIdents
        )

}


class HendelseNotFoundException() : IllegalArgumentException()
