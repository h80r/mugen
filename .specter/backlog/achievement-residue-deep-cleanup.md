# Investigação futura: simplificação/renomeação do que restou do sistema de conquistas

**Captured:** 2026-08-19
**Source:** exploration (investigação de liveness solicitada durante o planejamento de `remove-achievement-residue`)

## Idea
A investigação para a change `remove-achievement-residue` confirmou que não sobrou nenhum "subsistema de conquistas" morto para remover — a remoção anterior (`remove-achievements-system`) já fez esse trabalho corretamente. O que restou (`UnlockableManager`/gate de cosméticos, `ActivityDataRepository`, `StreakAchievementChecker`, os gráficos de atividade da aba Stats) está confirmado como código vivo e funcionando corretamente. Ainda assim, parte desse código carrega nomes que não refletem mais o que ele faz (ex.: `StreakAchievementChecker` só calcula streak de leitura, não checa conquistas; o pacote `presentation/achievement` hoje hospeda componentes de gráfico de atividade/stats, não UI de conquistas). Vale uma investigação futura dedicada a decidir se compensa renomear essas peças para reduzir a confusão, e se a chave de string legada `achievements` usada nas telas de backup/restore (`CreateBackupScreen.kt`, `RestoreBackupScreen.kt`, `CloudSyncOptionsScreen.kt`, `BackupOptions.kt`, `RestoreOptions.kt` — o texto exibido já diz corretamente "Activity log", só o nome da chave é antigo) merece uma renomeação pura.

## Notes
- Não é uma tarefa de remoção — tudo listado abaixo está confirmado **vivo e correto**, é só uma questão de clareza de nomenclatura/organização:
  - `UnlockableManager` (`data/src/main/java/tachiyomi/data/achievement/UnlockableManager.kt`) — gate de desbloqueio de cosméticos, usado por `SettingsCosmeticsScreen.kt`, `HomeHubTab.kt`, seletores de tema/navbar.
  - `ActivityDataRepository`/`ActivityDataRepositoryImpl` — rastreamento de atividade de leitura/streak, alimenta a aba Stats e a saudação da Home.
  - `StreakAchievementChecker` (`data/.../handler/checkers/StreakAchievementChecker.kt`) — calcula apenas streak de dias consecutivos, nome sugere algo mais amplo.
  - Componentes em `app/src/main/java/eu/kanade/presentation/achievement/components/` (`AchievementActivityGraph.kt`, `AchievementStatsComparison.kt`, `AchievementTimeFormatter.kt`, `ActivityStreakIndicator.kt`) — reutilizados pelos cards de atividade da aba Stats real, mas o pacote/nome dos arquivos ainda referencia "achievement".
- Possíveis ações futuras a avaliar (não decidir agora, só investigar): renomear `StreakAchievementChecker` → algo como `ReadingStreakCalculator`; mover/renomear o pacote `presentation/achievement` para refletir seu uso real (stats/atividade); renomear a chave de string `achievements` nas telas de backup/restore para algo como `activity_log` (mudança cosmética, sem impacto de comportamento).
- Confirmar antes de agir: se alguma dessas classes/pacotes é referenciada por nome em testes, documentação externa, ou dados persistidos (chaves de preferência, nomes de coluna) de um jeito que tornaria a renomeação arriscada.
