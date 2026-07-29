package eu.kanade.presentation.reader.novel

import android.graphics.Color
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookDocument
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngine
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineFlow
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSection
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSpine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Isolated whole-book reader surface.
 *
 * Unlike the chapter reader, the scrolled flow keeps several spine sections in one renderer document
 * and stitches the next chapter in before the reader reaches it, so crossing a chapter boundary is a
 * plain scroll. The paged flow still swaps one document per section. The location stays a
 * section/text offset shared by both layouts.
 */
@Composable
internal fun NovelBookReader(
    spine: NovelBookSpine,
    location: NovelBookLocation,
    flow: NovelBookEngineFlow,
    transitionStyleName: String = "SLIDE",
    loadDocument: suspend (NovelBookSection) -> NovelBookDocument,
    onLocationChanged: (NovelBookLocation) -> Unit,
    onSectionMeasured: (chapterId: Long, charCount: Int) -> Unit,
    onToggleReaderUi: () -> Unit,
    readerCss: String = "",
    resolveResource: (String) -> WebResourceResponse? = { null },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val operationMutex = remember { Mutex() }
    val latestSpine = rememberUpdatedState(spine)
    val latestLocation = rememberUpdatedState(location)
    val latestFlow = rememberUpdatedState(flow)
    val latestTransitionStyleName = rememberUpdatedState(transitionStyleName)
    val latestLoadDocument = rememberUpdatedState(loadDocument)
    val latestOnLocationChanged = rememberUpdatedState(onLocationChanged)
    val latestOnSectionMeasured = rememberUpdatedState(onSectionMeasured)
    val latestOnToggleReaderUi = rememberUpdatedState(onToggleReaderUi)
    val latestReaderCss = rememberUpdatedState(readerCss)
    val latestResolveResource = rememberUpdatedState(resolveResource)

    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<Throwable?>(null) }
    var opened by remember { mutableStateOf(false) }
    var appliedFlow by remember { mutableStateOf<NovelBookEngineFlow?>(null) }
    var appliedReaderCss by remember { mutableStateOf<String?>(null) }
    val latestOpened = rememberUpdatedState(opened)

    val webView = remember(context) {
        WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
        }
    }
    val engineHolder = remember { arrayOfNulls<NovelBookEngine>(1) }
    // The renderer is created before the stitching lambda exists, so boundary reports from the
    // document are routed through this holder instead of duplicating the logic.
    val stitchHolder = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val renderer = remember(webView) {
        NovelBookWebViewRenderer(
            webView = webView,
            onRelocated = { charOffset, sectionIndex ->
                engineHolder[0]?.onRendererRelocated(charOffset, sectionIndex)
            },
            onDocumentMeasured = { sectionIndex, chapterId, charCount ->
                engineHolder[0]?.onRendererMeasured(sectionIndex, chapterId, charCount)
            },
            onScrollBoundary = { forward ->
                stitchHolder[0]?.invoke(forward)
            },
            readerCss = { latestReaderCss.value },
            resolveResource = { requestUrl -> latestResolveResource.value(requestUrl) },
        )
    }
    val engine = remember(renderer) {
        NovelBookEngine(
            loadDocument = { section -> latestLoadDocument.value(section) },
            renderer = renderer,
            onLocationChanged = { nextLocation ->
                latestOnLocationChanged.value(nextLocation)
            },
            onSectionMeasured = { chapterId, charCount ->
                latestOnSectionMeasured.value(chapterId, charCount)
            },
        )
    }
    SideEffect {
        engineHolder[0] = engine
    }
    DisposableEffect(lifecycleOwner, engine, operationMutex) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && latestOpened.value) {
                coroutineScope.launch {
                    runCatching {
                        operationMutex.withLock {
                            engine.flushLocation()
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val spineIdentity = remember(spine.sections) { spine.sections.map { it.chapterId } }
    LaunchedEffect(spineIdentity) {
        if (spine.isEmpty) return@LaunchedEffect
        loading = true
        failure = null
        val openingFlow = latestFlow.value
        val openingReaderCss = latestReaderCss.value
        val result = runCatching {
            operationMutex.withLock {
                engine.open(
                    spine = spine,
                    location = location,
                    flow = openingFlow,
                )
            }
        }
        result.onSuccess {
            opened = true
            appliedFlow = openingFlow
            appliedReaderCss = openingReaderCss
        }
        result.onFailure { failure = it }
        loading = false
    }
    LaunchedEffect(flow, readerCss, opened) {
        if (spine.isEmpty || !opened) return@LaunchedEffect
        val flowChanged = appliedFlow != flow
        val styleChanged = appliedReaderCss != readerCss
        if (!flowChanged && !styleChanged) return@LaunchedEffect
        loading = true
        failure = null
        runCatching {
            operationMutex.withLock {
                engine.flushLocation()
                if (flowChanged) {
                    engine.setFlow(flow)
                } else {
                    engine.reload()
                }
            }
        }.onSuccess {
            appliedFlow = flow
            appliedReaderCss = readerCss
        }.onFailure { failure = it }
        loading = false
    }
    LaunchedEffect(location.sectionIndex, opened) {
        if (!opened || engine.location.sectionIndex == location.sectionIndex) return@LaunchedEffect
        loading = true
        failure = null
        runCatching {
            operationMutex.withLock {
                engine.open(
                    spine = latestSpine.value,
                    location = location,
                    flow = latestFlow.value,
                )
            }
        }.onSuccess {
            appliedFlow = latestFlow.value
            appliedReaderCss = latestReaderCss.value
        }.onFailure { failure = it }
        loading = false
    }

    val navigate: (Boolean) -> Unit = remember(engine, coroutineScope, operationMutex) {
        { forward ->
            coroutineScope.launch {
                failure = null
                runCatching {
                    operationMutex.withLock {
                        val style = latestTransitionStyleName.value
                        if (forward) engine.next(style) else engine.previous(style)
                    }
                }.onFailure { failure = it }
            }
        }
    }
    // A boundary report asks for the neighbouring chapter to be stitched into the live document, so
    // the reader scrolls into it instead of watching the document be replaced. A failed stitch is
    // deliberately silent: the chapter on screen stays perfectly readable.
    val stitch: (Boolean) -> Unit = remember(engine, coroutineScope, operationMutex) {
        { forward ->
            coroutineScope.launch {
                runCatching {
                    operationMutex.withLock {
                        engine.stitch(forward)
                    }
                }
            }
        }
    }
    DisposableEffect(webView, navigate, stitch) {
        stitchHolder[0] = stitch
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var downX = 0f
        var downY = 0f
        var downAt = 0L
        webView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downAt = event.eventTime
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - downX
                    val deltaY = event.y - downY
                    val isHorizontalSwipe = latestFlow.value == NovelBookEngineFlow.PAGINATED &&
                        abs(deltaX) >= touchSlop * 4f &&
                        abs(deltaX) > abs(deltaY) * 1.25f
                    when {
                        isHorizontalSwipe -> navigate(deltaX < 0f)
                        event.eventTime - downAt <= TAP_TIMEOUT_MILLIS &&
                            abs(deltaX) <= touchSlop &&
                            abs(deltaY) <= touchSlop -> {
                            when {
                                event.x < view.width * PREVIOUS_TAP_ZONE_FRACTION -> navigate(false)
                                event.x > view.width * NEXT_TAP_ZONE_FRACTION -> navigate(true)
                                else -> latestOnToggleReaderUi.value()
                            }
                        }
                    }
                }
            }
            false
        }
        onDispose {
            webView.setOnTouchListener(null)
            renderer.dispose()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view ->
                view.clearFocus()
            },
        )
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (failure != null) {
            Surface(modifier = Modifier.align(Alignment.Center)) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            loading = true
                            failure = null
                            runCatching {
                                operationMutex.withLock {
                                    engine.open(
                                        spine = latestSpine.value,
                                        location = latestLocation.value,
                                        flow = latestFlow.value,
                                    )
                                }
                            }.onSuccess {
                                opened = true
                                appliedFlow = latestFlow.value
                                appliedReaderCss = latestReaderCss.value
                            }
                                .onFailure { failure = it }
                            loading = false
                        }
                    },
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                }
            }
        }
    }
}

private const val TAP_TIMEOUT_MILLIS = 300L
private const val PREVIOUS_TAP_ZONE_FRACTION = 0.3f
private const val NEXT_TAP_ZONE_FRACTION = 0.7f
