package no.nav.tms.brannslukning.common.database

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Query
import kotliquery.action.ListResultQueryAction
import kotliquery.sessionOf
import kotliquery.using

interface Database {
    val dataSource: HikariDataSource
    fun update(queryBuilder: () -> Query) {
        using(sessionOf(dataSource)) {
            it.run(queryBuilder.invoke().asUpdate)
        }
    }

    fun <T> list(action: () -> ListResultQueryAction<T>): List<T> =
        using(sessionOf(dataSource)) {
            it.run(action.invoke())
        }


    fun batch(statement: String, params: List<Map<String, Any?>>) =
        using(sessionOf(dataSource)) {
            it.batchPreparedNamedStatement(statement, params)
        }
}
