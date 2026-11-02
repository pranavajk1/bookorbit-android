package com.bookorbit.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.network.Connectivity
import com.bookorbit.ui.BookAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Feeds [com.bookorbit.ui.LocalBookAvailability] for the signed-in app. */
@HiltViewModel
class BookAvailabilityViewModel @Inject constructor(
    downloads: DownloadsRepository,
    connectivity: Connectivity,
) : ViewModel() {
    val availability: StateFlow<BookAvailability> =
        combine(downloads.downloads, connectivity.offline) { list, offline ->
            val onDevice = list.filter { it.status.isCompleteDownloadStatus() }.associate { it.bookId to it.coverLocalPath }
            BookAvailability(onDevice, offline)
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, BookAvailability(offline = connectivity.offline.value))
}
