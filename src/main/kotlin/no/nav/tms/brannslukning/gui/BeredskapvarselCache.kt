package no.nav.tms.brannslukning.gui

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import no.nav.tms.brannslukning.alert.*
import java.util.*
import java.util.concurrent.TimeUnit

private val objectmapper = jacksonObjectMapper()

internal object BeredskapvarselCache {
    private val cache: Cache<String, String> = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .maximumSize(100)
        .build()

    fun putHendelse(tmpHendelse: TmpBeredskapsvarsel) {
        cache.put(tmpHendelse.id, jacksonObjectMapper().writeValueAsString(tmpHendelse))
    }

    fun getHendelse(hendelseId: String): TmpBeredskapsvarsel? = cache.getIfPresent(hendelseId)?.let {
        objectmapper.readValue(it, object : TypeReference<TmpBeredskapsvarsel>() {})
    }

    fun invalidateHendelse(hendelseId: String) {
        cache.invalidate(hendelseId)
    }

    fun tmpClose(id: String) {
        cache.invalidate(id)
    }
}

abstract class Beredskapsvarsel(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val initatedBy: User
)

class TmpBeredskapsvarsel(
    id: String = UUID.randomUUID().toString(),
    title: String,
    description: String,
    initatedBy: User
) : Beredskapsvarsel(id, title, description, initatedBy) {

    var varseltekst: String? = null
    var eksternTekst: String? = null

    var affectedUsers: List<String>? = null
    var affectedCount: Int? = null

    fun countUsersAffected() = affectedUsers?.size
        ?: affectedCount
        ?: 0

    var link: String? = null

    fun toOpprettAlert() = OpprettAlert(
        referenceId = id,
        tekster = Tekster(
            tittel = title,
            beskrivelse = description,
            beskjed = WebTekst(
                spraakkode = "nb",
                tekst = varseltekst!!,
                link = link!!
            ),
            eksternTekst = EksternTekst(
                tittel = "Varsel fra NAV",
                tekst = eksternTekst!!
            )
        ),
        opprettetAv = initatedBy,
        mottakere = affectedUsers!!
    )
}

data class User(val username: String, val oid: String)

class HendelseNotFoundException(alertRefId: String) : IllegalArgumentException("Fant beredskapsvarsel med id $alertRefId")
class VarslerNotFoundException(alertRefId: String) :
    IllegalArgumentException("Fant ikke varsler for beredskapsvarsel med id $alertRefId")
