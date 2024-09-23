package no.nav.tms.brannslukning.gui

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import no.nav.tms.brannslukning.alert.*
import java.util.*
import java.util.concurrent.TimeUnit

internal object BeredskapvarselCache {


    private val cache: Cache<String, TmpBeredskapsvarsel> = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .maximumSize(100)
        .build()

    fun putHendelse(tmpHendelse: TmpBeredskapsvarsel) {
        cache.put(tmpHendelse.id, tmpHendelse)
    }

    fun getHendelse(hendelseId: String): TmpBeredskapsvarsel? = cache.getIfPresent(hendelseId)

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

    var parsedFile: IdentParseResult? = null
        set(value) {
        field = value
        affectedUsers = value?.valid ?: emptyList()
        affectedCount = value?.valid?.size ?: 0
    }

    var affectedUsers: List<String> = emptyList()
    var affectedCount: Int = 0

    val errors: List<IdentParseResult.Error> get() = parsedFile?.errors ?: emptyList()

    var link: String? = null

    fun nonBlankLinkOrNull() = if (link.isNullOrBlank()) {
        null
    } else {
        link
    }

    fun toOpprettAlert() = OpprettAlert(
        referenceId = id,
        tekster = Tekster(
            tittel = title,
            beskrivelse = description,
            beskjed = WebTekst(
                spraakkode = "nb",
                tekst = varseltekst!!,
                link = nonBlankLinkOrNull()
            ),
            eksternTekst = EksternTekst(
                tittel = "Varsel fra NAV",
                tekst = eksternTekst!!
            )
        ),
        opprettetAv = initatedBy,
        mottakere = affectedUsers
    )
}

data class User(val username: String, val oid: String)

class HendelseNotFoundException(alertRefId: String) : IllegalArgumentException("Fant beredskapsvarsel med id $alertRefId")
class VarslerNotFoundException(alertRefId: String) :
    IllegalArgumentException("Fant ikke varsler for beredskapsvarsel med id $alertRefId")
