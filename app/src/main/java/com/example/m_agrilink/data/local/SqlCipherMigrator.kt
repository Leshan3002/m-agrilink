package com.example.m_agrilink.data.local

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.io.FileInputStream

/**
 * One-time plaintext -> SQLCipher migration for the pre-encryption installs.
 * Opens the legacy file with an empty key (SQLCipher reads unencrypted DBs
 * that way) and rekeys it in place. Any failure wipes the file so Room can
 * recreate it encrypted — cache/history data only, the app never bricks.
 */
object SqlCipherMigrator {

    private const val DB_NAME = "magrilink_database"

    fun ensureEncrypted(context: Context, passphrase: ByteArray) {
        SQLiteDatabase.loadLibs(context)
        val path = context.getDatabasePath(DB_NAME)
        if (!path.exists() || path.length() == 0L || isEncrypted(path)) return
        try {
            val hex = passphrase.joinToString("") { "%02x".format(it) }
            val db = SQLiteDatabase.openOrCreateDatabase(
                path.path, "".toCharArray(), null, null
            )
            try {
                db.rawExecSQL("PRAGMA rekey = \"x'$hex'\";")
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            try {
                if (path.exists()) path.delete()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun isEncrypted(file: File): Boolean {
        return try {
            val header = ByteArray(16)
            FileInputStream(file).use { it.read(header) }
            !String(header, Charsets.UTF_8).startsWith("SQLite format 3")
        } catch (e: Exception) {
            false
        }
    }
}
