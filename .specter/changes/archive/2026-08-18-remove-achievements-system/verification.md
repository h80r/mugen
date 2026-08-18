# Verification: Remove Achievements System

## Verdict: PASS

## Completeness
Every Scope item in `proposal.md` has corresponding completed work in `tasks.md`:
- Event bus and emission sites: deleted (tasks 3, 4.1.1, 4.3)
- `AchievementHandler`, migrations: deleted (tasks 4.1-4.2)
- Database sourceSet, `sqldelightachievements/`, `AchievementsDatabase.kt`: deleted (tasks 5.2-5.5), with the `ActivityDatabase` copy-migration precondition confirmed/resolved (task 5.1)
- `UnlockableManager` achievement coupling removed, class and availability methods kept (task 7)
- `PointsManager`, XP/level formula, achievement-only `UserProfile` fields: deleted — `UserProfile` itself was deleted entirely after investigation showed no non-achievement fields survived it (task 5.1a, 6.4-6.5)
- `achievements.json`, `Achievement`/`AchievementType`/`AchievementCategory` domain models: deleted (tasks 6.2-6.3), widened during execution to cover the full domain-model cluster (`AchievementTier`, `AchievementProgress`, `AchievementRarity`, `AchievementEvent`, `Reward`, `UserPoints`, `AchievementRule`, `RuleContext`)
- Achievement UI (`AchievementsTab`, `AchievementScreenVoyager`, `AchievementScreen`, `AchievementScreenModel`, `AchievementCard`, `AchievementCategoryTabs`, `AchievementTabsAndGrid`, tests): deleted (task 2)
- Spec retirement: `achievements` spec fully retired with an 8/8-requirement REMOVED delta (task 8.1)

Out-of-scope items were correctly preserved and independently verified working on-device: Treasury (task 9.5), Stats screen streak/comparison/yearly-activity (task 9.3), Cosméticos selector (task 9.4).

## Correctness
Full build and test suite verified (task 9.1): all 6 compile targets pass; 3164 unit tests, 0 failures attributable to this change (1 genuine regression found and fixed — `GreetingProviderTest`, widened to 30 draws after `achievementCount` left the greeting seed; 2 confirmed pre-existing/unrelated flaky/broken tests, verified via a `git worktree` comparison against `develop`).

Repo-wide greps (task 9.2) confirm no remaining references to `Achievement`, `PointsManager`, or `achievement_`-prefixed preference keys outside historical spec archives and two deliberately-kept out-of-scope consumers (Stats screen `androidTest` files, an unrelated `AuroraTheme.kt` color-name comment, and the still-live `"achievement_unlockables"` SharedPreferences filename — renaming it would wipe existing users' unlocked cosmetics).

On-device verification (tasks 9.3-9.5) exercised the three explicitly out-of-scope, adjacent systems this change was not supposed to break, confirming none of them regressed:
- Stats: streak, month comparison, yearly activity graph all render with real data.
- Cosméticos: every aura (easter-egg and former-achievement-reward) renders as a normal selectable card.
- Treasury: renders without crashing, 52/52 rewards unlocked with the debug bypass confirmed off (i.e. reflecting real default-unlock logic).

## Coherence
Implementation follows `design.md`'s 6-step ordering with two well-justified, explicitly documented deviations:
1. Steps 5 and 6 (and part of Step 4) were interleaved rather than strictly sequential, because `AchievementsDatabase` could not actually be deleted before `AchievementRepositoryImpl` and the `Achievement`-typed domain models it served were gone — the reverse of design.md's stated order. Documented in task 5's "ordering correction" note; the actual dependency chain (Treasury's `Achievement`-typed params → domain models → repository → database) is now recorded for future reference.
2. Step 6's method-keep list in design.md includes `isBadgeAvailable`/`isDisplayPreferenceAvailable`; these were correctly *not* kept, per a separate, earlier, user-confirmed decision (task 1.2) that the 3 reward ids they gated were dead weight nothing depended on. This is a deliberate scope refinement made explicit in the task notes, not an oversight.

No other deviations from design.md's intent were found. No CRITICAL or WARNING findings.

## Notable non-blocking observation (out of scope, not a finding against this change)
Treasury's Vault Hero displays a stale "VOID BROADCAST" aura name, sourced from an on-device `enabledAuras` preference value predating the unrelated `remove-void-broadcast` change (commit `9bfd808f6`). Confirmed via grep that zero live code references "Void Broadcast" — this is stale local device preference state, not a code defect, and not introduced by this change. Documented in task 9.5's notes for whoever picks up `remove-treasury-screen` next, since that change will touch this same file.
