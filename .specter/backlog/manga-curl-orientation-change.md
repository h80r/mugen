# Recompor o curl de mangá ao girar o dispositivo

**Captured:** 2026-09-01
**Source:** specter.archive (task 4.7 do change `add-manga-page-curl`, extraída sem implementar)

## Idea
A task 4.7 do change `add-manga-page-curl` pedia: ao sair do modo paisagem, os spreads devem colapsar para o curl de página única, e a dobra em andamento deve ser resetada em vez de animar através do resize. Isso **não foi implementado** — a task foi extraída para cá no momento do archive para não registrar como concluída uma funcionalidade que não existe.

O resto do change (grupos 1-4, exceto esta task) está implementado e validado no dispositivo.

## Notes
- A orientação **é** lida, em dois pontos de `MangaCurlViewer.kt`: `applyChapters` passa `orientation = activity.resources.configuration.orientation` para `buildMangaCurlItems`, e o estado do renderer calcula `spread = joinDoublePages && !direction.isVertical && orientation == ORIENTATION_LANDSCAPE`. A lógica de agrupamento já está correta.
- O que falta é o **gatilho**: `ReaderActivity` declara `android:configChanges="orientation|screenLayout|screenSize|..."` no manifest, então o Android não recria a activity ao girar. Nada re-executa `applyChapters`, e o agrupamento em spreads nunca é recalculado. O viewer continua exibindo o layout da orientação anterior.
- Verificado que o pager legado também não tem `onConfigurationChanged` — vale investigar como ele se recompõe ao girar antes de escolher a abordagem, para o curl seguir o mesmo caminho em vez de inventar outro.
- `applyChapters` já aceita `keepPage`, adicionado na task 4.6 justamente para um rebuild no meio do capítulo resolver de volta para a página que está sendo lida. É o mecanismo natural para reaproveitar aqui.
- Sobre resetar a dobra: já existe `awaitingIdleChapters`, que adia um re-list enquanto uma dobra está em andamento. Girar durante uma dobra provavelmente deve cancelar (`ExternalFold.cancel()`) em vez de adiar, já que a geometria da superfície muda por baixo do gesto.
