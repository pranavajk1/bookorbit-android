package com.bookorbit.core.network

import android.content.Context
import com.bookorbit.core.auth.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the last answer to every read the app makes (dashboard shelves, library pages, book details,
 * series, authors...) and hands it back when the server can't be reached, so the app shows what it
 * last saw instead of an error, the way the Kindle app keeps its library offline. Book files, covers
 * and audio are never stored here: books are kept by downloads, covers by the image cache.
 *
 * Answers are kept per server and per user, and only JSON bodies up to [MAX_BODY_BYTES] are stored.
 */
@Singleton
class OfflineCacheInterceptor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val connectivity: Connectivity,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cacheable = OfflineCachePolicy.isCacheable(request.method, request.url.encodedPath)
        if (!cacheable) return proceed(chain, request)

        val file = cacheFile(request)
        // No network at all: don't wait for the request to fail.
        if (!connectivity.deviceOnline.value) {
            file?.let(::cached)?.let { return it.toResponse(request) }
        }
        val response = try {
            proceed(chain, request)
        } catch (e: IOException) {
            file?.let(::cached)?.let { return it.toResponse(request) }
            throw e
        }
        // A proxy in front of a stopped server answers 502/503/504: as good as unreachable.
        if (response.code in GATEWAY_ERRORS) {
            file?.let(::cached)?.let {
                response.close()
                return it.toResponse(request)
            }
            return response
        }
        if (response.isSuccessful && file != null && response.body.contentType()?.subtype == "json") {
            runCatching {
                val body = response.peekBody(MAX_BODY_BYTES + 1)
                if (body.contentLength() <= MAX_BODY_BYTES) {
                    file.parentFile?.mkdirs()
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeBytes(body.bytes())
                    tmp.renameTo(file)
                }
            }
        }
        return response
    }

    private fun proceed(chain: Interceptor.Chain, request: Request): Response {
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            connectivity.onServerUnreachable()
            throw e
        }
        if (response.code in GATEWAY_ERRORS) connectivity.onServerUnreachable() else connectivity.onServerAnswered()
        return response
    }

    /** The stored answer for [request], or null when there's no signed-in user to keep it for. */
    private fun cacheFile(request: Request): File? {
        val user = session.currentUser?.id ?: return null
        val key = buildString {
            append(request.method).append(' ').append(request.url.toString())
            request.body?.let { body ->
                val buffer = Buffer()
                runCatching { body.writeTo(buffer) }
                append('\n').append(buffer.readUtf8())
            }
        }
        val scope = session.dataScope.ifEmpty { "default" }
        return File(rootDir(context), "$scope/$user/${sha256(key)}.json")
    }

    private fun cached(file: File): ByteArray? = runCatching { file.takeIf { it.isFile }?.readBytes() }.getOrNull()

    private fun ByteArray.toResponse(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .header("Content-Type", "application/json")
        .header(OFFLINE_HEADER, "1")
        .body(toResponseBody(JSON))
        .build()

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val OFFLINE_HEADER = "X-BookOrbit-Offline"
        private const val MAX_BODY_BYTES = 4L * 1024 * 1024
        private val GATEWAY_ERRORS = setOf(502, 503, 504)
        private val JSON = "application/json".toMediaType()

        fun rootDir(context: Context) = File(context.filesDir, "offline-cache")

        /** Forgets every stored answer, for signing out. */
        fun clear(context: Context) {
            rootDir(context).deleteRecursively()
        }
    }
}

/** Which requests are reads worth keeping for offline use. */
internal object OfflineCachePolicy {
    /** POST endpoints that only read (the library and query listings take their filters as a body). */
    private val READ_POSTS = listOf(Regex("/libraries/\\d+/books$"), Regex("/books/query$"))

    /**
     * Binary or per-session endpoints (files, covers, audio, fonts, sign-in), and the ones the app
     * already keeps offline itself: reading positions, bookmarks, highlights and reader settings.
     * A stale copy of those would win over the newer one on the device.
     */
    private val NEVER = listOf(
        Regex("/serve$"), Regex("/cover$"), Regex("/content$"), Regex("/file$"),
        Regex("/auth/"), Regex("/cbz/files/\\d+/pages/\\d+"),
        Regex("/progress$"), Regex("/audio-progress$"), Regex("/playback-state$"),
        Regex("/bookmarks"), Regex("/annotations"), Regex("/reader/"),
    )

    fun isCacheable(method: String, path: String): Boolean {
        if (NEVER.any { it.containsMatchIn(path) }) return false
        return when (method) {
            "GET" -> true
            "POST" -> READ_POSTS.any { it.containsMatchIn(path) }
            else -> false
        }
    }
}
