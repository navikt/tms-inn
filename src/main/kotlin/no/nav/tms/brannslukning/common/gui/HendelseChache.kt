package no.nav.tms.brannslukning.common.gui

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*
import java.util.concurrent.TimeUnit

private val objectmapper = jacksonObjectMapper()

internal object HendelseChache {
    private val log = KotlinLogging.logger {  }
    private val cache: Cache<String, String> = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(100)
        .build()

    fun putHendelse(tmpHendelse: TmpHendelse) {
        cache.put(tmpHendelse.id, jacksonObjectMapper().writeValueAsString(tmpHendelse))
    }

    fun getHendelse(hendelseId: String) = cache.getIfPresent(hendelseId)?.let {
        objectmapper.readValue(it, object : TypeReference<TmpHendelse>() {})
    } ?: throw HendelseNotFoundException()

    fun invalidateHendelse(hendelseId: String)  {
        //cache.invalidate(hendelseId)
        log.info { "TODO invalider når integrert med backend " }
    }

    fun getAllHendelser()=
        cache.asMap().values.map {
            objectmapper.readValue(it, object : TypeReference<TmpHendelse>() {})
        }.also {
            log.info { "TODO: erstatt med db kall" }
        }

    fun tmpClose(id: String) {
        log.info { "TODO: erstatt med db kall" }
        cache.invalidate(id)
    }


}

data class TmpHendelse(
    val id: String = UUID.randomUUID().toString(),
    val initatedBy: User,
    val varseltekst: String,
    val eksternTekst: String,
    val affectedUsers: List<String> = emptyList()
) {
    fun addAffectedUsers(userIdents: List<String>) = copy(affectedUsers = userIdents)
}
data class User(val preferredUsername:String, val oid:String)

class HendelseNotFoundException : IllegalArgumentException()
