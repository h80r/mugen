package eu.kanade.presentation.reader.novel

/**
 * DOM helpers for the continuous ("book mode") novel reader.
 *
 * Book mode keeps a single WebView document alive and streams chapter sections into it, so these
 * helpers only build HTML/JS strings: no Android types, no WebView access and nothing that can
 * affect the existing chapter-by-chapter reader.
 */

internal const val BOOK_SECTION_CLASS = "an-book-section"
internal const val BOOK_SECTION_BODY_CLASS = "an-book-section-body"
internal const val BOOK_SECTION_TITLE_CLASS = "an-book-section-title"
internal const val BOOK_SECTION_DIVIDER_CLASS = "an-book-divider"
internal const val BOOK_SECTION_PLACEHOLDER_CLASS = "an-book-section-placeholder"
internal const val BOOK_SECTION_ID_PREFIX = "__an_book_section_"

/** Set on the document element while the book is laid out as pages instead of a scroll. */
internal const val BOOK_PAGINATED_CLASS = "an-book-paginated"

/**
 * JavaScript expression resolving the element that actually scrolls the book document.
 *
 * In the scrolled flow that is `document.scrollingElement` (the `html` element). In the paginated
 * flow `html` is `overflow: hidden` and the horizontal column scroll lives on `body`, so every
 * `scrollLeft` write against `scrollingElement` was dropped silently, which is why paging never
 * moved the book.
 */
internal const val BOOK_SCROLLER_JS =
    "(function(){var r=document.documentElement;" +
        "var p=r?r.classList.contains('$BOOK_PAGINATED_CLASS'):false;" +
        "if(p&&document.body)return document.body;" +
        "return document.scrollingElement||r;})()"

/** JavaScript expression that is `true` while the document is laid out as pages. */
internal const val BOOK_PAGINATED_JS =
    "(!!document.documentElement&&" +
        "document.documentElement.classList.contains('$BOOK_PAGINATED_CLASS'))"

internal fun bookSectionElementId(sectionIndex: Int): String = "$BOOK_SECTION_ID_PREFIX$sectionIndex"

/**
 * Wraps one chapter's reader-ready HTML into a book section element.
 *
 * The section carries its index and chapter id as data attributes so the reader can map a scroll
 * position back to a chapter without reloading anything.
 */
internal fun buildBookSectionHtml(
    sectionIndex: Int,
    chapterId: Long,
    title: String?,
    bodyHtml: String,
    showDivider: Boolean = sectionIndex > 0,
    showHeading: Boolean = true,
): String {
    return buildString {
        append("<section id=\"")
        append(bookSectionElementId(sectionIndex))
        append("\" class=\"")
        append(BOOK_SECTION_CLASS)
        append("\" data-an-section=\"")
        append(sectionIndex)
        append("\" data-an-chapter=\"")
        append(chapterId)
        append("\">")
        if (showDivider) {
            append("<div class=\"")
            append(BOOK_SECTION_DIVIDER_CLASS)
            append("\" aria-hidden=\"true\"></div>")
        }
        if (showHeading && !title.isNullOrBlank()) {
            append("<h2 class=\"")
            append(BOOK_SECTION_TITLE_CLASS)
            append("\">")
            append(escapeBookHtmlText(title))
            append("</h2>")
        }
        append("<div class=\"")
        append(BOOK_SECTION_BODY_CLASS)
        append("\">")
        append(bodyHtml)
        append("</div></section>")
    }
}

/** Extra CSS for section dividers, inline chapter headings and pruned placeholders. */
internal fun buildBookSectionsCss(): String {
    return buildString {
        append("section.$BOOK_SECTION_CLASS {\n")
        append("  display: block !important;\n")
        append("}\n")
        append("div.$BOOK_SECTION_DIVIDER_CLASS {\n")
        append("  width: 62% !important;\n")
        append("  height: 1px !important;\n")
        append("  border: 0 !important;\n")
        append("  opacity: 0.28 !important;\n")
        append("  margin: 2.2em auto 1.6em auto !important;\n")
        append("  background: linear-gradient(90deg, transparent, currentColor, transparent) !important;\n")
        append("}\n")
        append("h2.$BOOK_SECTION_TITLE_CLASS {\n")
        append("  font-size: 1.12em !important;\n")
        append("  font-weight: 600 !important;\n")
        append("  text-indent: 0 !important;\n")
        append("  margin: 0 0 0.85em 0 !important;\n")
        append("}\n")
        append("section.$BOOK_SECTION_PLACEHOLDER_CLASS {\n")
        append("  overflow: hidden !important;\n")
        append("}\n")
        // Sections that were never rendered stay in the document as thin placeholders: that keeps
        // the whole book reachable (scrolling back into an unread section is what rehydrates it)
        // without inventing pixel heights. In the paginated flow they collapse instead, otherwise
        // every unread section would spill into blank pages.
        append("section[data-an-estimated=\"1\"] {\n")
        append("  padding: 0 !important;\n")
        append("  margin: 0 !important;\n")
        append("}\n")
        append("html.$BOOK_PAGINATED_CLASS section[data-an-estimated=\"1\"] {\n")
        append("  height: 0 !important;\n")
        append("  min-height: 0 !important;\n")
        append("}\n")
        // Pruned sections keep their measured pixel height so the scrolled flow stays stable, but
        // in the paginated flow that height becomes a run of blank pages, so collapse it there too.
        append("html.$BOOK_PAGINATED_CLASS section[data-an-placeholder=\"1\"] {\n")
        append("  height: 0 !important;\n")
        append("  min-height: 0 !important;\n")
        append("}\n")
        // Paginated flow: the book becomes a single column-per-viewport layout that is paged
        // horizontally, the same model foliate uses. Only the flow changes, never the book content,
        // so sections, progress and the relocate bridge keep working unchanged.
        append("html.$BOOK_PAGINATED_CLASS, html.$BOOK_PAGINATED_CLASS body {\n")
        append("  box-sizing: border-box !important;\n")
        append("  height: 100vh !important;\n")
        append("  max-height: 100vh !important;\n")
        append("  overflow: hidden !important;\n")
        append("  background-color: transparent !important;\n")
        append("}\n")
        append("html.$BOOK_PAGINATED_CLASS body {\n")
        append("  width: 100vw !important;\n")
        append("  height: 100vh !important;\n")
        append("  max-height: 100vh !important;\n")
        append("  box-sizing: border-box !important;\n")
        append("  column-width: 100vw !important;\n")
        append("  column-gap: 0 !important;\n")
        append("  column-fill: auto !important;\n")
        append("  overflow-x: auto !important;\n")
        append("  overflow-y: hidden !important;\n")
        append("  padding-left: 0 !important;\n")
        append("  padding-right: 0 !important;\n")
        append("  margin: 0 !important;\n")
        append("  background-color: transparent !important;\n")
        append("  scroll-behavior: smooth !important;\n")
        append("}\n")
        append("html.$BOOK_PAGINATED_CLASS body.an-page-turn-slide-next {\n")
        append("  animation: anBookPageSlideNext 0.24s cubic-bezier(0.25, 1, 0.5, 1);\n")
        append("}\n")
        append("html.$BOOK_PAGINATED_CLASS body.an-page-turn-slide-prev {\n")
        append("  animation: anBookPageSlidePrev 0.24s cubic-bezier(0.25, 1, 0.5, 1);\n")
        append("}\n")
        append("html.$BOOK_PAGINATED_CLASS body.an-page-turn-depth {\n")
        append("  animation: anBookPageDepth 0.26s cubic-bezier(0.25, 1, 0.5, 1);\n")
        append("}\n")
        append("html.$BOOK_PAGINATED_CLASS body.an-page-turn-curl,\n")
        append("html.$BOOK_PAGINATED_CLASS body.an-page-turn-bend {\n")
        append("  animation: anBookPageCurl 0.28s cubic-bezier(0.25, 1, 0.5, 1);\n")
        append("  transform-origin: right center !important;\n")
        append("}\n")
        append("html.$BOOK_PAGINATED_CLASS body.an-page-turn-book_flip,\n")
        append("html.$BOOK_PAGINATED_CLASS body.an-page-turn-book {\n")
        append("  animation: anBookPageBookFlip 0.30s cubic-bezier(0.25, 1, 0.5, 1);\n")
        append("  transform-origin: left center !important;\n")
        append("}\n")
        append("@keyframes anBookPageSlideNext {\n")
        append("  0% { transform: translateX(100%); }\n")
        append("  100% { transform: translateX(0); }\n")
        append("}\n")
        append("@keyframes anBookPageSlidePrev {\n")
        append("  0% { transform: translateX(-100%); }\n")
        append("  100% { transform: translateX(0); }\n")
        append("}\n")
        append("@keyframes anBookPageDepth {\n")
        append("  0% { transform: scale(0.92); opacity: 0.65; }\n")
        append("  100% { transform: scale(1); opacity: 1; }\n")
        append("}\n")
        append("@keyframes anBookPageCurl {\n")
        append("  0% { transform: perspective(1200px) rotateY(-180deg); opacity: 0.5; }\n")
        append("  100% { transform: perspective(1200px) rotateY(0deg); opacity: 1; }\n")
        append("}\n")
        append("@keyframes anBookPageBookFlip {\n")
        append("  0% { transform: perspective(1200px) rotateY(-180deg); opacity: 0.3; }\n")
        append("  50% { opacity: 0.7; }\n")
        append("  100% { transform: perspective(1200px) rotateY(0deg); opacity: 1; }\n")
        append("}\n")
        append("html.$BOOK_PAGINATED_CLASS section.$BOOK_SECTION_CLASS {\n")
        append("  box-sizing: border-box !important;\n")
        append("  padding-left: var(--an-reader-padding-left, 16px) !important;\n")
        append("  padding-right: var(--an-reader-padding-right, 16px) !important;\n")
        append("  background-color: transparent !important;\n")
        append("}\n")
        append("html.$BOOK_PAGINATED_CLASS p {\n")
        append("  break-inside: avoid-column !important;\n")
        append("  -webkit-column-break-inside: avoid !important;\n")
        append("}\n")
        append("html.$BOOK_PAGINATED_CLASS div.$BOOK_SECTION_DIVIDER_CLASS {\n")
        append("  break-after: column !important;\n")
        append("  margin: 0 auto !important;\n")
        append("}\n")
    }
}

/**
 * Inserts (or rehydrates) a section in index order.
 *
 * When the new content lands above the viewport the scroll offset is corrected by the height delta,
 * so reading position never jumps while sections stream in behind the reader.
 */
internal fun buildAppendBookSectionJavascript(
    sectionIndex: Int,
    sectionHtml: String,
    keepScrollAnchored: Boolean = true,
): String {
    val quotedHtml = quoteBookJsString(sectionHtml)
    val quotedId = quoteBookJsString(bookSectionElementId(sectionIndex))
    val anchorFlag = if (keepScrollAnchored) "true" else "false"
    return """
        (function() {
            const body = document.body;
            if (!body) return 'no-body';
            const html = $quotedHtml;
            const elementId = $quotedId;
            const sectionIndex = $sectionIndex;
            const keepAnchored = $anchorFlag;
            const template = document.createElement('template');
            template.innerHTML = html;
            const node = template.content.firstElementChild;
            if (!node) return 'no-node';
            const paginated = $BOOK_PAGINATED_JS;
            const scroller = $BOOK_SCROLLER_JS;
            const previousScrollTop = scroller ? scroller.scrollTop : 0;
            const previousScrollLeft = scroller ? scroller.scrollLeft : 0;
            const previousScrollWidth = scroller ? scroller.scrollWidth : 0;
            const existing = document.getElementById(elementId);
            const previousHeight = existing ? existing.offsetHeight : 0;
            if (existing) {
                existing.replaceWith(node);
            } else {
                const nodes = body.querySelectorAll('section.$BOOK_SECTION_CLASS');
                let anchor = null;
                for (const candidate of Array.from(nodes)) {
                    const index = parseInt(candidate.getAttribute('data-an-section') || '-1', 10);
                    if (index > sectionIndex) {
                        anchor = candidate;
                        break;
                    }
                }
                if (anchor) {
                    body.insertBefore(node, anchor);
                } else {
                    body.appendChild(node);
                }
            }
            if (keepAnchored && scroller && !paginated) {
                // Content that lands above the viewport must not push the reader down. A rehydrated
                // placeholder grows in place, so anchor on the node's top: its bottom has already
                // moved by the height delta and comparing it dropped the correction entirely.
                if (node.offsetTop <= previousScrollTop + 1) {
                    const delta = node.offsetHeight - previousHeight;
                    if (delta !== 0) {
                        scroller.scrollTop = Math.max(0, previousScrollTop + delta);
                    }
                }
            } else if (keepAnchored && scroller && paginated) {
                // The paginated flow lays the same book out along the x axis. Without this branch a
                // section appended behind the reader shifted every following column, so each window
                // sync threw the reader back onto an earlier page.
                if (node.offsetLeft <= previousScrollLeft + 1) {
                    const page = Math.max(1, window.innerWidth || 1);
                    const deltaWidth = scroller.scrollWidth - previousScrollWidth;
                    if (deltaWidth !== 0) {
                        const anchored = Math.max(0, previousScrollLeft + deltaWidth);
                        const maxLeft = Math.max(0, scroller.scrollWidth - page);
                        scroller.scrollLeft = Math.min(maxLeft, Math.round(anchored / page) * page);
                    }
                }
            }
            return 'ok';
        })();
    """.trimIndent()
}

/** Prunes a section that fell out of the active window, replacing it with a placeholder. */
internal fun buildPruneBookSectionJavascript(sectionIndex: Int): String {
    val elementId = quoteBookJsString(bookSectionElementId(sectionIndex))
    return """
        (function() {
            const existing = document.getElementById($elementId);
            if (!existing) return 'missing';
            const sectionIndex = $sectionIndex;
            const placeholder = document.createElement('section');
            placeholder.id = $elementId;
            placeholder.className = '$BOOK_SECTION_CLASS $BOOK_SECTION_PLACEHOLDER_CLASS';
            placeholder.setAttribute('data-an-section', String(sectionIndex));
            const chapterId = existing.getAttribute('data-an-chapter');
            if (chapterId) placeholder.setAttribute('data-an-chapter', chapterId);
            const height = existing.offsetHeight || 0;
            if (height > 0) {
                placeholder.setAttribute('data-an-placeholder', '1');
                placeholder.setAttribute('data-an-height', String(height));
                placeholder.style.setProperty('height', height + 'px', 'important');
            }
            existing.replaceWith(placeholder);
            return 'ok';
        })();
    """.trimIndent()
}

/** Reports section offsets and the current scroll position as JSON, for book-level positioning. */
internal fun buildBookSectionMetricsJavascript(): String {
    return """
        (function() {
            const paginated = $BOOK_PAGINATED_JS;
            const scroller = $BOOK_SCROLLER_JS;
            if (!scroller) {
                return JSON.stringify({ scrollTop: 0, viewportHeight: 0, contentHeight: 0, sections: [] });
            }
            // The paginated flow measures the same book along the x axis and reports it through the
            // same fields, so positioning, progress and the relocate bridge stay flow independent.
            const offset = paginated ? scroller.scrollLeft : scroller.scrollTop;
            const viewport = paginated ? (window.innerWidth || 0) : (window.innerHeight || 0);
            const content = paginated ? scroller.scrollWidth : scroller.scrollHeight;
            const nodes = document.querySelectorAll('section.$BOOK_SECTION_CLASS');
            const sections = Array.from(nodes).map(function(element) {
                const rect = element.getBoundingClientRect();
                return {
                    index: parseInt(element.getAttribute('data-an-section') || '-1', 10),
                    chapterId: element.getAttribute('data-an-chapter') || '',
                    top: Math.round((paginated ? rect.left : rect.top) + offset),
                    height: Math.round(paginated ? rect.width : rect.height),
                    pruned: element.getAttribute('data-an-placeholder') === '1',
                };
            });
            return JSON.stringify({
                scrollTop: offset,
                viewportHeight: viewport,
                contentHeight: content,
                sections: sections,
            });
        })();
    """.trimIndent()
}

/** Scrolls the book document to a given section and fraction inside it. */
internal fun buildScrollToBookSectionJavascript(
    sectionIndex: Int,
    sectionFraction: Float,
): String {
    val clampedFraction = sectionFraction.coerceIn(0f, 1f)
    return """
        (function() {
            const target = document.getElementById(${quoteBookJsString(bookSectionElementId(sectionIndex))});
            if (!target) return 'no-target';
            const paginated = $BOOK_PAGINATED_JS;
            const scroller = $BOOK_SCROLLER_JS;
            if (!scroller) return 'no-scroller';
            const rect = target.getBoundingClientRect();
            const next = target.nextElementSibling;
            if (paginated) {
                const page = Math.max(1, window.innerWidth || 1);
                const sectionLeft = scroller.scrollLeft + rect.left;
                const sectionWidth = (function() {
                    if (next && next.offsetLeft > target.offsetLeft) {
                        return next.offsetLeft - target.offsetLeft;
                    }
                    return Math.max(rect.width, scroller.scrollWidth - target.offsetLeft);
                })();
                const rawTarget = sectionLeft + Math.round(sectionWidth * $clampedFraction);
                const max = Math.max(0, scroller.scrollWidth - page);
                scroller.scrollLeft = Math.min(max, Math.round(rawTarget / page) * page);
            } else {
                const sectionTop = scroller.scrollTop + rect.top;
                const sectionHeight = (function() {
                    if (next && next.offsetTop > target.offsetTop) {
                        return next.offsetTop - target.offsetTop;
                    }
                    return Math.max(rect.height, scroller.scrollHeight - target.offsetTop);
                })();
                const rawTarget = sectionTop + Math.round(sectionHeight * $clampedFraction);
                const max = Math.max(0, scroller.scrollHeight - (window.innerHeight || 0));
                scroller.scrollTop = Math.min(max, Math.max(0, rawTarget));
            }
            if (typeof window.__anBookRelocate === 'function') {
                window.__anBookRelocate();
            }
            return 'ok';
        })();
    """.trimIndent()
}

/** Switches between scrolled and paginated flow while maintaining the reading position. */
internal fun buildBookFlowJavascript(paginated: Boolean): String {
    val enabled = if (paginated) "true" else "false"
    return """
        (function() {
            const root = document.documentElement;
            if (!root) return 'no-root';
            const paginated = $enabled;
            const hadClass = root.classList.contains('$BOOK_PAGINATED_CLASS');
            const previousScroller = $BOOK_SCROLLER_JS;
            const previousFraction = (function() {
                if (!previousScroller) return 0;
                if (hadClass) {
                    const max = Math.max(1, previousScroller.scrollWidth - (window.innerWidth || 0));
                    return previousScroller.scrollLeft / max;
                }
                const max = Math.max(1, previousScroller.scrollHeight - (window.innerHeight || 0));
                return previousScroller.scrollTop / max;
            })();
            if (paginated) {
                root.classList.add('$BOOK_PAGINATED_CLASS');
            } else {
                root.classList.remove('$BOOK_PAGINATED_CLASS');
            }
            if (document.body) {
                document.body.scrollLeft = 0;
                if (!paginated) document.body.scrollTop = 0;
            }
            root.scrollLeft = 0;
            if (paginated) root.scrollTop = 0;
            const scroller = $BOOK_SCROLLER_JS;
            if (scroller) {
                if (paginated) {
                    const page = Math.max(1, window.innerWidth || 1);
                    const max = Math.max(0, scroller.scrollWidth - page);
                    const target = Math.min(max, previousFraction * max);
                    scroller.scrollLeft = Math.min(max, Math.round(target / page) * page);
                } else {
                    const max = Math.max(0, scroller.scrollHeight - (window.innerHeight || 0));
                    scroller.scrollTop = Math.min(max, previousFraction * max);
                }
            }
            if (typeof window.__anBookRelocate === 'function') {
                window.__anBookRelocate();
            }
            return 'ok';
        })();
    """.trimIndent()
}

/**
 * Moves one page (paginated flow) or roughly one viewport (scrolled flow) forwards or backwards.
 * [delta] is `1` for the next page and `-1` for the previous one.
 */
internal fun buildBookPageTurnJavascript(
    delta: Int,
    transitionStyleName: String = "SLIDE",
): String {
    val step = if (delta >= 0) 1 else -1
    return """
        (function() {
            const root = document.documentElement;
            const paginated = $BOOK_PAGINATED_JS;
            const scroller = $BOOK_SCROLLER_JS;
            if (!scroller) return 'no-scroller';
            const step = $step;
            const style = "$transitionStyleName";
            if (paginated) {
                const page = Math.max(1, window.innerWidth || root.clientWidth || 1);
                const max = Math.max(0, scroller.scrollWidth - page);
                const current = scroller.scrollLeft;
                const currentPageIndex = Math.round(current / page);
                const targetPageIndex = Math.min(Math.floor(max / page), Math.max(0, currentPageIndex + step));
                const targetPos = targetPageIndex * page;
                const startPos = current;
                const distance = targetPos - startPos;
                if (style === 'INSTANT' || distance === 0) {
                    scroller.scrollLeft = targetPos;
                } else {
                    let animationClass = 'an-page-turn-' + style.toLowerCase();
                    if (style === 'SLIDE') {
                        animationClass += step > 0 ? '-next' : '-prev';
                    }
                    const targetBody = document.body;
                    if (targetBody) targetBody.classList.add(animationClass);
                    const startTime = performance.now();
                    const duration = (style === 'BOOK_FLIP' || style === 'BOOK') ? 300 : 260;
                    function stepAnim(now) {
                        const elapsed = now - startTime;
                        const progress = Math.min(1, elapsed / duration);
                        const ease = 0.5 - Math.cos(progress * Math.PI) / 2;
                        scroller.scrollLeft = Math.round(startPos + distance * ease);
                        if (progress < 1) {
                            requestAnimationFrame(stepAnim);
                        } else {
                            if (targetBody) targetBody.classList.remove(animationClass);
                            scroller.scrollLeft = targetPos;
                        }
                    }
                    requestAnimationFrame(stepAnim);
                }
            } else {
                const viewport = (window.innerHeight || 0) * 0.9;
                const max = Math.max(0, scroller.scrollHeight - (window.innerHeight || 0));
                const targetPos = Math.min(max, Math.max(0, scroller.scrollTop + step * viewport));
                if (style === 'INSTANT') {
                    scroller.scrollTop = targetPos;
                } else {
                    scroller.scrollTo({ top: targetPos, behavior: 'smooth' });
                }
            }
            if (typeof window.__anBookRelocate === 'function') {
                window.__anBookRelocate();
            }
            return 'ok';
        })();
    """.trimIndent()
}

internal fun quoteBookJsString(value: String): String {
    val builder = StringBuilder(value.length + 16)
    builder.append('"')
    for (char in value) {
        when (char) {
            '\\' -> builder.append("\\\\")
            '"' -> builder.append("\\\"")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            '<' -> builder.append("\\u003C")
            '>' -> builder.append("\\u003E")
            '&' -> builder.append("\\u0026")
            '\u2028' -> builder.append("\\u2028")
            '\u2029' -> builder.append("\\u2029")
            else -> {
                if (char.code < 0x20) {
                    builder.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    builder.append(char)
                }
            }
        }
    }
    builder.append('"')
    return builder.toString()
}

/**
 * One entry of the book skeleton: a spine section that exists in the document as a placeholder
 * before its content was ever loaded.
 */
internal data class NovelBookSkeletonSection(
    val sectionIndex: Int,
    val chapterId: Long,
    val estimatedHeightPx: Int = BOOK_SECTION_ESTIMATED_PLACEHOLDER_HEIGHT_PX,
)

/** Height of a placeholder for a section that has never been rendered. */
internal const val BOOK_SECTION_ESTIMATED_PLACEHOLDER_HEIGHT_PX = 64

/**
 * Seeds the document with one placeholder per spine section, in spine order.
 *
 * Without a skeleton the document only contained the sections rendered in the current session, so
 * resuming in the middle of a novel left nothing above the resume point: scrolling back was
 * impossible and the reader had no anchors for whole-book positioning. Placeholders are cheap (an
 * empty element with a small height) and are rehydrated in place by
 * [buildAppendBookSectionJavascript], which also corrects the scroll offset when they grow.
 */
internal fun buildBookSkeletonJavascript(sections: List<NovelBookSkeletonSection>): String {
    if (sections.isEmpty()) return "(function() { return 'empty'; })();"
    val payload = buildString {
        append('[')
        sections.sortedBy { it.sectionIndex }.forEachIndexed { position, section ->
            if (position > 0) append(',')
            append("{\"index\":")
            append(section.sectionIndex)
            append(",\"chapterId\":\"")
            append(section.chapterId)
            append("\",\"height\":")
            append(section.estimatedHeightPx.coerceAtLeast(1))
            append('}')
        }
        append(']')
    }
    val quotedPayload = quoteBookJsString(payload)
    val quotedPrefix = quoteBookJsString(BOOK_SECTION_ID_PREFIX)
    return """
        (function() {
            const body = document.body;
            if (!body) return 'no-body';
            const spec = JSON.parse($quotedPayload);
            const prefix = $quotedPrefix;
            const existing = new Map();
            body.querySelectorAll('section.$BOOK_SECTION_CLASS').forEach(function(element) {
                existing.set(parseInt(element.getAttribute('data-an-section') || '-1', 10), element);
            });
            let previous = null;
            let created = 0;
            for (const item of spec) {
                const found = existing.get(item.index);
                if (found) {
                    previous = found;
                    continue;
                }
                const element = document.createElement('section');
                element.id = prefix + item.index;
                element.className = '$BOOK_SECTION_CLASS $BOOK_SECTION_PLACEHOLDER_CLASS';
                element.setAttribute('data-an-section', String(item.index));
                element.setAttribute('data-an-chapter', item.chapterId);
                element.setAttribute('data-an-placeholder', '1');
                element.setAttribute('data-an-estimated', '1');
                element.setAttribute('data-an-height', String(item.height));
                element.style.setProperty('height', item.height + 'px', 'important');
                if (previous) {
                    previous.insertAdjacentElement('afterend', element);
                } else {
                    body.insertBefore(element, body.firstChild);
                }
                previous = element;
                created++;
            }
            return 'ok:' + created;
        })();
    """.trimIndent()
}

private fun escapeBookHtmlText(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
