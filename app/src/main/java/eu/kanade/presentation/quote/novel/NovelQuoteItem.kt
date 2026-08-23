package eu.kanade.presentation.quote.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.util.lang.toTimestampString
import tachiyomi.domain.quote.novel.model.NovelQuoteWithRelations
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun NovelQuoteItem(
    quote: NovelQuoteWithRelations,
    onClick: () -> Unit,
    onClickDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quote.text,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            val savedAt = remember(quote.savedAt) { quote.savedAt.toTimestampString() }
            Text(
                text = stringResource(
                    AYMR.strings.quotes_screen_source_line,
                    stringResource(AYMR.strings.quotes_screen_novel_type_label),
                    quote.novelTitle,
                    formatChapterNumber(quote.chapterNumber),
                    savedAt,
                ),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onClickDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
