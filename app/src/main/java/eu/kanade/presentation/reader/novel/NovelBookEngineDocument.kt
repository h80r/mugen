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
            #an-book-content img,
            #an-book-content svg,
            #an-book-content video {
              max-width: 100%;
              height: auto;
              object-fit: contain;
            }
        """.trimIndent()
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
            };
            const pageCount = function() {
              if (!isPaginated) return 1;
              // Overflow columns are painted outside the content box and do not reliably grow
              // scrollWidth, so the count is measured from a marker at the end of the section.
              let width = 0;
              if (sentinel) {
                const contentRect = content.getBoundingClientRect();
                const markerRect = sentinel.getBoundingClientRect();
                width = markerRect.left - contentRect.left + markerRect.width;
              }
              width = Math.max(width, content.scrollWidth, pagePitch);
              return Math.max(1, Math.ceil((width - 1) / pagePitch));
            };
            const currentPage = function() {
              if (!isPaginated) return 0;
              return Math.max(0, Math.min(pageIndex, pageCount() - 1));
            };
            // Translating the column box is deterministic: scrollLeft on an overflow-hidden
            // container is fought over by scroll anchoring and reset by relayouts.
            const goToPage = function(page) {
              const target = Math.max(0, Math.min(page, pageCount() - 1));
              pageIndex = target;
              setImportant(content, 'transform', 'translateX(' + (-target * pagePitch) + 'px)');
              return target;
            };
            const textNodes = function() {
              const nodes = [];
              const walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT);
              let node = walker.nextNode();
              while (node) {
                if ((node.nodeValue || '').length > 0) nodes.push(node);
                node = walker.nextNode();
              }
              return nodes;
            };
            const totalCharCount = function(nodes) {
              return nodes.reduce(function(total, node) {
                return total + (node.nodeValue || '').length;
              }, 0);
            };
            const fallbackCharOffset = function(nodes) {
              const total = totalCharCount(nodes);
              if (total <= 0) return 0;
              if (isPaginated) {
                const count = pageCount();
                const fraction = count <= 1 ? 0 : currentPage() / (count - 1);
                return Math.round(fraction * Math.max(0, total - 1));
              }
              const scrollable = Math.max(1, viewport.scrollHeight - viewport.clientHeight);
              const fraction = Math.max(0, Math.min(1, viewport.scrollTop / scrollable));
              return Math.round(fraction * Math.max(0, total - 1));
            };
            const charOffsetAtViewportStart = function() {
              const nodes = textNodes();
              if (nodes.length === 0) return 0;
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
              if (!range || !content.contains(range.startContainer)) return fallbackCharOffset(nodes);
              let offset = 0;
              for (const node of nodes) {
                if (node === range.startContainer) {
                  return offset + Math.max(0, Math.min(range.startOffset, (node.nodeValue || '').length));
                }
                if (range.startContainer.nodeType === Node.ELEMENT_NODE && range.startContainer.contains(node)) {
                  return offset;
                }
                offset += (node.nodeValue || '').length;
              }
              return fallbackCharOffset(nodes);
            };
            const relocate = function() {
              return JSON.stringify({ kind: 'moved', charOffset: charOffsetAtViewportStart() });
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
            // The scrolled flow holds a single section, so reaching the bottom of the document is
            // what has to open the next chapter. Without this the reader could only ever scroll the
            // chapter it started in, because only taps and swipes reported a document boundary.
            let endReported = false;
            const pushScrollBoundary = function() {
              if (isPaginated) return;
              const maximum = Math.max(0, viewport.scrollHeight - viewport.clientHeight);
              if (!(maximum > 0 && viewport.scrollTop >= maximum - 2)) {
                endReported = false;
                return;
              }
              if (endReported) return;
              endReported = true;
              reportBoundary('end');
            };
            viewport.addEventListener('scroll', function() {
              pushRelocated();
              pushScrollBoundary();
            }, { passive: true });
            // Going back needs an explicit gesture: resting at the top of a section must not pull in
            // the previous chapter on its own.
            let touchStartY = 0;
            let startReported = false;
            viewport.addEventListener('touchstart', function(event) {
              touchStartY = event.touches.length > 0 ? event.touches[0].clientY : 0;
              startReported = false;
            }, { passive: true });
            viewport.addEventListener('touchmove', function(event) {
              if (isPaginated || startReported || viewport.scrollTop > 1) return;
              const y = event.touches.length > 0 ? event.touches[0].clientY : touchStartY;
              if (y - touchStartY < Math.max(24, viewport.clientHeight * 0.12)) return;
              startReported = true;
              reportBoundary('start');
            }, { passive: true });
            // The geometry has to survive orientation changes, reader-chrome padding changes and
            // font reflows, so it is reapplied and the current page re-clamped on every resize.
            applyPagedGeometry();
            window.addEventListener('resize', function() {
              if (!isPaginated) return;
              applyPagedGeometry();
              goToPage(pageIndex);
              pushRelocated();
            });
            const goToCharOffset = function(charOffset) {
              const nodes = textNodes();
              const total = totalCharCount(nodes);
              if (nodes.length === 0 || total <= 0) return relocate();
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
              const range = document.createRange();
              range.setStart(target, Math.min(localOffset, (target.nodeValue || '').length));
              range.collapse(true);
              const rect = range.getBoundingClientRect();
              const viewportRect = viewport.getBoundingClientRect();
              if (isPaginated) {
                const contentRect = content.getBoundingClientRect();
                goToPage(Math.floor((rect.left - contentRect.left) / pagePitch));
              } else {
                const absoluteTop = viewport.scrollTop + rect.top - viewportRect.top;
                viewport.scrollTop = Math.max(0, absoluteTop);
              }
              return relocate();
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
              relocate: relocate,
              next: function() {
                if (isPaginated) {
                  const targetPage = currentPage() + 1;
                  if (targetPage >= pageCount()) return JSON.stringify({ kind: 'end' });
                  goToPage(targetPage);
                  return relocate();
                }
                const maximum = Math.max(0, viewport.scrollHeight - viewport.clientHeight);
                if (viewport.scrollTop >= maximum - 1) return JSON.stringify({ kind: 'end' });
                viewport.scrollTop = Math.min(maximum, viewport.scrollTop + viewport.clientHeight * 0.9);
                return relocate();
              },
              previous: function() {
                if (isPaginated) {
                  const targetPage = currentPage() - 1;
                  if (targetPage < 0) return JSON.stringify({ kind: 'start' });
                  goToPage(targetPage);
                  return relocate();
                }
                if (viewport.scrollTop <= 1) return JSON.stringify({ kind: 'start' });
                viewport.scrollTop = Math.max(0, viewport.scrollTop - viewport.clientHeight * 0.9);
                return relocate();
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
            }
            $readerCss
            $flowCss
            /* The shared reader CSS forces html, body { height: auto !important }, and because
               #an-book-viewport is fixed the document box collapses to the body padding, so the
               atmosphere (texture, background image, OLED gradient) painted on html/body only
               covered a strip at the top of the screen. The id selectors raise specificity so this
               wins no matter where the reader CSS ends up in the cascade. */
            html#an-book-root,
            html#an-book-root > body {
              height: 100% !important;
              min-height: 100% !important;
              max-height: 100% !important;
              /* The shared reader CSS uses background-attachment: fixed. Android WebView clips a
                 fixed background to the element box while sizing it against its own viewport rect,
                 which left an unpainted strip along the bottom edge of the book document. Scroll
                 attachment paints across the full element box instead. */
              background-attachment: scroll !important;
            }
            /* Full-screen atmosphere layer. It inherits the background the reader CSS put on body,
               so the texture/background image covers the whole viewport even if the document box
               collapses again for any reason. Kept below #an-book-viewport and non-interactive. */
            #an-book-atmosphere {
              position: fixed;
              inset: 0;
              z-index: 0;
              pointer-events: none;
              background-image: inherit;
              background-repeat: inherit;
              background-size: inherit;
              background-position: inherit;
              background-attachment: scroll;
            }
          </style>
        </head>
        <body data-an-section="${document.sectionIndex}" data-an-chapter="${document.chapterId}">
          <div id="an-book-atmosphere" aria-hidden="true"></div>
          <main id="an-book-viewport">
            <article id="an-book-content">${document.html}</article>
          </main>
          $engineScript
        </body>
        </html>
    """.trimIndent()
}
