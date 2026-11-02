package com.bookorbit.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.bookorbit.core.network.ImageUrls

/**
 * Provides [ImageUrls] to the composable tree so cards/details can build cover URLs without
 * threading the dependency through every screen. Set once in the authenticated app root.
 */
val LocalImageUrls = staticCompositionLocalOf<ImageUrls> {
    error("LocalImageUrls not provided")
}

/**
 * Which books are on the device (with the cover saved beside each, when there is one) and whether
 * the server can be reached, for every book card to show.
 */
data class BookAvailability(val downloaded: Map<Int, String?> = emptyMap(), val offline: Boolean = false) {
    fun isDownloaded(bookId: Int) = bookId in downloaded

    /** The downloaded cover, which shows offline whether or not the image cache still has it. */
    fun localCover(bookId: Int): String? = downloaded[bookId]

    /** Offline, only a downloaded book can be opened. */
    fun canOpen(bookId: Int) = !offline || isDownloaded(bookId)
}

val LocalBookAvailability = compositionLocalOf { BookAvailability() }
