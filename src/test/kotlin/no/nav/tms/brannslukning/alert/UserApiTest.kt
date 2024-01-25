package no.nav.tms.brannslukning.alert

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.jackson.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotliquery.queryOf
import no.nav.tms.brannslukning.alert.setup.database.LocalPostgresDatabase
import no.nav.tms.brannslukning.common.gui.User
import no.nav.tms.brannslukning.common.gui.brannslukningApi
import no.nav.tms.brannslukning.setup.database.Database
import no.nav.tms.brannslukning.setup.database.defaultObjectMapper
import no.nav.tms.brannslukning.setup.database.toJsonb
import no.nav.tms.token.support.azure.validation.mock.azureMock
import no.nav.tms.token.support.tokenx.validation.mock.LevelOfAssurance
import no.nav.tms.token.support.tokenx.validation.mock.tokenXMock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.text.DateFormat
import java.util.*

class UserApiTest {
    private val database = LocalPostgresDatabase.cleanDb()

    private val repository = AlertRepository(database)

    private val userIdent = "11111111111"

    private val objectMapper = defaultObjectMapper()

    @AfterEach
    fun deleteData() {
        database.clearTables()
    }

    @Test
    fun `henter relevant alert for bruker`() = testUserApi { client ->
        val alertMedMottakere = AlertWithRecipients(
            alertEntry = AlertEntry(
                referenceId = UUID.randomUUID().toString(),
                tekster = Tekster(
                    tittel = "Alert for test",
                    beskrivelse = "Alert for test med beskrivelse",
                    beskjed = Beskjed.withTekst(
                        link = "https://test",
                        spraakkode = "dummy",
                        internTekst = "dummy" ,
                        eksternTittel = "dummy",
                        eksternTekst = "dummy"

                    ),
                    webTekst = WebTekst(
                        domener = listOf("sub.domain"),
                        tekster = listOf(
                            Tekst(
                                spraakkode = "nb",
                                tekst = "Tekst for web",
                                default = true
                            )
                        )
                    )
                ),
                opprettetAv = User("TEST", "test")
            ),
            recipients = listOf(
                "11111111111",
                "22222222222",
                "33333333333",
            )
        )

        database.insertWebAlerts(alertMedMottakere)

        val alert = client.get("/user/alerts") {
            header("ident", "11111111111")
            header("domene", "sub.domain")
        }.bodyAsText()
            .let { objectMapper.readTree(it) }
            .also { it.size() shouldBe 1 }
            .first()

        alert["spraakkode"].asText() shouldBe "nb"
        alert["tekst"].asText() shouldBe "Tekst for web"
    }

    @KtorDsl
    private fun testUserApi(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {

        application {
            brannslukningApi(
                alertRepository = repository,
                authenticationConfig = {
                    authentication {
                        tokenXMock {
                            setAsDefault = false
                            alwaysAuthenticated = true
                            staticUserPid = userIdent
                            staticLevelOfAssurance = LevelOfAssurance.HIGH
                        }
                        azureMock {
                            setAsDefault = true
                            alwaysAuthenticated = true
                        }
                    }
                }
            )
        }

        this.block(
            client.config {
                install(ContentNegotiation) {
                    jackson {
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        registerModule(JavaTimeModule())
                        dateFormat = DateFormat.getDateTimeInstance()
                    }
                }
            }
        )
    }

}
private fun Database.insertWebAlerts(requests: AlertWithRecipients) {
    update {
        queryOf("""
                insert into alert_header(referenceId, tekster, opprettet, opprettetAv, aktiv)
                values (:referenceId, :tekster, :opprettet, :opprettetAv, :aktiv)
            """,
            mapOf(
                "referenceId" to requests.alertEntry.referenceId,
                "tekster" to requests.alertEntry.tekster.toJsonb(),
                "opprettet" to nowAtUtcZ(),
                "opprettetAv" to requests.alertEntry.opprettetAv.toJsonb(),
                "aktiv" to true
            )
        )
    }

    update {
        queryOf(
            """
                insert into web_alert_mottakere(alert_ref, mottakere, opprettet)
                values(:alertRef, :mottakere, :opprettet)
            """,
            mapOf(
                "alertRef" to requests.alertEntry.referenceId,
                "mottakere" to requests.recipients.toJsonb(),
                "opprettet" to nowAtUtcZ(),
            )
        )
    }
}
