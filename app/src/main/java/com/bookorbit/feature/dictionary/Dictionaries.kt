package com.bookorbit.feature.dictionary

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bookorbit.core.network.PublicClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A dictionary the app can download: one language, built from Wiktionary (see tools/dictionary). */
@Serializable
data class DictionaryPack(
    val lang: String,
    val version: Int,
    val url: String,
    /** Size on the device once installed. */
    val bytes: Long,
    /** Size of the download. */
    val gzipBytes: Long,
    val sha256: String,
    val entries: Int = 0,
)

@Serializable
internal data class DictionaryManifest(val packs: List<DictionaryPack>)

/** One language's dictionary as the settings and the lookup sheet show it. */
data class DictionaryState(
    val pack: DictionaryPack,
    /** The version on the device; null when it isn't downloaded. */
    val installedVersion: Int?,
    /** Downloading, and how far it has got (0..1); null when not downloading. */
    val progress: Float?,
    /** The last download failed. */
    val failed: Boolean,
) {
    val lang: String get() = pack.lang

    val installed: Boolean get() = installedVersion != null

    /** A newer build than the one on the device is available. */
    val updatable: Boolean get() = installedVersion != null && pack.version > installedVersion
}

/**
 * The reader's offline dictionaries: which can be downloaded (a manifest published with the app's
 * releases), which are on the device, and the open database for a language. The manifest and the
 * dictionaries themselves are the only things fetched; looking a word up never touches the network.
 */
@Singleton
class Dictionaries @Inject constructor(
    @ApplicationContext private val context: Context,
    @PublicClient private val client: OkHttpClient,
    private val json: Json,
) {
    private val dir = File(context.filesDir, "dictionaries").apply { mkdirs() }
    private val manifestFile = File(dir, "manifest.json")
    private val installedFile = File(dir, "installed.json")
    private val installedSerializer = MapSerializer(String.serializer(), Int.serializer())

    private val _catalog = MutableStateFlow(readManifest())
    /** The dictionaries that can be downloaded; null until the manifest has been fetched once. */
    val catalog: StateFlow<List<DictionaryPack>?> = _catalog.asStateFlow()

    private val _installed = MutableStateFlow(readInstalled())
    /** Language -> version of every dictionary on the device. */
    val installed: StateFlow<Map<String, Int>> = _installed.asStateFlow()

    private val open = HashMap<String, DictionarySqlite>()

    /** Every downloadable dictionary with whether it's on the device and how a download is going. */
    val states: Flow<List<DictionaryState>> = combine(
        _catalog,
        _installed,
        WorkManager.getInstance(context).getWorkInfosByTagFlow(DictionaryDownloadWorker.TAG),
    ) { catalog, installed, work ->
        val byLang = work.groupBy { it.tags.firstNotNullOfOrNull(DictionaryDownloadWorker::langOfTag) }
        catalog.orEmpty().map { pack ->
            val info = byLang[pack.lang]?.maxByOrNull { it.runAttemptCount }
            val active = info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED
            DictionaryState(
                pack = pack,
                installedVersion = installed[pack.lang],
                progress = if (active) info?.progress?.getFloat(DictionaryDownloadWorker.KEY_PROGRESS, 0f) ?: 0f else null,
                failed = info?.state == WorkInfo.State.FAILED && pack.lang !in installed,
            )
        }
    }

    fun state(lang: String): Flow<DictionaryState?> = states.map { list -> list.firstOrNull { it.lang == lang } }

    /** Fetches the list of downloadable dictionaries. Throws when it can't be reached. */
    suspend fun refreshCatalog(): List<DictionaryPack> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(MANIFEST_URL).build()
        val body = client.newBuilder().readTimeout(30, TimeUnit.SECONDS).build().newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            res.body.string()
        }
        val packs = json.decodeFromString(DictionaryManifest.serializer(), body).packs
        manifestFile.writeText(body)
        _catalog.value = packs
        packs
    }

    fun pack(lang: String): DictionaryPack? = _catalog.value?.firstOrNull { it.lang == lang }

    /** Starts downloading [lang]'s dictionary; it carries on in the background if the screen is left. */
    fun download(lang: String) {
        val pack = pack(lang) ?: return
        val request = OneTimeWorkRequestBuilder<DictionaryDownloadWorker>()
            .setInputData(workDataOf(DictionaryDownloadWorker.KEY_LANG to pack.lang))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .addTag(DictionaryDownloadWorker.TAG)
            .addTag(DictionaryDownloadWorker.tagFor(pack.lang))
            .build()
        // REPLACE lets a failed or outdated download start over; a running one only restarts its file.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DictionaryDownloadWorker.tagFor(pack.lang), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelDownload(lang: String) {
        WorkManager.getInstance(context).cancelUniqueWork(DictionaryDownloadWorker.tagFor(lang))
    }

    /** Removes [lang]'s dictionary from the device. */
    suspend fun remove(lang: String) = withContext(Dispatchers.IO) {
        synchronized(open) { open.remove(lang)?.close() }
        fileFor(lang).delete()
        _installed.value = _installed.value - lang
        writeInstalled(_installed.value)
    }

    /** The open dictionary for [lang], or null when none is on the device. */
    internal fun open(lang: String): DictionarySqlite? = synchronized(open) {
        open[lang]?.let { return it }
        if (lang !in _installed.value) return null
        val file = fileFor(lang).takeIf { it.isFile } ?: return null
        runCatching { DictionarySqlite.open(file, json) }.getOrNull()?.also { open[lang] = it }
    }

    /** Where [lang]'s dictionary lives once downloaded. */
    internal fun fileFor(lang: String) = File(dir, "$lang.sqlite")

    /** Called by the download worker once [file] is complete and checked: puts it in place. */
    internal fun install(pack: DictionaryPack, file: File) {
        synchronized(open) {
            open.remove(pack.lang)?.close()
            val target = fileFor(pack.lang)
            if (!file.renameTo(target)) {
                file.copyTo(target, overwrite = true)
                file.delete()
            }
        }
        _installed.value = _installed.value + (pack.lang to pack.version)
        writeInstalled(_installed.value)
    }

    internal fun tempFileFor(lang: String) = File(dir, "$lang.sqlite.part")

    private fun readManifest(): List<DictionaryPack>? =
        runCatching { json.decodeFromString(DictionaryManifest.serializer(), manifestFile.readText()).packs }.getOrNull()

    private fun readInstalled(): Map<String, Int> =
        runCatching { json.decodeFromString(installedSerializer, installedFile.readText()) }.getOrDefault(emptyMap())
            .filterKeys { fileFor(it).isFile }

    private fun writeInstalled(map: Map<String, Int>) {
        installedFile.writeText(json.encodeToString(installedSerializer, map))
    }

    companion object {
        /** The dictionaries are published as assets of this release of the app's repository. */
        const val MANIFEST_URL = "https://github.com/pranavajk1/bookorbit-android/releases/download/dictionaries-v1/dictionaries.json"
    }
}
