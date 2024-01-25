package no.nav.tms.brannslukning.common.gui

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.brannslukning.alert.*
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit

private val objectmapper = jacksonObjectMapper()

internal object HendelseCache {
    private val log = KotlinLogging.logger { }
    private val cache: Cache<String, String> = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .maximumSize(100)
        .build()
//Trigg deploy
    fun putHendelse(tmpHendelse: TmpHendelse) {
        cache.put(tmpHendelse.id, jacksonObjectMapper().writeValueAsString(tmpHendelse))
    }

    fun getHendelse(hendelseId: String): TmpHendelse? = cache.getIfPresent(hendelseId)?.let {
        objectmapper.readValue(it, object : TypeReference<TmpHendelse>() {})
    }

    fun invalidateHendelse(hendelseId: String) {
        cache.invalidate(hendelseId)
    }

    fun tmpClose(id: String) {
        cache.invalidate(id)
    }
}

data class TmpHendelse(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val initatedBy: User,
    val varseltekst: String,
    val eksternTekst: String,
    val affectedUsers: List<String> = emptyList(),
    val url: String
) {
    fun withAffectedUsers(userIdents: List<String>) = copy(affectedUsers = userIdents)
    fun withUpdatedText(beskjedTekst: String, url: String, eksternTekst: String, description: String,title: String): TmpHendelse = copy(
        varseltekst = beskjedTekst,
        url = url,
        eksternTekst = eksternTekst,
        description = description,
        title = title
    )

    fun toOpprettAlert() = OpprettAlert(
        referenceId = id,
        tekster = Tekster(
            tittel = title,
            beskrivelse = description,
            beskjed = Beskjed.withTekst(
                spraakkode = "nb",
                internTekst = varseltekst,
                eksternTekst = eksternTekst,
                eksternTittel = "Varsel fra NAV",
                link = url
            ),
            webTekst = WebTekst(
                tekster = listOf(Tekst("nb", varseltekst, default = true)),
                domener = listOf("www.intern.dev.nav.no")
            )
        ),
        opprettetAv = initatedBy,
        mottakere = affectedUsers,
        aktivFremTil = nowAtUtcZ().plusDays(7)
    )
}

data class User(val username: String, val oid: String)

class HendelseNotFoundException : IllegalArgumentException()
