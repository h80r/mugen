package eu.kanade.tachiyomi.ui.quote.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.quote.novel.interactor.DeleteNovelQuote
import tachiyomi.domain.quote.novel.interactor.GetNovelQuotes
import tachiyomi.domain.quote.novel.model.NovelQuoteWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelQuotesScreenModel(
    private val getNovelQuotes: GetNovelQuotes = Injekt.get(),
    private val deleteNovelQuote: DeleteNovelQuote = Injekt.get(),
) : ScreenModel {

    private val dialogState = MutableStateFlow<Dialog?>(null)

    val state: StateFlow<State> = getNovelQuotes.subscribe()
        .combine(dialogState) { quotes, dialog -> State(quotes = quotes, dialog = dialog) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State())

    fun setDialog(dialog: Dialog?) {
        dialogState.update { dialog }
    }

    fun deleteQuote(id: Long) {
        screenModelScope.launch {
            deleteNovelQuote.await(id)
        }
    }

    @Immutable
    data class State(
        val quotes: List<NovelQuoteWithRelations>? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data class Delete(val quote: NovelQuoteWithRelations) : Dialog
    }
}
