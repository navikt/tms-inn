package no.nav.tms.brannslukning.alert.setup.database

import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import no.nav.tms.brannslukning.setup.database.Database
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer

class LocalPostgresDatabase private constructor() : Database {

    private val memDataSource: HikariDataSource
    private val container = PostgreSQLContainer("postgres:15.5")

    companion object {
        private val instance by lazy {
            LocalPostgresDatabase().also {
                it.migrate()
                it.clearTables()
            }
        }

        fun cleanDb(): LocalPostgresDatabase {
            return instance
        }
    }

    fun clearTables() {
        update { queryOf("delete from web_alert_mottakere") }
        update { queryOf("delete from alert_varsel_queue") }
        update { queryOf("delete from alert_header") }
    }

    init {
        container.start()
        memDataSource = createDataSource()
    }

    override val dataSource: HikariDataSource
        get() = memDataSource

    private fun createDataSource(): HikariDataSource {
        return HikariDataSource().apply {
            jdbcUrl = container.jdbcUrl
            username = container.username
            password = container.password
            isAutoCommit = true
            validate()
        }
    }

    private fun migrate() {
        Flyway.configure()
            .connectRetries(3)
            .dataSource(dataSource)
            .load()
            .migrate()
    }
}
