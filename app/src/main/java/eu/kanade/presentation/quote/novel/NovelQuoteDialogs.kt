package eu.kanade.presentation.quote.novel

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun NovelQuoteDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(AYMR.strings.quotes_screen_delete_confirm_title)) },
        text = { Text(text = stringResource(AYMR.strings.quotes_screen_delete_confirm_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
