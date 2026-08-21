# Validar seleção de texto no modo BOOK

**Captured:** 2026-08-19
**Source:** specter.step (verificação manual das tasks 5.6/5.7 do change `improve-novel-reader-behavior-settings`)

## Idea
A task 5.7 do change `improve-novel-reader-behavior-settings` (paridade de seleção de texto entre modo página e modo scroll/BOOK) pede verificação manual da seleção de texto no modo BOOK. A implementação de código está feita — os mesmos pontos de wiring usados em `NovelReaderContentHost.kt` (coordinator compartilhado, `onSelectionGestureActiveChanged`, `userScrollEnabled`) foram replicados em `NovelBookContentHost.kt` — mas não foi possível testar ao vivo no dispositivo durante a verificação da task 5.7: a única forma de entrar em modo BOOK é "Criar livro (experimental)" na página de detalhes da novel, que compila TODOS os capítulos antes de ficar navegável. Na novel testada (Overgeared, 2059 capítulos) a compilação travou em 0% por vários minutos e foi abortada.

## Notes
- Escolher uma novel com poucos capítulos (ou aguardar a compilação terminar em background) antes de tentar de novo.
- Validar os mesmos cenários já confirmados no modo scroll comum: long-press sem disparar scroll indevido, drag de handles cruzando parágrafos, ações "Selecionar frase"/"Selecionar parágrafo", e que o scroll normal (sem seleção ativa) continue funcionando bem — incluindo drags rápidos, lentos, e com aceleração/desaceleração no meio do gesto (ver achados da própria task 5.6, que expôs um bug real de arbitração de gesto sob esses padrões).
- Se ao testar surgir o mesmo tipo de bug encontrado no modo scroll (fix aplicado depois da verificação inicial), aplicar a mesma correção a `NovelBookContentHost.kt`/`NovelPageReaderPageContent.kt` caso ainda não tenha sido generalizada.
