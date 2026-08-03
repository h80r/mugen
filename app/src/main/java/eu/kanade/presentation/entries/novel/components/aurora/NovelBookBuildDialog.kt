package eu.kanade.presentation.entries.novel.components.aurora

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.book.novel.NovelBookBuildProgress
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Single dialog for the whole "make a book" flow.
 *
 * Confirming the missing downloads used to close the prompt and open a second, unrelated dialog for
 * the progress bar. Both are the same step for the reader, so they now share one window that simply
 * swaps its body once the build starts.
 *
 * The surface is opaque on purpose: Aurora card colours are translucent glass meant to sit on top of
 * the app background, and reusing them for a floating dialog made the dialog see-through.
 */
@Composable
fun NovelBookBuildDialog(
    progress: NovelBookBuildProgress?,
    missingChapterCount: Int?,
    onDownloadMissing: () -> Unit,
    onBuildPartial: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (progress == null && missingChapterCount == null) return
    val colors = AuroraTheme.colors
    val building = progress != null
    // Composited over the opaque background so the translucent card tint keeps the Aurora look
    // without letting the screen behind it show through.
    val containerColor = colors.cardBackground.compositeOver(colors.background)

    Dialog(
        // A running build must not be dismissed by a stray tap outside the dialog.
        onDismissRequest = { if (!building) onDismissRequest() },
        properties = DialogProperties(
            dismissOnBackPress = !building,
            dismissOnClickOutside = !building,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .border(
                    width = 1.dp,
                    color = colors.divider,
                    shape = RoundedCornerShape(24.dp),
                ),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(
                        if (building) {
                            AYMR.strings.novel_book_building
                        } else {
                            AYMR.strings.novel_book_missing_downloads_title
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (progress != null) {
                    val phaseText = when (progress.phase) {
                        NovelBookBuildProgress.Phase.DOWNLOADING ->
                            stringResource(AYMR.strings.novel_book_downloading_chapters)
                        NovelBookBuildProgress.Phase.MERGING,
                        NovelBookBuildProgress.Phase.PARSING,
                        -> stringResource(AYMR.strings.novel_book_building)
                    }
                    Text(
                        text = "$phaseText  ${progress.done}/${progress.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (progress.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = colors.accent,
                        )
                    } else {
                        // The first seconds have no chapter count yet, so the bar stays indeterminate
                        // instead of sitting at zero and looking stuck.
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = colors.accent,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(progress.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = stringResource(
                            AYMR.strings.novel_book_missing_downloads_body,
                            missingChapterCount ?: 0,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onBuildPartial) {
                            Text(
                                text = stringResource(AYMR.strings.novel_book_missing_downloads_partial),
                                color = colors.textSecondary,
                            )
                        }
                        TextButton(onClick = onDownloadMissing) {
                            Text(
                                text = stringResource(AYMR.strings.novel_book_missing_downloads_download),
                                color = colors.accent,
                            )
                        }
                    }
                }
            }
        }
    }
}
