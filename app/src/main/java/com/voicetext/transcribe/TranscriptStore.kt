package com.voicetext.transcribe

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.TimeUnit

/** A saved transcript, keyed by the SHA-256 fingerprint of its audio. */
data class Transcript(
    val audioHash: String,
    val text: String,
    val createdAt: Long,
    val lastOpenedAt: Long,
)

/**
 * Local history of transcripts (plain SQLite, no extra libraries).
 *
 * Only the audio's fingerprint and the transcript text are stored, never the
 * audio itself. Entries expire [RETENTION_MS] after they were last opened
 * (re-shared or viewed in History); [purgeExpired] removes them.
 *
 * All methods do disk I/O: call them off the main thread. Use [get] so the
 * whole app shares one database connection.
 */
class TranscriptStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_HASH TEXT PRIMARY KEY NOT NULL,
                $COL_TEXT TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_LAST_OPENED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_last_opened ON $TABLE($COL_LAST_OPENED_AT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Returns the transcript for [audioHash], if any, and marks it as just opened. */
    fun open(audioHash: String, now: Long = System.currentTimeMillis()): Transcript? {
        val found = readableDatabase.query(
            TABLE, null, "$COL_HASH = ?", arrayOf(audioHash), null, null, null
        ).use { if (it.moveToFirst()) it.toTranscript() else null } ?: return null

        touch(audioHash, now)
        return found.copy(lastOpenedAt = now)
    }

    fun save(audioHash: String, text: String, now: Long = System.currentTimeMillis()): Transcript {
        val values = ContentValues().apply {
            put(COL_HASH, audioHash)
            put(COL_TEXT, text)
            put(COL_CREATED_AT, now)
            put(COL_LAST_OPENED_AT, now)
        }
        writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return Transcript(audioHash, text, now, now)
    }

    /** All transcripts, most recently transcribed first. */
    fun list(): List<Transcript> =
        readableDatabase.query(TABLE, null, null, null, null, null, "$COL_CREATED_AT DESC").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toTranscript()) }
        }

    fun touch(audioHash: String, now: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply { put(COL_LAST_OPENED_AT, now) }
        writableDatabase.update(TABLE, values, "$COL_HASH = ?", arrayOf(audioHash))
    }

    fun delete(audioHash: String) {
        writableDatabase.delete(TABLE, "$COL_HASH = ?", arrayOf(audioHash))
    }

    fun clearAll() {
        writableDatabase.delete(TABLE, null, null)
    }

    /** Deletes transcripts not opened in the last [RETENTION_MS]. */
    fun purgeExpired(now: Long = System.currentTimeMillis()) {
        writableDatabase.delete(
            TABLE, "$COL_LAST_OPENED_AT < ?", arrayOf((now - RETENTION_MS).toString())
        )
    }

    private fun Cursor.toTranscript() = Transcript(
        audioHash = getString(getColumnIndexOrThrow(COL_HASH)),
        text = getString(getColumnIndexOrThrow(COL_TEXT)),
        createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
        lastOpenedAt = getLong(getColumnIndexOrThrow(COL_LAST_OPENED_AT)),
    )

    companion object {
        val RETENTION_MS: Long = TimeUnit.DAYS.toMillis(30)

        @Volatile private var instance: TranscriptStore? = null

        fun get(context: Context): TranscriptStore =
            instance ?: synchronized(this) {
                instance ?: TranscriptStore(context).also { instance = it }
            }

        private const val DB_NAME = "transcripts.db"
        private const val DB_VERSION = 1
        private const val TABLE = "transcripts"
        private const val COL_HASH = "audio_hash"
        private const val COL_TEXT = "text"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_LAST_OPENED_AT = "last_opened_at"
    }
}
