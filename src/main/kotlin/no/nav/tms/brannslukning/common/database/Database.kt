package no.nav.tms.brannslukning.common.database

import com.zaxxer.hikari.HikariDataSource
import no.nav.personbruker.dittnav.varselbestiller.common.RetriableDatabaseException
import no.nav.personbruker.dittnav.varselbestiller.common.UnretriableDatabaseException
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException

interface Database {

    val dataSource: HikariDataSource

    fun <T> dbQuery(operationToExecute: Connection.() -> T): T {
        return dataSource.connection.use { openConnection ->
            try {
                openConnection.operationToExecute().apply {
                    openConnection.commit()
                }

            } catch (e: Exception) {
                try {
                    openConnection.rollback()
                } catch (rollbackException: Exception) {
                    e.addSuppressed(rollbackException)
                }
                throw e
            }
        }
    }

    fun <T> queryWithExceptionTranslation(
        identifier: String? = null,
        operationToExecute: Connection.() -> T
    ): T {
        return translateExternalExceptionsToInternalOnes(identifier) {
            dbQuery {
                operationToExecute()
            }
        }
    }
}

inline fun <T> translateExternalExceptionsToInternalOnes(identifier: String? = null, databaseActions: () -> T): T {
    return try {
        databaseActions()

    } catch (te: SQLTransientException) {
        val message = "Skriving til databasen feilet grunnet en periodisk feil."
        throw RetriableDatabaseException(message, te,identifier)

    } catch (re: SQLRecoverableException) {
        val message = "Skriving til databasen feilet grunnet en periodisk feil."
        throw RetriableDatabaseException(message, re,identifier)

    } catch (pe: PSQLException) {
        val message = "Det skjedde en SQL relatert feil ved skriving til databasen."
        val ure = UnretriableDatabaseException(message, pe,identifier)
        pe.sqlState?.map { sqlState -> ure.addContext("sqlState", sqlState) }
        throw ure

    } catch (se: SQLException) {
        val message = "Det skjedde en SQL relatert feil ved skriving til databasen."
        val ure = UnretriableDatabaseException(message, se, identifier)
        se.sqlState?.map { sqlState -> ure.addContext("sqlState", sqlState) }
        throw ure

    } catch (e: Exception) {
        val message = "Det skjedde en ukjent feil ved skriving til databasen."
        throw UnretriableDatabaseException(message, e, identifier)
    }
}
