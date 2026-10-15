# Verto App Focus, Fresh Start and Ajustes (Plan 4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** "Só 1 coisa" in full screen with the spec's selection rules, a guilt-free reset, the "Que bom te ver" screen after days away, and an Ajustes screen whose values persist and take effect while the app is open.

**Architecture:** Selection lives in the domain (`TunnelRules.candidates(today, shownPlans)`), computed by `TodayViewModel` from the `Today` it already builds; the Hoje focus card shows the first candidate and opens a full-screen dialog. Settings become a `SettingsSource` read on every use, backed by a `SettingsRepository` over a platform `SettingsStore` (SharedPreferences on Android, memory in tests). Reset, step splitting and "last opened" are small data-layer pieces that write through `LocalStore`, so every change also syncs.

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.12.2 (Material3 1.9.0), Room KMP 2.8.5 (no schema change), Koin 4.2.2, kotlinx-datetime 0.8.0.

**Spec:** `~/Repos/Verto/docs/superpowers/specs/2026-10-15-verto-design.md` (sections "Regras de domínio" — Tunnel Vision, Reset, Volta após ausência — and "Telas e superfícies" — Tunnel Vision em tela cheia, Ajustes).

## Global Constraints

- Branch `feat/app-focus` from `main`; integrate later by **rebase + fast-forward**, no merge commit.
- Tunnel Vision selection, verbatim from the spec:
  1. energy filter: `low` → only `low` steps; `medium` → `low` and `medium`; `high` → all, including `hyperfocus`;
  2. priority: step of a routine whose anchor already fired (suggested plan: C with `low` energy, A otherwise; a plan she chose on Hoje wins) → next step of a `hard` task → next step of a task pinned to today;
  3. "Outro" moves to the next candidate and records nothing negative;
  4. "Tá grande demais" breaks the step right there.
  With no candidate: "Nada pra agora. Tá tudo bem."
- Reset, verbatim: `pinnedToToday = false` on every task; `raw` inbox items become `incubated`; nothing is marked late and nothing is deleted. One button, no dramatic confirmation.
- Volta após ausência: after N days without opening (default 4, configurable), before any list: "Que bom te ver. Quer começar com um dia limpo?" with **[Reset]** and **[Ver como estava]**.
- Ajustes holds: virada do dia, dias de ausência, horário de silêncio, conta e estado do sync (the last two through the existing "Conta e sync" screen). Settings are per device and are not synced.
- Copy: no counters of pending things, no red, never "atrasado", never "erro". User copy in Brazilian Portuguese; code, identifiers and comments in English; commits in Portuguese.
- No new dependencies. No Room schema change (new DAO queries are fine).
- Her redesign (commit `aa84ef6`) is the visual base: reuse `ScreenHeader`, `SectionHeader`, `EditorSection`, `EditorScaffold`, `Hint`, `FocusCard`; do not restyle existing screens.

## Review Focus

1. **Changing "virada do dia" while Hoje is open.** Hoje must move to the right logical day on the next resume, not after a restart. Pinned by `TodayViewModelTest.changingTheDayStart_movesHojeOnResume` (Task 1).
2. **"Tá grande demais" on a step in the middle of a task.** The new pieces take its place in order, later steps keep their order, the task stays open and the change syncs. Pinned by `StepRepositoryTest.split_taskStepInTheMiddle_keepsTheOrder` (Task 3).
3. **"Outro" until the end.** With candidates left it must never show the empty screen; after the last one it comes back around. Pinned by `TodayViewModelTest.tunnelOther_movesOn_andComesBackAround` (Task 4).
4. **Coming back after days away.** "Que bom te ver" shows once per return, not on every resume, and not on the very first launch. Pinned by `UsageTrackerTest.returningAfterTheThreshold_offersOnce` and `firstLaunch_offersNothing` (Task 3).
5. **Reset keeps everything.** No row deleted, dates kept, only pins and raw inbox states change, and those changes reach the outbox. Pinned by `ResetRepositoryTest.freshStart_unpinsAndIncubates_withoutDeletingAnything` (Task 3).

---

## File Structure

```
shared/domain/src/commonMain/kotlin/com/verto/domain/
├── VertoSettings.kt                    (Task 1: quiet hours, SettingsSource)
└── rules/TunnelRules.kt                (Task 2)
shared/domain/src/commonTest/kotlin/com/verto/domain/rules/TunnelRulesTest.kt   (Task 2)
shared/data/src/commonMain/kotlin/com/verto/data/
├── settings/SettingsRepository.kt      (Task 1: SettingsStore, InMemorySettingsStore, SettingsRepository)
├── db/Daos.kt                          (Task 3: TaskDao.active)
├── sync/LocalStore.kt                  (Task 3: last opened)
└── repository/
    ├── RoutineRepository.kt            (Task 1: SettingsSource)
    ├── StepRepository.kt               (Task 3)
    ├── ResetRepository.kt              (Task 3)
    └── UsageTracker.kt                 (Task 3)
shared/data/src/jvmTest/kotlin/com/verto/data/
├── DataGraph.kt                        (Task 1, Task 3)
├── settings/SettingsRepositoryTest.kt  (Task 1)
└── repository/{StepRepositoryTest,ResetRepositoryTest,UsageTrackerTest}.kt  (Task 3)
composeApp/src/commonMain/kotlin/com/verto/ui/
├── App.kt, AppViewModel.kt, FreshStartScreen.kt          (Task 5)
├── today/TodayViewModel.kt, TodayScreen.kt, TunnelOverlay.kt  (Tasks 1, 4)
├── inbox/InboxViewModel.kt, plan/PlanViewModel.kt        (Task 1)
├── settings/SettingsViewModel.kt, SettingsScreen.kt      (Task 6)
├── navigation/Routes.kt, VertoNavHost.kt, plan/PlanScreen.kt  (Task 6)
└── di/VertoModule.kt                                     (Tasks 1, 3, 5, 6)
composeApp/src/jvmTest/kotlin/com/verto/ui/
├── UiTestGraph.kt                                        (Tasks 1, 3)
├── today/TodayViewModelTest.kt                           (Tasks 1, 4)
├── AppViewModelTest.kt                                   (Task 5)
└── settings/SettingsViewModelTest.kt                     (Task 6)
androidApp/src/main/kotlin/com/verto/android/
├── PrefsSettingsStore.kt                                 (Task 1)
└── VertoApplication.kt                                   (Task 1)
README.md                                                 (Task 7)
```

---

### Task 1: Settings that persist and apply while the app is open

**Files:**
- Modify: `shared/domain/src/commonMain/kotlin/com/verto/domain/VertoSettings.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/settings/SettingsRepository.kt`
- Modify: `shared/data/src/commonMain/kotlin/com/verto/data/repository/RoutineRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayViewModel.kt`, `inbox/InboxViewModel.kt`, `plan/PlanViewModel.kt`, `di/VertoModule.kt`
- Create: `androidApp/src/main/kotlin/com/verto/android/PrefsSettingsStore.kt`; Modify: `androidApp/src/main/kotlin/com/verto/android/VertoApplication.kt`
- Modify (tests): `shared/data/src/jvmTest/kotlin/com/verto/data/DataGraph.kt`, `composeApp/src/jvmTest/kotlin/com/verto/ui/UiTestGraph.kt`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/settings/SettingsRepositoryTest.kt`, new case in `composeApp/src/jvmTest/kotlin/com/verto/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Produces: `fun interface SettingsSource { fun current(): VertoSettings }` (package `com.verto.domain`); `VertoSettings.quietStart` / `quietEnd: LocalTime` (defaults 22:00 / 08:00); `interface SettingsStore { fun load(): VertoSettings?; fun save(settings: VertoSettings) }`, `class InMemorySettingsStore(saved: VertoSettings? = null)`, `class SettingsRepository(store: SettingsStore) : SettingsSource` with `val settings: StateFlow<VertoSettings>` and `fun update(change: (VertoSettings) -> VertoSettings)` (package `com.verto.data.settings`).
- Changes: `RoutineRepository`, `TodayViewModel`, `InboxViewModel`, `PlanViewModel` take `settings: SettingsSource` instead of `VertoSettings`. In `UiTestGraph`, `settings` becomes a `SettingsRepository` (it implements `SettingsSource`, so existing `graph.settings` arguments keep compiling).

- [ ] **Step 1: Create the branch**

```bash
cd ~/Repos/VertoApp
git checkout main
git checkout -b feat/app-focus
```

- [ ] **Step 2: Write the failing tests**

`shared/data/src/jvmTest/kotlin/com/verto/data/settings/SettingsRepositoryTest.kt`:

```kotlin
package com.verto.data.settings

import com.verto.domain.VertoSettings
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryTest {

    @Test
    fun nothingSaved_meansTheDefaults() {
        assertEquals(VertoSettings(), SettingsRepository(InMemorySettingsStore()).current())
    }

    @Test
    fun whatWasSaved_isWhatItStartsWith() {
        val repository = SettingsRepository(InMemorySettingsStore(VertoSettings(dayStartsAt = LocalTime(3, 0))))

        assertEquals(LocalTime(3, 0), repository.current().dayStartsAt)
    }

    @Test
    fun update_savesAndPublishes() {
        val store = InMemorySettingsStore()
        val repository = SettingsRepository(store)

        repository.update { it.copy(quietStart = LocalTime(23, 0)) }

        assertEquals(LocalTime(23, 0), repository.current().quietStart)
        assertEquals(LocalTime(23, 0), repository.settings.value.quietStart)
        assertEquals(LocalTime(23, 0), store.load()?.quietStart)
    }
}
```

Append to `composeApp/src/jvmTest/kotlin/com/verto/ui/today/TodayViewModelTest.kt` (inside the class, before its closing brace):

```kotlin
    @Test
    fun changingTheDayStart_movesHojeOnResume() = runTest {
        graph.advanceTo(uiAt(16, 2))
        val vm = viewModel()
        assertEquals(LocalDate(2026, 10, 15), vm.content().today.day)

        graph.settings.update { it.copy(dayStartsAt = LocalTime(0, 0)) }
        vm.onResume()

        vm.content { it.today.day == LocalDate(2026, 10, 16) }
    }
```

In `composeApp/src/jvmTest/kotlin/com/verto/ui/UiTestGraph.kt`, replace `    val settings = VertoSettings()` with:

```kotlin
    val settings = SettingsRepository(InMemorySettingsStore())
```

and replace its import `import com.verto.domain.VertoSettings` with:

```kotlin
import com.verto.data.settings.InMemorySettingsStore
import com.verto.data.settings.SettingsRepository
```

In `shared/data/src/jvmTest/kotlin/com/verto/data/DataGraph.kt`, replace `    val routines = RoutineRepository(store, clock, ids, { TestZone }, settings)` with:

```kotlin
    val routines = RoutineRepository(store, clock, ids, { TestZone }, { settings })
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :shared:data:jvmTest :composeApp:jvmTest --console=plain`
Expected: compilation FAIL (`Unresolved reference 'SettingsRepository'`, `'InMemorySettingsStore'`).

- [ ] **Step 4: Settings in the domain**

Replace the whole of `shared/domain/src/commonMain/kotlin/com/verto/domain/VertoSettings.kt`:

`shared/domain/src/commonMain/kotlin/com/verto/domain/VertoSettings.kt`:

```kotlin
package com.verto.domain

import kotlinx.datetime.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** Values the spec calls configurable. Ajustes edits the day start, the absence threshold and the quiet hours. */
data class VertoSettings(
    val dayStartsAt: LocalTime = LocalTime(4, 0),
    val absenceThreshold: Duration = 4.days,
    val incubationAfter: Duration = 3.days,
    val pinnedSoftLimit: Int = 3,
    val deadlineHorizonDays: Int = 3,
    val recentlyDoneWindow: Duration = 48.hours,
    val quietStart: LocalTime = LocalTime(22, 0),
    val quietEnd: LocalTime = LocalTime(8, 0),
)

/** Read on every use, like the time zone: Ajustes can change a value while a screen is open. */
fun interface SettingsSource {
    fun current(): VertoSettings
}
```

- [ ] **Step 5: The repository**

`shared/data/src/commonMain/kotlin/com/verto/data/settings/SettingsRepository.kt`:

```kotlin
package com.verto.data.settings

import com.verto.domain.SettingsSource
import com.verto.domain.VertoSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Per-device settings, read synchronously: they are needed the moment a screen opens. */
interface SettingsStore {
    fun load(): VertoSettings?
    fun save(settings: VertoSettings)
}

class InMemorySettingsStore(private var saved: VertoSettings? = null) : SettingsStore {
    override fun load(): VertoSettings? = saved
    override fun save(settings: VertoSettings) {
        saved = settings
    }
}

class SettingsRepository(private val store: SettingsStore) : SettingsSource {

    private val _settings = MutableStateFlow(store.load() ?: VertoSettings())
    val settings: StateFlow<VertoSettings> = _settings.asStateFlow()

    override fun current(): VertoSettings = _settings.value

    fun update(change: (VertoSettings) -> VertoSettings) {
        val next = change(_settings.value)
        store.save(next)
        _settings.value = next
    }
}
```

- [ ] **Step 6: Read settings on every use**

In `shared/data/src/commonMain/kotlin/com/verto/data/repository/RoutineRepository.kt`:
- replace `    private val settings: VertoSettings,` with `    private val settings: SettingsSource,`;
- replace every `settings.dayStartsAt` with `settings.current().dayStartsAt`;
- replace the import `com.verto.domain.VertoSettings` with `com.verto.domain.SettingsSource`.

In `composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayViewModel.kt`:
- replace `    private val settings: VertoSettings,` with `    private val settings: SettingsSource,`;
- replace `                    settings = settings,` with `                    settings = settings.current(),`;
- replace `LogicalDay.of(instant, zones.current(), settings.dayStartsAt)` with `LogicalDay.of(instant, zones.current(), settings.current().dayStartsAt)`;
- replace the import `com.verto.domain.VertoSettings` with `com.verto.domain.SettingsSource`.

In `composeApp/src/commonMain/kotlin/com/verto/ui/inbox/InboxViewModel.kt`:
- replace `    settings: VertoSettings,` with `    settings: SettingsSource,`;
- replace `viewModelScope.launch { inbox.incubateStale(settings) }` with `viewModelScope.launch { inbox.incubateStale(settings.current()) }`;
- replace the import `com.verto.domain.VertoSettings` with `com.verto.domain.SettingsSource`.

In `composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanViewModel.kt`:
- replace `    settings: VertoSettings,` with `    settings: SettingsSource,`;
- replace `LogicalDay.of(it, zones.current(), settings.dayStartsAt)` with `LogicalDay.of(it, zones.current(), settings.current().dayStartsAt)`;
- replace the import `com.verto.domain.VertoSettings` with `com.verto.domain.SettingsSource`.

In `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt`, replace `    single { VertoSettings() }` with:

```kotlin
    single { SettingsRepository(get()) }
    single<SettingsSource> { get<SettingsRepository>() }
```

(imports: `com.verto.data.settings.SettingsRepository`, `com.verto.domain.SettingsSource`; drop `com.verto.domain.VertoSettings`), and change the doc comment to `/** Expects a VertoDatabase, a TokenStore, a SettingsStore and an HttpClient from the platform module (androidApp). */`.

- [ ] **Step 7: Android store**

`androidApp/src/main/kotlin/com/verto/android/PrefsSettingsStore.kt`:

```kotlin
package com.verto.android

import android.content.Context
import com.verto.data.settings.SettingsStore
import com.verto.domain.VertoSettings
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.days

/** Only the values Ajustes edits are stored; the rest keep their defaults. Included in backups on purpose. */
class PrefsSettingsStore(context: Context) : SettingsStore {

    private val prefs = context.getSharedPreferences("verto_settings", Context.MODE_PRIVATE)

    override fun load(): VertoSettings? {
        if (!prefs.contains(DAY_STARTS_AT)) return null
        val defaults = VertoSettings()
        return try {
            defaults.copy(
                dayStartsAt = LocalTime.parse(prefs.getString(DAY_STARTS_AT, null) ?: return null),
                absenceThreshold = prefs.getInt(ABSENCE_DAYS, defaults.absenceThreshold.inWholeDays.toInt()).days,
                quietStart = LocalTime.parse(prefs.getString(QUIET_START, null) ?: defaults.quietStart.toString()),
                quietEnd = LocalTime.parse(prefs.getString(QUIET_END, null) ?: defaults.quietEnd.toString()),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun save(settings: VertoSettings) {
        prefs.edit()
            .putString(DAY_STARTS_AT, settings.dayStartsAt.toString())
            .putInt(ABSENCE_DAYS, settings.absenceThreshold.inWholeDays.toInt())
            .putString(QUIET_START, settings.quietStart.toString())
            .putString(QUIET_END, settings.quietEnd.toString())
            .apply()
    }

    private companion object {
        const val DAY_STARTS_AT = "day_starts_at"
        const val ABSENCE_DAYS = "absence_days"
        const val QUIET_START = "quiet_start"
        const val QUIET_END = "quiet_end"
    }
}
```

In `androidApp/src/main/kotlin/com/verto/android/VertoApplication.kt`, inside the platform `module { ... }`, add after `single<TokenStore> { PrefsTokenStore(androidContext()) }`:

```kotlin
                    single<SettingsStore> { PrefsSettingsStore(androidContext()) }
```

(import `com.verto.data.settings.SettingsStore`). If `kotlinx.datetime.LocalTime` does not resolve in androidApp, add `implementation(libs.kotlinx.datetime)` to `androidApp/build.gradle.kts` and ledger it.

- [ ] **Step 8: Run everything**

Run: `./gradlew :shared:domain:jvmTest :shared:data:jvmTest :composeApp:jvmTest :androidApp:assembleDebug --console=plain`
Expected: PASS; BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add shared composeApp androidApp
git commit -m "feat(settings): ajustes salvos no aparelho e lidos a cada uso"
```

---

### Task 2: "Só 1 coisa" selection rules

**Files:**
- Create: `shared/domain/src/commonMain/kotlin/com/verto/domain/rules/TunnelRules.kt`
- Test: `shared/domain/src/commonTest/kotlin/com/verto/domain/rules/TunnelRulesTest.kt`

**Interfaces:**
- Consumes: `Today`, `AnchorSection`, `RoutineCard`, `TaskCard` (with `upcomingSteps`/`nextStep`) from `TodayBuilder.kt`.
- Produces (package `com.verto.domain.rules`):
  - `sealed interface TunnelItem { val key: String }` with `data class RoutineStep(val anchor: Anchor, val routine: Routine, val step: Step)` and `data class TaskStep(val task: Task, val step: Step?)` (`step == null`: a task without steps is its own item)
  - `object TunnelRules { fun fits(step: StepEnergy, energy: EnergyLevel): Boolean; fun candidates(today: Today, shownPlans: Map<String, Plan> = emptyMap()): List<TunnelItem> }`

- [ ] **Step 1: Write the failing test**

`shared/domain/src/commonTest/kotlin/com/verto/domain/rules/TunnelRulesTest.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.Oct15
import com.verto.domain.SaoPaulo
import com.verto.domain.VertoSettings
import com.verto.domain.anchor
import com.verto.domain.at
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.AnchorTrigger
import com.verto.domain.model.DueKind
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.Step
import com.verto.domain.model.StepEnergy
import com.verto.domain.model.StepOwner
import com.verto.domain.model.Task
import com.verto.domain.routine
import com.verto.domain.routineStep
import com.verto.domain.task
import com.verto.domain.taskStep
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TunnelRulesTest {

    private val lunchFired = AnchorEvent("e1", "lunch", Oct15, at(15, 12), AnchorTrigger.Tap)

    private fun today(
        energy: EnergyLevel = EnergyLevel.Medium,
        steps: List<Step> = emptyList(),
        tasks: List<Task> = emptyList(),
        events: List<AnchorEvent> = listOf(lunchFired),
    ) = TodayBuilder.build(
        TodayInput(
            day = Oct15,
            zone = SaoPaulo,
            settings = VertoSettings(),
            energy = energy,
            anchors = listOf(anchor("lunch", 12), anchor("dinner", 19)),
            routines = listOf(routine("walk", "lunch"), routine("dishes", "dinner")),
            steps = steps,
            logs = emptyList(),
            events = events,
            tasks = tasks,
            now = at(15, 13),
        ),
    )

    private fun ids(items: List<TunnelItem>) = items.map { it.key }

    private fun stepOf(id: String, taskId: String, energy: StepEnergy, order: Int = 0) =
        Step(id, StepOwner.OfTask(taskId), order, id, energy, null)

    @Test
    fun onlyAnchorsThatFired_giveRoutineSteps() {
        val items = TunnelRules.candidates(
            today(steps = listOf(routineStep("walkA", "walk", Plan.A), routineStep("dishesA", "dishes", Plan.A))),
        )

        assertEquals(listOf("step:walkA"), ids(items))
    }

    @Test
    fun lowEnergy_takesThePlanCStep_andOnlyLightSteps() {
        val items = TunnelRules.candidates(
            today(
                energy = EnergyLevel.Low,
                steps = listOf(
                    routineStep("walkA", "walk", Plan.A, energy = StepEnergy.Medium),
                    routineStep("walkC", "walk", Plan.C),
                    stepOf("heavy", "ir", StepEnergy.Medium),
                ),
                tasks = listOf(task("ir", pinned = true)),
            ),
        )

        assertEquals(listOf("step:walkC"), ids(items))
    }

    @Test
    fun theChosenPlan_winsOverTheSuggestion() {
        val items = TunnelRules.candidates(
            today(
                energy = EnergyLevel.Low,
                steps = listOf(routineStep("walkA", "walk", Plan.A), routineStep("walkC", "walk", Plan.C)),
            ),
            shownPlans = mapOf("walk" to Plan.A),
        )

        assertEquals(listOf("step:walkA"), ids(items))
    }

    @Test
    fun order_isRoutineThenHardDeadlinesThenPinned() {
        val items = TunnelRules.candidates(
            today(
                steps = listOf(
                    routineStep("walkA", "walk", Plan.A),
                    taskStep("pinnedStep", "pinned"),
                    taskStep("soonStep", "soon"),
                    taskStep("laterStep", "later"),
                ),
                tasks = listOf(
                    task("pinned", pinned = true),
                    task("soon", dueDate = LocalDate(2026, 10, 16), dueKind = DueKind.Hard),
                    task("later", pinned = true, dueDate = LocalDate(2026, 10, 25), dueKind = DueKind.Hard),
                ),
            ),
        )

        assertEquals(listOf("step:walkA", "step:soonStep", "step:laterStep", "step:pinnedStep"), ids(items))
    }

    @Test
    fun aNextStepTooHeavyForToday_makesThatTaskWait() {
        val items = TunnelRules.candidates(
            today(
                energy = EnergyLevel.Medium,
                steps = listOf(stepOf("deep", "ir", StepEnergy.Hyperfocus, 0), stepOf("light", "ir", StepEnergy.Low, 1)),
                tasks = listOf(task("ir", pinned = true)),
                events = emptyList(),
            ),
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun aTaskWithoutSteps_isItsOwnItem() {
        val items = TunnelRules.candidates(today(tasks = listOf(task("ligar", pinned = true)), events = emptyList()))

        assertEquals(listOf("task:ligar"), ids(items))
    }

    @Test
    fun highEnergy_takesHyperfocusToo() {
        assertTrue(TunnelRules.fits(StepEnergy.Hyperfocus, EnergyLevel.High))
        assertTrue(TunnelRules.fits(StepEnergy.Medium, EnergyLevel.Medium))
        assertTrue(!TunnelRules.fits(StepEnergy.Medium, EnergyLevel.Low))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :shared:domain:jvmTest --console=plain`
Expected: compilation FAIL, `Unresolved reference 'TunnelRules'`.

- [ ] **Step 3: Write the rules**

`shared/domain/src/commonMain/kotlin/com/verto/domain/rules/TunnelRules.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.model.Anchor
import com.verto.domain.model.DueKind
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.Step
import com.verto.domain.model.StepEnergy
import com.verto.domain.model.Task

/** One thing to do now, in "Só 1 coisa". */
sealed interface TunnelItem {
    val key: String

    data class RoutineStep(val anchor: Anchor, val routine: Routine, val step: Step) : TunnelItem {
        override val key: String get() = "step:${step.id}"
    }

    /** [step] is null for a task with no steps: the task itself is the thing to do. */
    data class TaskStep(val task: Task, val step: Step?) : TunnelItem {
        override val key: String get() = step?.let { "step:${it.id}" } ?: "task:${task.id}"
    }
}

object TunnelRules {

    fun fits(step: StepEnergy, energy: EnergyLevel): Boolean = when (energy) {
        EnergyLevel.Low -> step == StepEnergy.Low
        EnergyLevel.Medium -> step != StepEnergy.Hyperfocus
        EnergyLevel.High -> true
    }

    /**
     * Routine steps of anchors that already fired, then hard deadlines (soonest first), then
     * pinned tasks. Steps are taken in order: when the next one is too heavy for today's
     * energy, that routine or task waits instead of jumping ahead.
     */
    fun candidates(today: Today, shownPlans: Map<String, Plan> = emptyMap()): List<TunnelItem> {
        val routineSteps = today.sections.filter { it.triggered }.flatMap { section ->
            section.routines.filter { it.outcome == null }.mapNotNull { card ->
                val plan = shownPlans[card.routine.id] ?: card.suggestedPlan
                card.steps(plan).firstOrNull { !it.done }?.step
                    ?.takeIf { fits(it.energy, today.energy) }
                    ?.let { TunnelItem.RoutineStep(section.anchor, card.routine, it) }
            }
        }
        val open = (today.deadlines + today.pinned).distinctBy { it.task.id }
        val hard = open.filter { it.task.dueKind == DueKind.Hard && it.task.dueDate != null }.sortedBy { it.task.dueDate }
        val pinned = today.pinned.filter { card -> hard.none { it.task.id == card.task.id } }
        return routineSteps + (hard + pinned).mapNotNull { taskItem(it, today.energy) }
    }

    private fun taskItem(card: TaskCard, energy: EnergyLevel): TunnelItem? {
        val next = card.nextStep
        return when {
            next != null -> next.takeIf { fits(it.energy, energy) }?.let { TunnelItem.TaskStep(card.task, it) }
            card.totalSteps == 0 -> TunnelItem.TaskStep(card.task, null)
            else -> null
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :shared:domain:jvmTest --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/domain
git commit -m "feat(focus): regras do Só 1 coisa (energia, âncora disparada, prazo real, fixadas)"
```

---

### Task 3: Split a step, reset the day, notice a return

**Files:**
- Modify: `shared/data/src/commonMain/kotlin/com/verto/data/db/Daos.kt` (`TaskDao.active`)
- Modify: `shared/data/src/commonMain/kotlin/com/verto/data/sync/LocalStore.kt` (last opened)
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/repository/StepRepository.kt`, `ResetRepository.kt`, `UsageTracker.kt`
- Modify (tests): `shared/data/src/jvmTest/kotlin/com/verto/data/DataGraph.kt`, `composeApp/src/jvmTest/kotlin/com/verto/ui/UiTestGraph.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/repository/StepRepositoryTest.kt`, `ResetRepositoryTest.kt`, `UsageTrackerTest.kt`

**Interfaces:**
- Consumes: `ResetRules.plan`, `AbsenceRules.shouldOfferFreshStart` (Plan 2); `SettingsSource` (Task 1).
- Produces:
  - `class StepRepository(store: LocalStore, clock: Clock, ids: IdGenerator)` with `suspend fun split(stepId: String, titles: List<String>): List<String>` (new ids, in order; empty when nothing changed)
  - `class ResetRepository(store: LocalStore, clock: Clock)` with `suspend fun freshStart()`
  - `class UsageTracker(store: LocalStore, settings: SettingsSource, clock: Clock)` with `suspend fun checkIn(): Boolean` (true = offer the fresh start)
  - `LocalStore.lastOpenedAt(): Instant?`, `LocalStore.setLastOpenedAt(at: Instant)`
  - `DataGraph` and `UiTestGraph` gain `steps`, `reset`, `usage`.

- [ ] **Step 1: Write the failing tests**

`shared/data/src/jvmTest/kotlin/com/verto/data/repository/StepRepositoryTest.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.domain.model.StepEnergy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StepRepositoryTest {

    private val graph = DataGraph()

    @AfterTest
    fun close() = graph.db.close()

    private fun step(title: String) = StepDraft(null, title, StepEnergy.Medium)

    @Test
    fun split_taskStepInTheMiddle_keepsTheOrder() = runTest {
        val taskId = graph.tasks.save(TaskDraft(null, "Fazer IR", steps = listOf(step("abrir site"), step("declarar tudo"), step("enviar"))))
        val middle = graph.tasks.draftFor(taskId)!!.steps[1].id!!
        graph.db.outboxDao().deleteSent(graph.db.outboxDao().all().map { it.seq })

        graph.steps.split(middle, listOf(" baixar programa ", "", "importar recibos"))

        assertEquals(
            listOf("abrir site", "baixar programa", "importar recibos", "enviar"),
            graph.tasks.draftFor(taskId)!!.steps.map { it.title },
        )
        assertNull(graph.tasks.observeTasks().first().single().doneAt)
        assertTrue(graph.db.outboxDao().all().any { it.entityId == middle && it.deletedAt != null })
    }

    @Test
    fun split_routineStep_staysInItsPlan() = runTest {
        val anchorId = graph.routines.saveAnchor(AnchorDraft(null, "Acordei", LocalTime(7, 0), LocalTime(9, 0)))
        val routineId = graph.routines.saveRoutine(
            RoutineDraft(null, "Alongar", anchorId, DayOfWeek.entries.toSet(), listOf(step("20 min")), listOf(step("1 min"), step("respirar"))),
        )
        val firstC = graph.db.stepDao().forRoutine(routineId).first { it.plan == "c" }.id

        val created = graph.steps.split(firstC, listOf("levantar os braços"))

        val rows = graph.db.stepDao().forRoutine(routineId)
        assertEquals(listOf("levantar os braços", "respirar"), rows.filter { it.plan == "c" }.map { it.title })
        assertEquals(listOf("20 min"), rows.filter { it.plan == "a" }.map { it.title })
        assertEquals("low", rows.single { it.id == created.single() }.energy)
    }

    @Test
    fun split_withOnlyBlankPieces_changesNothing() = runTest {
        val taskId = graph.tasks.save(TaskDraft(null, "Ligar", steps = listOf(step("achar número"))))
        val only = graph.tasks.draftFor(taskId)!!.steps.single().id!!

        assertTrue(graph.steps.split(only, listOf(" ", "")).isEmpty())
        assertEquals(listOf("achar número"), graph.tasks.draftFor(taskId)!!.steps.map { it.title })
    }
}
```

`shared/data/src/jvmTest/kotlin/com/verto/data/repository/ResetRepositoryTest.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.domain.model.DueKind
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResetRepositoryTest {

    private val graph = DataGraph()

    @AfterTest
    fun close() = graph.db.close()

    @Test
    fun freshStart_unpinsAndIncubates_withoutDeletingAnything() = runTest {
        val due = LocalDate(2026, 10, 12)
        graph.tasks.save(TaskDraft(null, "IR", dueDate = due, dueKind = DueKind.Hard, pinnedToToday = true))
        graph.tasks.save(TaskDraft(null, "Ligar", pinnedToToday = true))
        val raw = graph.inbox.capture("ideia solta", InboxSource.App)!!
        val processed = graph.inbox.capture("já resolvida", InboxSource.App)!!
        graph.inbox.setState(processed, InboxState.Processed)
        graph.db.outboxDao().deleteSent(graph.db.outboxDao().all().map { it.seq })

        graph.reset.freshStart()

        val tasks = graph.tasks.observeTasks().first()
        assertEquals(2, tasks.size)
        assertTrue(tasks.none { it.pinnedToToday })
        assertEquals(due, tasks.single { it.title == "IR" }.dueDate)
        assertEquals(InboxState.Incubated, graph.inbox.get(raw)?.state)
        assertEquals(InboxState.Processed, graph.inbox.get(processed)?.state)
        val queued = graph.db.outboxDao().all()
        assertEquals(3, queued.size)
        assertTrue(queued.all { it.deletedAt == null })
    }
}
```

`shared/data/src/jvmTest/kotlin/com/verto/data/repository/UsageTrackerTest.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.data.localAt
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageTrackerTest {

    private val graph = DataGraph()

    @AfterTest
    fun close() = graph.db.close()

    @Test
    fun firstLaunch_offersNothing() = runTest {
        assertFalse(graph.usage.checkIn())
    }

    @Test
    fun aShortBreak_offersNothing() = runTest {
        graph.clock.now = localAt(10, 10)
        graph.usage.checkIn()
        graph.clock.now = localAt(13, 9)

        assertFalse(graph.usage.checkIn())
    }

    @Test
    fun returningAfterTheThreshold_offersOnce() = runTest {
        graph.clock.now = localAt(10, 10)
        graph.usage.checkIn()
        graph.clock.now = localAt(15, 10)

        assertTrue(graph.usage.checkIn())
        assertFalse(graph.usage.checkIn())
    }
}
```

In `shared/data/src/jvmTest/kotlin/com/verto/data/DataGraph.kt`, add at the end of the class body (before its closing brace):

```kotlin
    val steps = StepRepository(store, clock, ids)
    val reset = ResetRepository(store, clock)
    val usage = UsageTracker(store, { settings }, clock)
```

(imports: `com.verto.data.repository.ResetRepository`, `com.verto.data.repository.StepRepository`, `com.verto.data.repository.UsageTracker`).

In `composeApp/src/jvmTest/kotlin/com/verto/ui/UiTestGraph.kt`, add inside `class UiTestGraph`, after `val days = ...`:

```kotlin
    val steps = StepRepository(store, clock, ids)
    val reset = ResetRepository(store, clock)
    val usage = UsageTracker(store, settings, clock)
```

(imports: `com.verto.data.repository.ResetRepository`, `com.verto.data.repository.StepRepository`, `com.verto.data.repository.UsageTracker`).

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :shared:data:jvmTest --console=plain`
Expected: compilation FAIL (`Unresolved reference 'StepRepository'`…).

- [ ] **Step 3: DAO and LocalStore additions**

In `shared/data/src/commonMain/kotlin/com/verto/data/db/Daos.kt`, inside `interface TaskDao`, after the `observeActive()` query, add:

```kotlin

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    suspend fun active(): List<TaskEntity>
```

In `shared/data/src/commonMain/kotlin/com/verto/data/sync/LocalStore.kt`, add after `suspend fun setLastSyncedAt(...)`:

```kotlin

    suspend fun lastOpenedAt(): Instant? = db.syncStateDao().get(LAST_OPENED_AT)?.let { Instant.parse(it) }

    suspend fun setLastOpenedAt(at: Instant) = db.syncStateDao().put(SyncStateEntity(LAST_OPENED_AT, at.toString()))
```

and add `const val LAST_OPENED_AT = "last_opened_at"` to its companion object.

- [ ] **Step 4: The three pieces**

`shared/data/src/commonMain/kotlin/com/verto/data/repository/StepRepository.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.IdGenerator
import com.verto.data.db.StepEntity
import com.verto.data.sync.LocalStore
import com.verto.data.sync.wire
import com.verto.domain.model.StepEnergy
import kotlin.time.Clock

class StepRepository(
    private val store: LocalStore,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val stepDao = store.db.stepDao()

    /**
     * "Tá grande demais": the step is replaced, in place, by smaller ones. They start light,
     * since the point is a step small enough to begin. Returns the new ids in order.
     */
    suspend fun split(stepId: String, titles: List<String>): List<String> {
        val pieces = titles.map { it.trim() }.filter { it.isNotEmpty() }
        if (pieces.isEmpty()) return emptyList()
        val nowMs = clock.now().toEpochMilliseconds()
        return store.write {
            val original = stepDao.get(stepId)?.takeIf { it.deletedAt == null } ?: return@write emptyList()
            val siblings = when {
                original.taskId != null -> stepDao.forTask(original.taskId)
                else -> stepDao.forRoutine(checkNotNull(original.routineId)).filter { it.plan == original.plan }
            }
            val created = pieces.map { title ->
                StepEntity(
                    id = ids.newId(),
                    taskId = original.taskId,
                    routineId = original.routineId,
                    plan = original.plan,
                    order = 0,
                    title = title,
                    energy = StepEnergy.Low.wire(),
                    doneAt = null,
                    updatedAt = nowMs,
                    deletedAt = null,
                )
            }
            val newIds = created.map { it.id }.toSet()
            siblings.flatMap { if (it.id == stepId) created else listOf(it) }.forEachIndexed { index, step ->
                if (step.id in newIds || step.order != index) put(step.copy(order = index, updatedAt = nowMs))
            }
            put(original.copy(deletedAt = nowMs, updatedAt = nowMs))
            created.map { it.id }
        }
    }
}
```

`shared/data/src/commonMain/kotlin/com/verto/data/repository/ResetRepository.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.sync.LocalStore
import com.verto.data.sync.wire
import com.verto.data.toDomain
import com.verto.domain.model.InboxState
import com.verto.domain.rules.ResetRules
import kotlin.time.Clock

class ResetRepository(private val store: LocalStore, private val clock: Clock) {

    /** A clean day in one transaction: unpin everything, put raw braindumps aside. Nothing is deleted. */
    suspend fun freshStart() {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            val tasks = store.db.taskDao().active()
            val inbox = store.db.inboxDao().active()
            val plan = ResetRules.plan(tasks.map { it.toDomain() }, inbox.map { it.toDomain() })
            val unpin = plan.tasksToUnpin.map { it.id }.toSet()
            val incubate = plan.inboxToIncubate.map { it.id }.toSet()
            tasks.filter { it.id in unpin }.forEach { put(it.copy(pinnedToToday = false, updatedAt = nowMs)) }
            inbox.filter { it.id in incubate }.forEach { put(it.copy(state = InboxState.Incubated.wire(), updatedAt = nowMs)) }
        }
    }
}
```

`shared/data/src/commonMain/kotlin/com/verto/data/repository/UsageTracker.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.sync.LocalStore
import com.verto.domain.SettingsSource
import com.verto.domain.rules.AbsenceRules
import kotlin.time.Clock

class UsageTracker(
    private val store: LocalStore,
    private val settings: SettingsSource,
    private val clock: Clock,
) {
    /**
     * Called every time the app comes to the front. Records the visit and says whether this one
     * comes after a long enough absence to offer a clean day; the next call already says no.
     */
    suspend fun checkIn(): Boolean {
        val now = clock.now()
        val last = store.lastOpenedAt()
        store.setLastOpenedAt(now)
        return AbsenceRules.shouldOfferFreshStart(last, now, settings.current().absenceThreshold)
    }
}
```

In `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt`, add after `singleOf(::DayRepository)`:

```kotlin
    singleOf(::StepRepository)
    singleOf(::ResetRepository)
    singleOf(::UsageTracker)
```

(imports: `com.verto.data.repository.ResetRepository`, `com.verto.data.repository.StepRepository`, `com.verto.data.repository.UsageTracker`).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :shared:data:jvmTest :composeApp:jvmTest --console=plain`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared composeApp
git commit -m "feat(focus): quebrar passo, dia limpo e registro da última abertura"
```

---

### Task 4: "Só 1 coisa" in full screen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/today/TunnelOverlay.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayScreen.kt`
- Test: new cases in `composeApp/src/jvmTest/kotlin/com/verto/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: `TunnelRules`, `TunnelItem` (Task 2); `StepRepository.split` (Task 3).
- Produces: `TodayUiState.Content.tunnel: List<TunnelItem>` (candidates minus the ones skipped with "Outro", back to the full list once all were skipped); `TodayViewModel(…, clock, steps: StepRepository)` with `fun tunnelDone(item: TunnelItem)`, `fun tunnelOther()`, `fun splitStep(stepId: String, titles: List<String>)`.

- [ ] **Step 1: Write the failing tests**

In `composeApp/src/jvmTest/kotlin/com/verto/ui/today/TodayViewModelTest.kt`, change `viewModel()` so that its argument list ends with `graph.clock, graph.steps,` (add `graph.steps` after `graph.clock`). Then append inside the class:

```kotlin
    @Test
    fun tunnel_startsWithTheStepOfTheAnchorThatFired() = runTest {
        val anchorId = lunchRoutine()
        val vm = viewModel()
        vm.tapAnchor(anchorId)

        val first = vm.content { it.tunnel.isNotEmpty() }.tunnel.first()

        assertEquals("20 min de caminhada", (first as TunnelItem.RoutineStep).step.title)
    }

    @Test
    fun tunnelOther_movesOn_andComesBackAround() = runTest {
        val anchorId = lunchRoutine()
        graph.tasks.save(TaskDraft(null, "ligar pro dentista", pinnedToToday = true))
        val vm = viewModel()
        vm.tapAnchor(anchorId)
        vm.content { it.tunnel.size == 2 }

        vm.tunnelOther()
        val second = vm.content { it.tunnel.first() is TunnelItem.TaskStep }
        assertEquals(1, second.tunnel.size)

        vm.tunnelOther()
        vm.content { it.tunnel.first() is TunnelItem.RoutineStep && it.tunnel.size == 2 }
    }

    @Test
    fun tunnelDone_onATaskWithoutSteps_finishesIt() = runTest {
        graph.tasks.save(TaskDraft(null, "ligar pro dentista", pinnedToToday = true))
        val vm = viewModel()
        val item = vm.content { it.tunnel.isNotEmpty() }.tunnel.single()

        vm.tunnelDone(item)

        vm.content { it.tunnel.isEmpty() && it.today.recentlyDone.isNotEmpty() }
    }

    @Test
    fun splitStep_putsTheFirstPieceInFront() = runTest {
        graph.tasks.save(
            TaskDraft(null, "Fazer IR", pinnedToToday = true, steps = listOf(StepDraft(null, "fazer tudo", StepEnergy.Low))),
        )
        val vm = viewModel()
        val item = vm.content { it.tunnel.isNotEmpty() }.tunnel.single() as TunnelItem.TaskStep

        vm.splitStep(item.step!!.id, listOf("abrir o site", "baixar o programa"))

        vm.content { (it.tunnel.firstOrNull() as? TunnelItem.TaskStep)?.step?.title == "abrir o site" }
    }
```

(imports to add if missing: `com.verto.data.repository.TaskDraft`, `com.verto.domain.rules.TunnelItem`).

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :composeApp:jvmTest --console=plain`
Expected: compilation FAIL (`Unresolved reference 'tunnel'`, `'tunnelOther'`…).

- [ ] **Step 3: The view model**

In `composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayViewModel.kt`:

1. In `TodayUiState.Content`, add a last property:

```kotlin
        /** "Só 1 coisa": what to do now first, then the rest in order. */
        val tunnel: List<TunnelItem> = emptyList(),
```

2. Add the constructor parameter after `private val clock: Clock,`:

```kotlin
    private val steps: StepRepository,
```

3. After `private val expandedOverride = ...`, add:

```kotlin
    // "Outro" only hides an item for now; nothing about it is recorded anywhere.
    private val tunnelSkipped = MutableStateFlow<Set<String>>(emptySet())
```

4. In the day-change `onEach` (the one that resets `shownPlans` and `expandedOverride`), add `tunnelSkipped.value = emptySet()`.

5. Replace

```kotlin
            combine(shownPlans, expandedOverride, lastTick, ::Triple),
        ) { routineDay, taskList, steps, energy, (plans, expanded, now) ->
```

with

```kotlin
            combine(shownPlans, expandedOverride, lastTick, tunnelSkipped, ::ScreenInputs),
        ) { routineDay, taskList, steps, energy, (plans, expanded, now, skipped) ->
```

and replace

```kotlin
            TodayUiState.Content(today, plans, expanded ?: setOfNotNull(today.expandedAnchorId))
```

with

```kotlin
            val candidates = TunnelRules.candidates(today, plans)
            // Once everything was skipped, the list starts over instead of going empty.
            val tunnel = candidates.filter { it.key !in skipped }.ifEmpty { candidates }
            TodayUiState.Content(today, plans, expanded ?: setOfNotNull(today.expandedAnchorId), tunnel)
```

6. Add the functions before `private fun dayOf(...)`:

```kotlin
    fun tunnelDone(item: TunnelItem) = when (item) {
        is TunnelItem.RoutineStep -> toggleRoutineStep(item.step.id)
        is TunnelItem.TaskStep -> item.step?.let { toggleTaskStep(it.id) } ?: completeTask(item.task.id)
    }

    fun tunnelOther() {
        val content = state.value as? TodayUiState.Content ?: return
        val current = content.tunnel.firstOrNull() ?: return
        tunnelSkipped.update { skipped ->
            // The list already wrapped around: start a new round from here.
            if (content.tunnel.all { it.key in skipped }) setOf(current.key) else skipped + current.key
        }
    }

    fun splitStep(stepId: String, titles: List<String>) {
        viewModelScope.launch { steps.split(stepId, titles) }
    }
```

7. At the end of the file add:

```kotlin

private data class ScreenInputs(
    val plans: Map<String, Plan>,
    val expanded: Set<String>?,
    val now: Instant,
    val skipped: Set<String>,
)
```

Imports to add: `com.verto.data.repository.StepRepository`, `com.verto.domain.rules.TunnelItem`, `com.verto.domain.rules.TunnelRules`.

- [ ] **Step 4: The full-screen overlay**

`composeApp/src/commonMain/kotlin/com/verto/ui/today/TunnelOverlay.kt`:

```kotlin
package com.verto.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.verto.domain.model.StepEnergy
import com.verto.domain.rules.TunnelItem
import com.verto.ui.common.Hint
import com.verto.ui.common.label

internal fun TunnelItem.title(): String = when (this) {
    is TunnelItem.RoutineStep -> step.title
    is TunnelItem.TaskStep -> step?.title ?: task.title
}

internal fun TunnelItem.context(): String = when (this) {
    is TunnelItem.RoutineStep -> "${anchor.name} · ${routine.name}"
    is TunnelItem.TaskStep -> if (step != null) task.title else "Tarefa de hoje"
}

internal fun TunnelItem.energy(): StepEnergy? = when (this) {
    is TunnelItem.RoutineStep -> step.energy
    is TunnelItem.TaskStep -> step?.energy
}

internal fun TunnelItem.stepId(): String? = when (this) {
    is TunnelItem.RoutineStep -> step.id
    is TunnelItem.TaskStep -> step?.id
}

/** Everything else out of sight: one thing, and the four ways out of it. */
@Composable
internal fun TunnelOverlay(
    item: TunnelItem?,
    canSkip: Boolean,
    onDone: (TunnelItem) -> Unit,
    onOther: () -> Unit,
    onSplit: (stepId: String, titles: List<String>) -> Unit,
    onExit: () -> Unit,
) {
    Dialog(onDismissRequest = onExit, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                if (item == null) {
                    Text("Nada pra agora. Tá tudo bem.", style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Sair") }
                    return@Column
                }
                var splitting by remember(item.key) { mutableStateOf(false) }
                Text(item.context(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.title(), style = MaterialTheme.typography.headlineMedium)
                item.energy()?.let {
                    Text(it.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
                val stepId = item.stepId()
                if (splitting && stepId != null) {
                    SplitForm(
                        onCancel = { splitting = false },
                        onConfirm = { titles ->
                            onSplit(stepId, titles)
                            splitting = false
                        },
                    )
                } else {
                    Button(onClick = { onDone(item) }, modifier = Modifier.fillMaxWidth()) { Text("Feito") }
                    OutlinedButton(onClick = onOther, enabled = canSkip, modifier = Modifier.fillMaxWidth()) { Text("Outro") }
                    if (stepId != null) {
                        TextButton(onClick = { splitting = true }) { Text("Tá grande demais") }
                    }
                    TextButton(onClick = onExit) { Text("Sair") }
                }
            }
        }
    }
}

@Composable
private fun SplitForm(onCancel: () -> Unit, onConfirm: (List<String>) -> Unit) {
    var pieces by remember { mutableStateOf(listOf("", "")) }
    Hint("Quebra em pedaços menores. O primeiro vira o próximo.")
    pieces.forEachIndexed { index, text ->
        OutlinedTextField(
            value = text,
            onValueChange = { value -> pieces = pieces.toMutableList().also { it[index] = value } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pedaço ${index + 1}") },
            singleLine = true,
        )
    }
    TextButton(onClick = { pieces = pieces + "" }) { Text("Mais um pedaço") }
    Button(onClick = { onConfirm(pieces) }, enabled = pieces.any { it.isNotBlank() }, modifier = Modifier.fillMaxWidth()) {
        Text("Trocar por esses passos")
    }
    TextButton(onClick = onCancel) { Text("Cancelar") }
}
```

If `label()` for `StepEnergy` lives elsewhere than `com.verto.ui.common`, import it from where `TodayScreen.kt` imports it.

- [ ] **Step 5: Hoje uses the same selection and opens the overlay**

In `composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayScreen.kt`:

1. Replace the whole block that starts with `    val routineFocus = today.sections.asSequence()` and ends with `    val focus = routineFocus ?: taskFocus` with:

```kotlin
    val tunnelItem = content.tunnel.firstOrNull()
    val focus = tunnelItem?.let {
        FocusItem(title = it.title(), context = it.context(), energy = it.energy(), onComplete = { viewModel.tunnelDone(it) })
    }
    var tunnelOpen by rememberSaveable { mutableStateOf(false) }
    if (tunnelOpen) {
        TunnelOverlay(
            item = tunnelItem,
            canSkip = content.tunnel.size > 1,
            onDone = viewModel::tunnelDone,
            onOther = viewModel::tunnelOther,
            onSplit = viewModel::splitStep,
            onExit = { tunnelOpen = false },
        )
    }
```

2. Replace `            item { FocusCard(focus) }` with `            item { FocusCard(focus, onOpen = { tunnelOpen = true }) }`.

3. Replace

```kotlin
private fun FocusCard(focus: FocusItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
```

with

```kotlin
private fun FocusCard(focus: FocusItem, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
```

Remove imports that become unused (the compiler warns; keep the build warning-free for this file).

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :composeApp:jvmTest :androidApp:assembleDebug --console=plain`
Expected: PASS; BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp
git commit -m "feat(focus): Só 1 coisa em tela cheia (Feito, Outro, Tá grande demais, Sair)"
```

---

### Task 5: "Que bom te ver" after days away

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/AppViewModel.kt`, `composeApp/src/commonMain/kotlin/com/verto/ui/FreshStartScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/App.kt`, `di/VertoModule.kt`
- Test: `composeApp/src/jvmTest/kotlin/com/verto/ui/AppViewModelTest.kt`

**Interfaces:**
- Consumes: `UsageTracker.checkIn`, `ResetRepository.freshStart` (Task 3).
- Produces: `class AppViewModel(usage: UsageTracker, reset: ResetRepository)` with `val offerFreshStart: StateFlow<Boolean>`, `fun onForeground()`, `fun startClean()`, `fun keepAsItWas()`.

- [ ] **Step 1: Write the failing test**

`composeApp/src/jvmTest/kotlin/com/verto/ui/AppViewModelTest.kt`:

```kotlin
package com.verto.ui

import com.verto.data.repository.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

class AppViewModelTest : ViewModelTest() {

    private fun viewModel() = AppViewModel(graph.usage, graph.reset)

    @Test
    fun firstLaunch_goesStraightToTheApp() = runTest {
        val vm = viewModel()

        vm.onForeground()

        assertFalse(vm.offerFreshStart.value)
    }

    @Test
    fun afterDaysAway_offersAFreshStart_andResetClearsToday() = runTest {
        graph.clock.now = uiAt(10, 10)
        graph.usage.checkIn()
        graph.tasks.save(TaskDraft(null, "Fazer IR", pinnedToToday = true))
        graph.clock.now = uiAt(15, 10)
        val vm = viewModel()

        vm.onForeground()
        vm.offerFreshStart.first { it }
        vm.startClean()

        assertFalse(vm.offerFreshStart.value)
        graph.tasks.observeTasks().first { tasks -> tasks.none { it.pinnedToToday } }
    }

    @Test
    fun seeingHowItWas_keepsEverything() = runTest {
        graph.clock.now = uiAt(10, 10)
        graph.usage.checkIn()
        graph.tasks.save(TaskDraft(null, "Fazer IR", pinnedToToday = true))
        graph.clock.now = uiAt(15, 10)
        val vm = viewModel()
        vm.onForeground()
        vm.offerFreshStart.first { it }

        vm.keepAsItWas()

        assertFalse(vm.offerFreshStart.value)
        graph.tasks.observeTasks().first { tasks -> tasks.single().pinnedToToday }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :composeApp:jvmTest --console=plain`
Expected: compilation FAIL, `Unresolved reference 'AppViewModel'`.

- [ ] **Step 3: View model, screen, app**

`composeApp/src/commonMain/kotlin/com/verto/ui/AppViewModel.kt`:

```kotlin
package com.verto.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.ResetRepository
import com.verto.data.repository.UsageTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(private val usage: UsageTracker, private val reset: ResetRepository) : ViewModel() {

    private val _offerFreshStart = MutableStateFlow(false)
    val offerFreshStart: StateFlow<Boolean> = _offerFreshStart.asStateFlow()

    fun onForeground() {
        viewModelScope.launch {
            if (usage.checkIn()) _offerFreshStart.value = true
        }
    }

    fun startClean() {
        _offerFreshStart.value = false
        viewModelScope.launch { reset.freshStart() }
    }

    fun keepAsItWas() {
        _offerFreshStart.value = false
    }
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/FreshStartScreen.kt`:

```kotlin
package com.verto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verto.ui.common.Hint

/** Shown before any list after days away: coming back must not start with a wall of leftovers. */
@Composable
fun FreshStartScreen(onReset: () -> Unit, onKeep: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Que bom te ver.", style = MaterialTheme.typography.headlineMedium)
        Text("Quer começar com um dia limpo?", style = MaterialTheme.typography.titleLarge)
        Hint("Dia limpo tira tudo de Pra hoje e guarda os despejos soltos. Nada é apagado.")
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset") }
        OutlinedButton(onClick = onKeep, modifier = Modifier.fillMaxWidth()) { Text("Ver como estava") }
    }
}
```

Replace the whole of `composeApp/src/commonMain/kotlin/com/verto/ui/App.kt`:

`composeApp/src/commonMain/kotlin/com/verto/ui/App.kt`:

```kotlin
package com.verto.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.ui.navigation.VertoNavHost
import com.verto.ui.theme.VertoTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(appViewModel: AppViewModel = koinViewModel()) {
    val offerFreshStart by appViewModel.offerFreshStart.collectAsStateWithLifecycle()
    LifecycleResumeEffect(appViewModel) {
        appViewModel.onForeground()
        onPauseOrDispose { }
    }
    VertoTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (offerFreshStart) {
                FreshStartScreen(onReset = appViewModel::startClean, onKeep = appViewModel::keepAsItWas)
            } else {
                VertoNavHost()
            }
        }
    }
}
```

In `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt`, add `viewModelOf(::AppViewModel)` next to the other `viewModelOf` lines (import `com.verto.ui.AppViewModel`).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :composeApp:jvmTest :androidApp:assembleDebug --console=plain`
Expected: PASS; BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp
git commit -m "feat(focus): tela Que bom te ver depois de dias sem abrir"
```

---

### Task 6: Ajustes

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/settings/SettingsViewModel.kt`, `SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/navigation/Routes.kt`, `VertoNavHost.kt`, `plan/PlanScreen.kt`, `di/VertoModule.kt`
- Test: `composeApp/src/jvmTest/kotlin/com/verto/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository` (Task 1), `ResetRepository` (Task 3), `clockDigits`, `parseClock`, `hhmm`, `ClockMask` (`com.verto.ui.common`), `AccountRoute` (Plan 3).
- Produces: `class SettingsViewModel(settings: SettingsRepository, reset: ResetRepository)`; `SettingsRoute`; `PlanScreen(onOpenSettings = ...)` replacing `onOpenAccount`.

- [ ] **Step 1: Write the failing test**

`composeApp/src/jvmTest/kotlin/com/verto/ui/settings/SettingsViewModelTest.kt`:

```kotlin
package com.verto.ui.settings

import com.verto.data.repository.TaskDraft
import com.verto.ui.ViewModelTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class SettingsViewModelTest : ViewModelTest() {

    private fun viewModel() = SettingsViewModel(graph.settings, graph.reset)

    @Test
    fun dayStart_isSavedRightAway() {
        val vm = viewModel()

        vm.setDayStartHour(5)

        assertEquals(LocalTime(5, 0), graph.settings.current().dayStartsAt)
        assertEquals(5, vm.state.value.dayStartHour)
    }

    @Test
    fun absenceDays_stayBetweenTwoAndFourteen() {
        val vm = viewModel()

        repeat(5) { vm.changeAbsenceDays(-1) }
        assertEquals(2, vm.state.value.absenceDays)

        repeat(20) { vm.changeAbsenceDays(+1) }
        assertEquals(14, vm.state.value.absenceDays)
        assertEquals(14.days, graph.settings.current().absenceThreshold)
    }

    @Test
    fun quietHours_needTwoDifferentValidTimes() {
        val vm = viewModel()

        vm.setQuietStart("25")
        vm.saveQuietHours()
        assertEquals("Use horários como 22:00.", vm.state.value.message)

        vm.setQuietStart("2300")
        vm.setQuietEnd("2300")
        vm.saveQuietHours()
        assertEquals("O silêncio precisa começar e terminar em horários diferentes.", vm.state.value.message)

        vm.setQuietEnd("0700")
        vm.saveQuietHours()
        assertEquals("Horário de silêncio salvo.", vm.state.value.message)
        assertEquals(LocalTime(23, 0), graph.settings.current().quietStart)
        assertEquals(LocalTime(7, 0), graph.settings.current().quietEnd)
    }

    @Test
    fun freshStart_clearsToday_andSaysSo() = runTest {
        graph.tasks.save(TaskDraft(null, "Fazer IR", pinnedToToday = true))
        val vm = viewModel()

        vm.freshStart()

        vm.state.first { it.cleaned }
        graph.tasks.observeTasks().first { tasks -> tasks.none { it.pinnedToToday } }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :composeApp:jvmTest --console=plain`
Expected: compilation FAIL, `Unresolved reference 'SettingsViewModel'`.

- [ ] **Step 3: The view model**

`composeApp/src/commonMain/kotlin/com/verto/ui/settings/SettingsViewModel.kt`:

```kotlin
package com.verto.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.ResetRepository
import com.verto.data.settings.SettingsRepository
import com.verto.ui.common.clockDigits
import com.verto.ui.common.hhmm
import com.verto.ui.common.parseClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.days

data class SettingsUiState(
    val dayStartHour: Int,
    val absenceDays: Int,
    val quietStart: String,
    val quietEnd: String,
    val message: String? = null,
    val cleaned: Boolean = false,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val reset: ResetRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        settings.current().let {
            SettingsUiState(
                dayStartHour = it.dayStartsAt.hour,
                absenceDays = it.absenceThreshold.inWholeDays.toInt(),
                quietStart = clockDigits(it.quietStart.hhmm()),
                quietEnd = clockDigits(it.quietEnd.hhmm()),
            )
        },
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun setDayStartHour(hour: Int) {
        settings.update { it.copy(dayStartsAt = LocalTime(hour, 0)) }
        _state.update { it.copy(dayStartHour = hour) }
    }

    fun changeAbsenceDays(delta: Int) {
        val days = (_state.value.absenceDays + delta).coerceIn(MIN_ABSENCE_DAYS, MAX_ABSENCE_DAYS)
        settings.update { it.copy(absenceThreshold = days.days) }
        _state.update { it.copy(absenceDays = days) }
    }

    fun setQuietStart(text: String) = _state.update { it.copy(quietStart = clockDigits(text), message = null) }

    fun setQuietEnd(text: String) = _state.update { it.copy(quietEnd = clockDigits(text), message = null) }

    fun saveQuietHours() {
        val start = parseClock(_state.value.quietStart)
        val end = parseClock(_state.value.quietEnd)
        val message = when {
            start == null || end == null -> "Use horários como 22:00."
            start == end -> "O silêncio precisa começar e terminar em horários diferentes."
            else -> {
                settings.update { it.copy(quietStart = start, quietEnd = end) }
                "Horário de silêncio salvo."
            }
        }
        _state.update { it.copy(message = message) }
    }

    fun freshStart() {
        viewModelScope.launch {
            reset.freshStart()
            _state.update { it.copy(cleaned = true) }
        }
    }

    private companion object {
        const val MIN_ABSENCE_DAYS = 2
        const val MAX_ABSENCE_DAYS = 14
    }
}
```

- [ ] **Step 4: The screen**

`composeApp/src/commonMain/kotlin/com/verto/ui/settings/SettingsScreen.kt`:

```kotlin
package com.verto.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.ui.common.ClockMask
import com.verto.ui.common.EditorScaffold
import com.verto.ui.common.EditorSection
import com.verto.ui.common.Hint
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(onOpenAccount: () -> Unit, onClose: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EditorScaffold(title = "Ajustes", onBack = onClose) {
        EditorSection("Virada do dia", hint = "Antes desse horário ainda conta como ontem.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..6).forEach { hour ->
                    FilterChip(
                        selected = state.dayStartHour == hour,
                        onClick = { viewModel.setDayStartHour(hour) },
                        label = { Text("0$hour:00") },
                    )
                }
            }
        }
        EditorSection("Volta depois de um tempo", hint = "Depois de quantos dias sem abrir o Verto oferece um dia limpo.") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { viewModel.changeAbsenceDays(-1) }) { Text("−") }
                Text("${state.absenceDays} dias", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { viewModel.changeAbsenceDays(+1) }) { Text("+") }
            }
        }
        EditorSection("Horário de silêncio", hint = "Quando o Verto tiver notificações, elas ficam quietas nesse intervalo.") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.quietStart,
                    onValueChange = viewModel::setQuietStart,
                    modifier = Modifier.weight(1f),
                    label = { Text("De") },
                    singleLine = true,
                    visualTransformation = ClockMask,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = state.quietEnd,
                    onValueChange = viewModel::setQuietEnd,
                    modifier = Modifier.weight(1f),
                    label = { Text("Até") },
                    singleLine = true,
                    visualTransformation = ClockMask,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Button(onClick = viewModel::saveQuietHours, modifier = Modifier.fillMaxWidth()) { Text("Salvar horário") }
            state.message?.let { Hint(it) }
        }
        EditorSection("Dia limpo", hint = "Tira tudo de Pra hoje e guarda os despejos soltos. Nada é apagado.") {
            OutlinedButton(onClick = viewModel::freshStart, modifier = Modifier.fillMaxWidth()) { Text("Começar do zero") }
            if (state.cleaned) Hint("Pronto. O dia tá limpo.")
        }
        EditorSection("Conta e sync", hint = "Login, sair e quando foi a última sincronização.") {
            TextButton(onClick = onOpenAccount) { Text("Abrir Conta e sync") }
        }
    }
}
```

- [ ] **Step 5: Route, gear in Planejar, Koin**

Append to `composeApp/src/commonMain/kotlin/com/verto/ui/navigation/Routes.kt`:

```kotlin

@Serializable
data object SettingsRoute
```

In `composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanScreen.kt`:
- rename the parameter `onOpenAccount: () -> Unit,` to `onOpenSettings: () -> Unit,`;
- replace `IconButton(onClick = onOpenAccount) {` with `IconButton(onClick = onOpenSettings) {`;
- replace `Icon(Icons.Filled.AccountCircle, contentDescription = "Conta e sync")` with `Icon(Icons.Filled.Settings, contentDescription = "Ajustes")`;
- replace the import `androidx.compose.material.icons.filled.AccountCircle` with `androidx.compose.material.icons.filled.Settings`.

In `composeApp/src/commonMain/kotlin/com/verto/ui/navigation/VertoNavHost.kt`:
- replace `                    onOpenAccount = { nav.navigate(AccountRoute) },` with `                    onOpenSettings = { nav.navigate(SettingsRoute) },`;
- after `composable<AccountRoute> { ... }` add:

```kotlin
            composable<SettingsRoute> {
                SettingsScreen(onOpenAccount = { nav.navigate(AccountRoute) }, onClose = { nav.popBackStack() })
            }
```

(import `com.verto.ui.settings.SettingsScreen`).

In `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt`, add `viewModelOf(::SettingsViewModel)` (import `com.verto.ui.settings.SettingsViewModel`).

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :composeApp:jvmTest :androidApp:assembleDebug --console=plain`
Expected: PASS; BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp
git commit -m "feat(settings): tela Ajustes (virada do dia, ausência, silêncio, dia limpo, conta)"
```

---

### Task 7: Run it and document it

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: the whole branch.
- Produces: a verified install and a README section.

- [ ] **Step 1: Full suite**

Run: `./gradlew :shared:domain:jvmTest :shared:data:jvmTest :composeApp:jvmTest :androidApp:assembleDebug --console=plain`
Expected: all PASS; BUILD SUCCESSFUL.

- [ ] **Step 2: Walk the flows on the emulator**

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:installDebug
```

| # | Do | Expected |
|---|---|---|
| 1 | Hoje with a fired anchor and a pinned task | The focus card shows the routine step; tapping the card opens "Só 1 coisa" in full screen |
| 2 | "Outro" | The pinned task shows; "Outro" again comes back to the routine step |
| 3 | "Tá grande demais" on a task step → two pieces → "Trocar por esses passos" | The first piece is now the item; the task editor lists the pieces in that place |
| 4 | "Feito" until nothing fits | "Nada pra agora. Tá tudo bem." |
| 5 | Planejar → engrenagem → Ajustes → virada 00:00 at a time between 00:00 and 04:00 | Back on Hoje (after resume) the date is the calendar day |
| 6 | Ajustes → horário de silêncio 23 / 23 → Salvar | Calm message about different times; 23 / 07 saves |
| 7 | Ajustes → "Começar do zero" | "Pronto. O dia tá limpo."; Hoje has no "Pra hoje" |
| 8 | `adb -s emulator-5554 shell "date"` shows today; set the emulator clock 5 days ahead (Settings → System → Date & time, automatic off) and reopen the app | "Que bom te ver." with Reset and Ver como estava; restore automatic time afterwards |

Screenshot rows 1, 5 and 8 for the record. If a row fails, debug with superpowers:systematic-debugging, add a test that reproduces it, fix, rerun the suite.

- [ ] **Step 3: README**

Append to `README.md`:

````markdown

## Só 1 coisa, dia limpo e Ajustes

- **Só 1 coisa**: o card do topo da Hoje mostra um passo só, escolhido pela energia do dia (baixa = só passos leves; média = leves e médios; alta = todos), na ordem: rotina de âncora que já disparou → tarefa com prazo real → tarefa fixada. Tocar no card abre em tela cheia com Feito, Outro, Tá grande demais e Sair.
- **Dia limpo**: tira tudo de Pra hoje e incuba os despejos soltos. Nada é apagado nem marcado como atrasado. Fica em Ajustes e na tela "Que bom te ver", que aparece depois de alguns dias sem abrir o app.
- **Ajustes** (engrenagem no Planejar): virada do dia, dias de ausência, horário de silêncio e Conta e sync. Os ajustes ficam neste aparelho.

```bash
./gradlew :shared:domain:jvmTest --tests '*TunnelRules*'
```
````

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: README com Só 1 coisa, dia limpo e Ajustes"
```
