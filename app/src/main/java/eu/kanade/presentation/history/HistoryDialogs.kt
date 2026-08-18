package eu.kanade.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.random.Random

@Composable
fun HistoryDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: (Boolean) -> Unit,
    isManga: Boolean,
) {
    var removeEverything by remember { mutableStateOf(false) }

    val colors = AuroraTheme.colors
    val confirmButtonColors = ButtonDefaults.textButtonColors(contentColor = colors.accent)
    val dismissButtonColors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)
    val dialogShape = RoundedCornerShape(24.dp)
    val descriptionColor = colors.textSecondary
    val containerColor = colors.surface
    val titleContentColor = colors.textPrimary
    val textContentColor = colors.textPrimary

    AlertDialog(
        title = {
            Text(text = stringResource(MR.strings.action_remove))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                val subtitle = if (isManga) {
                    MR.strings.dialog_with_checkbox_remove_description
                } else {
                    AYMR.strings.dialog_with_checkbox_remove_description_anime
                }
                Text(
                    text = stringResource(subtitle),
                    color = descriptionColor,
                )

                LabeledCheckbox(
                    label = if (isManga) {
                        stringResource(AYMR.strings.dialog_with_checkbox_reset)
                    } else {
                        stringResource(AYMR.strings.dialog_with_checkbox_reset_anime)
                    },
                    checked = removeEverything,
                    onCheckedChange = { removeEverything = it },
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete(removeEverything)
                    onDismissRequest()
                },
                colors = confirmButtonColors,
            ) {
                Text(text = stringResource(MR.strings.action_remove))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                colors = dismissButtonColors,
            ) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        containerColor = containerColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        shape = dialogShape,
    )
}

@Composable
fun HistoryDeleteAllDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val confirmButtonColors = ButtonDefaults.textButtonColors(contentColor = colors.accent)
    val dismissButtonColors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)
    val dialogShape = RoundedCornerShape(24.dp)
    val descriptionColor = colors.textSecondary
    val containerColor = colors.surface
    val titleContentColor = colors.textPrimary
    val textContentColor = colors.textPrimary

    AlertDialog(
        title = {
            Text(text = stringResource(MR.strings.action_remove_everything))
        },
        text = {
            Text(
                text = stringResource(MR.strings.clear_history_confirmation),
                color = descriptionColor,
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete()
                    onDismissRequest()
                },
                colors = confirmButtonColors,
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                colors = dismissButtonColors,
            ) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        containerColor = containerColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        shape = dialogShape,
    )
}

@PreviewLightDark
@Composable
private fun HistoryDeleteDialogPreview() {
    TachiyomiPreviewTheme {
        HistoryDeleteDialog(
            onDismissRequest = {},
            onDelete = {},
            isManga = Random.nextBoolean(),
        )
    }
}
