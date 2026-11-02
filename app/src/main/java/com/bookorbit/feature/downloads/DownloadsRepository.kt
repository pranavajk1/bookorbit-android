package com.bookorbit.feature.downloads

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.bookorbit.core.auth.SessionManager
import com.bookorbit.core.db.DownloadDao
import com.bookorbit.core.db.DownloadEntity
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.settings.AppSettingsStore
import com.bookorbit.core.settings.DownloadLocationStore
import com.bookorbit.core.storage.DownloadFileNaming
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.delete
import dagger.hilt.android.qualifiers.ApplicationContext
import com.bookorbit.core.network.Connectivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Orchestrates downloads (WorkManager) and exposes the offline catalog. */
@Singleton
class DownloadsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val json: Json,
    private val appSettings: AppSettingsStore,
    private val locationStore: DownloadLocationStore,
    private val session: SessionManager,
    private val connectivity: Connectivity,
) {
    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    fun observe(bookId: Int): Flow<DownloadEntity?> = dao.observe(bookId)

    suspend fun get(bookId: Int): DownloadEntity? = dao.get(bookId)

    suspend fun isDownloaded(bookId: Int): Boolean = dao.get(bookId)?.status?.isCompleteDownloadStatus() == true

    /** The BookDetail persisted at download time, so book detail/reader/player screens work fully offline. */
    suspend fun cachedBook(bookId: Int): BookDetail? =
        dao.get(bookId)?.let { runCatching { json.decodeFromString(BookDetail.serializer(), it.bookJson) }.getOrNull() }

    /** Map of fileId -> local file path for a completed download (else empty). */
    suspend fun localFiles(bookId: Int): Map<Int, String> {
        val entity = dao.get(bookId)?.takeIf { it.status.isCompleteDownloadStatus() } ?: return emptyMap()
        return decodeFiles(entity).associate { it.id to it.localPath }
    }

    suspend fun coverPath(bookId: Int): String? =
        dao.get(bookId)?.takeIf { it.status.isCompleteDownloadStatus() }?.coverLocalPath

    fun decodeFiles(entity: DownloadEntity): List<DownloadedFile> =
        runCatching { json.decodeFromString(ListSerializer(DownloadedFile.serializer()), entity.filesJson) }
            .getOrDefault(emptyList())

    suspend fun startDownload(book: BookDetail) = enqueue(book, forReading = false)

    /**
     * Makes sure [book] is on the device so it can be read now and offline later, and returns its
     * fileId -> local path map. Starts the download straight away on any network (opening a book is
     * a request to read it now, so the Wi-Fi only setting doesn't hold it back) and waits for it,
     * reporting progress to [onProgress]. Leaving before it finishes doesn't stop it: the download
     * carries on in the background like one started from the Download button.
     *
     * Throws [BookNotOnDeviceException] when there's no network to fetch it with, and an
     * [IOException] when an attempt fails.
     */
    suspend fun downloadForReading(book: BookDetail, onProgress: (Float) -> Unit): Map<Int, String> {
        localFiles(book.id).takeIf { it.isNotEmpty() }?.let { return it }
        if (BookFiles.downloadableFiles(book).isEmpty()) return emptyMap()
        if (connectivity.offline.value) throw BookNotOnDeviceException()

        val work = WorkManager.getInstance(context)
        val tag = DownloadWorker.tag(book.id)
        val running = work.getWorkInfosForUniqueWorkFlow(tag).first().any { it.state == WorkInfo.State.RUNNING }
        if (!running) enqueue(book, forReading = true)

        val outcome = combine(dao.observe(book.id), work.getWorkInfosForUniqueWorkFlow(tag)) { entity, infos ->
            val info = infos.firstOrNull()
            when {
                entity == null -> ReadingDownload.Failed(null)
                entity.status.isCompleteDownloadStatus() -> ReadingDownload.Done
                entity.status == DownloadStatus.FAILED.name -> ReadingDownload.Failed(entity.lastError)
                // Waiting again after a run: that attempt failed, and the worker will retry later on its own.
                info?.state == WorkInfo.State.ENQUEUED && info.runAttemptCount > 0 -> ReadingDownload.Failed(null)
                info?.state == WorkInfo.State.ENQUEUED && !connectivity.deviceOnline.value -> ReadingDownload.Offline
                else -> {
                    onProgress(entity.progress)
                    null
                }
            }
        }.mapNotNull { it }.first()

        return when (outcome) {
            ReadingDownload.Done -> localFiles(book.id)
            ReadingDownload.Offline -> throw BookNotOnDeviceException()
            is ReadingDownload.Failed -> throw IOException(outcome.reason ?: "Couldn't download this book.")
        }
    }

    private sealed interface ReadingDownload {
        data object Done : ReadingDownload
        data object Offline : ReadingDownload
        data class Failed(val reason: String?) : ReadingDownload
    }

    private suspend fun enqueue(book: BookDetail, forReading: Boolean) {
        if (BookFiles.downloadableFiles(book).isEmpty()) return
        dao.upsert(
            DownloadEntity(
                bookId = book.id,
                title = book.title,
                authors = book.authors.joinToString(", ") { it.name },
                narrators = book.audioMetadata?.narrators?.joinToString(", ") { it.name } ?: "",
                isAudiobook = BookFiles.isAudiobook(book),
                format = BookFiles.downloadableFiles(book).firstOrNull()?.format,
                sizeBytes = 0,
                downloadedAt = System.currentTimeMillis(),
                coverLocalPath = null,
                bookJson = json.encodeToString(BookDetail.serializer(), book),
                filesJson = "[]",
                status = DownloadStatus.DOWNLOADING.name,
                progress = 0f,
            ),
        )

        val wifiOnly = !forReading && appSettings.wifiOnlyDownloads.first()
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_BOOK_ID to book.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(DownloadWorker.tag(book.id))
            .apply { if (forReading) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()

        // A reader waiting on it replaces a download still queued for Wi-Fi; the worker resumes partial files.
        val policy = if (forReading) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(DownloadWorker.tag(book.id), policy, request).await()
    }

    /** Restarts a download after a terminal [DownloadStatus.FAILED], using the last-known book detail. */
    suspend fun retryDownload(bookId: Int) {
        cachedBook(bookId)?.let { startDownload(it) }
    }

    suspend fun delete(bookId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(DownloadWorker.tag(bookId))
        val entity = dao.get(bookId)
        dao.delete(bookId)
        entity?.let {
            deleteSafFolderIfAny(it)
            // Per-file fallback in case the folder-name re-derivation misses (e.g. the tree Uri
            // changed since download) - each file still gets removed via its own stored ref.
            decodeFiles(it).forEach { f -> LocalRef.parse(f.localPath).delete(context) }
        }
        // Handles the cover (always internal) plus legacy/fallback-mode files in one sweep.
        DownloadFolders.internalDir(context, session.dataScope, bookId).deleteRecursively()
    }

    private suspend fun deleteSafFolderIfAny(entity: DownloadEntity) {
        val tree = locationStore.treeUri.first() ?: return
        val name = DownloadFileNaming.bookFolderName(entity.title, entity.bookId)
        runCatching { DocumentFile.fromTreeUri(context, tree)?.findFile(name)?.delete() }
    }

    /** Cancels and removes every download, for the Settings screen's "clear all downloads" action. */
    suspend fun deleteAll() {
        dao.observeAll().first().forEach { delete(it.bookId) }
    }
}

/** The book isn't downloaded and there's no connection to download it with. */
class BookNotOnDeviceException : IOException("This book isn't on this device")
