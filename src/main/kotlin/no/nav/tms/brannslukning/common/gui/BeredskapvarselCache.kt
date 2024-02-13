package no.nav.tms.brannslukning.common.gui

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

class ReadOnlyBeredskapsvarsel(
    id: String,
    title: String,
    description: String,
    initatedBy: User,
    val varselTekst: String,
    val eksternTekst: String,
    val url: String,
    val affectedCount: Int
) : Beredskapsvarsel(id, title, description, initatedBy)

class TmpBeredskapsvarsel(
    id: String = UUID.randomUUID().toString(),
    title: String,
    description: String,
    initatedBy: User
) : Beredskapsvarsel(id, title, description, initatedBy) {
    var varseltekst: String? = null
    fun varseltekstGuaranteed(): String = varseltekst.stringPropertyAssured("varseltekst")

    var eksternTekst: String? = null
    fun eksternTekstGuaranteed(): String = eksternTekst.stringPropertyAssured("eksternTekst")

    var affectedUsers: List<String> = emptyList()
        set(value) {
            field = value
            affectedCount = value.size
        }
    var affectedCount: Int = 0
    var url: String? = null
    fun urlGuaranteed() = url.stringPropertyAssured("url")

    fun toOpprettAlert() = OpprettAlert(
        referenceId = id,
        tekster = Tekster(
            tittel = title,
            beskrivelse = description,
            beskjed = WebTekst(
                spraakkode = "nb",
                tekst = varseltekstGuaranteed(),
                link = urlGuaranteed()
            ),
            eksternTekst = EksternTekst(
                tittel = "Varsel fra NAV",
                tekst = eksternTekstGuaranteed()
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

class PropertyAccessException(property: String) : Exception("$property er ikke satt")

fun String?.stringPropertyAssured(propertyName: String): String = this ?: throw PropertyAccessException(propertyName)