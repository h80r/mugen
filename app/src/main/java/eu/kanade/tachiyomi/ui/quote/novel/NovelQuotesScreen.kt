package eu.kanade.tachiyomi.ui.quote.novel

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.quote.novel.NovelQuoteDeleteDialog
import eu.kanade.presentation.quote.novel.NovelQuotesScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen

class NovelQuotesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { NovelQuotesScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        NovelQuotesScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            navigateUp = navigator::pop,
            onDialogChange = screenModel::setDialog,
            onClickQuote = { quote ->
                navigator.push(NovelReaderScreen(chapterId = quote.chapterId, seekQuoteText = quote.text))
            },
        )

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is NovelQuotesScreenModel.Dialog.Delete -> {
                NovelQuoteDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { screenModel.deleteQuote(dialog.quote.id) },
                )
            }
            null -> Unit
        }
    }
}
