package no.nav.tms.brannslukning.common

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import java.net.InetAddress
import java.time.Instant

class PodLeaderElection(
    private val electionPath: String = getElectionUrl(),
    private val podName: String = InetAddress.getLocalHost().hostName,
    private val queryIntervalSeconds: Long = 60L
) {
    private var isLeader: Boolean = false
    private var previousQuery: Instant? = null

    suspend fun isLeader(): Boolean {
        if (shouldQueryForLeader()) {
            queryForLeader()
        }

        return isLeader
    }

    private val httpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                dateFormat = DateFormat.getDateTimeInstance()
            }
        }

        install(HttpTimeout)
    }

    private suspend fun queryForLeader() {
        val response: ElectorResponse = httpClient.get(electionPath).body()

        isLeader = response.name == podName

        previousQuery = Instant.now()
    }

    private fun shouldQueryForLeader() =
        if(previousQuery == null) {
            true
        } else {
            (Instant.now().epochSecond - previousQuery!!.epochSecond) > queryIntervalSeconds
        }


    companion object {
        private fun getElectionUrl(): String {
            val path = getEnvVar("ELECTOR_PATH", "")

            return if (path.isNotBlank()) {
                "http://$path"
            } else throw RuntimeException("Fant ikke variabel ELECTOR_PATH")
        }
    }
}
