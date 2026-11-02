package com.bookorbit.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bookorbit.R
import kotlin.math.roundToInt

/**
 * What a reader shows while its book gets ready: a spinner, or, while the book is being downloaded
 * to the device, how far that has got.
 */
@Composable
internal fun ReaderLoading(downloadProgress: Float?, tint: Color, modifier: Modifier = Modifier) {
    if (downloadProgress == null) {
        CircularProgressIndicator(modifier)
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.reader_downloading_book, (downloadProgress * 100).roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.width(200.dp))
    }
}
