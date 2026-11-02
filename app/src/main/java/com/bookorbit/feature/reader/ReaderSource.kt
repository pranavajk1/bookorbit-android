package com.bookorbit.feature.reader

import android.content.Context
import com.bookorbit.core.model.BookDetail
import com.bookorbit.core.model.BookFileRef
import com.bookorbit.core.model.BookFiles
import com.bookorbit.core.network.ApiService
import com.bookorbit.core.network.Connectivity
import com.bookorbit.core.settings.AppSettingsStore
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.exists
import com.bookorbit.feature.downloads.BookNotOnDeviceException
import com.bookorbit.feature.downloads.DownloadsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a book to a readable local file for the WebView: the host owns all networking; the
 * WebView only ever sees local bytes. A downloaded book opens from the device. Otherwise opening it
 * downloads it (the Kindle way, see [AppSettingsStore.downloadOnOpen]) so it reads offline from then
 * on; with that setting off it is fetched once into a reader cache reused on reopen.
 */
@Singleton
class ReaderSource @Inject constructor(
    private val api: ApiService,
    private val downloads: DownloadsRepository,
    private val appSettings: AppSettingsStore,
    private val connectivity: Connectivity,
    @ApplicationContext private val context: Context,
) {
    data class ResolvedFile(val ref: LocalRef, val format: String, val fileId: Int)

    /** Resolve the foliate-readable file for a book to a local file. */
    suspend fun resolve(book: BookDetail, onDownloadProgress: (Float) -> Unit = {}): ResolvedFile {
        val ref = BookFiles.readableFile(book) ?: throw IllegalStateException("This book has no readable file.")
        return resolveFile(book, ref, onDownloadProgress)
    }

    /** Resolve the PDF file for a book to a local file (native PDF reader). */
    suspend fun resolvePdf(book: BookDetail, onDownloadProgress: (Float) -> Unit = {}): ResolvedFile {
        val ref = BookFiles.pdfFile(book) ?: throw IllegalStateException("This book has no PDF file.")
        return resolveFile(book, ref, onDownloadProgress)
    }

    /**
     * Resolve a specific book file to a local file: a download wins; then, online, the book is
     * downloaded (reporting [onDownloadProgress]) or fetched into the reader cache; offline, a cached
     * copy from before is used, else [BookNotOnDeviceException].
     */
    suspend fun resolveFile(book: BookDetail, ref: BookFileRef, onDownloadProgress: (Float) -> Unit = {}): ResolvedFile = withContext(Dispatchers.IO) {
        val format = ref.format?.lowercase().orEmpty()
        val ext = format.filter { it.isLetterOrDigit() }.ifEmpty { "bin" }

        downloadedCopy(book, ref, format)?.let { return@withContext it }

        val cacheDir = File(context.cacheDir, "reader-cache").apply { mkdirs() }
        val dest = File(cacheDir, "${book.id}-${ref.id}.$ext")
        val size = ref.sizeBytes
        val cached = dest.exists() && (size == null || size <= 0 || dest.length() == size)

        if (connectivity.offline.value) {
            if (cached) return@withContext ResolvedFile(LocalRef.PlainFile(dest), format, ref.id)
            throw BookNotOnDeviceException()
        }

        if (appSettings.downloadOnOpen.first()) {
            val files = downloads.downloadForReading(book, onDownloadProgress)
            files[ref.id]?.let { path ->
                val local = LocalRef.parse(path)
                if (local.exists(context)) {
                    dest.delete() // superseded by the download
                    return@withContext ResolvedFile(local, format, ref.id)
                }
            }
        }

        // Reuse a cached copy when it matches the server-reported size.
        if (cached) return@withContext ResolvedFile(LocalRef.PlainFile(dest), format, ref.id)

        api.serveFile(ref.id).byteStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        ResolvedFile(LocalRef.PlainFile(dest), format, ref.id)
    }

    private suspend fun downloadedCopy(book: BookDetail, ref: BookFileRef, format: String): ResolvedFile? {
        val path = downloads.localFiles(book.id)[ref.id] ?: return null
        val local = LocalRef.parse(path)
        return if (local.exists(context)) ResolvedFile(local, format, ref.id) else null
    }
}
