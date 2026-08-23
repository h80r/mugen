package eu.kanade.presentation.quote.novel

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.ui.quote.novel.NovelQuotesScreenModel
import tachiyomi.domain.quote.novel.model.NovelQuoteWithRelations
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun NovelQuotesScreen(
    state: NovelQuotesScreenModel.State,
    snackbarHostState: SnackbarHostState,
    navigateUp: () -> Unit,
    onDialogChange: (NovelQuotesScreenModel.Dialog?) -> Unit,
    onClickQuote: (NovelQuoteWithRelations) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(AYMR.strings.aurora_quotes),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        state.quotes.let {
            if (it == null) {
                LoadingScreen(Modifier.padding(contentPadding))
            } else if (it.isEmpty()) {
                EmptyScreen(
                    message = stringResource(AYMR.strings.quotes_screen_empty_message),
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                FastScrollLazyColumn(contentPadding = contentPadding) {
                    items(
                        items = it,
                        key = { quote -> quote.id },
                    ) { quote ->
                        NovelQuoteItem(
                            quote = quote,
                            onClick = { onClickQuote(quote) },
                            onClickDelete = { onDialogChange(NovelQuotesScreenModel.Dialog.Delete(quote)) },
                        )
                    }
                }
            }
        }
    }
}
