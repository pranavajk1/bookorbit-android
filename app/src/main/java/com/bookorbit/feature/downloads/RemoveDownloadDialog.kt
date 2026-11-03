package com.bookorbit.feature.downloads

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bookorbit.R
import com.bookorbit.ui.theme.BookOrbitTheme

/**
 * Asks before a download is removed. Without a connection a removed book can't be fetched again,
 * so a stray tap on the book page's "Downloaded" button or the Downloads list mustn't delete it.
 */
@Composable
fun RemoveDownloadDialog(title: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remove_download_title)) },
        text = {
            Text(
                if (title.isNullOrBlank()) stringResource(R.string.remove_download_body_untitled)
                else stringResource(R.string.remove_download_body, title),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = BookOrbitTheme.tokens.destructive),
            ) { Text(stringResource(R.string.remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
