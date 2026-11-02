package com.bookorbit.feature.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bookorbit.R
import com.bookorbit.feature.reader.languageName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DictionariesViewModel @Inject constructor(
    private val dictionaries: Dictionaries,
) : ViewModel() {
    val states: StateFlow<List<DictionaryState>> = dictionaries.states
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _listUnavailable = MutableStateFlow(false)
    /** The list of dictionaries couldn't be fetched and none was kept from before. */
    val listUnavailable = _listUnavailable.asStateFlow()

    /** Fetches the current list, keeping the one from before when it can't. */
    fun refresh() {
        viewModelScope.launch {
            val ok = runCatching { dictionaries.refreshCatalog() }.isSuccess
            _listUnavailable.value = !ok && dictionaries.catalog.value == null
        }
    }

    fun download(lang: String) = dictionaries.download(lang)

    fun cancel(lang: String) = dictionaries.cancelDownload(lang)

    fun remove(lang: String) {
        viewModelScope.launch { dictionaries.remove(lang) }
    }
}

/**
 * Every language's dictionary with its size, to download or remove. The ones on the device come
 * first; the reader looks words up in the one for each book's language.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionariesSheet(onDismiss: () -> Unit, vm: DictionariesViewModel = hiltViewModel()) {
    val states by vm.states.collectAsStateWithLifecycle()
    val listUnavailable by vm.listUnavailable.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.dictionaries), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.dictionaries_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            when {
                states.isEmpty() && listUnavailable -> Text(
                    stringResource(R.string.dictionaries_list_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                states.isEmpty() -> CircularProgressIndicator(Modifier.padding(24.dp).size(28.dp).align(Alignment.CenterHorizontally))
                else -> LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    val sorted = states.sortedWith(compareByDescending<DictionaryState> { it.installed }.thenBy { languageName(it.lang) })
                    items(sorted, key = { it.lang }) { state ->
                        DictionaryRow(state, onDownload = { vm.download(state.lang) }, onCancel = { vm.cancel(state.lang) }, onRemove = { vm.remove(state.lang) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryRow(state: DictionaryState, onDownload: () -> Unit, onCancel: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val size = android.text.format.Formatter.formatShortFileSize(context, if (state.installed) state.pack.bytes else state.pack.gzipBytes)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(languageName(state.lang), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        when {
                            state.progress != null -> R.string.dictionary_state_downloading
                            state.installed -> R.string.dictionary_state_on_device
                            state.failed -> R.string.dictionary_state_failed
                            else -> R.string.dictionary_state_download_size
                        },
                        size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.failed && state.progress == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                state.progress != null -> TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                state.updatable -> TextButton(onClick = onDownload) { Text(stringResource(R.string.dictionary_update)) }
                state.installed -> TextButton(onClick = onRemove) { Text(stringResource(R.string.remove)) }
                else -> TextButton(onClick = onDownload) { Text(stringResource(R.string.download)) }
            }
        }
        state.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
    }
}
