package com.bookorbit.feature.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bookorbit.R
import com.bookorbit.core.network.PublicClient
import com.bookorbit.feature.downloads.DownloadNotifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Downloads one language's dictionary: streams the gzipped file, checks its SHA-256 against the
 * manifest, unpacks it beside the installed ones and swaps it in only once it opens as a database.
 */
@HiltWorker
class DictionaryDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dictionaries: Dictionaries,
    @PublicClient private val client: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    private val lang get() = inputData.getString(KEY_LANG).orEmpty()

    private fun title() = applicationContext.getString(
        R.string.dictionary_notif_title,
        Locale.forLanguageTag(lang).getDisplayLanguage(Locale.getDefault()),
    )

    override suspend fun getForegroundInfo(): ForegroundInfo =
        DownloadNotifications.foregroundInfo(applicationContext, notificationKey(), title(), null)

    override suspend fun doWork(): Result {
        val pack = dictionaries.pack(lang) ?: runCatching { dictionaries.refreshCatalog() }.getOrNull()?.firstOrNull { it.lang == lang }
            ?: return Result.failure()
        runCatching { setForeground(getForegroundInfo()) }
        val part = dictionaries.tempFileFor(lang)
        return try {
            withContext(Dispatchers.IO) { fetch(pack, part) }
            dictionaries.install(pack, part)
            Result.success()
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "dictionary $lang failed (attempt $runAttemptCount)", e)
            part.delete()
            if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
        }
    }

    private suspend fun fetch(pack: DictionaryPack, part: java.io.File) {
        val request = Request.Builder().url(pack.url).build()
        val longClient = client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
        longClient.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val digest = MessageDigest.getInstance("SHA-256")
            val counted = CountingInputStream(res.body.byteStream())
            var lastReported = 0L
            GZIPInputStream(DigestInputStream(counted, digest), BUFFER).use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        if (counted.count - lastReported >= REPORT_BYTES) {
                            lastReported = counted.count
                            val progress = (counted.count.toFloat() / pack.gzipBytes).coerceIn(0f, 0.99f)
                            setProgress(workDataOf(KEY_PROGRESS to progress))
                            runCatching { setForeground(DownloadNotifications.foregroundInfo(applicationContext, notificationKey(), title(), progress)) }
                        }
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!sha.equals(pack.sha256, ignoreCase = true)) throw IOException("Checksum mismatch")
        }
        // A file that doesn't open as a database must never replace a working one.
        SQLiteDatabase.openDatabase(part.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT count(*) FROM entries LIMIT 1", null).use { it.moveToFirst() }
        }
    }

    /** Below every book id (they start at 1), so a dictionary's notification never replaces a book's. */
    private fun notificationKey() = -10_000 - (lang.hashCode() and 0x3ff)

    private class CountingInputStream(private val inner: java.io.InputStream) : java.io.FilterInputStream(inner) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }

    companion object {
        const val TAG = "dictionary"
        const val KEY_LANG = "lang"
        const val KEY_PROGRESS = "progress"
        private const val TAG_PREFIX = "dictionary-"
        private const val BUFFER = 64 * 1024
        private const val REPORT_BYTES = 512 * 1024L
        private const val MAX_ATTEMPTS = 3

        fun tagFor(lang: String) = TAG_PREFIX + lang

        fun langOfTag(tag: String): String? = tag.takeIf { it.startsWith(TAG_PREFIX) }?.removePrefix(TAG_PREFIX)
    }
}
