package com.magrilink.web.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Asynchronous PostgreSQL conduit backed by HikariCP + Exposed.
 *
 * Call [init] once at startup. Use [dbQuery] for thread-safe queries
 * (confined to Dispatchers.IO via newSuspendedTransaction).
 */
object DatabaseFactory {

    private var dataSource: HikariDataSource? = null

    @Volatile
    var available: Boolean = false
        private set

    fun init(
        jdbcUrl: String = System.getenv("DATABASE_URL")
            ?: "jdbc:postgresql://localhost:5432/magrilink",
        user: String = System.getenv("DB_USER") ?: "magrilink",
        password: String = System.getenv("DB_PASSWORD") ?: "magrilink",
        maximumPoolSize: Int = 10
    ) {
        if (dataSource != null) return

        try {
            val config = HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                this.username = user
                this.password = password
                this.driverClassName = "org.postgresql.Driver"
                this.maximumPoolSize = maximumPoolSize
                this.isAutoCommit = false
                this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                this.poolName = "magrilink-pg-pool"
                this.initializationFailTimeout = 5000
                this.connectionTimeout = 5000
                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
                addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            }

            val ds = HikariDataSource(config)
            dataSource = ds
            Database.connect(ds)

            transaction {
                SchemaUtils.create(FarmersTable)
            }
            available = true
            println("DatabaseFactory: PostgreSQL connected at $jdbcUrl")
        } catch (e: Exception) {
            println("DatabaseFactory: DB unavailable, running in fallback matrix mode: ${e.message}")
            try {
                dataSource?.close()
            } catch (_: Exception) {
            }
            dataSource = null
            available = false
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T {
        if (!available) throw IllegalStateException("DB not available - fallback mode")
        return newSuspendedTransaction(Dispatchers.IO) { block() }
    }

    fun close() {
        try {
            dataSource?.close()
        } catch (_: Exception) {
        }
        dataSource = null
        available = false
    }
}
