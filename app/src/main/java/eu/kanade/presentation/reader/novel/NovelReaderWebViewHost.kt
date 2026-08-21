package eu.kanade.presentation.reader.novel

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.SelectedTextAction
import tachiyomi.i18n.aniyomi.AYMR

class NovelReaderWebView(context: Context) : WebView(context) {
    var localSelection: NovelSelectedTextSelection? = null
    var onSelectedTextSelectionChanged: ((NovelSelectedTextSelection?) -> Unit)? = null
    var isExecutingAction = false
    var isDictionaryEnabled = false
    var isTranslationEnabled = false

    init {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        // Returning null suppresses Android's floating contextual toolbar while leaving the DOM
        // range and WebView's native selection handles intact for the Aurora console.
        return null
    }
}

fun createNovelReaderWebView(context: Context): WebView {
    return NovelReaderWebView(context)
}
