package eu.kanade.presentation.category.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraControlContainerColor
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReorderableCollectionItemScope.CategoryListItem(
    category: Category,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onToggleHomeHub: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val textColor = if (category.hidden) colors.textSecondary else colors.textPrimary
    val actionColors = IconButtonDefaults.iconButtonColors(contentColor = colors.textSecondary)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = resolveAuroraControlContainerColor(colors),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                category.hidden -> colors.warning.copy(alpha = 0.35f)
                category.hiddenFromHomeHub -> colors.textSecondary.copy(alpha = 0.3f)
                else -> colors.accent.copy(alpha = 0.2f)
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRename)
                .padding(vertical = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .padding(
                        start = MaterialTheme.padding.medium,
                        end = MaterialTheme.padding.medium,
                    )
                    .draggableHandle(),
            )
            Text(
                text = category.name,
                modifier = Modifier.weight(1f),
                color = textColor,
            )
            IconButton(
                onClick = onRename,
                colors = actionColors,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(MR.strings.action_rename_category),
                    tint = colors.accent,
                )
            }
            IconButton(
                onClick = onHide,
                colors = actionColors,
                content = {
                    Icon(
                        imageVector = if (category.hidden) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(AYMR.strings.action_hide),
                        tint = if (!category.hidden) colors.accent else Color.Unspecified,
                    )
                },
            )
            IconButton(
                onClick = onToggleHomeHub,
                colors = actionColors,
                content = {
                    Icon(
                        imageVector = Icons.Outlined.House,
                        contentDescription = stringResource(AYMR.strings.action_hide_from_home_hub),
                        tint = if (category.hiddenFromHomeHub) {
                            colors.textSecondary.copy(alpha = 0.4f)
                        } else {
                            colors.accent
                        },
                    )
                },
            )
            IconButton(
                onClick = onDelete,
                colors = actionColors,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                    tint = colors.error,
                )
            }
        }
    }
}
