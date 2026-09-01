# Remover a instrumentação temporária do curl de mangá

**Captured:** 2026-09-01
**Source:** specter.archive (change `add-manga-page-curl`, instrumentação mantida a pedido durante a depuração)

## Idea
A depuração do curl em paisagem com duas páginas foi feita inteiramente por logs instrumentados — foi o que resolveu uma sequência de bugs que resistiram a inspeção de código (alinhamento das metades, quadro de coordenadas do arrasto, eixo da dobra, flicker no assentamento, verso espelhado, página errada sob a dobra). O usuário pediu para manter os logs "até segunda ordem" e essa ordem nunca veio, então eles seguem no código. Este item é essa segunda ordem, para quando o curl estiver estável.

São ~92 call sites em 7 arquivos, e parte disso vazou para a API da biblioteca vendorizada — é o principal motivo para limpar antes de considerar o `pagecurl` fechado.

## Notes
- **Arquivo a deletar:** `app/src/main/java/eu/kanade/presentation/reader/curl/SpreadCurlDiagnostics.kt` (o objeto inteiro, incluindo `enabled`, `logChanged`, `nextGesture`, `f`/`f2`).
- **Call sites a remover:** `MangaSpreadCurlRenderer.kt` (a maioria), `MangaCurlViewer.kt`, `ExternalFoldDriver.kt`, `PageCurl.kt`, `CurlDraw.kt`. Fases logadas: `renderer`, `layout.handoff`, `layout.column`, `layout.surface`, `layout.half`, `layout.image`, `content.half`, `overlay.compose`, `overlay.ready`, `sync`, `turn.programmatic`, `drag.zone`, `drag.vs.tap`, `drag.start`, `drag.move`, `drag.finish`, `drag.cancel`, `tap`, `nav.tap`, `fold.route`, `fold.finish`, `driver.*`, `curl.branch`, `curl.stack`, `curl.slotmap`, `curl.backlayer`.
- **Parâmetros de API a reverter** (estes são os que mais incomodam, porque entraram na biblioteca vendorizada):
  - `PageCurl(curlDebugName: String = "?")` — nos dois overloads, incluindo o repasse no delegante
  - `Modifier.drawCurl(debugName: String = "?")` em `CurlDraw.kt`
  - `ExternalFold.debugName` / `ExternalFoldDriver(debugName)` — está na interface pública, não só na classe
  - `ReaderPageImageView.debugPageView` — acessor `internal` adicionado só para os logs
  - `CurlTouchDispatcher.readingOrderMirrored` — hoje **só** alimenta os diagnósticos; nenhuma lógica de gesto lê o valor (a inversão R2L vive no `invertDirection` do driver). Some junto com os logs.
  - campos de sampling: `ExternalFoldDriver.updateCount` + `UPDATE_LOG_EVERY`, `CurlTouchDispatcher.gestureId` + `lastUpdateLogAt` + `UPDATE_LOG_INTERVAL_MS`
- **Não remover junto:** `SETTLE_EPSILON` e `Edge.pulledBackTowards` em `PageTurnAnimation.kt`. Parecem de depuração pelo nome mas são correções de comportamento — impedem que a dobra caia no caminho rápido do `drawCurl` que não desenha nada.
- Vale aproveitar a mesma passada para remover `leftGesturesEnabled` / `rightGesturesEnabled` em `MangaSpreadCurlRenderer.kt` (~linhas 354-355): são calculados e nunca lidos, o gating real é o `overlayCovering`. Verificado com `git stash` que são anteriores a este change, por isso não foram tocados durante as correções.
- Ao terminar: `./gradlew :app:compileDebugKotlin` e `./gradlew spotlessCheck`, e um teste no dispositivo confirmando que uma virada por toque e outra por arrasto continuam corretas (a instrumentação não deveria afetar comportamento, mas a remoção mexe em assinaturas na biblioteca).
