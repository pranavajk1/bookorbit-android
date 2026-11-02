package com.bookorbit.feature.downloads

import com.bookorbit.core.db.AudioProgressDao
import com.bookorbit.core.db.DownloadDao
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.db.ReaderProgressDao
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.BookDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The downloaded books as cards, most recently read or listened to first, with the reading position
 * kept on the device. This is what Home and the library show when the server can't be reached, the
 * way a streaming app falls back to its downloads.
 */
@Singleton
class OnDeviceBooks @Inject constructor(
    downloadDao: DownloadDao,
    readerProgressDao: ReaderProgressDao,
    audioProgressDao: AudioProgressDao,
    private val json: Json,
) {
    val books: Flow<List<BookCard>> = combine(
        downloadDao.observeAll(),
        readerProgressDao.observeAll(),
        audioProgressDao.observeAll(),
    ) { downloads, reading, listening ->
        val readingByFile = reading.associateBy { it.fileId }
        val listeningByBook = listening.associateBy { it.bookId }
        downloads
            .filter { it.status.isCompleteDownloadStatus() }
            .mapNotNull { entity -> decode(entity)?.let { entity to it } }
            .map { (entity, book) ->
                val read = book.files.mapNotNull { readingByFile[it.id] }.maxByOrNull { it.updatedAt }
                val listened = listeningByBook[book.id]
                val lastUsed = maxOf(read?.updatedAt ?: 0L, listened?.updatedAt ?: 0L)
                val progress = if (entity.isAudiobook) listened?.percentage else read?.percentage
                OnDevice(book.toCard(progress), lastUsed, entity.downloadedAt)
            }
            .sortedWith(compareByDescending<OnDevice> { it.lastUsed }.thenByDescending { it.downloadedAt })
            .map { it.card }
    }

    private data class OnDevice(val card: BookCard, val lastUsed: Long, val downloadedAt: Long)

    private fun decode(entity: DownloadEntity): BookDetail? =
        runCatching { json.decodeFromString(BookDetail.serializer(), entity.bookJson) }.getOrNull()
}

/** A detail as the lighter card shape the shelves and grids draw. */
internal fun BookDetail.toCard(readingProgress: Double?) = BookCard(
    id = id,
    title = title,
    authors = authors.map { it.name },
    seriesName = seriesName,
    seriesIndex = seriesIndex,
    files = files,
    publishedYear = publishedYear,
    language = language,
    genres = genres,
    rating = rating,
    readingProgress = readingProgress,
    addedAt = addedAt,
    hasCover = true,
    tags = tags,
    narrators = audioMetadata?.narrators?.map { it.name }.orEmpty(),
)
