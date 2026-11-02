package com.bookorbit.feature.dictionary

import android.database.sqlite.SQLiteDatabase
import com.bookorbit.feature.reader.DictionaryDefinition
import com.bookorbit.feature.reader.DictionarySource
import com.bookorbit.feature.reader.StoredEntry
import com.bookorbit.feature.reader.StoredForm
import com.bookorbit.feature.reader.StoredMatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** A downloaded dictionary file (built by tools/dictionary/build_dictionary.py), opened read-only. */
internal class DictionarySqlite private constructor(
    private val db: SQLiteDatabase,
    private val json: Json,
) : DictionarySource {

    @Serializable
    private data class Sense(val g: String, val x: String? = null)

    private val senses = ListSerializer(Sense.serializer())

    override fun find(key: String) = match("key = ?", key)

    override fun findFolded(folded: String) = match("(key = ?1 OR fold = ?1)", folded)

    private fun match(where: String, value: String): StoredMatch {
        val entries = db.rawQuery("SELECT key, word, pos, ipa, senses FROM entries WHERE $where", arrayOf(value)).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val defs = runCatching { json.decodeFromString(senses, c.getString(4)) }.getOrDefault(emptyList())
                    add(StoredEntry(c.getString(1) ?: c.getString(0), c.getString(2), c.getString(3), defs.map { DictionaryDefinition(it.g, it.x) }))
                }
            }
        }
        val forms = db.rawQuery(
            "SELECT f.key, f.pos, f.lemma, l.text FROM forms f LEFT JOIN labels l ON l.id = f.label WHERE ${where.replace("key", "f.key").replace("fold", "f.fold")}",
            arrayOf(value),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val lemma = c.getString(2)
                    add(StoredForm(c.getString(0), c.getString(1), lemma, c.getString(3)?.replace("{}", lemma)))
                }
            }
        }
        // Inflection-table forms are stored by exact spelling only, so a folded lookup matches them as typed.
        val inflections = db.rawQuery(
            "SELECT i.key, w.word FROM inflections i JOIN lemmas w ON w.id = i.lemma WHERE i.key = ?",
            arrayOf(value),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(StoredForm(c.getString(0), null, c.getString(1), null))
            }
        }
        return StoredMatch(entries, forms + inflections)
    }

    fun close() = db.close()

    companion object {
        fun open(file: File, json: Json) =
            DictionarySqlite(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS), json)
    }
}
