package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookDocument
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineFlow

/** Builds the isolated section document used by the dedicated book renderer. */
internal fun buildNovelBookEngineDocumentHtml(
    document: NovelBookDocument,
    flow: NovelBookEngineFlow,
    documentGeneration: Long = 0L,
    readerCss: String = "",
): String {
    val flowCss = when (flow) {
        NovelBookEngineFlow.PAGINATED -> """
            #an-book-viewport {
              position: fixed;
              inset: 0;
              overflow: hidden;
              touch-action: none;
            }
            #an-book-content {
              /* Only the parts of the paged geometry that cannot be fought over live here: the
                 reader stylesheet forces width/height/padding/background with !important, so the
                 column box itself is sized from JS with inline !important styles, which beat every
                 stylesheet rule. See applyPagedGeometry below. */
              box-sizing: border-box !important;
              column-fill: auto !important;
              overflow: visible !important;
              /* Texture, background image and OLED gradient are painted on html/body, so every
                 layer above them has to stay see-through or the reader shows a flat colour. */
              background: transparent !important;
            }
            #an-book-content img,
            #an-book-content svg,
            #an-book-content video {
              object-fit: contain;
              break-inside: avoid;
              page-break-inside: avoid;
            }
            /* Block the voice is reading, in the paged flow. */
            #an-book-content [data-an-tts-highlight] {
              background-color: rgba(128, 128, 128, 0.28);
              border-radius: 4px;
            }
            /* Marker used to measure how many columns the section actually produced. */
            #an-book-end {
              display: block;
              width: 1px;
              height: 1px;
            }
        """.trimIndent()
        NovelBookEngineFlow.SCROLLED -> """
            #an-book-viewport {
              position: fixed;
              inset: 0;
              overflow-x: hidden;
              overflow-y: auto;
              background: transparent !important;
            }
            #an-book-content {
              box-sizing: border-box;
              width: 100%;
              min-height: 100%;
              /* Texture, background image and OLED gradient are painted on html/body, so this
                 layer must stay see-through instead of covering them with a flat colour. */
              background: transparent !important;
            }
            /* One block per chapter the reader stitched into the document, so crossing a chapter
               is a plain scroll instead of a document swap. */
            .an-book-section {
              display: block;
              width: 100%;
              background: transparent !important;
            }
            /* Block the voice is reading. The chapter WebView paints its highlight from its own
               script; the book document is built here, so without this rule the anchor script
               marked the right paragraph and nothing at all changed on screen. */
            #an-book-content [data-an-tts-highlight] {
              background-color: rgba(128, 128, 128, 0.28);
              border-radius: 4px;
            }
            #an-book-content img,
            #an-book-content svg,
            #an-book-content video {
              max-width: 100%;
              height: auto;
              object-fit: contain;
            }
        """.trimIndent()
    }
    // Only the scrolled flow stitches chapters together, so only it wraps its content in a section
    // element. The paged flow keeps the bare markup its column geometry was tuned against.
    val sectionMarkup = when (flow) {
        NovelBookEngineFlow.PAGINATED -> document.html
        NovelBookEngineFlow.SCROLLED ->
            "<section class=\"an-book-section\" " +
                "data-an-index=\"${document.sectionIndex}\" " +
                "data-an-chapter=\"${document.chapterId}\">${document.html}</section>"
    }
    val engineScript = """
        <script>
          (function() {
            const viewport = document.getElementById('an-book-viewport');
            const content = document.getElementById('an-book-content');
            const isPaginated = document.documentElement.dataset.anBookFlow === 'paginated';
            const documentGeneration = $documentGeneration;
            // Boot marker: a paged document that never reaches the ready handshake still leaves a
            // trace in logcat, and the error hook surfaces exceptions thrown by this script.
            console.log('an-book-boot flow=' + document.documentElement.dataset.anBookFlow +
              ' generation=' + documentGeneration +
              ' window=' + window.innerWidth + 'x' + window.innerHeight +
              ' viewport=' + (viewport ? viewport.clientWidth + 'x' + viewport.clientHeight : 'missing') +
              ' kids=' + (content ? content.childElementCount : 'missing'));
            window.addEventListener('error', function(event) {
              console.log('an-book-error ' + ((event && event.message) ? event.message : 'unknown'));
            });
            // Inline !important styles beat every stylesheet rule, including the reader CSS and
            // any user CSS, which is why the paged geometry is applied from here.
            const setImportant = function(element, property, value) {
              element.style.setProperty(property, value, 'important');
            };
            let pageColumn = 1;
            let pageGap = 0;
            let pagePitch = 1;
            let pageIndex = 0;
            // Paged geometry is measured as a delta between rects of the column box. A page turn
            // animates that box (depth scales it, book rotates it, curl tilts it), so anything
            // measured mid-turn came back distorted: the page count collapsed, a turn could report a
            // premature end of the chapter and the reported text offset snapped to its start, which
            // is why the paged progress bar only ever sat at the beginning or the end. Measurements
            // are taken while the box is settled and cached for the duration of the turn.
            let turnActive = false;
            let pageCountCache = 1;
            let pageOffsetCache = { page: -1, offset: 0 };
            const sentinel = (function() {
              if (!isPaginated) return null;
              const marker = document.createElement('span');
              marker.id = 'an-book-end';
              marker.setAttribute('aria-hidden', 'true');
              content.appendChild(marker);
              return marker;
            })();
            const applyPagedGeometry = function() {
              if (!isPaginated) return;
              const style = window.getComputedStyle(content);
              const padLeft = parseFloat(style.paddingLeft) || 0;
              const padRight = parseFloat(style.paddingRight) || 0;
              const padTop = parseFloat(style.paddingTop) || 0;
              const padBottom = parseFloat(style.paddingBottom) || 0;
              const width = Math.max(1, viewport.clientWidth || window.innerWidth || 1);
              const height = Math.max(1, viewport.clientHeight || window.innerHeight || 1);
              pageColumn = Math.max(1, width - padLeft - padRight);
              pageGap = Math.max(0, padLeft + padRight);
              pagePitch = pageColumn + pageGap;
              setImportant(content, 'width', width + 'px');
              setImportant(content, 'height', height + 'px');
              setImportant(content, 'max-height', height + 'px');
              setImportant(content, 'min-height', '0px');
              setImportant(content, 'column-count', 'auto');
              setImportant(content, 'column-width', pageColumn + 'px');
              setImportant(content, 'column-gap', pageGap + 'px');
              setImportant(content, 'column-fill', 'auto');
              // An unbreakable element taller than the page box moves to the next column and
              // leaves the page blank: that is what painted one strip and nothing else.
              const columnHeight = Math.max(1, height - padTop - padBottom);
              const media = content.querySelectorAll('img, svg, video');
              for (let index = 0; index < media.length; index += 1) {
                setImportant(media[index], 'max-width', pageColumn + 'px');
                setImportant(media[index], 'max-height', columnHeight + 'px');
                setImportant(media[index], 'height', 'auto');
              }
              // Cached measurements belong to the geometry that produced them.
              turnActive = false;
              pageOffsetCache = { page: -1, offset: 0 };
            };
            const pageCount = function() {
              if (!isPaginated) return 1;
              // A turn is in flight, so the last settled measurement is the truthful one.
              if (turnActive) return pageCountCache;
              // Overflow columns are painted outside the content box and do not reliably grow
              // scrollWidth, so the count is measured from a marker at the end of the section.
              let width = 0;
              if (sentinel) {
                const contentRect = content.getBoundingClientRect();
                const markerRect = sentinel.getBoundingClientRect();
                width = markerRect.left - contentRect.left + markerRect.width;
              }
              width = Math.max(width, content.scrollWidth, pagePitch);
              pageCountCache = Math.max(1, Math.ceil((width - 1) / pagePitch));
              return pageCountCache;
            };
            const currentPage = function() {
              if (!isPaginated) return 0;
              return Math.max(0, Math.min(pageIndex, pageCount() - 1));
            };
            // Translating the column box is deterministic: scrollLeft on an overflow-hidden
            // container is fought over by scroll anchoring and reset by relayouts.
            // The paged geometry is applied with inline !important styles, so the page turn has
            // to be animated from here as well: a stylesheet rule can never beat an inline
            // !important transform, which is why every style looked instant.
            const TURN_DURATION_MILLIS = 320;
            const turnOrigin = function(style) {
              if (style === 'book' || style === 'book_flip') return 'left center';
              if (style === 'curl') return 'bottom right';
              return '50% 50%';
            };
            const turnTransform = function(style, offsetPx, forward) {
              const base = 'translateX(' + offsetPx + 'px)';
              if (style === 'depth') return base + ' scale(0.86) translateZ(-140px)';
              if (style === 'book' || style === 'book_flip') {
                return base + ' rotateY(' + (forward ? -24 : 24) + 'deg) scale(0.94)';
              }
              if (style === 'curl') return base + ' rotateZ(-5deg) rotateX(9deg) scale(0.94)';
              return base;
            };
            let turnTimer = 0;
            const settlePage = function(offsetPx, durationMillis) {
              setImportant(content, 'transition',
                'transform ' + durationMillis + 'ms cubic-bezier(0.25, 1, 0.5, 1), opacity ' +
                durationMillis + 'ms ease-out, filter ' + durationMillis + 'ms ease-out');
              setImportant(content, 'transform', 'translateX(' + offsetPx + 'px)');
              setImportant(content, 'opacity', '1');
              setImportant(content, 'filter', 'none');
            };
            const goToPage = function(page, styleName) {
              const target = Math.max(0, Math.min(page, pageCount() - 1));
              const style = String(styleName || 'SLIDE').toLowerCase();
              const startPage = pageIndex;
              pageIndex = target;
              const startX = -startPage * pagePitch;
              const targetX = -target * pagePitch;
              const forward = target >= startPage;
              if (turnTimer !== 0) {
                window.clearTimeout(turnTimer);
                turnTimer = 0;
              }
              // A rapid turn can start on top of a running animation, so the column box is snapped
              // back to a pure translation of the page being left. Everything measured below then
              // sees settled geometry, and the turn still animates because the transition property is
              // set again right after.
              turnActive = false;
              setImportant(content, 'transition', 'none');
              setImportant(content, 'transform', 'translateX(' + startX + 'px)');
              setImportant(content, 'opacity', '1');
              setImportant(content, 'filter', 'none');
              pageOffsetCache = { page: target, offset: measurePageOffset(target) };
              setImportant(content, 'transform-origin', turnOrigin(style));
              if (style === 'instant' || startPage === target) {
                setImportant(content, 'transition', 'none');
                setImportant(content, 'transform', 'translateX(' + targetX + 'px)');
                setImportant(content, 'opacity', '1');
                setImportant(content, 'filter', 'none');
                return target;
              }
              if (style === 'slide') {
                turnActive = true;
                settlePage(targetX, TURN_DURATION_MILLIS);
                window.setTimeout(function() { turnActive = false; }, TURN_DURATION_MILLIS);
                return target;
              }
              // Two phases: the current page first lifts, tilts or curls away, then the target
              // page settles back into a flat, fully opaque column box.
              const half = Math.max(90, Math.round(TURN_DURATION_MILLIS / 2));
              turnActive = true;
              setImportant(content, 'transition',
                'transform ' + half + 'ms ease-in, opacity ' + half + 'ms ease-in, filter ' +
                half + 'ms ease-in');
              setImportant(content, 'transform', turnTransform(style, startX, forward));
              setImportant(content, 'opacity', '0.82');
              setImportant(content, 'filter', 'brightness(0.88)');
              turnTimer = window.setTimeout(function() {
                turnTimer = 0;
                settlePage(targetX, half);
                window.setTimeout(function() { turnActive = false; }, half);
              }, half);
              return target;
            };
            // The scrolled document can hold several chapters at once, so a position is only
            // unambiguous as (section, offset inside that section). The paged document has no section
            // wrappers and reports -1, which keeps the engine on the section it opened.
            const sectionNodes = function() {
              return Array.prototype.slice.call(content.children).filter(function(node) {
                return node.classList && node.classList.contains('an-book-section');
              });
            };
            const sectionIndexOf = function(node) {
              if (!node || typeof node.getAttribute !== 'function') return -1;
              const value = parseInt(node.getAttribute('data-an-index'), 10);
              return isNaN(value) ? -1 : value;
            };
            const sectionChapterOf = function(node) {
              if (!node || typeof node.getAttribute !== 'function') return 0;
              const value = parseInt(node.getAttribute('data-an-chapter'), 10);
              return isNaN(value) ? 0 : value;
            };
            const sectionNodeAt = function(sectionIndex) {
              const nodes = sectionNodes();
              for (let index = 0; index < nodes.length; index += 1) {
                if (sectionIndexOf(nodes[index]) === sectionIndex) return nodes[index];
              }
              return null;
            };
            const resolveSectionNode = function(sectionIndex) {
              const nodes = sectionNodes();
              if (nodes.length === 0) return content;
              if (sectionIndex === undefined || sectionIndex === null || sectionIndex < 0) return nodes[0];
              return sectionNodeAt(sectionIndex);
            };
            const textNodesIn = function(root) {
              const nodes = [];
              if (!root) return nodes;
              const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
              let node = walker.nextNode();
              while (node) {
                if ((node.nodeValue || '').length > 0) nodes.push(node);
                node = walker.nextNode();
              }
              return nodes;
            };
            const textNodes = function() {
              return textNodesIn(content);
            };
            const totalCharCount = function(nodes) {
              return nodes.reduce(function(total, node) {
                return total + (node.nodeValue || '').length;
              }, 0);
            };
            // Section the viewport currently starts in: the last one whose top edge is at or above
            // the top of the viewport.
            const currentSectionNode = function() {
              const nodes = sectionNodes();
              if (nodes.length === 0) return content;
              const top = viewport.getBoundingClientRect().top;
              let candidate = nodes[0];
              for (let index = 0; index < nodes.length; index += 1) {
                if (nodes[index].getBoundingClientRect().top - top <= 1) candidate = nodes[index];
              }
              return candidate;
            };
            const fallbackOffsetIn = function(sectionNode) {
              const total = totalCharCount(textNodesIn(sectionNode));
              if (total <= 0) return 0;
              if (isPaginated) {
                const count = pageCount();
                const fraction = count <= 1 ? 0 : currentPage() / (count - 1);
                return Math.round(fraction * Math.max(0, total - 1));
              }
              const rect = sectionNode.getBoundingClientRect();
              const top = viewport.getBoundingClientRect().top;
              const height = Math.max(1, rect.height);
              const fraction = Math.max(0, Math.min(1, (top - rect.top) / height));
              return Math.round(fraction * Math.max(0, total - 1));
            };
            const offsetWithin = function(sectionNode, container, offsetInContainer) {
              const nodes = textNodesIn(sectionNode);
              // A caret can land on an element instead of text (a point in the page padding or in the
              // gap between two blocks). Resolving it to the child it points at keeps the offset where
              // the reader is; it used to collapse onto the first text node, i.e. the chapter start.
              let target = container;
              let targetOffset = offsetInContainer;
              if (container && container.nodeType === Node.ELEMENT_NODE) {
                const children = container.childNodes;
                const index = Math.max(0, Math.min(offsetInContainer, children.length - 1));
                target = children.length > 0 ? children[index] : container;
                targetOffset = 0;
              }
              let offset = 0;
              for (const node of nodes) {
                if (node === target) {
                  return offset + Math.max(0, Math.min(targetOffset, (node.nodeValue || '').length));
                }
                if (target && target.nodeType === Node.ELEMENT_NODE && target.contains(node)) {
                  return offset;
                }
                offset += (node.nodeValue || '').length;
              }
              return offset;
            };
            // Range over the character at an exact text offset of a section. Restoring a position and
            // asking which page an offset landed on both need it, so it lives in one place.
            const rangeAtOffset = function(nodes, charOffset) {
              const total = totalCharCount(nodes);
              if (nodes.length === 0 || total <= 0) return null;
              let remaining = Math.max(0, Math.min(Number(charOffset) || 0, total - 1));
              let target = nodes[nodes.length - 1];
              let localOffset = (target.nodeValue || '').length;
              for (const node of nodes) {
                const length = (node.nodeValue || '').length;
                if (remaining <= length) {
                  target = node;
                  localOffset = remaining;
                  break;
                }
                remaining -= length;
              }
              const length = (target.nodeValue || '').length;
              const start = Math.max(0, Math.min(localOffset, length));
              const range = document.createRange();
              range.setStart(target, start);
              range.setEnd(target, Math.min(length, start + 1));
              return range;
            };
            // A range on whitespace between blocks has an empty rect, which would read as column 0,
            // so the enclosing element answers for it instead.
            const rectOfRange = function(range) {
              const rect = range.getBoundingClientRect();
              if (rect.width > 0 || rect.height > 0) return rect;
              const parent = range.startContainer.parentElement;
              return parent ? parent.getBoundingClientRect() : rect;
            };
            const pageOfOffset = function(nodes, charOffset, contentRect) {
              const range = rangeAtOffset(nodes, charOffset);
              if (!range) return 0;
              const rect = rectOfRange(range);
              return Math.max(0, Math.floor((rect.left - contentRect.left) / pagePitch));
            };
            // The paged flow always knows which page it is on, so its text offset is derived from the
            // page with a binary search over the column geometry instead of hit-testing a point.
            const measurePageOffset = function(page) {
              const nodes = textNodes();
              const total = totalCharCount(nodes);
              if (total <= 0) return 0;
              const target = Math.max(0, page);
              if (target === 0) return 0;
              const contentRect = content.getBoundingClientRect();
              let low = 0;
              let high = total - 1;
              let best = total - 1;
              while (low <= high) {
                const middle = (low + high) >> 1;
                if (pageOfOffset(nodes, middle, contentRect) >= target) {
                  best = middle;
                  high = middle - 1;
                } else {
                  low = middle + 1;
                }
              }
              return best;
            };
            const charOffsetAtPage = function(page) {
              if (turnActive || pageOffsetCache.page === page) return pageOffsetCache.offset;
              const offset = measurePageOffset(page);
              pageOffsetCache = { page: page, offset: offset };
              return offset;
            };
            const locationAtViewportStart = function() {
              const sectionNode = currentSectionNode();
              if (!sectionNode) return { sectionIndex: -1, charOffset: 0 };
              if (isPaginated) {
                return {
                  sectionIndex: sectionIndexOf(sectionNode),
                  charOffset: charOffsetAtPage(currentPage())
                };
              }
              const bounds = viewport.getBoundingClientRect();
              const x = Math.min(bounds.right - 1, bounds.left + Math.max(1, viewport.clientWidth * 0.08));
              const y = Math.min(bounds.bottom - 1, bounds.top + Math.max(1, viewport.clientHeight * 0.08));
              let range = document.caretRangeFromPoint ? document.caretRangeFromPoint(x, y) : null;
              if (!range && document.caretPositionFromPoint) {
                const position = document.caretPositionFromPoint(x, y);
                if (position) {
                  range = document.createRange();
                  range.setStart(position.offsetNode, position.offset);
                  range.collapse(true);
                }
              }
              if (!range || !sectionNode.contains(range.startContainer)) {
                return {
                  sectionIndex: sectionIndexOf(sectionNode),
                  charOffset: fallbackOffsetIn(sectionNode)
                };
              }
              return {
                sectionIndex: sectionIndexOf(sectionNode),
                charOffset: offsetWithin(sectionNode, range.startContainer, range.startOffset)
              };
            };
            const charOffsetAtViewportStart = function() {
              return locationAtViewportStart().charOffset;
            };
            const relocate = function() {
              const position = locationAtViewportStart();
              return JSON.stringify({
                kind: 'moved',
                sectionIndex: position.sectionIndex,
                charOffset: position.charOffset
              });
            };
            let relocateFrame = 0;
            const pushRelocated = function() {
              if (relocateFrame !== 0) return;
              relocateFrame = requestAnimationFrame(function() {
                relocateFrame = 0;
                try {
                  if (window.AnBookNative && typeof window.AnBookNative.onRelocated === 'function') {
                    window.AnBookNative.onRelocated($documentGeneration, relocate());
                  }
                } catch (_) {
                  // The native renderer may have been detached while this frame was pending.
                }
              });
            };
            const reportBoundary = function(kind) {
              try {
                if (window.AnBookNative && typeof window.AnBookNative.onBoundary === 'function') {
                  window.AnBookNative.onBoundary($documentGeneration, kind);
                }
              } catch (_) {
                // The native renderer may have been detached while this frame was pending.
              }
            };
            const reportSectionMeasured = function(sectionNode) {
              const index = sectionIndexOf(sectionNode);
              if (index < 0) return;
              try {
                if (window.AnBookNative && typeof window.AnBookNative.onSectionMeasured === 'function') {
                  window.AnBookNative.onSectionMeasured(
                    $documentGeneration,
                    index,
                    sectionChapterOf(sectionNode),
                    totalCharCount(textNodesIn(sectionNode)));
                }
              } catch (_) {
                // The native renderer may have been detached while this frame was pending.
              }
            };
            // A boundary report is a request for more content well before the edge, not a jump at
            // the edge: by the time the reader scrolls into the next chapter it is already part of
            // this document, so there is nothing to swap and nothing to jump over.
            let stitchForwardRequested = -1;
            let stitchBackwardRequested = -1;
            const pushStitchRequests = function() {
              if (isPaginated) return;
              const nodes = sectionNodes();
              if (nodes.length === 0) return;
              const height = Math.max(1, viewport.clientHeight);
              const maximum = Math.max(0, viewport.scrollHeight - height);
              const lastIndex = sectionIndexOf(nodes[nodes.length - 1]);
              const firstIndex = sectionIndexOf(nodes[0]);
              if (maximum - viewport.scrollTop <= height * 1.25 && stitchForwardRequested !== lastIndex) {
                stitchForwardRequested = lastIndex;
                reportBoundary('end');
              }
              if (firstIndex > 0 && viewport.scrollTop <= height * 0.5 && stitchBackwardRequested !== firstIndex) {
                stitchBackwardRequested = firstIndex;
                reportBoundary('start');
              }
            };
            viewport.addEventListener('scroll', function() {
              pushRelocated();
              pushStitchRequests();
            }, { passive: true });
            // The geometry has to survive orientation changes, reader-chrome padding changes and
            // font reflows, so it is reapplied and the current page re-clamped on every resize.
            applyPagedGeometry();
            window.addEventListener('resize', function() {
              if (!isPaginated) return;
              applyPagedGeometry();
              goToPage(pageIndex, 'INSTANT');
              pushRelocated();
            });
            const scrollToOffsetIn = function(sectionNode, charOffset) {
              const nodes = textNodesIn(sectionNode);
              const range = rangeAtOffset(nodes, charOffset);
              if (!range) return relocate();
              if (isPaginated) {
                goToPage(pageOfOffset(nodes, charOffset, content.getBoundingClientRect()), 'INSTANT');
              } else {
                const rect = rectOfRange(range);
                const viewportRect = viewport.getBoundingClientRect();
                viewport.scrollTop = Math.max(0, viewport.scrollTop + rect.top - viewportRect.top);
              }
              return relocate();
            };
            const goToCharOffset = function(charOffset, sectionIndex) {
              const node = resolveSectionNode(sectionIndex);
              if (!node) return relocate();
              return scrollToOffsetIn(node, charOffset);
            };
            // Reopening the book restores by fraction: the stored position was a fraction of an
            // estimated chapter length, and only this document knows the real one.
            const goToFraction = function(fraction, sectionIndex) {
              const node = resolveSectionNode(sectionIndex);
              if (!node) return relocate();
              const total = totalCharCount(textNodesIn(node));
              const safe = Math.max(0, Math.min(Number(fraction) || 0, 1));
              return scrollToOffsetIn(node, Math.round(safe * Math.max(0, total - 1)));
            };
            const buildSectionNode = function(sectionIndex, chapterId, html) {
              const node = document.createElement('section');
              node.className = 'an-book-section';
              node.setAttribute('data-an-index', String(sectionIndex));
              node.setAttribute('data-an-chapter', String(chapterId));
              node.innerHTML = html;
              return node;
            };
            // Images decode after insertion, so a section added above the viewport keeps growing.
            // Compensating every height change it causes is what holds the reading position still.
            const holdPositionWhileLoading = function(node) {
              let last = node.offsetHeight;
              const fix = function() {
                const height = node.offsetHeight;
                const delta = height - last;
                if (delta === 0) return;
                last = height;
                if (node.getBoundingClientRect().top < viewport.getBoundingClientRect().top) {
                  viewport.scrollTop = Math.max(0, viewport.scrollTop + delta);
                }
              };
              const images = node.querySelectorAll('img');
              for (let index = 0; index < images.length; index += 1) {
                images[index].addEventListener('load', fix, { once: true });
                images[index].addEventListener('error', fix, { once: true });
              }
            };
            const appendSection = function(sectionIndex, chapterId, html) {
              if (isPaginated) return false;
              if (sectionNodeAt(sectionIndex)) return true;
              const node = buildSectionNode(sectionIndex, chapterId, html);
              content.appendChild(node);
              holdPositionWhileLoading(node);
              reportSectionMeasured(node);
              stitchForwardRequested = -1;
              pushRelocated();
              return true;
            };
            const prependSection = function(sectionIndex, chapterId, html) {
              if (isPaginated) return false;
              if (sectionNodeAt(sectionIndex)) return true;
              const node = buildSectionNode(sectionIndex, chapterId, html);
              const before = viewport.scrollHeight;
              content.insertBefore(node, content.firstChild);
              const after = viewport.scrollHeight;
              viewport.scrollTop = Math.max(0, viewport.scrollTop + (after - before));
              holdPositionWhileLoading(node);
              reportSectionMeasured(node);
              stitchBackwardRequested = -1;
              pushRelocated();
              return true;
            };
            const replaceSection = function(sectionIndex, chapterId, html) {
              if (isPaginated) return false;
              const node = sectionNodeAt(sectionIndex);
              if (!node) return false;
              const above = node.getBoundingClientRect().top < viewport.getBoundingClientRect().top;
              const before = viewport.scrollHeight;
              const fresh = buildSectionNode(sectionIndex, chapterId, html);
              node.parentNode.replaceChild(fresh, node);
              const after = viewport.scrollHeight;
              if (above) viewport.scrollTop = Math.max(0, viewport.scrollTop + (after - before));
              holdPositionWhileLoading(fresh);
              reportSectionMeasured(fresh);
              pushRelocated();
              return true;
            };
            const removeSection = function(sectionIndex) {
              if (isPaginated) return false;
              const node = sectionNodeAt(sectionIndex);
              if (!node) return true;
              const above = node.getBoundingClientRect().top < viewport.getBoundingClientRect().top;
              const before = viewport.scrollHeight;
              node.parentNode.removeChild(node);
              const after = viewport.scrollHeight;
              if (above) viewport.scrollTop = Math.max(0, viewport.scrollTop - (before - after));
              stitchForwardRequested = -1;
              stitchBackwardRequested = -1;
              return true;
            };
            const waitForImage = function(image) {
              if (image.complete) {
                return typeof image.decode === 'function'
                  ? image.decode().catch(function() { return undefined; })
                  : Promise.resolve();
              }
              return new Promise(function(resolve) {
                let settled = false;
                const settle = function() {
                  if (settled) return;
                  settled = true;
                  resolve();
                };
                image.addEventListener('load', settle, { once: true });
                image.addEventListener('error', settle, { once: true });
                window.setTimeout(settle, 5000);
              }).then(function() {
                return typeof image.decode === 'function'
                  ? image.decode().catch(function() { return undefined; })
                  : undefined;
              });
            };
            const images = Array.from(content.querySelectorAll('img'));
            const ready = Promise.all(images.map(waitForImage))
              .then(function() {
                return new Promise(function(resolve) {
                  requestAnimationFrame(function() {
                    requestAnimationFrame(resolve);
                  });
                });
              })
              .then(function() {
                applyPagedGeometry();
                // Ask for the neighbouring chapters immediately: a chapter shorter than the prefetch
                // margin must not wait for a scroll event to become continuous.
                pushStitchRequests();
                // Mirrored into logcat by the renderer's WebChromeClient so the real layout numbers
                // of the book document can be inspected with: adb logcat -s NovelBookWebView
                try {
                  const contentStyle = window.getComputedStyle(content);
                  const rootStyle = window.getComputedStyle(document.documentElement);
                  console.log('an-book-diag ' + JSON.stringify({
                    flow: document.documentElement.dataset.anBookFlow,
                    vw: viewport.clientWidth,
                    vh: viewport.clientHeight,
                    cw: content.offsetWidth,
                    ch: content.offsetHeight,
                    csw: content.scrollWidth,
                    csh: content.scrollHeight,
                    pitch: pagePitch,
                    pages: pageCount(),
                    page: currentPage(),
                    height: contentStyle.height,
                    padding: contentStyle.padding,
                    columnWidth: contentStyle.columnWidth,
                    columnCount: contentStyle.columnCount,
                    columnGap: contentStyle.columnGap,
                    columnFill: contentStyle.columnFill,
                    transform: contentStyle.transform,
                    visibility: contentStyle.visibility,
                    opacity: contentStyle.opacity,
                    bodyH: document.body.clientHeight,
                    rootH: document.documentElement.clientHeight,
                    rootBg: String(rootStyle.backgroundImage).slice(0, 60),
                    kids: content.childElementCount,
                    chars: (content.textContent || '').trim().length
                  }));
                } catch (_) {
                  // Diagnostics must never break the document.
                }
                const payload = JSON.stringify({
                  kind: 'ready',
                  pageCount: pageCount(),
                  currentPage: currentPage(),
                  charOffset: charOffsetAtViewportStart(),
                  charCount: totalCharCount(textNodes())
                });
                try {
                  if (window.AnBookNative && typeof window.AnBookNative.onReady === 'function') {
                    window.AnBookNative.onReady($documentGeneration, payload);
                  }
                } catch (_) {
                  // A stale document must not keep the new renderer open waiting for its callback.
                }
                return payload;
              });
            window.__anBookEngine = Object.freeze({
              ready: ready,
              goTo: goToCharOffset,
              goToFraction: goToFraction,
              relocate: relocate,
              appendSection: appendSection,
              prependSection: prependSection,
              replaceSection: replaceSection,
              removeSection: removeSection,
              next: function(styleName) {
                if (isPaginated) {
                  const targetPage = currentPage() + 1;
                  if (targetPage >= pageCount()) return JSON.stringify({ kind: 'end' });
                  goToPage(targetPage, styleName);
                  return relocate();
                }
                const maximum = Math.max(0, viewport.scrollHeight - viewport.clientHeight);
                if (viewport.scrollTop >= maximum - 1) return JSON.stringify({ kind: 'end' });
                viewport.scrollTop = Math.min(maximum, viewport.scrollTop + viewport.clientHeight * 0.9);
                return relocate();
              },
              previous: function(styleName) {
                if (isPaginated) {
                  const targetPage = currentPage() - 1;
                  if (targetPage < 0) return JSON.stringify({ kind: 'start' });
                  goToPage(targetPage, styleName);
                  return relocate();
                }
                if (viewport.scrollTop <= 1) return JSON.stringify({ kind: 'start' });
                viewport.scrollTop = Math.max(0, viewport.scrollTop - viewport.clientHeight * 0.9);
                return relocate();
              },
              canScrollForward: function() {
                if (!viewport) return false;
                if (isPaginated) return currentPage() < pageCount() - 1;
                return viewport.scrollTop < (viewport.scrollHeight - viewport.clientHeight - 1);
              },
              scrollBy: function(px) {
                if (isPaginated || !viewport || !px) return 0;
                const before = viewport.scrollTop;
                const maximum = Math.max(0, viewport.scrollHeight - viewport.clientHeight);
                viewport.scrollTop = Math.max(0, Math.min(maximum, before + px));
                pushRelocated();
                pushStitchRequests();
                return Math.round(viewport.scrollTop - before);
              }
            });
          })();
        </script>
    """.trimIndent()
    return """
        <!doctype html>
        <html id="an-book-root" data-an-book-flow="${flow.name.lowercase()}">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
          <style>
            html, body {
              box-sizing: border-box;
              width: 100%;
              height: 100%;
              margin: 0;
              padding: 0;
              overflow: hidden;
              background-color: transparent !important;
              background-image: none !important;
            }
            $readerCss
            $flowCss
            html#an-book-root,
            html#an-book-root > body {
              height: 100% !important;
              min-height: 100% !important;
              max-height: 100% !important;
              background-color: transparent !important;
              background-image: none !important;
            }
            #an-book-atmosphere {
              display: none !important;
            }
            #an-book-viewport {
              perspective: 1400px !important;
              perspective-origin: 50% 50% !important;
            }
            #an-book-content {
              transform-style: preserve-3d !important;
              will-change: transform, opacity, filter !important;
            }
          </style>
        </head>
        <body data-an-section="${document.sectionIndex}" data-an-chapter="${document.chapterId}">
          <main id="an-book-viewport">
            <article id="an-book-content">$sectionMarkup</article>
          </main>
          $engineScript
        </body>
        </html>
    """.trimIndent()
}
