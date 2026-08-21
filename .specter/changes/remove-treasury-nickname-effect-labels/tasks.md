# Tasks

## 1. Add string resources and swap hardcoded labels
- [ ] 1.1 Add `aurora_nickname_effect_aurora_crown`, `_glitch_rune`, `_cipher`, `_trinity_prism`, `_shadow_crown`, `_rank_sigils` to `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` (English values, no "(Treasury)" suffix — e.g. "Aurora Crown", "Glitch Rune", "Cipher Sigil", "Trinity Prism", "Shadow Crown", "Rank Sigils")
- [ ] 1.2 Add the same 6 keys to `i18n-aniyomi/src/commonMain/moko-resources/pt-rBR/strings.xml` with Portuguese values dropping the parenthetical (e.g. "Coroa da Aurora", "Runa Glitch", "Sigilo Cifrado", "Prisma da Trindade", "Coroa Sombria", "Sigilos de Rank" — adjust for natural pt-BR phrasing)
- [ ] 1.3 Replace the 6 hardcoded literals in `NicknameEffectPreset.label()` (`app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeHubTab.kt:1298-1304`) with `stringResource(AYMR.strings.aurora_nickname_effect_*)` calls matching the new keys
- [ ] 1.4 Manually verify: open Home > change apelido > Efeito picker in pt-BR and confirm all 17 options show Portuguese labels with no "(Treasury)" text
