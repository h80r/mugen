# Changelog

All notable changes to this project will be documented in this file.

The format is a modified version of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
- `Added` - for new features.
- `Changed ` - for changes in existing functionality.
- `Improved` - for enhancement or optimization in existing functionality.
- `Removed` - for now removed features.
- `Fixed` - for any bug fixes.
- `Other` - for technical stuff.

## Unreleased

## [v0.72.81] - 2026-09-01

### Adicionado

- Leitor de curvar página (page curl) para mangá, com animação de virada de página.
- Modo de leitura em duas páginas no formato paisagem para o leitor de novels.
- Salvamento e navegação de citações de novels, com toque para pular direto ao trecho.
- Central de texto selecionado no leitor de novels, reunindo tradução, dicionário e outras ações de seleção.
- Configuração de suporte a assinatura de release local para builds assinadas fora da CI.

### Alterado

- Identidade do app renomeada para **Mugen** (novo pacote `dev.h80r.mugen` e novo esquema de deep link).
- Seção "Mais" reorganizada, com remoção da interface antiga que não era mais usada.
- Tradução para português do Brasil completada em 100%.

### Corrigido

- Diversas correções no leitor de mangá em modo de página dupla, incluindo pulo de página em deslize rápido.
- A aba de curvar página agora mostra a página vizinha real.

### Removido

- Sistema de conquistas removido por completo (banco de dados, telas e eventos).
- Tela do Tesouro e seus atalhos removidos.
- Easter eggs "Aurora Heart", "Lattice Resonance" e "Void Broadcast" removidos; cosméticos de recompensa passam a vir desbloqueados por padrão.

## [v0.60] - 2026-08-10

### Adicionado

- Modo "Livro" do leitor de novels: leitura contínua com paginação e rolagem, progresso preciso, cache de seções em disco, TTS acompanhando o texto e exportação para FB2.
- Fluxo nativo de livro pré-compilado para renderização instantânea de novels locais.
- Suporte a arquivos locais em PDF e FB2 para fontes de mangá e novel.
- Importação de backups do LNReader (formato zip 2.x) e compatibilidade de backup com apps irmãos.
- Contagem regressiva do próximo episódio na tela de episódios (Aurora).
- Regras de substituição de texto no estilo Legado no leitor de novels.
- Temas Tokyo Night (Moon e Day) e paletas de tema exclusivas.
- Filtro de idioma da fonte na biblioteca e na busca global.
- Prefetch de imagens de capítulo em segundo plano e ações de contexto de imagem (Aurora) no leitor.
- Configuração de densidade da lista de episódios.
- Estimativa de tamanho de download, tempo restante e detalhes da fila em duas linhas.
- Auto-atualização opcional para extensões privadas.

### Alterado

- Leitor de novels reescrito internamente em controladores dedicados, com transição de capítulo mais fluida e sem piscadas.
- Runtime JS de novels migrado de J2V8 para QuickJS, com melhor compatibilidade de plugins.
- Compressão Zstd habilitada nas requisições de rede.
- Cartões da biblioteca, busca e ordenação passam a usar o título de exibição.
- Sincronização de capítulos e fontes unificada em uma única chamada de atualização.

### Melhorado

- Desempenho da biblioteca com coleções grandes e menos recomposições.
- Primeiro quadro das telas de anime, mangá e novel mais rápido, com caches de TTL e resolução instantânea de prévia.
- Leitor webtoon otimizado: menos processamento na thread de UI e menos alocações.

### Corrigido

- Tela preta na transição de fim de capítulo em tela cheia.
- Persistência do progresso da página 1 e piscada na troca de capítulo.
- Toques em áreas vazias da página e seleção flutuante no leitor.
- Capa do usuário não era mais sobrescrita por miniatura gerada em fontes locais.
- Duplicatas na biblioteca de novels causadas pela junção de categorias.
- Crash no carrossel de títulos quando a activity era interrompida.
- Vários ajustes de navegação por capítulo no modo livro e em transições contínuas.

## [v0.59] - 2026-08-04

### Adicionado

- Dicionário offline StarDict para o leitor de novels, com histórico de consultas, revisão em flashcards e favoritos.
- Assistente de importação de biblioteca do AniList.
- Importação do Shikimori estendida a mangá e ranobe; revisão manual e vínculo com trackers.
- Prévia de episódios de anime enriquecida por metadados externos (TMDB, Kitsu, AniList, Jikan, Simkl, Shikimori).
- Suporte a arquivos locais em PDF e FB2 para mangá e novel.
- Campo livre de memo/anotação em todos os tipos de entrada e em capítulos/episódios.
- Importação de metadados a partir do tracker na tela de editar metadados.
- Acessibilidade de movimento reduzido na Aurora.

### Alterado

- Telas de editar metadados, notas, download de novels e exportação de EPUB redesenhadas como painéis Aurora de vidro fosco.
- Configurações do leitor e da biblioteca migradas para componentes Aurora com desfoque de fundo.
- Assistentes de importação (AniList, Anixart, Shikimori) unificados no design Aurora.
- Aba de capítulos passa a usar cache TTL e resolução instantânea de prévia.

### Melhorado

- Carregamento da tela de anime e do runtime de plugins de novel, com menos travamentos na thread principal.
- Capas persistidas no cache de disco do Coil e recarregadas ao voltar a rede.

### Corrigido

- Travamentos por contenção de mutex de plugin e verificação de capítulo desatualizada.
- Estouro de rótulo em abas em cápsula para idiomas longos.
- Bug de superfície escura no tema claro do Aurora Prime.
- Crash de DI nulo em utilitários de log de crash.

## [v0.58] - 2026-07-20

### Adicionado

- Pager horizontal multi-fonte para as abas de navegação.
- Backend de instalação de extensões via Dhizuku, além de isolamento de diretórios privados por tipo de mídia.
- Seleção múltipla de gênero e busca entre mídias nas telas de entrada Aurora e na navegação de fontes.
- Persistência da saudação e da sequência (streak) entre reinícios a frio.
- Sistema de política de modo anônimo, com auto-incógnito ciente de conteúdo NSFW.
- Localização completa em árabe (ar) para leitor de novels, sistema de séries, player e configurações.

### Alterado

- Repositórios de extensão substituídos por "lojas" de extensão, com migração para todos os usuários.
- Vários backports de correções do Mihon (downloads, leitor, armazenamento SAF, webtoon).

### Melhorado

- Lançamento a frio mais rápido: trabalho não crítico adiado para depois do primeiro quadro.
- Otimizações de carregamento da tela de novel e arredondamento de versão de lib de extensão.

### Corrigido

- Crash de AGSL RuntimeShader em aparelhos anteriores à API 33.
- Restauração de backups antigos do Aniyomi/Tadami com payload de anime/novel co-codificado.
- Strings de idioma erradas na instalação de extensões multi-idioma.
- 12 strings corrompidas em UTF-8 na tradução russa.
- Congelamento do diálogo do MangaUpdates no Android 16.

## [v0.57] - 2026-07-12

### Adicionado

- Suporte a streaming de torrent baseado em TorrServer.
- Suporte a extensões de novel nativas em Kotlin (APK), com fluxo de confiança/desinstalação para extensões não confiáveis.
- Importação via CSV do Anixart e importação do Shikimori.
- Sistema de tutorial para iniciantes com marcações guiadas (coach marks).
- Documentos legais dentro do app e grupo de suporte no Telegram.
- Tempo estimado de leitura restante no indicador de página.
- Filtro de binarização adaptativa e modo de ajuste inteligente (auto-corte de margens) para webtoon em telas e-ink.
- Junção de páginas duplas no leitor de mangá e pré-carregamento dinâmico de páginas.
- Consulta a dicionário online e melhorias na seleção de texto.
- Sistema de camadas cinematográficas Shinkai aplicado a todas as telas.

### Alterado

- Onboarding sem seleção de repositórios pré-definidos; etapa de aviso legal adicionada.
- Instalador de extensões APK unificado com fallback OkHttp e múltiplos backends.
- Animação de progresso de download trocada por um gato em pixel art.

### Corrigido

- Fallback para formato de tag alternativo ao buscar a release do GitHub.
- Correções de mapeamento de campos em backups do Mihon.
- Crash "coletar duas vezes de pageEventFlow" na navegação.
- Perda de estado ao deslizar entre telas de navegação de fonte.

## [v0.54] - 2026-06-27

### Adicionado

- Fluxo de auto-scroll do leitor de novels com pausa configurável no fim do capítulo e atraso adaptativo.
- Overhaul da exportação de EPUB, com extração de recursos e metadados EPUB 3.0.
- Tradução de legendas no player de vídeo, com mascaramento de tags e glossário.
- Assistente de migração no estilo Komikku para mangá.
- Fonte "OmniSource" para adicionar títulos manualmente via busca global.
- Notas editáveis pelo usuário para entradas de anime, mangá e novel.
- Botões da barra inferior do leitor de mangá personalizáveis, com ordem por arrastar e soltar.
- Sistema de conquistas com categoria de novels, níveis de raridade e desbloqueáveis.
- Suporte a extensões de novel nativas em Kotlin (`ConfigurableNovelSource`).
- Backup em nuvem via pasta SAF e sincronização com Google Drive.
- Recurso de sugestões de títulos com provedores AniList, MAL e MangaUpdates.
- Fonte local de novels para navegar EPUBs de uma pasta do dispositivo.
- Rastreadores Trakt e TMDB para anime.

### Alterado

- Ordenação padrão de capítulos passa a ser "fonte crescente" para mangá e novels.
- Agrupamento virtual dinâmico da biblioteca para anime, mangá e novels, com frequências de auto-atualização separadas por tipo.
- Interceptor do Cloudflare otimizado, preservando o cookie de liberação resolvido pelo usuário.
- Tela de downloads renomeada e telas de configuração restiladas no design Aurora.

### Melhorado

- Desempenho de banco de dados com índices e gatilhos condicionais.
- Carregamento de capas e renderização da navegação de catálogo.
- Coleta de estado ciente do ciclo de vida em toda a interface.

### Corrigido

- Bloqueios da thread principal na migração, movida para o dispatcher de IO.
- Vazamento de coroutine e índice de cache de tradução desatualizado.
- Crash de OOM na fila de download de novels.
- Crash "Key '25'" em grades de fonte de mangá/anime.
- Progresso de leitura que retrocedia ao reabrir o capítulo.
- Persistência de progresso no modo anônimo.

## [v0.45] - 2026-05-21

### Adicionado

- Fluxo de configuração inicial (onboarding) com repositórios de fontes.
- Sistema do Tesouro com recompensas exclusivas e efeitos visuais.
- Efeitos premium de perfil e molduras de avatar.
- Feed v2/v3 com SQLDelight, reordenação, buscas salvas e suporte a backup, estendido a anime e novel.
- Provedores de tradução via IA adicionais (Mistral, NVIDIA, Ollama Cloud), com fila de tradução em lote e cancelamento por item.
- Rastreamento de novels com sincronização automática (NovelUpdates e NovelList).
- Seleção de texto no leitor independente da tradução.
- Cache de disco persistente para capas (LRU de 128 MB).
- Reprodução de auto-scroll que lembra a velocidade entre sessões.
- Botões da barra inferior do leitor de mangá personalizáveis.

### Alterado

- Aparência da barra de navegação inferior no estilo Aurora (pílula flutuante), com opções de estilo.
- Design de vidro (glassmorphism) aplicado a cartões de atualização e recentes no tema claro.
- Importação de EPUB reescrita para copiar arquivos para o diretório da fonte local.

### Melhorado

- Carregamento de capas e navegação de catálogo.
- Recomposições da biblioteca reduzidas por memoização.
- Migrações movidas para fora da thread principal.

### Corrigido

- Colisões de cache de imagem por incluir o ID da entrada na chave de cache.
- Perda de submissões de formulário POST no WebView (corrige login no ranobelib).
- `IndexOutOfBoundsException` na seleção de intervalo da biblioteca.
- Crash por chave duplicada ao listar extensões de múltiplos repositórios.
- Recuperação de tarefas de download travadas no estado "baixando".

## [v0.44] - 2026-05-15

### Adicionado

- Fila de tradução em lote por IA, com ação de tradução na barra de seleção.
- Assistente de migração de mangá no estilo Komikku.
- Fonte "OmniSource" (beta) para adicionar títulos manualmente via busca global.
- Notas editáveis pelo usuário para anime, mangá e novel.
- Suporte a Intl polyfill e mais métodos do Cheerio no runtime J2V8 (compatibilidade de plugins).
- Opção de auto-atualização "Ao abrir o app".
- Diálogo para renomear repositórios de extensão e nome de exibição personalizado.

### Alterado

- Barra de abas de categoria da biblioteca centraliza a categoria selecionada com animação.
- Cartões do leitor e telas de configuração restilados (superfícies flutuantes no tema claro).

### Corrigido

- Recorte de texto de página no leitor de novels e paginação levando em conta o padding do glifo.
- Progresso de leitura passa a usar contagem de lidos em vez de posição na lista.
- Crash por chave duplicada em grades e listas da biblioteca.
- Retenção do progresso no modo anônimo.
- Cache de disco de download de novels e sincronização adiada com o sistema de arquivos.

## [v0.42] - 2026-05-06

### Adicionado

- Layout vertical opcional de fontes fixadas na navegação.
- Seletor de repositório de extensões com avisos.
- Migração de anime nos menus Aurora.
- Nomes de exibição personalizados para repositórios de extensão, com migração.
- Métodos de mutação do Cheerio (before/after/append/prepend/empty) e módulo `@libs/aes` no runtime.
- Estilo de navegação inferior "Aurora" com pílula flutuante.

### Alterado

- Confiabilidade do bypass do Cloudflare melhorada, com User-Agent consistente com o WebView (Chrome mobile).
- Leitor adota o `EpubReader` do tsundoku, com divisão de capítulos por TOC e suporte a múltiplos formatos.
- Capítulos de novels ordenados de forma crescente para leitura natural.
- Arquivo de log de crash renomeado de "aniyomi" para o nome do fork.

### Corrigido

- Crash `ForegroundServiceStartNotAllowedException` ao instalar extensão em segundo plano.
- Contraste dos ícones da barra de status nos temas Aurora.
- Compatibilidade de plugins JS de novel (escopo global, polling assíncrono, sanitizador, cabeçalhos de fetch).
- Login no NovelList (URL de entrada e domínio do cookie).

## [v0.41] - 2026-05-01

### Adicionado

- Auto-scroll do leitor com cooldown ao toque, FAB flutuante, aceleração e painel de configurações redesenhado.
- Roadmap de personalização do player (configuração de layout, gating de chrome, correspondência de legendas).
- Provedor Ollama Cloud para tradução, com rótulo "(Free)" para modelos de nível gratuito.
- Resolver de sincronização de progresso compartilhado entre trackers.

### Corrigido

- Slider de página pulando para o capítulo anterior.
- `IndexOutOfBoundsException` em `toggleRangeSelection` quando a lista muda.
- Extensão não persistindo após instalação em alguns aparelhos.
- Regressão de progresso do leitor de novels (progresso salvo retrocedendo).
- Estouro de texto de aba com reticências em abas roláveis.

## [v0.40] - 2026-04-28

### Adicionado

- Sistema de cancelamento de tradução com tratamento de erro por item e melhorias de notificação.
- Infraestrutura de rastreamento de novels (banco, DI, implementações de tracker, UI) com auto-sync via NovelUpdates e NovelList.
- Toque longo para abrir o diálogo de categoria nas telas de título.
- Indicador de rotação contínua para atualizações de biblioteca ativas.
- Suporte a reasoning e novos provedores nos serviços de tradução do leitor.
- Visualização de séries na biblioteca com pilha de 5 capas e rótulos destacados.

### Corrigido

- Linhas órfãs e recorte de texto na base da página no modo paginado.
- Crash de constraint UNIQUE ao re-enfileirar capítulos para tradução por IA.
- Ciclo de vida da fila de tradução (uso de KEEP em vez de REPLACE, ordem de cancelamento).
- `localStorage` retornando valores brutos para evitar `JSON.parse` em objetos.
- Orientação restaurada após sair do player, em vez de forçar retrato.
- Ícones da barra de status mantidos em branco na shell principal.

## [v0.39] - 2026-04-24

### Adicionado

- Categorias e capa de série, com suporte a estado de série excluída.
- Provedor de tradução Mistral no leitor de novels.
- Fonte local de novels (`LocalNovelSource`) para navegar EPUBs de uma pasta.
- Divisão de EPUB multi-capítulo em capítulos individuais via spine do OPF.
- Modo imersivo da biblioteca Aurora e cobertura ampliada de feedback tátil.
- Tradução opcional de títulos e rótulos de status via `MR.strings`.
- Esqueleto de sincronização via Google Drive.

### Alterado

- Diálogo de exportação de EPUB substituído por uma bottom sheet moderna.
- Configurações do leitor de IA de novels redesenhadas.
- Feedback tátil movido para "Avançado" e opções de movimento para "Exibição".
- Ramos de scanlator habilitados por padrão.

### Corrigido

- Sombra de borda de página respeitada no leitor de novels.
- Listas de páginas de capítulo mantidas intactas; cache ignorado para plugins específicos.
- Capas de novels que não carregavam na biblioteca Aurora (cabeçalho Referer).
- Descarte por arrasto de bottom sheets adaptativas.
- Salvamento de histórico do leitor de novels respeitando o modo anônimo.

## [v0.37] - 2026-04-14

### Adicionado

- Tela de ajuda interna com links de suporte.
- Fila persistente de tradução (tabela dedicada, job em segundo plano, notificações de progresso e ação de cancelar).
- Configurações de idioma do tradutor por IA e ação "marcar capítulos anteriores".
- Opções de abrir pasta e excluir para traduções baixadas.
- Ratings e ações de capítulo de novels.

### Alterado

- Botão de traduzir passa a enfileirar a tradução em vez de abrir o leitor.
- Suporte a tradução via ML Kit removido em favor do tradutor por IA.
- Interface do Gemini localizada; strings russas fixas migradas para recursos MOKO.
- Runtime JS de novels e busca de capas refatorados.

### Corrigido

- Compatibilidade de nome de elemento com plugins Cheerio padrão.
- `URLEncoder.encode` para compatibilidade com Android 11.
- Lentidão ao abrir a tela de downloads.
- Bottom sheets adaptativas que não fechavam de forma confiável.

## [v0.36] - 2026-04-11

### Adicionado

- Importação de novels EPUB como entradas locais, com pontos de entrada dedicados.
- Ações de rolagem e menu no TTS, preservando a fonte de fala traduzida.

### Alterado

- Personalização Aurora passa a vir habilitada por padrão.
- Saudações Aurora traduzidas para inglês; nickname padrão localizado.

### Corrigido

- Compatibilidade de plugins do LNReader.
- Limpeza de downloads traduzidos e evento de cache de download duplicado.

## [v0.30 - v0.35] - 2026-03-15 a 2026-04-11

### Alterado

- Ciclo intensivo de desenvolvimento do suporte a novels (ranobe): iterações sucessivas de leitor, fontes, runtime de plugins e importação de EPUB, entregues como uma série de PRs "Ranobe novel" (versões 0.30 a 0.35). As mudanças relevantes ao usuário deste período foram consolidadas nas versões seguintes.

## [v0.29] - 2026-03-11

### Added

- Added a novel reader custom font catalog with support for built-in fonts, local private fonts, and user-imported fonts.
- Added collapsible `Local` and `My fonts` sections in the novel reader settings, including import and removal actions for user fonts.

### Changed

- Moved local private novel reader fonts to the ignored `app/src/main/assets/local/fonts/` path so public builds stay clean while local builds can preload extra fonts.
- Improved novel reader WebView font handling so file-backed fonts resolve through the same selection pipeline as built-in fonts.

### Added

- Added a description for the horizontal seek gesture setting ([@kenkoro](https://github.com/kenkoro)) ([#2224](https://github.com/aniyomiorg/aniyomi/pull/2224))

### Fixed

- Swapped keyEvent listeners for left and right keyboard arrow keys as they were swapped in the code causing the opposite of the desired behavior([@alphastark](https://github.com/alphastark)) ([#2219](https://github.com/aniyomiorg/aniyomi/pull/2219))
- Fix some malformed translated strings that made the player quit when Aniskip was enabled ([@686udjie](https://github.com/686udjie)) ([#2217](https://github.com/aniyomiorg/aniyomi/pull/2217))

## [v0.18.1.2] - 2025-10-28
### Fixed

- Fix Hosters feature detection (again) ([@hollowshiroyuki](https://github.com/hollowshiroyuki)) ([#2216](https://github.com/aniyomiorg/aniyomi/pull/2216))

## [v0.18.1.1] - 2025-10-26
### Fixed

- Fix source Seasons/Hosters feature detection ([@hollowshiroyuki](https://github.com/hollowshiroyuki)) ([#2195](https://github.com/aniyomiorg/aniyomi/pull/2195))
- Fix shared download cache messing up downloaded episodes detection ([@choppeh](https://github.com/choppeh)) ([#2184](https://github.com/aniyomiorg/aniyomi/pull/2184))
- Fix Shikimori anime tracking ([@danya140](https://github.com/danya140)) ([#2205](https://github.com/aniyomiorg/aniyomi/pull/2205))

### Improved

- Make volume gesture the same sensitivity as brightness ([@jmir1](https://github.com/jmir1))

## [v0.18.1.0] - 2025-10-02
### Fixed

- Fix list view resetting scroll upon exiting child ([@quickdesh](https://github.com/quickdesh)) ([#1982](https://github.com/aniyomiorg/aniyomi/pull/1982))
- Fix episode number parsing ([@Secozzi](https://github.com/Secozzi)) ([#2096](https://github.com/aniyomiorg/aniyomi/pull/2096))
- Fix tracking menu not opening on add to library ([@Secozzi](https://github.com/Secozzi)) ([#2098](https://github.com/aniyomiorg/aniyomi/pull/2098))
- Fix stop/continue anime download button ([@Secozzi](https://github.com/Secozzi)) ([#2099](https://github.com/aniyomiorg/aniyomi/pull/2099))
- Fix creating/restoring backups between mihon and aniyomi ([@Secozzi](https://github.com/Secozzi)) ([#2117](https://github.com/aniyomiorg/aniyomi/pull/2117))

### Added

- Add support for new parameters from ext lib 16 ([@quickdesh](https://github.com/quickdesh)) ([#1982](https://github.com/aniyomiorg/aniyomi/pull/1982))
- Add player settings to the main settings screen ([@jmir1](https://github.com/jmir1)) ([#2081](https://github.com/aniyomiorg/aniyomi/pull/2081))
- Add seasons support ([@Secozzi](https://github.com/Secozzi)) ([#2095](https://github.com/aniyomiorg/aniyomi/pull/2095))

## [v0.18.0.1] - 2025-07-06
### Fixed

- Fix crash on migration ([@Secozzi](https://github.com/Secozzi)) ([#2079](https://github.com/aniyomiorg/aniyomi/pull/2079))

## [v0.18.0.0] - 2025-07-05
### Added

- Set mpv's media-title property ([@Secozzi](https://github.com/Secozzi)) ([#1672](https://github.com/aniyomiorg/aniyomi/pull/1672))
- Add mpvKt to external players ([@Secozzi](https://github.com/Secozzi)) ([#1674](https://github.com/aniyomiorg/aniyomi/pull/1674))
- Add video filters ([@abdallahmehiz](https://github.com/abdallahmehiz)) ([#1698](https://github.com/aniyomiorg/aniyomi/pull/1698))
- Show hours and minutes in relative time strings ([@jmir1](https://github.com/jmir1)) ([`1f3be7b`](https://github.com/aniyomiorg/aniyomi/commit/1f3be7b523136039b3b60213f2cee7959a9367d7))
  - Fix some issues with relative date calculations ([@jmir1](https://github.com/jmir1)) ([`03e1ecd`](https://github.com/aniyomiorg/aniyomi/commit/03e1ecd75edd2ea15dc8732ffeab32c6af26b202))
- Add better auto sub select ([@Secozzi](https://github.com/Secozzi)) ([#1706](https://github.com/aniyomiorg/aniyomi/pull/1706))
- Copy the file location when using ext downloader ([@quickdesh](https://github.com/quickdesh)) ([#1758](https://github.com/aniyomiorg/aniyomi/pull/1758))
- Replace player with mpvKt ([@Secozzi](https://github.com/Secozzi)) ([#1834](https://github.com/aniyomiorg/aniyomi/pull/1834), [#1855](https://github.com/aniyomiorg/aniyomi/pull/1855), [#1859](https://github.com/aniyomiorg/aniyomi/pull/1859), [#1860](https://github.com/aniyomiorg/aniyomi/pull/1860))
  - Move player preferences to separate section ([@Secozzi](https://github.com/Secozzi)) ([#1819](https://github.com/aniyomiorg/aniyomi/pull/1819))
- Implement video hosters ([@Secozzi](https://github.com/Secozzi)) ([#1892](https://github.com/aniyomiorg/aniyomi/pull/1892))
- Add size slider for the "List Display" Mode ([@MavikBow](https://github.com/MavikBow)) ([#1906](https://github.com/aniyomiorg/aniyomi/pull/1906))
  - Make the default list a set size and make browse list scale ([@MavikBow](https://github.com/MavikBow)) ([#1914](https://github.com/aniyomiorg/aniyomi/pull/1914))
- Allow negative brightness values (dimming) ([@jmir1](https://github.com/jmir1)) ([#1915](https://github.com/aniyomiorg/aniyomi/pull/1915))
- Add new lua functions for custom buttons ([@Secozzi](https://github.com/Secozzi)) ([#1980](https://github.com/aniyomiorg/aniyomi/pull/1980))
- Use timestamps provided by extensions ([@Secozzi](https://github.com/Secozzi)) ([#1983](https://github.com/aniyomiorg/aniyomi/pull/1983))
- Add titles to player sheets + consistency with More sheet ([@quickdesh](https://github.com/quickdesh)) ([#2015](https://github.com/aniyomiorg/aniyomi/pull/2015))
- Add script & script-opts editor to player settings ([@Secozzi](https://github.com/Secozzi)) ([#2019](https://github.com/aniyomiorg/aniyomi/pull/2019))

### Improved

- Show "Now" instead of "0 minutes ago" ([@Secozzi](https://github.com/Secozzi)) ([#1715](https://github.com/aniyomiorg/aniyomi/pull/1715))
- Add headers when using 1dm as external player ([@Secozzi](https://github.com/Secozzi)) ([#2032](https://github.com/aniyomiorg/aniyomi/pull/2032))

### Fixed

- Fix enhanced tracking for jellyfin ([@Secozzi](https://github.com/Secozzi)) ([#1656](https://github.com/aniyomiorg/aniyomi/pull/1656), [#1658](https://github.com/aniyomiorg/aniyomi/pull/1658))
- Use different status strings for anime trackers ([@jmir1](https://github.com/jmir1)) ([`74b32a3`](https://github.com/aniyomiorg/aniyomi/commit/74b32a3a0b323ed2f6f7929e131dcb4901e7bf9b))
- Fix Shikimori tracking for anime ([@jmir1](https://github.com/jmir1)) ([`58817c7`](https://github.com/aniyomiorg/aniyomi/commit/58817c724e2808072ff273329cee261d12084927))
- Group updates by date and not time ([@jmir1](https://github.com/jmir1)) ([`c83ebf3`](https://github.com/aniyomiorg/aniyomi/commit/c83ebf322f48d41ca1ad0105262160ecb7cde991))
- Fix airing time not showing ([@Secozzi](https://github.com/Secozzi)) ([#1720](https://github.com/aniyomiorg/aniyomi/pull/1720))
- Don't invalidate anime downloads on startup ([@Secozzi](https://github.com/Secozzi)) ([#1753](https://github.com/aniyomiorg/aniyomi/pull/1753))
- Fix hidden categories getting reset after delete/reorder ([@cuong-tran](https://github.com/cuong-tran)) ([#1780](https://github.com/aniyomiorg/aniyomi/pull/1780))
- Fix episode progress not being saved and duplicate tracks ([@perokhe](https://github.com/perokhe)) ([#1784](https://github.com/aniyomiorg/aniyomi/pull/1784), [#1785](https://github.com/aniyomiorg/aniyomi/pull/1785))
- Fix subtitle select not matching two letter language codes ([@Secozzi](https://github.com/Secozzi)) ([#1805](https://github.com/aniyomiorg/aniyomi/pull/1805))
- Fix potential intent extra npe ([@quickdesh](https://github.com/quickdesh)) ([#1816](https://github.com/aniyomiorg/aniyomi/pull/1816))
- Fix history date header duplication ([@quickdesh](https://github.com/quickdesh)) ([#1817](https://github.com/aniyomiorg/aniyomi/pull/1817))
- Fix migrations not getting context correctly ([@Secozzi](https://github.com/Secozzi)) ([#1820](https://github.com/aniyomiorg/aniyomi/pull/1820))
- Fix various issues due to replacing the player with mpvKt
  - Fix gesture seeking not seeking to start and end ([@perokhe](https://github.com/perokhe)) ([#1865](https://github.com/aniyomiorg/aniyomi/pull/1865))
  - Fix crash when opening player settings in tablet ui ([@Secozzi](https://github.com/Secozzi)) ([#1868](https://github.com/aniyomiorg/aniyomi/pull/1868))
  - Fix episode list in player not respecting filters & crash when exiting while stuff is loading ([@Secozzi](https://github.com/Secozzi)) ([#1869](https://github.com/aniyomiorg/aniyomi/pull/1869))
  - Fix episode being marked as seen at start ([@perokhe](https://github.com/perokhe)) ([#1871](https://github.com/aniyomiorg/aniyomi/pull/1871))
  - Fix player not being paused when loading tracks after changing quality ([@Secozzi](https://github.com/Secozzi)) ([#1878](https://github.com/aniyomiorg/aniyomi/pull/1878))
  - Fix lag when toggling player ui ([@Secozzi](https://github.com/Secozzi)) ([#1887](https://github.com/aniyomiorg/aniyomi/pull/1887))
  - Fix audio selection not working on external audio tracks ([@Secozzi](https://github.com/Secozzi)) ([#1901](https://github.com/aniyomiorg/aniyomi/pull/1901))
  - Reset "hide player controls time" when pressing custom button ([@Secozzi](https://github.com/Secozzi)) ([#1902](https://github.com/aniyomiorg/aniyomi/pull/1902))
  - Don't unpause on share and save ([@Secozzi](https://github.com/Secozzi)) ([#1905](https://github.com/aniyomiorg/aniyomi/pull/1905))
  - Fix player pausing with gesture seek ([@perokhe](https://github.com/perokhe)) ([#1916](https://github.com/aniyomiorg/aniyomi/pull/1916))
  - Fix potential npe issues with mpv-lib ([@Secozzi](https://github.com/Secozzi)) ([#1921](https://github.com/aniyomiorg/aniyomi/pull/1921))
  - Dismiss chapter sheet on chapter select ([@Secozzi](https://github.com/Secozzi)) ([#1976](https://github.com/aniyomiorg/aniyomi/pull/1976))
  - Fix some issues caused by [`10e28cc`](https://github.com/aniyomiorg/aniyomi/commit/10e28cc4092758cf38d27cc14aadf539698738f2) ([@Secozzi](https://github.com/Secozzi)) ([#1981](https://github.com/aniyomiorg/aniyomi/pull/1981))
  - Fix npe issue caused in player controls ([@Secozzi](https://github.com/Secozzi)) ([#1986](https://github.com/aniyomiorg/aniyomi/pull/1986))
- Replace some manga strings with respective anime strings ([@perokhe](https://github.com/perokhe)) ([#1864](https://github.com/aniyomiorg/aniyomi/pull/1864))
- Open correct tab from extension update notifications ([@jmir1](https://github.com/jmir1)) ([`161471d`](https://github.com/aniyomiorg/aniyomi/commit/161471d94a2350c0c983eeeccd3b7ac0dc66d429))
- Fix sub-auto not loading all external subtitle files ([@perokhe](https://github.com/perokhe)) ([#1866](https://github.com/aniyomiorg/aniyomi/pull/1866))
- Fix `ALSearchItem.format` nullability ([@Secozzi](https://github.com/Secozzi)) ([#1910](https://github.com/aniyomiorg/aniyomi/pull/1910))
- Don't format mpv preferences ([@Secozzi](https://github.com/Secozzi)) ([#1939](https://github.com/aniyomiorg/aniyomi/pull/1939))
- Prevent crash on app death when watching in external player ([@Secozzi](https://github.com/Secozzi)) ([#1945](https://github.com/aniyomiorg/aniyomi/pull/1945))
- Don't run unnecessary stuff when exiting the player ([@Secozzi](https://github.com/Secozzi)) ([#1961](https://github.com/aniyomiorg/aniyomi/pull/1961))
- Fix some downloader issues ([@Secozzi](https://github.com/Secozzi)) ([#1964](https://github.com/aniyomiorg/aniyomi/pull/1964))
  - Fix downloader not working for certain types of tracks & duration sometimes not being logged ([@Secozzi](https://github.com/Secozzi)) ([#2001](https://github.com/aniyomiorg/aniyomi/pull/2001))
- Fix some issues with intro skip length ([@jmir1](https://github.com/jmir1)) ([`72cac57`](https://github.com/aniyomiorg/aniyomi/commit/72cac57d8e66366cbc0f3106eb351c82250c460b), [`25dd3ea`](https://github.com/aniyomiorg/aniyomi/commit/25dd3ea69fb217de7b0485c29e4a9b970737fd45))
- Force clipboard to use UI thread when copying path for external players ([@quickdesh](https://github.com/quickdesh)) ([#1994](https://github.com/aniyomiorg/aniyomi/pull/1994))
- Use application directory for storing files used by mpv ([@Secozzi](https://github.com/Secozzi)) ([#1995](https://github.com/aniyomiorg/aniyomi/pull/1995))
- Update backup warning string (follow Mihon) ([@cuong-tran](https://github.com/cuong-tran)) ([#2012](https://github.com/aniyomiorg/aniyomi/pull/2012))
- Fix issues with episode deletion & more ([@quickdesh](https://github.com/quickdesh)) ([#2017](https://github.com/aniyomiorg/aniyomi/pull/2017))
- Fix vertical slider width issues and shift boost volume value to slider ([@quickdesh](https://github.com/quickdesh)) ([#2018](https://github.com/aniyomiorg/aniyomi/pull/2018))
- Fix MyAnimeList login ([@choppeh](https://github.com/choppeh)) ([#2035](https://github.com/aniyomiorg/aniyomi/pull/2035))
- Call sort methods for videos and hosters ([@cuong-tran](https://github.com/cuong-tran)) ([#2058](https://github.com/aniyomiorg/aniyomi/pull/2058))
- Invalidate preferred languages in settings ([@Secozzi](https://github.com/Secozzi)) ([#2075](https://github.com/aniyomiorg/aniyomi/pull/2075))
- Fix crash when using sort by airing time ([@quickdesh](https://github.com/quickdesh)) ([#2076](https://github.com/aniyomiorg/aniyomi/pull/2076))

### Other

- Merge from mihon until 0.16.5 ([@Secozzi](https://github.com/Secozzi)) ([#1663](https://github.com/aniyomiorg/aniyomi/pull/1663))
  - Merge until latest mihon commits ([@Secozzi](https://github.com/Secozzi)) ([#1693](https://github.com/aniyomiorg/aniyomi/pull/1693))
  - Merge until latest mihon commits (v0.17.0) ([@Secozzi](https://github.com/Secozzi)) ([#1804](https://github.com/aniyomiorg/aniyomi/pull/1804))
  - Merge until latest mihon commits (v0.18.0) ([@Secozzi](https://github.com/Secozzi)) ([#1863](https://github.com/aniyomiorg/aniyomi/pull/1863))
- Remove ACRA crash report analytics ([@jmir1](https://github.com/jmir1)) ([`d3c6a15`](https://github.com/aniyomiorg/aniyomi/commit/d3c6a159d82ca239c10e8f5822c3b2046c5545f2), [`5ae35c8`](https://github.com/aniyomiorg/aniyomi/commit/5ae35c891b90ae927200185641240280effaf667))

## [v0.16.4.3] - 2024-07-01
### Fixed

- Fix extensions disappearing due to errors with the ClassLoader ([@jmir1](https://github.com/jmir1)) ([`959f84a`](https://github.com/aniyomiorg/aniyomi/commit/959f84ab41859f90c458c076d83d363ae086e47f))

## [v0.16.4.2] - 2024-07-01
### Fixed

- Hotfix to eliminate all proguard issues causing errors and crashes ([@jmir1](https://github.com/jmir1)) ([`a8cd723`](https://github.com/aniyomiorg/aniyomi/commit/a8cd7233dfdf26c98ff86b1871a7ac5774379b5e), [`a7644c2`](https://github.com/aniyomiorg/aniyomi/commit/a7644c268153fc0b9f10c27202591f960c6f6384), [`5045fa1`](https://github.com/aniyomiorg/aniyomi/commit/5045fa18ce5a1faa2130f1a33609e43d8453f078))

## [v0.16.4.1] - 2024-07-01
### Fixed

- Hotfix release to address errors with extensions ([@jmir1](https://github.com/jmir1)) ([`98d2528`](https://github.com/aniyomiorg/aniyomi/commit/98d252866e17beba7d9a4d094797e23c05ead6c1))

## [v0.16.4.0] - 2024-07-01
### Fixed

- Fix pip not broadcasting intent in A14+ ([@quickdesh](https://github.com/quickdesh)) ([#1603](https://github.com/aniyomiorg/aniyomi/pull/1603))
- Fix advanced player settings crash in android ≤ 10 ([@perokhe](https://github.com/perokhe)) ([#1627](https://github.com/aniyomiorg/aniyomi/pull/1627))

### Improved

- Hide the skip intro button if the skipped amount == 0 ([@abdallahmehiz](https://github.com/abdallahmehiz)) ([#1598](https://github.com/aniyomiorg/aniyomi/pull/1598))

### Other

- Merge from mihon until mihon 0.16.2 ([@Secozzi](https://github.com/Secozzi)) ([#1578](https://github.com/aniyomiorg/aniyomi/pull/1578))
  - Merge from mihon until 0.16.4 ([@Secozzi](https://github.com/Secozzi)) ([#1601](https://github.com/aniyomiorg/aniyomi/pull/1601))
