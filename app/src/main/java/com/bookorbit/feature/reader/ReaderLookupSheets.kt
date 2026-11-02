package com.bookorbit.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import com.bookorbit.R
import com.bookorbit.ui.asString

/**
 * The selected word's definitions from the dictionary on the device, grouped by the word they came
 * from when the lookup followed it to another ("assiduously" to "assiduous"). Without a dictionary
 * for the book's language, it offers to download one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefineSheet(
    state: ReaderViewModel.DefineState,
    onDownloadDictionary: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            val result = state.result
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    result?.word ?: state.word,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                result?.phonetic?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            val missing = state.missing
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                missing != null -> MissingDictionaryOffer(missing, onDownloadDictionary, onRetry)
                result == null -> Text(
                    stringResource(R.string.dictionary_no_definition),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    result.entries.groupBy { it.sourceWord }.entries.forEachIndexed { i, (word, entries) ->
                        if (i > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(word, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.size(4.dp))
                        }
                        entries.forEach { entry -> DictionarySenses(entry) }
                    }
                    Text(
                        stringResource(R.string.dictionary_credit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

/** No dictionary for the book's language yet: what it is, its size, and a button to get it. */
@Composable
private fun MissingDictionaryOffer(missing: ReaderViewModel.MissingDictionary, onDownload: () -> Unit, onRetry: () -> Unit) {
    val language = languageName(missing.lang)
    val dictionary = missing.state
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            missing.listUnavailable -> {
                Text(stringResource(R.string.dictionary_offline_needed, language), style = MaterialTheme.typography.bodyMedium, color = muted)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            !missing.listLoaded -> CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            dictionary == null -> Text(stringResource(R.string.dictionary_unavailable_for, language), style = MaterialTheme.typography.bodyMedium, color = muted)
            dictionary.progress != null -> {
                Text(stringResource(R.string.dictionary_downloading, language), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { dictionary.progress }, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                Text(stringResource(R.string.dictionary_download_offer, language), style = MaterialTheme.typography.bodyMedium)
                if (dictionary.failed) {
                    Text(stringResource(R.string.dictionary_download_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onDownload) {
                    Text(stringResource(R.string.dictionary_download_button, formatSize(dictionary.pack.gzipBytes)))
                }
            }
        }
    }
}

/** A language code as the reader's own language names it ("fr" -> "French"). */
internal fun languageName(lang: String): String =
    Locale.forLanguageTag(lang).getDisplayLanguage(Locale.getDefault()).ifBlank { lang }
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
private fun formatSize(bytes: Long): String = android.text.format.Formatter.formatShortFileSize(LocalContext.current, bytes)

@Composable
private fun DictionarySenses(entry: DictionaryEntry) {
    if (entry.partOfSpeech.isNotBlank()) {
        Text(
            partOfSpeechLabel(entry.partOfSpeech).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
    }
    entry.definitions.forEachIndexed { i, def ->
        Row(modifier = Modifier.padding(vertical = 3.dp)) {
            Text(
                "${i + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            Column {
                Text(def.definition, style = MaterialTheme.typography.bodyMedium)
                def.example?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The dictionary's short part-of-speech codes ("adj", "adv") as words. */
@Composable
private fun partOfSpeechLabel(code: String): String = when (code) {
    "noun" -> stringResource(R.string.pos_noun)
    "verb" -> stringResource(R.string.pos_verb)
    "adj" -> stringResource(R.string.pos_adjective)
    "adv" -> stringResource(R.string.pos_adverb)
    "pron" -> stringResource(R.string.pos_pronoun)
    "prep" -> stringResource(R.string.pos_preposition)
    "conj" -> stringResource(R.string.pos_conjunction)
    "intj" -> stringResource(R.string.pos_interjection)
    "det" -> stringResource(R.string.pos_determiner)
    "article" -> stringResource(R.string.pos_article)
    "num" -> stringResource(R.string.pos_numeral)
    "particle" -> stringResource(R.string.pos_particle)
    "contraction" -> stringResource(R.string.pos_contraction)
    "abbrev" -> stringResource(R.string.pos_abbreviation)
    else -> code.replace('_', ' ')
}

/** The selection translated into the chosen language, with the language picker and a copy button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationSheet(
    state: ReaderViewModel.TranslationState,
    onTarget: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.translate_into),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReaderTranslator.LANGUAGES.forEach { code ->
                    FilterChip(
                        selected = code == state.target,
                        onClick = { if (code != state.target) onTarget(code) },
                        label = { Text(ReaderTranslator.displayName(code)) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    state.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                val result = state.result
                when {
                    state.loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                    state.error != null -> LookupError(state.error.asString(), onRetry)
                    result != null -> {
                        Text(result.translatedText, style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                stringResource(R.string.translate_provider, result.provider),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onCopy(result.translatedText) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy_text))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LookupError(message: String, onRetry: () -> Unit) {
    Column {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/** Floating control while the book is read aloud: pause or resume, and stop. */
@Composable
fun ReadAloudBar(
    paused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 6.dp,
        color = Color(0xFF1E1E1E),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Filled.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(if (paused) R.string.read_aloud_paused else R.string.read_aloud_playing),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            )
            Box {
                if (paused) {
                    IconButton(onClick = onResume) { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.resume_reading)) }
                } else {
                    IconButton(onClick = onPause) { Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.pause_reading)) }
                }
            }
            IconButton(onClick = onStop) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.stop_reading)) }
        }
    }
}
