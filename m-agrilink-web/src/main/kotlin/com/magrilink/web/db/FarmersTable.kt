package com.magrilink.web.db

import org.jetbrains.exposed.sql.Table

/**
 * Secure registry of farmer accounts.
 *
 * Columns:
 * - id: primary auto-increment key
 * - countyLocation: county location vector (e.g. Tana River Corridor)
 * - activeCrop: active crop registry (e.g. Mango)
 * - ownerAccount: account registered owner (default Levis Lekesio Dev Node)
 */
object FarmersTable : Table("farmers") {
    val id = integer("id").autoIncrement()
    val countyLocation = varchar("county_location", 128)
    val activeCrop = varchar("active_crop", 128)
    val ownerAccount = varchar("owner_account", 128).default("Levis Lekesio Dev Node")

    override val primaryKey = PrimaryKey(id)
}
