package com.magrilink.web.routes

import com.magrilink.web.data.KenyaCounties
import com.magrilink.web.db.DatabaseFactory
import com.magrilink.web.db.FarmersTable
import com.magrilink.web.model.MarketPriceEntry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.Instant
import kotlinx.coroutines.CancellationException
import org.jetbrains.exposed.sql.selectAll

/**
 * GET /api/v1/market-analytics
 *
 * Thread-safe read of [FarmersTable] via [DatabaseFactory.dbQuery].
 * Returns a JSON array covering the 47-county price matrix.
 * Falls back to a sanitized static matrix when the table is empty
 * or the database is unreachable.
 */
fun Route.marketAnalyticsRoutes() {
    get("/api/v1/market-analytics") {
        val cropParam = call.parameters["crop"]?.trim().orEmpty()
        val requestedCrop = cropParam.ifEmpty { "Mango" }

        val entries: List<MarketPriceEntry> = if (!DatabaseFactory.available) {
            buildFallbackMatrix(requestedCrop)
        } else {
            try {
                val rows = DatabaseFactory.dbQuery {
                    FarmersTable.selectAll().limit(200).map { row ->
                        Triple(
                            row[FarmersTable.countyLocation],
                            row[FarmersTable.activeCrop],
                            row[FarmersTable.ownerAccount]
                        )
                    }
                }

                if (rows.isEmpty()) {
                    buildFallbackMatrix(requestedCrop)
                } else {
                    val now = Instant.now().toString()
                    val filtered = if (cropParam.isEmpty()) {
                        rows
                    } else {
                        rows.filter { it.second.equals(requestedCrop, ignoreCase = true) }
                    }
                    val source = filtered.ifEmpty { rows }
                    source.mapIndexed { index, triple ->
                        MarketPriceEntry(
                            county = sanitizeCounty(triple.first),
                            crop = triple.second,
                            priceKshPerKg = deterministicPrice(triple.first, triple.second, index),
                            ownerAccount = triple.third,
                            updatedAt = now
                        )
                    }.take(47)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                application.environment.log.warn("DB unavailable, serving fallback matrix", e)
                buildFallbackMatrix(requestedCrop)
            }
        }

        call.respond(HttpStatusCode.OK, entries)
    }
}

private fun buildFallbackMatrix(crop: String): List<MarketPriceEntry> {
    val now = Instant.now().toString()
    return KenyaCounties.all.mapIndexed { index, county ->
        MarketPriceEntry(
            county = county,
            crop = crop,
            priceKshPerKg = deterministicPrice(county, crop, index),
            ownerAccount = "Levis Lekesio Dev Node",
            updatedAt = now
        )
    }
}

private fun sanitizeCounty(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "Nairobi"
    return trimmed.replace(Regex("\\s+"), " ").take(128)
}

private fun deterministicPrice(county: String, crop: String, index: Int): Double {
    val base = 40.0 + ((county.hashCode() and 0x7fffffff) % 120) + ((crop.hashCode() and 0x7fffffff) % 30)
    val jitter = (index % 7) * 1.5
    return ((base + jitter) * 100.0).toInt() / 100.0
}
