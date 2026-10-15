# VertoApp — Plano 2: app offline (domínio, banco local e as 3 abas)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app (KMP + Compose Multiplatform) where Jess captures braindumps, plans tasks with micro-steps and elastic A/C routines anchored to events, and sees a calm "Hoje" — all working 100% offline, with every write already queued in a local outbox for the sync engine of Plan 3.

**Architecture:** Four Gradle modules. `:shared:domain` is pure Kotlin (entities + rules, no platform code) and holds most tests. `:shared:data` is Room KMP: one table per entity plus an `outbox` table written in the same transaction as every entity write. `:composeApp` is a KMP library with the Compose UI, ViewModels and Koin wiring. `:androidApp` is the thin Android application (AGP 9 no longer lets a KMP module be the application). Targets: Android + JVM; the JVM target runs the tests and keeps Android APIs out of common code.

**Tech Stack:** Kotlin 2.4.21, AGP 9.4.1, Gradle 9.4.1, Compose Multiplatform 1.12.2 (Material3 1.9.0), JetBrains lifecycle 2.11.0 / navigation-compose 2.9.2, Room 2.8.5 + KSP 2.3.12 + sqlite-bundled 2.7.1, Koin 4.2.2, kotlinx-datetime 0.8.0 (`kotlin.time.Instant`/`Clock`), kotlinx-coroutines 1.11.0, kotlinx-serialization 1.11.0, kotlin.test.

**Spec:** `~/Repos/Verto/docs/superpowers/specs/2026-10-15-verto-design.md` (sections "Modelo de domínio", "Regras de domínio", "Telas e superfícies", "Arquitetura"). Server contract the local payloads must match: `~/Repos/Verto/src/Verto.Application/UseCases/Sync/SyncEntityCatalog.cs`.

**Scope:** Plan 2 of the spec's build order. Out of scope here: sync engine and login (Plan 3); Tunnel Vision, reset button, return-after-absence screen and Ajustes (Plan 4 — the domain rules for reset and absence are built and tested here); widget, Quick Settings tile, share target, notifications (Plan 5); voice capture (v2).

**Reference repo:** `~/Repos/BillFolderApp` (Android-only: Hilt/Retrofit/Room 2.6). Copy its Gradle wrapper and `.gitignore`; its libraries are replaced by Koin, Room KMP and kotlinx in this project.

## Global Constraints

- Package root `com.verto`; applicationId `com.verto.android`; `minSdk = 26`, `compileSdk = 36`, `targetSdk = 36`; JVM bytecode 17.
- Versions exactly as in `gradle/libs.versions.toml` (Task 1). Adding a dependency not in that file is a deviation to ledger.
- Code, identifiers and comments in English; every user-facing string in Brazilian Portuguese; commit subjects in Portuguese, BillFolder style (`feat(today): ...`). Comments explain WHY only.
- Planner business rules live in `:shared:domain` (spec: "regras de negócio no cliente"); ViewModels only orchestrate.
- Every entity write goes through `LocalStore.write { put(...) }`, which upserts the row and enqueues an outbox row in the same transaction. No DAO write outside `WriteScope`.
- Every entity row has a client-generated UUID `id`, `updatedAt` and `deletedAt` (soft delete). Reads filter `deletedAt IS NULL`.
- Logical day turns at **04:00** (`VertoSettings.dayStartsAt`); every "today" in the app is the logical day.
- Plan C counts as success: no streaks, no "falhou", no counters of pending items, no alarm red, and the word "atrasado" never appears. Overdue hard deadlines read "prazo era dia DD/MM".
- Pinned-to-today soft limit is **3** (gentle note, never blocks). Hard deadlines show from **3** days ahead. Raw braindumps incubate after **3** days, silently.
- Calm UI: no navigation animations, generous spacing, body text 18sp.
- Local payload keys must match `SyncEntityCatalog.cs` exactly: `inbox_item` {text, createdAt, source, state}; `task` {title, pinnedToToday}; `step` {order, title, energy, and taskId or routineId+plan}; `anchor` {name, windowStart, windowEnd}; `anchor_event` {anchorId, day, triggeredAt, source}; `routine` {name, anchorId, weekdays}; `routine_log` {routineId, day, outcome}; `day_energy` {day, level, setAt}.

## Review Focus

1. **Acting after midnight.** Checking a routine step at 01:00 must count for the logical day that started at 04:00 the previous calendar day, not for "tomorrow". Pinned by `toggleStep_afterMidnight_countsForTheLogicalDay` (Task 7).
2. **Deleting an anchor that routines still use.** Must be refused with a calm message, never leave orphaned routines that silently vanish from Hoje. Pinned by `deleteAnchor_inUse_isRefused` (Task 7).
3. **Editing a task's steps.** Removing the only undone step finishes the task; adding a new step to a finished task reopens it. Pinned by `save_removingLastOpenStep_finishesTask` and `save_addingStepToDoneTask_reopensIt` (Task 7).
4. **Blank capture.** Whitespace-only braindumps must not create items (a mis-tap on "Guardar" is common). Pinned by `capture_blank_isIgnored` (Task 7).
5. **The day turns while the app is open.** At 04:00 Hoje must move to the new day and drop yesterday's plan choices. Pinned by `dayRollover_movesToNewDay_andResetsPlanChoices` (Task 12).

## File Structure

```
VertoApp/
├── settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew(+bat), gradle/wrapper/*
├── shared/domain/                      pure Kotlin, android + jvm
│   └── src/commonMain/kotlin/com/verto/domain/
│       ├── VertoSettings.kt            tunables (day start, limits)
│       ├── model/Model.kt              entities + enums
│       └── rules/                      LogicalDay, AnchorRules, RoutineRules, TodayBuilder, TaskRules, InboxRules, ResetRules, AbsenceRules
├── shared/data/                        Room KMP, android + jvm
│   └── src/
│       ├── commonMain/kotlin/com/verto/data/
│       │   ├── db/                     entities, DAOs, VertoDatabase
│       │   ├── sync/                   payloads, Wire (enum <-> string), LocalStore/WriteScope (outbox)
│       │   ├── Mappers.kt, Platform.kt (Clock/Id/Zone seams)
│       │   └── repository/             Inbox, Task, Routine, Day repositories + drafts
│       ├── androidMain/…/DatabaseBuilder.android.kt
│       └── jvmMain/…/DatabaseBuilder.jvm.kt
├── composeApp/                         Compose UI library, android + jvm
│   └── src/commonMain/kotlin/com/verto/ui/
│       ├── App.kt, di/VertoModule.kt, navigation/ (routes + NavHost)
│       ├── theme/Theme.kt, common/ (Formats, Labels, Ticker, StepListEditor)
│       ├── today/, inbox/, plan/, editor/ (task, routine, anchor)
└── androidApp/                         application: VertoApplication (Koin + DB), MainActivity
```

---

### Task 1: Project skeleton (4 modules build, domain test runs)

**Files:**
- Create (copied): `.gitignore`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `local.properties`
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `shared/domain/build.gradle.kts`, `shared/domain/src/commonMain/kotlin/com/verto/domain/VertoSettings.kt`
- Create: `shared/data/build.gradle.kts` (empty source sets until Task 6)
- Create: `composeApp/build.gradle.kts`, `composeApp/src/commonMain/kotlin/com/verto/ui/App.kt`
- Create: `androidApp/build.gradle.kts`, `androidApp/src/main/AndroidManifest.xml`, `androidApp/src/main/kotlin/com/verto/android/MainActivity.kt`
- Test: `shared/domain/src/commonTest/kotlin/com/verto/domain/VertoSettingsTest.kt`

**Interfaces:**
- Produces: Gradle project accessors `projects.shared.domain`, `projects.shared.data`, `projects.composeApp`; catalog aliases used by every later task; `VertoSettings(dayStartsAt, absenceThreshold, incubationAfter, pinnedSoftLimit, deadlineHorizonDays)`; `@Composable fun App()`.

- [ ] **Step 1: Copy wrapper, ignore file and SDK path from BillFolderApp**

```bash
cd ~/Repos/VertoApp
cp ~/Repos/BillFolderApp/gradlew ~/Repos/BillFolderApp/gradlew.bat ~/Repos/BillFolderApp/.gitignore ~/Repos/BillFolderApp/local.properties .
mkdir -p gradle/wrapper && cp ~/Repos/BillFolderApp/gradle/wrapper/* gradle/wrapper/
printf '\n**/build/\n.kotlin/\n' >> .gitignore
grep distributionUrl gradle/wrapper/gradle-wrapper.properties
```

Expected: `distributionUrl=...gradle-9.4.1-bin.zip`. `local.properties` is git-ignored; it only carries `sdk.dir`.

- [ ] **Step 2: Write the root build files and version catalog**

`settings.gradle.kts`:

```kotlin
rootProject.name = "VertoApp"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":shared:domain")
include(":shared:data")
include(":composeApp")
include(":androidApp")
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
kotlin.code.style=official
android.useAndroidX=true
```

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.21"
ksp = "2.3.12"
composeMultiplatform = "1.12.2"
composeMaterial3 = "1.9.0"
composeMaterialIcons = "1.7.3"
jetbrainsLifecycle = "2.11.0"
jetbrainsNavigation = "2.9.2"
androidxActivity = "1.13.0"
room = "2.8.5"
sqlite = "2.7.1"
koin = "4.2.2"
kotlinxCoroutines = "1.11.0"
kotlinxDatetime = "0.8.0"
kotlinxSerialization = "1.11.0"

[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "composeMultiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "composeMultiplatform" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "composeMultiplatform" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "composeMaterial3" }
compose-material-icons-core = { module = "org.jetbrains.compose.material:material-icons-core", version.ref = "composeMaterialIcons" }
androidx-lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "jetbrainsLifecycle" }
androidx-lifecycle-runtime-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "jetbrainsLifecycle" }
androidx-navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "jetbrainsNavigation" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidxActivity" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
koin-bom = { module = "io.insert-koin:koin-bom", version.ref = "koin" }
koin-core = { module = "io.insert-koin:koin-core" }
koin-android = { module = "io.insert-koin:koin-android" }
koin-compose = { module = "io.insert-koin:koin-compose" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel" }

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidKotlinMultiplatformLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 3: Write the module build files**

`shared/domain/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    android {
        namespace = "com.verto.domain"
        compileSdk = 36
        minSdk = 26
        compilerOptions.configure { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
```

`shared/data/build.gradle.kts` (Room arrives in Task 6; the module compiles empty until then):

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    android {
        namespace = "com.verto.data"
        compileSdk = 36
        minSdk = 26
        compilerOptions.configure { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.domain)
        }
    }
}
```

`composeApp/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.verto.ui"
        compileSdk = 36
        minSdk = 26
        compilerOptions.configure { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    compilerOptions {
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.domain)
            implementation(projects.shared.data)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

`androidApp/build.gradle.kts` (AGP 9 compiles Kotlin itself; only the Compose compiler plugin is applied):

```kotlin
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.verto.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.verto.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.shared.data)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
}
```

- [ ] **Step 4: Write the failing settings test**

`shared/domain/src/commonTest/kotlin/com/verto/domain/VertoSettingsTest.kt`:

```kotlin
package com.verto.domain

import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class VertoSettingsTest {

    @Test
    fun defaultsMatchTheSpec() {
        val settings = VertoSettings()

        assertEquals(LocalTime(4, 0), settings.dayStartsAt)
        assertEquals(4.days, settings.absenceThreshold)
        assertEquals(3.days, settings.incubationAfter)
        assertEquals(3, settings.pinnedSoftLimit)
        assertEquals(3, settings.deadlineHorizonDays)
    }
}
```

- [ ] **Step 5: Run it to verify it fails**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:domain:jvmTest`
Expected: FAIL — compilation error `Unresolved reference 'VertoSettings'`. (The first run downloads Gradle 9.4.1 and dependencies.)

- [ ] **Step 6: Write `VertoSettings`, the placeholder UI and the Android shell**

`shared/domain/src/commonMain/kotlin/com/verto/domain/VertoSettings.kt`:

```kotlin
package com.verto.domain

import kotlinx.datetime.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** Values the spec calls configurable; they become editable with the Ajustes screen (Plan 4). */
data class VertoSettings(
    val dayStartsAt: LocalTime = LocalTime(4, 0),
    val absenceThreshold: Duration = 4.days,
    val incubationAfter: Duration = 3.days,
    val pinnedSoftLimit: Int = 3,
    val deadlineHorizonDays: Int = 3,
)
```

`composeApp/src/commonMain/kotlin/com/verto/ui/App.kt` (replaced in Task 8):

```kotlin
package com.verto.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun App() {
    MaterialTheme {
        Text("Verto")
    }
}
```

`androidApp/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:label="Verto"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

`androidApp/src/main/kotlin/com/verto/android/MainActivity.kt`:

```kotlin
package com.verto.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.verto.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}
```

- [ ] **Step 7: Run the test and build the APK**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:domain:jvmTest :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`; the test report shows `VertoSettingsTest > defaultsMatchTheSpec PASSED` (check with `--info` or `shared/domain/build/reports/tests/jvmTest/index.html`).

If Gradle rejects a DSL name (for example the `android {}` block inside `kotlin {}` on this AGP), use the name the error suggests and ledger the ruling — the module layout stays.

- [ ] **Step 8: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "chore: esqueleto KMP do VertoApp (domain, data, composeApp, androidApp)"
```

---

### Task 2: Domain model and the logical day

**Files:**
- Create: `shared/domain/src/commonMain/kotlin/com/verto/domain/model/Model.kt`
- Create: `shared/domain/src/commonMain/kotlin/com/verto/domain/rules/LogicalDay.kt`
- Create: `shared/domain/src/commonTest/kotlin/com/verto/domain/Fixtures.kt`
- Test: `shared/domain/src/commonTest/kotlin/com/verto/domain/rules/LogicalDayTest.kt`

**Interfaces:**
- Produces (package `com.verto.domain.model`): enums `StepEnergy {Low, Medium, Hyperfocus}`, `EnergyLevel {Low, Medium, High}`, `InboxSource {Widget, Tile, Share, App}`, `InboxState {Raw, Processed, Incubated, Discarded}`, `DueKind {Hard, Soft}`, `Plan {A, C}`, `RoutineOutcome {A, C, Skipped}`, `AnchorTrigger {Tap, Window}`; data classes `InboxItem(id, text, createdAt: Instant, source, state)`, `Task(id, title, notes: String?, dueDate: LocalDate?, dueKind: DueKind?, pinnedToToday: Boolean, doneAt: Instant?)`, `Step(id, owner: StepOwner, order: Int, title, energy: StepEnergy, doneAt: Instant?)`, `StepOwner.OfTask(taskId)` / `StepOwner.OfRoutine(routineId, plan)`, `Anchor(id, name, windowStart: LocalTime, windowEnd: LocalTime)`, `AnchorEvent(id, anchorId, day: LocalDate, triggeredAt: Instant, source: AnchorTrigger)`, `Routine(id, name, anchorId, weekdays: Set<DayOfWeek>)`, `RoutineLog(id, routineId, day, outcome)`, `DayEnergy(id, day, level, setAt)`.
- Produces: `LogicalDay.of(instant: Instant, zone: TimeZone, dayStartsAt: LocalTime): LocalDate`, `LogicalDay.startOf(day: LocalDate, zone: TimeZone, dayStartsAt: LocalTime): Instant`.
- Produces (test-only, `com.verto.domain` commonTest): `SaoPaulo: TimeZone`, `FourAm: LocalTime`, `Oct15: LocalDate` (a Thursday), `at(day: Int, hour: Int, minute: Int = 0): Instant` (October 2026, São Paulo), builders `anchor(...)`, `routine(...)`, `routineStep(...)`, `taskStep(...)`, `task(...)`.

`Task.doneAt` is not in the spec's table: a task without steps needs a way to be finished, and Hoje must stop showing finished tasks. It travels in the `task` payload as an extra key (the server stores payloads opaquely).

- [ ] **Step 1: Write the fixtures and the failing test**

`shared/domain/src/commonTest/kotlin/com/verto/domain/Fixtures.kt`:

```kotlin
package com.verto.domain

import com.verto.domain.model.Anchor
import com.verto.domain.model.DueKind
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.Step
import com.verto.domain.model.StepEnergy
import com.verto.domain.model.StepOwner
import com.verto.domain.model.Task
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

val SaoPaulo: TimeZone = TimeZone.of("America/Sao_Paulo")
val FourAm = LocalTime(4, 0)

/** Thursday. */
val Oct15 = LocalDate(2026, 10, 15)

fun at(day: Int, hour: Int, minute: Int = 0): Instant =
    LocalDateTime(2026, 10, day, hour, minute).toInstant(SaoPaulo)

fun anchor(id: String, start: Int, end: Int = start + 2, name: String = id) =
    Anchor(id, name, LocalTime(start, 0), LocalTime(end, 0))

fun routine(id: String, anchorId: String, weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(), name: String = id) =
    Routine(id, name, anchorId, weekdays)

fun routineStep(id: String, routineId: String, plan: Plan, order: Int = 0, doneAt: Instant? = null, energy: StepEnergy = StepEnergy.Low) =
    Step(id, StepOwner.OfRoutine(routineId, plan), order, id, energy, doneAt)

fun taskStep(id: String, taskId: String, order: Int = 0, doneAt: Instant? = null) =
    Step(id, StepOwner.OfTask(taskId), order, id, StepEnergy.Low, doneAt)

fun task(
    id: String,
    pinned: Boolean = false,
    dueDate: LocalDate? = null,
    dueKind: DueKind? = null,
    doneAt: Instant? = null,
) = Task(id, id, null, dueDate, dueKind, pinned, doneAt)
```

`shared/domain/src/commonTest/kotlin/com/verto/domain/rules/LogicalDayTest.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.FourAm
import com.verto.domain.Oct15
import com.verto.domain.SaoPaulo
import com.verto.domain.at
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class LogicalDayTest {

    @Test
    fun beforeDayStart_belongsToPreviousDay() {
        assertEquals(LocalDate(2026, 10, 15), LogicalDay.of(at(16, 3, 59), SaoPaulo, FourAm))
    }

    @Test
    fun atDayStart_belongsToSameDay() {
        assertEquals(Oct15, LogicalDay.of(at(15, 4, 0), SaoPaulo, FourAm))
    }

    @Test
    fun lateNight_belongsToSameDay() {
        assertEquals(Oct15, LogicalDay.of(at(15, 23, 30), SaoPaulo, FourAm))
    }

    @Test
    fun startOf_isDayStartInTheZone() {
        assertEquals(at(15, 4, 0), LogicalDay.startOf(Oct15, SaoPaulo, FourAm))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:domain:jvmTest --tests "com.verto.domain.rules.LogicalDayTest"`
Expected: FAIL — `Unresolved reference 'Anchor'` / `'LogicalDay'`.

- [ ] **Step 3: Write the model and `LogicalDay`**

`shared/domain/src/commonMain/kotlin/com/verto/domain/model/Model.kt`:

```kotlin
package com.verto.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class StepEnergy { Low, Medium, Hyperfocus }

enum class EnergyLevel { Low, Medium, High }

enum class InboxSource { Widget, Tile, Share, App }

enum class InboxState { Raw, Processed, Incubated, Discarded }

enum class DueKind { Hard, Soft }

enum class Plan { A, C }

enum class RoutineOutcome { A, C, Skipped }

enum class AnchorTrigger { Tap, Window }

data class InboxItem(
    val id: String,
    val text: String,
    val createdAt: Instant,
    val source: InboxSource,
    val state: InboxState,
)

data class Task(
    val id: String,
    val title: String,
    val notes: String?,
    val dueDate: LocalDate?,
    val dueKind: DueKind?,
    val pinnedToToday: Boolean,
    val doneAt: Instant?,
)

sealed interface StepOwner {
    data class OfTask(val taskId: String) : StepOwner
    data class OfRoutine(val routineId: String, val plan: Plan) : StepOwner
}

data class Step(
    val id: String,
    val owner: StepOwner,
    val order: Int,
    val title: String,
    val energy: StepEnergy,
    val doneAt: Instant?,
)

data class Anchor(
    val id: String,
    val name: String,
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
)

data class AnchorEvent(
    val id: String,
    val anchorId: String,
    val day: LocalDate,
    val triggeredAt: Instant,
    val source: AnchorTrigger,
)

data class Routine(
    val id: String,
    val name: String,
    val anchorId: String,
    val weekdays: Set<DayOfWeek>,
)

data class RoutineLog(
    val id: String,
    val routineId: String,
    val day: LocalDate,
    val outcome: RoutineOutcome,
)

data class DayEnergy(
    val id: String,
    val day: LocalDate,
    val level: EnergyLevel,
    val setAt: Instant,
)
```

`shared/domain/src/commonMain/kotlin/com/verto/domain/rules/LogicalDay.kt`:

```kotlin
package com.verto.domain.rules

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The planner's day turns at [dayStartsAt] (04:00 by default), so 01:00 still belongs to "yesterday". */
object LogicalDay {

    fun of(instant: Instant, zone: TimeZone, dayStartsAt: LocalTime): LocalDate {
        val local = instant.toLocalDateTime(zone)
        return if (local.time < dayStartsAt) local.date.minus(1, DateTimeUnit.DAY) else local.date
    }

    fun startOf(day: LocalDate, zone: TimeZone, dayStartsAt: LocalTime): Instant =
        day.atTime(dayStartsAt).toInstant(zone)
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:domain:jvmTest`
Expected: PASS — 5 tests (1 settings + 4 logical day).

- [ ] **Step 5: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "feat(domain): modelo do planner e dia lógico com virada às 4h"
```

---

### Task 3: Anchor rules

**Files:**
- Create: `shared/domain/src/commonMain/kotlin/com/verto/domain/rules/AnchorRules.kt`
- Test: `shared/domain/src/commonTest/kotlin/com/verto/domain/rules/AnchorRulesTest.kt`

**Interfaces:**
- Consumes: model + `LogicalDay` + fixtures (Task 2).
- Produces: `AnchorRules.windowOpensAt(anchor: Anchor, day: LocalDate, zone: TimeZone, dayStartsAt: LocalTime): Instant`; `AnchorRules.isTriggered(anchor: Anchor, day: LocalDate, events: List<AnchorEvent>): Boolean`; `AnchorRules.canTap(anchor: Anchor, day: LocalDate, events: List<AnchorEvent>): Boolean`; `AnchorRules.windowsToRecord(anchors: List<Anchor>, day: LocalDate, events: List<AnchorEvent>, now: Instant, zone: TimeZone, dayStartsAt: LocalTime): List<Anchor>`.

- [ ] **Step 1: Write the failing test**

`shared/domain/src/commonTest/kotlin/com/verto/domain/rules/AnchorRulesTest.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.FourAm
import com.verto.domain.Oct15
import com.verto.domain.SaoPaulo
import com.verto.domain.anchor
import com.verto.domain.at
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.AnchorTrigger
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnchorRulesTest {

    private val breakfast = anchor("breakfast", start = 6)
    private val lunch = anchor("lunch", start = 12)
    private val dinner = anchor("dinner", start = 19)

    private fun tapped(anchorId: String) =
        AnchorEvent("e-$anchorId", anchorId, Oct15, at(15, 7), AnchorTrigger.Tap)

    @Test
    fun windowOpensAt_isWindowStartOnTheLogicalDay() {
        assertEquals(at(15, 12), AnchorRules.windowOpensAt(lunch, Oct15, SaoPaulo, FourAm))
    }

    @Test
    fun windowOpensAt_beforeDayStart_fallsOnTheNextCalendarDate() {
        val lateNight = anchor("late", start = 1)

        assertEquals(at(16, 1), AnchorRules.windowOpensAt(lateNight, Oct15, SaoPaulo, FourAm))
    }

    @Test
    fun windowsToRecord_returnsOpenedWindowsNotYetTriggered() {
        val result = AnchorRules.windowsToRecord(
            anchors = listOf(breakfast, lunch, dinner),
            day = Oct15,
            events = listOf(tapped("breakfast")),
            now = at(15, 12, 30),
            zone = SaoPaulo,
            dayStartsAt = FourAm,
        )

        assertEquals(listOf(lunch), result)
    }

    @Test
    fun canTap_onlyBeforeAnythingTriggeredTheAnchorToday() {
        assertTrue(AnchorRules.canTap(lunch, Oct15, emptyList()))
        assertFalse(AnchorRules.canTap(breakfast, Oct15, listOf(tapped("breakfast"))))
    }

    @Test
    fun isTriggered_ignoresEventsFromOtherDays() {
        val yesterday = AnchorEvent("old", "lunch", LocalDate(2026, 10, 14), at(14, 12), AnchorTrigger.Window)

        assertFalse(AnchorRules.isTriggered(lunch, Oct15, listOf(yesterday)))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:domain:jvmTest --tests "com.verto.domain.rules.AnchorRulesTest"`
Expected: FAIL — `Unresolved reference 'AnchorRules'`.

- [ ] **Step 3: Write `AnchorRules`**

`shared/domain/src/commonMain/kotlin/com/verto/domain/rules/AnchorRules.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * An anchor fires once per logical day: whichever comes first, the user's tap
 * ("Almocei") or its time window opening, which is the safety net for forgetful days.
 */
object AnchorRules {

    fun windowOpensAt(anchor: Anchor, day: LocalDate, zone: TimeZone, dayStartsAt: LocalTime): Instant {
        // A window before the day start (e.g. 01:00) belongs to the end of the logical day.
        val date = if (anchor.windowStart < dayStartsAt) day.plus(1, DateTimeUnit.DAY) else day
        return date.atTime(anchor.windowStart).toInstant(zone)
    }

    fun isTriggered(anchor: Anchor, day: LocalDate, events: List<AnchorEvent>): Boolean =
        events.any { it.anchorId == anchor.id && it.day == day }

    fun canTap(anchor: Anchor, day: LocalDate, events: List<AnchorEvent>): Boolean =
        !isTriggered(anchor, day, events)

    fun windowsToRecord(
        anchors: List<Anchor>,
        day: LocalDate,
        events: List<AnchorEvent>,
        now: Instant,
        zone: TimeZone,
        dayStartsAt: LocalTime,
    ): List<Anchor> = anchors.filter { anchor ->
        !isTriggered(anchor, day, events) && now >= windowOpensAt(anchor, day, zone, dayStartsAt)
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:domain:jvmTest`
Expected: PASS — 10 tests.

- [ ] **Step 5: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "feat(domain): âncoras disparam por toque ou pela janela de horário"
```

---

### Task 4: Routine rules and the "Hoje" builder

**Files:**
- Create: `shared/domain/src/commonMain/kotlin/com/verto/domain/rules/RoutineRules.kt`
- Create: `shared/domain/src/commonMain/kotlin/com/verto/domain/rules/TodayBuilder.kt`
- Test: `shared/domain/src/commonTest/kotlin/com/verto/domain/rules/RoutineRulesTest.kt`
- Test: `shared/domain/src/commonTest/kotlin/com/verto/domain/rules/TodayBuilderTest.kt`

**Interfaces:**
- Consumes: model, `LogicalDay`, `AnchorRules`, `VertoSettings`, fixtures.
- Produces: `RoutineRules.suggestedPlan(energy: EnergyLevel): Plan`; `RoutineRules.isScheduledOn(routine: Routine, day: LocalDate): Boolean`; `RoutineRules.stepsOf(routineId: String, plan: Plan, steps: List<Step>): List<Step>`; `RoutineRules.isDoneOn(step: Step, day: LocalDate, zone: TimeZone, dayStartsAt: LocalTime): Boolean`; `RoutineRules.completedOutcome(routineId: String, steps: List<Step>, day: LocalDate, zone: TimeZone, dayStartsAt: LocalTime): RoutineOutcome?`.
- Produces: `TodayInput(day, zone, settings, energy: EnergyLevel, anchors, routines, steps, logs, events, tasks)`; `Today(day, energy, sections: List<AnchorSection>, expandedAnchorId: String?, pinned: List<TaskCard>, pinnedOverSoftLimit: Boolean, deadlines: List<TaskCard>)`; `AnchorSection(anchor, triggered: Boolean, routines: List<RoutineCard>)`; `RoutineCard(routine, suggestedPlan: Plan, stepsA: List<StepState>, stepsC: List<StepState>, outcome: RoutineOutcome?)` with `fun steps(plan: Plan): List<StepState>`; `StepState(step, done: Boolean)`; `TaskCard(task, nextStep: Step?, doneSteps: Int, totalSteps: Int)`; `TodayBuilder.build(input: TodayInput): Today`.

- [ ] **Step 1: Write the failing tests**

`shared/domain/src/commonTest/kotlin/com/verto/domain/rules/RoutineRulesTest.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.FourAm
import com.verto.domain.Oct15
import com.verto.domain.SaoPaulo
import com.verto.domain.at
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.routine
import com.verto.domain.routineStep
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineRulesTest {

    private fun outcome(vararg steps: com.verto.domain.model.Step) =
        RoutineRules.completedOutcome("r", steps.toList(), Oct15, SaoPaulo, FourAm)

    @Test
    fun suggestedPlan_isMinimalOnlyOnLowEnergy() {
        assertEquals(Plan.C, RoutineRules.suggestedPlan(EnergyLevel.Low))
        assertEquals(Plan.A, RoutineRules.suggestedPlan(EnergyLevel.Medium))
        assertEquals(Plan.A, RoutineRules.suggestedPlan(EnergyLevel.High))
    }

    @Test
    fun isScheduledOn_followsWeekdays() {
        val weekends = routine("r", "a", weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))

        assertFalse(RoutineRules.isScheduledOn(weekends, Oct15))
        assertTrue(RoutineRules.isScheduledOn(routine("r", "a"), Oct15))
    }

    @Test
    fun completedOutcome_planAComplete_isA() {
        assertEquals(
            RoutineOutcome.A,
            outcome(routineStep("a1", "r", Plan.A, doneAt = at(15, 8)), routineStep("c1", "r", Plan.C)),
        )
    }

    @Test
    fun completedOutcome_onlyPlanCComplete_isC() {
        assertEquals(
            RoutineOutcome.C,
            outcome(routineStep("a1", "r", Plan.A), routineStep("c1", "r", Plan.C, doneAt = at(15, 8))),
        )
    }

    @Test
    fun completedOutcome_bothComplete_isA() {
        assertEquals(
            RoutineOutcome.A,
            outcome(routineStep("a1", "r", Plan.A, doneAt = at(15, 8)), routineStep("c1", "r", Plan.C, doneAt = at(15, 8))),
        )
    }

    @Test
    fun completedOutcome_stepsDoneYesterday_doNotCount() {
        assertNull(outcome(routineStep("a1", "r", Plan.A, doneAt = at(14, 20))))
    }

    @Test
    fun completedOutcome_emptyPlan_neverCompletes() {
        assertNull(outcome(routineStep("a1", "r", Plan.A)))
    }

    @Test
    fun isDoneOn_afterMidnight_countsForTheLogicalDay() {
        val step = routineStep("a1", "r", Plan.A, doneAt = at(16, 1))

        assertTrue(RoutineRules.isDoneOn(step, Oct15, SaoPaulo, FourAm))
    }
}
```

`shared/domain/src/commonTest/kotlin/com/verto/domain/rules/TodayBuilderTest.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.Oct15
import com.verto.domain.SaoPaulo
import com.verto.domain.VertoSettings
import com.verto.domain.anchor
import com.verto.domain.at
import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.AnchorTrigger
import com.verto.domain.model.DueKind
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineLog
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.model.Step
import com.verto.domain.model.Task
import com.verto.domain.routine
import com.verto.domain.routineStep
import com.verto.domain.task
import com.verto.domain.taskStep
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodayBuilderTest {

    private fun build(
        energy: EnergyLevel = EnergyLevel.Medium,
        anchors: List<Anchor> = emptyList(),
        routines: List<Routine> = emptyList(),
        steps: List<Step> = emptyList(),
        logs: List<RoutineLog> = emptyList(),
        events: List<AnchorEvent> = emptyList(),
        tasks: List<Task> = emptyList(),
    ) = TodayBuilder.build(
        TodayInput(Oct15, SaoPaulo, VertoSettings(), energy, anchors, routines, steps, logs, events, tasks),
    )

    @Test
    fun sections_followWindowOrder_andSkipAnchorsWithoutRoutinesToday() {
        val today = build(
            anchors = listOf(anchor("dinner", 19), anchor("breakfast", 6), anchor("lunch", 12)),
            routines = listOf(
                routine("stretch", "breakfast"),
                routine("dishes", "dinner"),
                routine("weekend", "lunch", weekdays = setOf(DayOfWeek.SATURDAY)),
            ),
        )

        assertEquals(listOf("breakfast", "dinner"), today.sections.map { it.anchor.id })
    }

    @Test
    fun lowEnergy_suggestsPlanC() {
        val today = build(
            energy = EnergyLevel.Low,
            anchors = listOf(anchor("breakfast", 6)),
            routines = listOf(routine("stretch", "breakfast")),
        )

        assertEquals(Plan.C, today.sections.single().routines.single().suggestedPlan)
    }

    @Test
    fun routineCard_carriesTodaysOutcomeAndStepState() {
        val today = build(
            anchors = listOf(anchor("breakfast", 6)),
            routines = listOf(routine("stretch", "breakfast")),
            steps = listOf(
                routineStep("a1", "stretch", Plan.A, order = 1),
                routineStep("a0", "stretch", Plan.A, order = 0, doneAt = at(15, 7)),
                routineStep("c0", "stretch", Plan.C),
            ),
            logs = listOf(RoutineLog("l", "stretch", Oct15, RoutineOutcome.C)),
        )
        val card = today.sections.single().routines.single()

        assertEquals(RoutineOutcome.C, card.outcome)
        assertEquals(listOf("a0" to true, "a1" to false), card.stepsA.map { it.step.id to it.done })
        assertEquals(listOf("c0"), card.steps(Plan.C).map { it.step.id })
    }

    @Test
    fun expandedAnchor_isFirstSectionWithAnUnloggedRoutine() {
        val today = build(
            anchors = listOf(anchor("breakfast", 6), anchor("lunch", 12)),
            routines = listOf(routine("stretch", "breakfast"), routine("walk", "lunch")),
            logs = listOf(RoutineLog("l", "stretch", Oct15, RoutineOutcome.Skipped)),
        )

        assertEquals("lunch", today.expandedAnchorId)
    }

    @Test
    fun expandedAnchor_isNullWhenEverythingIsLogged() {
        val today = build(
            anchors = listOf(anchor("breakfast", 6)),
            routines = listOf(routine("stretch", "breakfast")),
            logs = listOf(RoutineLog("l", "stretch", Oct15, RoutineOutcome.A)),
        )

        assertNull(today.expandedAnchorId)
    }

    @Test
    fun triggeredFlag_comesFromTodaysEvents() {
        val today = build(
            anchors = listOf(anchor("breakfast", 6)),
            routines = listOf(routine("stretch", "breakfast")),
            events = listOf(AnchorEvent("e", "breakfast", Oct15, at(15, 7), AnchorTrigger.Tap)),
        )

        assertTrue(today.sections.single().triggered)
    }

    @Test
    fun pinned_overSoftLimit_isFlaggedButStillListed() {
        val today = build(tasks = (1..4).map { task("t$it", pinned = true) })

        assertEquals(4, today.pinned.size)
        assertTrue(today.pinnedOverSoftLimit)
    }

    @Test
    fun pinned_excludesFinishedTasks() {
        val today = build(tasks = listOf(task("open", pinned = true), task("done", pinned = true, doneAt = at(15, 9))))

        assertEquals(listOf("open"), today.pinned.map { it.task.id })
        assertFalse(today.pinnedOverSoftLimit)
    }

    @Test
    fun deadlines_areOpenUnpinnedHardDueWithinThreeDaysIncludingPast() {
        val today = build(
            tasks = listOf(
                task("past", dueDate = LocalDate(2026, 10, 12), dueKind = DueKind.Hard),
                task("soon", dueDate = LocalDate(2026, 10, 18), dueKind = DueKind.Hard),
                task("far", dueDate = LocalDate(2026, 10, 19), dueKind = DueKind.Hard),
                task("soft", dueDate = LocalDate(2026, 10, 16), dueKind = DueKind.Soft),
                task("pinned", pinned = true, dueDate = LocalDate(2026, 10, 16), dueKind = DueKind.Hard),
                task("done", dueDate = LocalDate(2026, 10, 16), dueKind = DueKind.Hard, doneAt = at(15, 9)),
            ),
        )

        assertEquals(listOf("past", "soon"), today.deadlines.map { it.task.id })
    }

    @Test
    fun taskCard_nextStepIsFirstUndoneByOrder() {
        val today = build(
            tasks = listOf(task("ir", pinned = true)),
            steps = listOf(
                taskStep("s2", "ir", order = 2),
                taskStep("s0", "ir", order = 0, doneAt = at(15, 9)),
                taskStep("s1", "ir", order = 1),
            ),
        )
        val card = today.pinned.single()

        assertEquals("s1", card.nextStep?.id)
        assertEquals(1, card.doneSteps)
        assertEquals(3, card.totalSteps)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:domain:jvmTest --tests "com.verto.domain.rules.RoutineRulesTest" --tests "com.verto.domain.rules.TodayBuilderTest"`
Expected: FAIL — `Unresolved reference 'RoutineRules'` / `'TodayBuilder'`.

- [ ] **Step 3: Write `RoutineRules` and `TodayBuilder`**

`shared/domain/src/commonMain/kotlin/com/verto/domain/rules/RoutineRules.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.model.Step
import com.verto.domain.model.StepOwner
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

object RoutineRules {

    fun suggestedPlan(energy: EnergyLevel): Plan = if (energy == EnergyLevel.Low) Plan.C else Plan.A

    fun isScheduledOn(routine: Routine, day: LocalDate): Boolean = day.dayOfWeek in routine.weekdays

    fun stepsOf(routineId: String, plan: Plan, steps: List<Step>): List<Step> =
        steps.filter { it.owner == StepOwner.OfRoutine(routineId, plan) }.sortedBy { it.order }

    /** Routine steps repeat daily: a step counts as done only if it was checked on this logical day. */
    fun isDoneOn(step: Step, day: LocalDate, zone: TimeZone, dayStartsAt: LocalTime): Boolean =
        step.doneAt?.let { LogicalDay.of(it, zone, dayStartsAt) == day } ?: false

    /** A plan counts once every one of its steps is done; A wins when both are complete. */
    fun completedOutcome(
        routineId: String,
        steps: List<Step>,
        day: LocalDate,
        zone: TimeZone,
        dayStartsAt: LocalTime,
    ): RoutineOutcome? {
        fun complete(plan: Plan): Boolean {
            val planSteps = stepsOf(routineId, plan, steps)
            return planSteps.isNotEmpty() && planSteps.all { isDoneOn(it, day, zone, dayStartsAt) }
        }
        return when {
            complete(Plan.A) -> RoutineOutcome.A
            complete(Plan.C) -> RoutineOutcome.C
            else -> null
        }
    }
}
```

`shared/domain/src/commonMain/kotlin/com/verto/domain/rules/TodayBuilder.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.VertoSettings
import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.DueKind
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineLog
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.model.Step
import com.verto.domain.model.StepOwner
import com.verto.domain.model.Task
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

data class TodayInput(
    val day: LocalDate,
    val zone: TimeZone,
    val settings: VertoSettings,
    val energy: EnergyLevel,
    val anchors: List<Anchor>,
    val routines: List<Routine>,
    val steps: List<Step>,
    val logs: List<RoutineLog>,
    val events: List<AnchorEvent>,
    val tasks: List<Task>,
)

data class StepState(val step: Step, val done: Boolean)

data class RoutineCard(
    val routine: Routine,
    val suggestedPlan: Plan,
    val stepsA: List<StepState>,
    val stepsC: List<StepState>,
    val outcome: RoutineOutcome?,
) {
    fun steps(plan: Plan): List<StepState> = if (plan == Plan.A) stepsA else stepsC
}

data class AnchorSection(
    val anchor: Anchor,
    val triggered: Boolean,
    val routines: List<RoutineCard>,
)

data class TaskCard(
    val task: Task,
    val nextStep: Step?,
    val doneSteps: Int,
    val totalSteps: Int,
)

data class Today(
    val day: LocalDate,
    val energy: EnergyLevel,
    val sections: List<AnchorSection>,
    val expandedAnchorId: String?,
    val pinned: List<TaskCard>,
    val pinnedOverSoftLimit: Boolean,
    val deadlines: List<TaskCard>,
)

/** "Hoje" is derived on every change, never stored, so there is no backlog to pile up. */
object TodayBuilder {

    fun build(input: TodayInput): Today {
        val scheduled = input.routines.filter { RoutineRules.isScheduledOn(it, input.day) }
        val logs = input.logs.filter { it.day == input.day }.associateBy { it.routineId }
        val suggested = RoutineRules.suggestedPlan(input.energy)

        val sections = input.anchors
            .sortedWith(compareBy<Anchor> { it.windowStart }.thenBy { it.name })
            .mapNotNull { anchor ->
                val routines = scheduled.filter { it.anchorId == anchor.id }.sortedBy { it.name }
                if (routines.isEmpty()) {
                    null
                } else {
                    AnchorSection(
                        anchor = anchor,
                        triggered = AnchorRules.isTriggered(anchor, input.day, input.events),
                        routines = routines.map { routine ->
                            RoutineCard(
                                routine = routine,
                                suggestedPlan = suggested,
                                stepsA = stepStates(routine.id, Plan.A, input),
                                stepsC = stepStates(routine.id, Plan.C, input),
                                outcome = logs[routine.id]?.outcome,
                            )
                        },
                    )
                }
            }

        val open = input.tasks.filter { it.doneAt == null }
        val pinned = open
            .filter { it.pinnedToToday }
            .sortedWith(compareBy<Task, LocalDate?>(nullsLast()) { it.dueDate }.thenBy { it.title })
        val horizon = input.day.plus(input.settings.deadlineHorizonDays, DateTimeUnit.DAY)
        val deadlines = open
            .filter { task ->
                !task.pinnedToToday && task.dueKind == DueKind.Hard && task.dueDate?.let { it <= horizon } == true
            }
            .sortedBy { it.dueDate }

        return Today(
            day = input.day,
            energy = input.energy,
            sections = sections,
            expandedAnchorId = sections.firstOrNull { section -> section.routines.any { it.outcome == null } }?.anchor?.id,
            pinned = pinned.map { card(it, input.steps) },
            pinnedOverSoftLimit = pinned.size > input.settings.pinnedSoftLimit,
            deadlines = deadlines.map { card(it, input.steps) },
        )
    }

    private fun stepStates(routineId: String, plan: Plan, input: TodayInput): List<StepState> =
        RoutineRules.stepsOf(routineId, plan, input.steps).map {
            StepState(it, RoutineRules.isDoneOn(it, input.day, input.zone, input.settings.dayStartsAt))
        }

    private fun card(task: Task, steps: List<Step>): TaskCard {
        val own = steps.filter { it.owner == StepOwner.OfTask(task.id) }.sortedBy { it.order }
        return TaskCard(
            task = task,
            nextStep = own.firstOrNull { it.doneAt == null },
            doneSteps = own.count { it.doneAt != null },
            totalSteps = own.size,
        )
    }
}
```

- [ ] **Step 4: Run them to verify they pass**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:domain:jvmTest`
Expected: PASS — 28 tests (10 earlier + 8 routine + 10 today).

- [ ] **Step 5: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "feat(domain): regras de rotina A/C e montagem do Hoje"
```

---

### Task 5: Task, inbox, reset and absence rules

**Files:**
- Create: `shared/domain/src/commonMain/kotlin/com/verto/domain/rules/TaskRules.kt`
- Create: `shared/domain/src/commonMain/kotlin/com/verto/domain/rules/InboxRules.kt`
- Create: `shared/domain/src/commonMain/kotlin/com/verto/domain/rules/ResetRules.kt`
- Create: `shared/domain/src/commonMain/kotlin/com/verto/domain/rules/AbsenceRules.kt`
- Test: `shared/domain/src/commonTest/kotlin/com/verto/domain/rules/LifecycleRulesTest.kt`

**Interfaces:**
- Consumes: model, fixtures.
- Produces: `TaskRules.doneAtAfterStepChange(task: Task, taskSteps: List<Step>, now: Instant): Instant?`; `InboxRules.toIncubate(items: List<InboxItem>, now: Instant, after: Duration): List<InboxItem>`; `ResetRules.plan(tasks: List<Task>, inbox: List<InboxItem>): ResetPlan` with `ResetPlan(tasksToUnpin: List<Task>, inboxToIncubate: List<InboxItem>)`; `AbsenceRules.shouldOfferFreshStart(lastOpenedAt: Instant?, now: Instant, threshold: Duration): Boolean`. Reset and absence are consumed by Plan 4.

- [ ] **Step 1: Write the failing test**

`shared/domain/src/commonTest/kotlin/com/verto/domain/rules/LifecycleRulesTest.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.at
import com.verto.domain.model.InboxItem
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import com.verto.domain.task
import com.verto.domain.taskStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class LifecycleRulesTest {

    private fun item(id: String, createdDay: Int, state: InboxState = InboxState.Raw) =
        InboxItem(id, id, at(createdDay, 10), InboxSource.App, state)

    @Test
    fun taskWithSteps_finishesWhenAllStepsAreDone() {
        val now = at(15, 10)

        assertEquals(now, TaskRules.doneAtAfterStepChange(task("t"), listOf(taskStep("s", "t", doneAt = at(15, 9))), now))
    }

    @Test
    fun taskWithSteps_reopensWhenAStepIsUndone() {
        val done = task("t", doneAt = at(15, 9))

        assertNull(TaskRules.doneAtAfterStepChange(done, listOf(taskStep("s", "t")), at(15, 10)))
    }

    @Test
    fun taskWithSteps_keepsItsOriginalFinishTime() {
        val done = task("t", doneAt = at(15, 9))

        assertEquals(at(15, 9), TaskRules.doneAtAfterStepChange(done, listOf(taskStep("s", "t", doneAt = at(15, 9))), at(15, 11)))
    }

    @Test
    fun taskWithoutSteps_isOnlyFinishedByHand() {
        assertNull(TaskRules.doneAtAfterStepChange(task("t"), emptyList(), at(15, 10)))
        assertEquals(at(15, 9), TaskRules.doneAtAfterStepChange(task("t", doneAt = at(15, 9)), emptyList(), at(15, 10)))
    }

    @Test
    fun incubation_takesOnlyRawItemsOlderThanTheThreshold() {
        val items = listOf(item("old", 11), item("fresh", 13), item("oldKept", 10, InboxState.Processed))

        assertEquals(listOf("old"), InboxRules.toIncubate(items, at(14, 10), 3.days).map { it.id })
    }

    @Test
    fun reset_unpinsEveryPinnedTask_andIncubatesRawItems() {
        val plan = ResetRules.plan(
            tasks = listOf(task("pinned", pinned = true), task("free")),
            inbox = listOf(item("raw", 15), item("done", 15, InboxState.Processed)),
        )

        assertEquals(listOf("pinned"), plan.tasksToUnpin.map { it.id })
        assertEquals(listOf("raw"), plan.inboxToIncubate.map { it.id })
    }

    @Test
    fun absence_offersFreshStartOnlyAfterTheThreshold() {
        assertTrue(AbsenceRules.shouldOfferFreshStart(at(10, 10), at(14, 10), 4.days))
        assertFalse(AbsenceRules.shouldOfferFreshStart(at(11, 10), at(14, 10), 4.days))
        assertFalse(AbsenceRules.shouldOfferFreshStart(null, at(14, 10), 4.days))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:domain:jvmTest --tests "com.verto.domain.rules.LifecycleRulesTest"`
Expected: FAIL — `Unresolved reference 'TaskRules'`.

- [ ] **Step 3: Write the four rule objects**

`shared/domain/src/commonMain/kotlin/com/verto/domain/rules/TaskRules.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.model.Step
import com.verto.domain.model.Task
import kotlin.time.Instant

object TaskRules {

    /**
     * A task with steps finishes itself when its last step is checked and reopens when
     * one is unchecked or added; a task without steps only finishes by hand.
     */
    fun doneAtAfterStepChange(task: Task, taskSteps: List<Step>, now: Instant): Instant? = when {
        taskSteps.isEmpty() -> task.doneAt
        taskSteps.all { it.doneAt != null } -> task.doneAt ?: now
        else -> null
    }
}
```

`shared/domain/src/commonMain/kotlin/com/verto/domain/rules/InboxRules.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.model.InboxItem
import com.verto.domain.model.InboxState
import kotlin.time.Duration
import kotlin.time.Instant

object InboxRules {

    /** Moved aside silently: an untouched braindump should never turn into guilt. */
    fun toIncubate(items: List<InboxItem>, now: Instant, after: Duration): List<InboxItem> =
        items.filter { it.state == InboxState.Raw && now - it.createdAt >= after }
}
```

`shared/domain/src/commonMain/kotlin/com/verto/domain/rules/ResetRules.kt`:

```kotlin
package com.verto.domain.rules

import com.verto.domain.model.InboxItem
import com.verto.domain.model.InboxState
import com.verto.domain.model.Task

data class ResetPlan(
    val tasksToUnpin: List<Task>,
    val inboxToIncubate: List<InboxItem>,
)

/** A fresh start archives nothing and marks nothing late: it only clears today's commitments. */
object ResetRules {

    fun plan(tasks: List<Task>, inbox: List<InboxItem>): ResetPlan = ResetPlan(
        tasksToUnpin = tasks.filter { it.pinnedToToday },
        inboxToIncubate = inbox.filter { it.state == InboxState.Raw },
    )
}
```

`shared/domain/src/commonMain/kotlin/com/verto/domain/rules/AbsenceRules.kt`:

```kotlin
package com.verto.domain.rules

import kotlin.time.Duration
import kotlin.time.Instant

object AbsenceRules {

    fun shouldOfferFreshStart(lastOpenedAt: Instant?, now: Instant, threshold: Duration): Boolean =
        lastOpenedAt != null && now - lastOpenedAt >= threshold
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:domain:jvmTest`
Expected: PASS — 35 tests.

- [ ] **Step 5: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "feat(domain): conclusão de tarefa, incubação, reset e volta após ausência"
```

---
### Task 6: Room database, payloads and the outbox

**Files:**
- Modify: `shared/data/build.gradle.kts` (full replacement)
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/db/Entities.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/db/Daos.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/db/VertoDatabase.kt`
- Create: `shared/data/src/androidMain/kotlin/com/verto/data/db/DatabaseBuilder.android.kt`
- Create: `shared/data/src/jvmMain/kotlin/com/verto/data/db/DatabaseBuilder.jvm.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/sync/Wire.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/sync/Payloads.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/sync/LocalStore.kt`
- Create: `shared/data/schemas/` (generated by Room on build; commit it)
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/sync/LocalStoreTest.kt`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/sync/PayloadContractTest.kt`

**Interfaces:**
- Consumes: domain model (Task 2).
- Produces (package `com.verto.data.db`): entities `InboxItemEntity(id, text, createdAt: Long, source, state, updatedAt: Long, deletedAt: Long?)`, `TaskEntity(id, title, notes?, dueDate: String?, dueKind: String?, pinnedToToday, doneAt: Long?, updatedAt, deletedAt?)`, `StepEntity(id, taskId?, routineId?, plan?, order: Int /* column "position" */, title, energy, doneAt?, updatedAt, deletedAt?)`, `AnchorEntity(id, name, windowStart: String, windowEnd: String, updatedAt, deletedAt?)`, `AnchorEventEntity(id, anchorId, day: String, triggeredAt: Long, source, updatedAt, deletedAt?)`, `RoutineEntity(id, name, anchorId, weekdays: String /* "1,3,5" ISO */, updatedAt, deletedAt?)`, `RoutineLogEntity(id, routineId, day, outcome, updatedAt, deletedAt?)`, `DayEnergyEntity(id, day, level, setAt: Long, updatedAt, deletedAt?)`, `OutboxEntity(seq: Long autoinc, entityType, entityId, payload: String, deletedAt: Long?, clientUpdatedAt: Long)` unique on (entityType, entityId).
- Produces DAOs: `InboxDao{observeActive, active, get, upsert}`, `TaskDao{observeActive, get, upsert}`, `StepDao{observeActive, get, forTask, forRoutine, upsert}`, `AnchorDao{observeAnchors, activeAnchors, getAnchor, observeEventsOn(day), eventsOn(day), upsertAnchor, upsertEvent}`, `RoutineDao{observeRoutines, getRoutine, forAnchor, observeLogsOn(day), logFor(routineId, day), upsertRoutine, upsertLog}`, `DayEnergyDao{observeLatest(day), latest(day), upsert}`, `OutboxDao{enqueue, all}`; `VertoDatabase` with one accessor per DAO; `vertoDatabase(context: Context)` (android), `vertoDatabase(file: File)` and `inMemoryVertoDatabase()` (jvm).
- Produces (package `com.verto.data.sync`): `LocalStore(db: VertoDatabase)` with `suspend fun <T> write(block: suspend WriteScope.() -> T): T`; `WriteScope.put(entity)` overloads for all eight entity types; internal `Enum<*>.wire(): String` (lowercase name) and `String.toEnum<E>()`; internal `SyncJson`.
- Wire values: every enum travels as its lowercase name (`low`, `hyperfocus`, `raw`, `a`, `skipped`, `tap`, ...); instants as ISO-8601 strings; dates as `YYYY-MM-DD`; times as `HH:mm`; weekdays as ISO numbers (Monday = 1).

- [ ] **Step 1: Replace `shared/data/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "com.verto.data"
        compileSdk = 36
        minSdk = 26
        compilerOptions.configure { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    compilerOptions {
        optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.domain)
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}
```

- [ ] **Step 2: Write the failing tests**

`shared/data/src/jvmTest/kotlin/com/verto/data/sync/LocalStoreTest.kt`:

```kotlin
package com.verto.data.sync

import com.verto.data.db.InboxItemEntity
import com.verto.data.db.inMemoryVertoDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalStoreTest {

    private val db = inMemoryVertoDatabase()
    private val store = LocalStore(db)

    @AfterTest
    fun close() = db.close()

    private fun inbox(id: String, text: String, updatedAt: Long) =
        InboxItemEntity(id, text, createdAt = 1_000, source = "app", state = "raw", updatedAt = updatedAt, deletedAt = null)

    @Test
    fun put_writesTheRowAndAnOutboxEntry() = runTest {
        store.write { put(inbox("i1", "comprar pão", 1_000)) }

        assertEquals("comprar pão", db.inboxDao().get("i1")?.text)
        val entry = db.outboxDao().all().single()
        assertEquals("inbox_item", entry.entityType)
        assertEquals("i1", entry.entityId)
        assertEquals(1_000, entry.clientUpdatedAt)
    }

    @Test
    fun rewritingARecord_coalescesIntoOneOutboxEntry() = runTest {
        store.write { put(inbox("i1", "antes", 1_000)) }
        store.write { put(inbox("i1", "depois", 2_000)) }

        val entry = db.outboxDao().all().single()
        assertTrue(entry.payload.contains("\"text\":\"depois\""))
        assertEquals(2_000, entry.clientUpdatedAt)
    }

    @Test
    fun failedWrite_rollsBackRowAndOutbox() = runTest {
        assertFailsWith<IllegalStateException> {
            store.write {
                put(inbox("i1", "some", 1_000))
                error("boom")
            }
        }

        assertNull(db.inboxDao().get("i1"))
        assertTrue(db.outboxDao().all().isEmpty())
    }
}
```

`shared/data/src/jvmTest/kotlin/com/verto/data/sync/PayloadContractTest.kt`:

```kotlin
package com.verto.data.sync

import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.DayEnergyEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import com.verto.data.db.inMemoryVertoDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Mirrors SyncEntityCatalog.cs in the Verto API: a missing key there means a rejected push. */
class PayloadContractTest {

    private val required = mapOf(
        "inbox_item" to listOf("text", "createdAt", "source", "state"),
        "task" to listOf("title", "pinnedToToday"),
        "step" to listOf("order", "title", "energy"),
        "anchor" to listOf("name", "windowStart", "windowEnd"),
        "anchor_event" to listOf("anchorId", "day", "triggeredAt", "source"),
        "routine" to listOf("name", "anchorId", "weekdays"),
        "routine_log" to listOf("routineId", "day", "outcome"),
        "day_energy" to listOf("day", "level", "setAt"),
    )

    private val db = inMemoryVertoDatabase()
    private val store = LocalStore(db)

    @AfterTest
    fun close() = db.close()

    private suspend fun writeOneOfEach() = store.write {
        put(InboxItemEntity("i", "texto", 1_000, "app", "raw", 1_000, null))
        put(TaskEntity("t", "IR", null, "2026-10-20", "hard", true, null, 1_000, null))
        put(StepEntity("s", null, "r", "c", 0, "alongar", "low", null, 1_000, null))
        put(AnchorEntity("a", "Almocei", "12:00", "14:00", 1_000, null))
        put(AnchorEventEntity("e", "a", "2026-10-15", 1_000, "tap", 1_000, null))
        put(RoutineEntity("r", "Exercício", "a", "1,3,5", 1_000, null))
        put(RoutineLogEntity("l", "r", "2026-10-15", "c", 1_000, null))
        put(DayEnergyEntity("d", "2026-10-15", "low", 1_000, 1_000, null))
    }

    @Test
    fun everyPayload_hasTheKeysTheServerRequires() = runTest {
        writeOneOfEach()

        val entries = db.outboxDao().all()
        assertEquals(required.keys, entries.map { it.entityType }.toSet())
        entries.forEach { entry ->
            val payload = Json.parseToJsonElement(entry.payload).jsonObject
            required.getValue(entry.entityType).forEach { key ->
                assertTrue(key in payload, "${entry.entityType} payload is missing $key")
            }
        }
    }

    @Test
    fun routineStep_carriesRoutineIdAndPlan() = runTest {
        writeOneOfEach()

        val step = Json.parseToJsonElement(db.outboxDao().all().single { it.entityType == "step" }.payload).jsonObject
        assertNotEquals(JsonNull, step["routineId"])
        assertEquals("c", step["plan"]?.jsonPrimitive?.content)
    }

    @Test
    fun routineWeekdays_travelAsIsoNumbers() = runTest {
        writeOneOfEach()

        val routine = Json.parseToJsonElement(db.outboxDao().all().single { it.entityType == "routine" }.payload).jsonObject
        assertEquals(listOf("1", "3", "5"), routine.getValue("weekdays").jsonArray.map { it.jsonPrimitive.content })
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:data:jvmTest`
Expected: FAIL — `Unresolved reference 'InboxItemEntity'` / `'LocalStore'`.

- [ ] **Step 4: Write entities, DAOs and the database**

`shared/data/src/commonMain/kotlin/com/verto/data/db/Entities.kt`:

```kotlin
package com.verto.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "inbox_items")
data class InboxItemEntity(
    @PrimaryKey val id: String,
    val text: String,
    val createdAt: Long,
    val source: String,
    val state: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String?,
    val dueDate: String?,
    val dueKind: String?,
    val pinnedToToday: Boolean,
    val doneAt: Long?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "steps", indices = [Index("taskId"), Index("routineId")])
data class StepEntity(
    @PrimaryKey val id: String,
    val taskId: String?,
    val routineId: String?,
    val plan: String?,
    // "order" is an SQL keyword.
    @ColumnInfo(name = "position") val order: Int,
    val title: String,
    val energy: String,
    val doneAt: Long?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "anchors")
data class AnchorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val windowStart: String,
    val windowEnd: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "anchor_events", indices = [Index(value = ["anchorId", "day"])])
data class AnchorEventEntity(
    @PrimaryKey val id: String,
    val anchorId: String,
    val day: String,
    val triggeredAt: Long,
    val source: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "routines", indices = [Index("anchorId")])
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val anchorId: String,
    val weekdays: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "routine_logs", indices = [Index(value = ["routineId", "day"])])
data class RoutineLogEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val day: String,
    val outcome: String,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "day_energy", indices = [Index("day")])
data class DayEnergyEntity(
    @PrimaryKey val id: String,
    val day: String,
    val level: String,
    val setAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** One pending row per record: rewriting a record replaces its entry, so a push sends only the latest state. */
@Entity(tableName = "outbox", indices = [Index(value = ["entityType", "entityId"], unique = true)])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val entityType: String,
    val entityId: String,
    val payload: String,
    val deletedAt: Long?,
    val clientUpdatedAt: Long,
)
```

`shared/data/src/commonMain/kotlin/com/verto/data/db/Daos.kt`:

```kotlin
package com.verto.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox_items WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<InboxItemEntity>>

    @Query("SELECT * FROM inbox_items WHERE deletedAt IS NULL")
    suspend fun active(): List<InboxItemEntity>

    @Query("SELECT * FROM inbox_items WHERE id = :id")
    suspend fun get(id: String): InboxItemEntity?

    @Upsert
    suspend fun upsert(entity: InboxItemEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY title COLLATE NOCASE")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Upsert
    suspend fun upsert(entity: TaskEntity)
}

@Dao
interface StepDao {
    @Query("SELECT * FROM steps WHERE deletedAt IS NULL ORDER BY position")
    fun observeActive(): Flow<List<StepEntity>>

    @Query("SELECT * FROM steps WHERE id = :id")
    suspend fun get(id: String): StepEntity?

    @Query("SELECT * FROM steps WHERE taskId = :taskId AND deletedAt IS NULL ORDER BY position")
    suspend fun forTask(taskId: String): List<StepEntity>

    @Query("SELECT * FROM steps WHERE routineId = :routineId AND deletedAt IS NULL ORDER BY plan, position")
    suspend fun forRoutine(routineId: String): List<StepEntity>

    @Upsert
    suspend fun upsert(entity: StepEntity)
}

@Dao
interface AnchorDao {
    @Query("SELECT * FROM anchors WHERE deletedAt IS NULL ORDER BY windowStart, name")
    fun observeAnchors(): Flow<List<AnchorEntity>>

    @Query("SELECT * FROM anchors WHERE deletedAt IS NULL")
    suspend fun activeAnchors(): List<AnchorEntity>

    @Query("SELECT * FROM anchors WHERE id = :id")
    suspend fun getAnchor(id: String): AnchorEntity?

    @Query("SELECT * FROM anchor_events WHERE day = :day AND deletedAt IS NULL")
    fun observeEventsOn(day: String): Flow<List<AnchorEventEntity>>

    @Query("SELECT * FROM anchor_events WHERE day = :day AND deletedAt IS NULL")
    suspend fun eventsOn(day: String): List<AnchorEventEntity>

    @Upsert
    suspend fun upsertAnchor(entity: AnchorEntity)

    @Upsert
    suspend fun upsertEvent(entity: AnchorEventEntity)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutine(id: String): RoutineEntity?

    @Query("SELECT * FROM routines WHERE anchorId = :anchorId AND deletedAt IS NULL")
    suspend fun forAnchor(anchorId: String): List<RoutineEntity>

    @Query("SELECT * FROM routine_logs WHERE day = :day AND deletedAt IS NULL")
    fun observeLogsOn(day: String): Flow<List<RoutineLogEntity>>

    @Query("SELECT * FROM routine_logs WHERE routineId = :routineId AND day = :day AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun logFor(routineId: String, day: String): RoutineLogEntity?

    @Upsert
    suspend fun upsertRoutine(entity: RoutineEntity)

    @Upsert
    suspend fun upsertLog(entity: RoutineLogEntity)
}

@Dao
interface DayEnergyDao {
    @Query("SELECT * FROM day_energy WHERE day = :day AND deletedAt IS NULL ORDER BY setAt DESC LIMIT 1")
    fun observeLatest(day: String): Flow<DayEnergyEntity?>

    @Query("SELECT * FROM day_energy WHERE day = :day AND deletedAt IS NULL ORDER BY setAt DESC LIMIT 1")
    suspend fun latest(day: String): DayEnergyEntity?

    @Upsert
    suspend fun upsert(entity: DayEnergyEntity)
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY seq")
    suspend fun all(): List<OutboxEntity>
}
```

`shared/data/src/commonMain/kotlin/com/verto/data/db/VertoDatabase.kt`:

```kotlin
package com.verto.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        InboxItemEntity::class,
        TaskEntity::class,
        StepEntity::class,
        AnchorEntity::class,
        AnchorEventEntity::class,
        RoutineEntity::class,
        RoutineLogEntity::class,
        DayEnergyEntity::class,
        OutboxEntity::class,
    ],
    version = 1,
)
@ConstructedBy(VertoDatabaseConstructor::class)
abstract class VertoDatabase : RoomDatabase() {
    abstract fun inboxDao(): InboxDao
    abstract fun taskDao(): TaskDao
    abstract fun stepDao(): StepDao
    abstract fun anchorDao(): AnchorDao
    abstract fun routineDao(): RoutineDao
    abstract fun dayEnergyDao(): DayEnergyDao
    abstract fun outboxDao(): OutboxDao
}

// Room's KSP processor generates the actual object for every target.
@Suppress("KotlinNoActualForExpect")
expect object VertoDatabaseConstructor : RoomDatabaseConstructor<VertoDatabase> {
    override fun initialize(): VertoDatabase
}
```

`shared/data/src/androidMain/kotlin/com/verto/data/db/DatabaseBuilder.android.kt`:

```kotlin
package com.verto.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun vertoDatabase(context: Context): VertoDatabase {
    val app = context.applicationContext
    return Room.databaseBuilder<VertoDatabase>(app, app.getDatabasePath("verto.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
```

`shared/data/src/jvmMain/kotlin/com/verto/data/db/DatabaseBuilder.jvm.kt`:

```kotlin
package com.verto.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

fun vertoDatabase(file: File): VertoDatabase =
    Room.databaseBuilder<VertoDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/** Throwaway database for the JVM tests of this module and of composeApp. */
fun inMemoryVertoDatabase(): VertoDatabase =
    Room.inMemoryDatabaseBuilder<VertoDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
```

- [ ] **Step 5: Write the wire format, payloads and `LocalStore`**

`shared/data/src/commonMain/kotlin/com/verto/data/sync/Wire.kt`:

```kotlin
package com.verto.data.sync

internal fun Enum<*>.wire(): String = name.lowercase()

internal inline fun <reified E : Enum<E>> String.toEnum(): E = enumValues<E>().first { it.wire() == this }
```

`shared/data/src/commonMain/kotlin/com/verto/data/sync/Payloads.kt`:

```kotlin
package com.verto.data.sync

import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.DayEnergyEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal val SyncJson = Json { encodeDefaults = true }

// Key names are the sync contract with the API (SyncEntityCatalog.cs); rename nothing here without the server.

@Serializable
internal data class InboxPayload(val text: String, val createdAt: String, val source: String, val state: String)

@Serializable
internal data class TaskPayload(
    val title: String,
    val notes: String?,
    val dueDate: String?,
    val dueKind: String?,
    val pinnedToToday: Boolean,
    val doneAt: String?,
)

@Serializable
internal data class StepPayload(
    val taskId: String?,
    val routineId: String?,
    val plan: String?,
    val order: Int,
    val title: String,
    val energy: String,
    val doneAt: String?,
)

@Serializable
internal data class AnchorPayload(val name: String, val windowStart: String, val windowEnd: String)

@Serializable
internal data class AnchorEventPayload(val anchorId: String, val day: String, val triggeredAt: String, val source: String)

@Serializable
internal data class RoutinePayload(val name: String, val anchorId: String, val weekdays: List<Int>)

@Serializable
internal data class RoutineLogPayload(val routineId: String, val day: String, val outcome: String)

@Serializable
internal data class DayEnergyPayload(val day: String, val level: String, val setAt: String)

private fun Long.iso(): String = Instant.fromEpochMilliseconds(this).toString()

internal fun InboxItemEntity.toPayload() = InboxPayload(text, createdAt.iso(), source, state)

internal fun TaskEntity.toPayload() = TaskPayload(title, notes, dueDate, dueKind, pinnedToToday, doneAt?.iso())

internal fun StepEntity.toPayload() = StepPayload(taskId, routineId, plan, order, title, energy, doneAt?.iso())

internal fun AnchorEntity.toPayload() = AnchorPayload(name, windowStart, windowEnd)

internal fun AnchorEventEntity.toPayload() = AnchorEventPayload(anchorId, day, triggeredAt.iso(), source)

internal fun RoutineEntity.toPayload() =
    RoutinePayload(name, anchorId, weekdays.split(',').filter { it.isNotBlank() }.map { it.toInt() })

internal fun RoutineLogEntity.toPayload() = RoutineLogPayload(routineId, day, outcome)

internal fun DayEnergyEntity.toPayload() = DayEnergyPayload(day, level, setAt.iso())
```

`shared/data/src/commonMain/kotlin/com/verto/data/sync/LocalStore.kt`:

```kotlin
package com.verto.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.DayEnergyEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.OutboxEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import com.verto.data.db.VertoDatabase
import kotlinx.serialization.encodeToString

class LocalStore(val db: VertoDatabase) {

    /** Row and outbox entry commit together, so the sync engine can never miss a local change. */
    suspend fun <T> write(block: suspend WriteScope.() -> T): T =
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction { WriteScope(db).block() }
        }
}

class WriteScope internal constructor(private val db: VertoDatabase) {

    suspend fun put(entity: InboxItemEntity) {
        db.inboxDao().upsert(entity)
        enqueue("inbox_item", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: TaskEntity) {
        db.taskDao().upsert(entity)
        enqueue("task", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: StepEntity) {
        db.stepDao().upsert(entity)
        enqueue("step", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: AnchorEntity) {
        db.anchorDao().upsertAnchor(entity)
        enqueue("anchor", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: AnchorEventEntity) {
        db.anchorDao().upsertEvent(entity)
        enqueue("anchor_event", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: RoutineEntity) {
        db.routineDao().upsertRoutine(entity)
        enqueue("routine", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: RoutineLogEntity) {
        db.routineDao().upsertLog(entity)
        enqueue("routine_log", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    suspend fun put(entity: DayEnergyEntity) {
        db.dayEnergyDao().upsert(entity)
        enqueue("day_energy", entity.id, SyncJson.encodeToString(entity.toPayload()), entity.deletedAt, entity.updatedAt)
    }

    private suspend fun enqueue(type: String, id: String, payload: String, deletedAt: Long?, updatedAt: Long) =
        db.outboxDao().enqueue(
            OutboxEntity(entityType = type, entityId = id, payload = payload, deletedAt = deletedAt, clientUpdatedAt = updatedAt),
        )
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:data:jvmTest`
Expected: PASS — 6 tests. `shared/data/schemas/com.verto.data.db.VertoDatabase/1.json` now exists.

If the rollback test fails (rows survive the exception), the DAO calls are not joining the writer transaction: stop and debug with superpowers:systematic-debugging before going on — every later task relies on this guarantee.

- [ ] **Step 7: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "feat(data): banco Room com outbox gravado na mesma transação"
```

---

### Task 7: Repositories

**Files:**
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/Platform.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/Mappers.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/repository/Drafts.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/repository/InboxRepository.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/repository/TaskRepository.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/repository/RoutineRepository.kt`
- Create: `shared/data/src/commonMain/kotlin/com/verto/data/repository/DayRepository.kt`
- Create: `shared/data/src/jvmTest/kotlin/com/verto/data/DataGraph.kt`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/repository/InboxRepositoryTest.kt`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/repository/TaskRepositoryTest.kt`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/repository/RoutineRepositoryTest.kt`
- Test: `shared/data/src/jvmTest/kotlin/com/verto/data/repository/DayRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 6 (`LocalStore`, DAOs, `wire`/`toEnum`), domain rules (Tasks 3–5).
- Produces (package `com.verto.data`): `fun interface IdGenerator { fun newId(): String }`, `object UuidIdGenerator : IdGenerator`, `fun interface ZoneProvider { fun current(): TimeZone }`, `object SystemZone : ZoneProvider`.
- Produces (package `com.verto.data.repository`): `StepDraft(id: String?, title, energy)`, `TaskDraft(id: String?, title, notes = null, dueDate = null, dueKind = null, pinnedToToday = false, steps = emptyList(), done = false)`, `AnchorDraft(id: String?, name, windowStart: LocalTime, windowEnd: LocalTime)`, `RoutineDraft(id: String?, name, anchorId, weekdays: Set<DayOfWeek>, stepsA: List<StepDraft>, stepsC: List<StepDraft>)`, `RoutineDay(anchors, routines, events, logs)`.
- `InboxRepository(store, clock: Clock, ids: IdGenerator)`: `observe(): Flow<List<InboxItem>>`, `get(id): InboxItem?`, `capture(text, source): String?`, `setState(id, state)`, `incubateStale(settings: VertoSettings)`.
- `TaskRepository(store, clock, ids)`: `observeTasks(): Flow<List<Task>>`, `observeAllSteps(): Flow<List<Step>>`, `draftFor(id): TaskDraft?`, `save(draft): String`, `setPinned(id, pinned)`, `setDone(id, done)`, `toggleStep(stepId)`, `delete(id)`.
- `RoutineRepository(store, clock, ids, zones: ZoneProvider, settings: VertoSettings)`: `observeAnchors()`, `observeRoutines()`, `observeDay(day): Flow<RoutineDay>`, `anchorDraft(id): AnchorDraft?`, `saveAnchor(draft): String`, `deleteAnchor(id): Boolean`, `routineDraft(id): RoutineDraft?`, `saveRoutine(draft): String`, `deleteRoutine(id)`, `tapAnchor(anchorId, day)`, `recordOpenedWindows(day)`, `toggleStep(stepId, day)`, `skip(routineId, day)`, `clearLog(routineId, day)`.
- `DayRepository(store, clock, ids)`: `observeEnergy(day): Flow<EnergyLevel?>`, `setEnergy(day, level)`.
- Test-only (`com.verto.data`, jvmTest): `TestClock(var now: Instant)`, `SequentialIds`, `TestZone`, `localAt(day, hour, minute = 0)`, `DataGraph(start)` exposing `clock, ids, settings, db, store, inbox, tasks, routines, days`.

- [ ] **Step 1: Write the test graph and the failing tests**

`shared/data/src/jvmTest/kotlin/com/verto/data/DataGraph.kt`:

```kotlin
package com.verto.data

import com.verto.data.db.inMemoryVertoDatabase
import com.verto.data.repository.DayRepository
import com.verto.data.repository.InboxRepository
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.TaskRepository
import com.verto.data.sync.LocalStore
import com.verto.domain.VertoSettings
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

class TestClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

class SequentialIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "id-${++next}"
}

val TestZone: TimeZone = TimeZone.of("America/Sao_Paulo")

fun localAt(day: Int, hour: Int, minute: Int = 0): Instant =
    LocalDateTime(2026, 10, day, hour, minute).toInstant(TestZone)

class DataGraph(start: Instant = localAt(15, 10)) {
    val clock = TestClock(start)
    val ids = SequentialIds()
    val settings = VertoSettings()
    val db = inMemoryVertoDatabase()
    val store = LocalStore(db)
    val inbox = InboxRepository(store, clock, ids)
    val tasks = TaskRepository(store, clock, ids)
    val routines = RoutineRepository(store, clock, ids, { TestZone }, settings)
    val days = DayRepository(store, clock, ids)
}
```

`shared/data/src/jvmTest/kotlin/com/verto/data/repository/InboxRepositoryTest.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.data.localAt
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InboxRepositoryTest {

    private val graph = DataGraph()

    @AfterTest
    fun close() = graph.db.close()

    @Test
    fun capture_trimsAndStoresARawItem() = runTest {
        graph.inbox.capture("  comprar pão  ", InboxSource.App)

        val item = graph.inbox.observe().first().single()
        assertEquals("comprar pão", item.text)
        assertEquals(InboxState.Raw, item.state)
    }

    @Test
    fun capture_blank_isIgnored() = runTest {
        assertNull(graph.inbox.capture("   ", InboxSource.App))

        assertTrue(graph.inbox.observe().first().isEmpty())
        assertTrue(graph.db.outboxDao().all().isEmpty())
    }

    @Test
    fun setState_keepsTheItemWithItsNewState() = runTest {
        val id = graph.inbox.capture("ideia", InboxSource.App)!!

        graph.inbox.setState(id, InboxState.Discarded)

        assertEquals(InboxState.Discarded, graph.inbox.get(id)?.state)
    }

    @Test
    fun incubateStale_movesOnlyOldRawItems() = runTest {
        graph.clock.now = localAt(11, 10)
        val old = graph.inbox.capture("velho", InboxSource.App)!!
        graph.clock.now = localAt(14, 9)
        val fresh = graph.inbox.capture("novo", InboxSource.App)!!
        graph.clock.now = localAt(14, 10)

        graph.inbox.incubateStale(graph.settings)

        assertEquals(InboxState.Incubated, graph.inbox.get(old)?.state)
        assertEquals(InboxState.Raw, graph.inbox.get(fresh)?.state)
    }
}
```

`shared/data/src/jvmTest/kotlin/com/verto/data/repository/TaskRepositoryTest.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.domain.model.DueKind
import com.verto.domain.model.StepEnergy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRepositoryTest {

    private val graph = DataGraph()
    private val tasks = graph.tasks

    @AfterTest
    fun close() = graph.db.close()

    private fun step(title: String, id: String? = null) = StepDraft(id, title, StepEnergy.Low)

    private suspend fun task(id: String) = tasks.observeTasks().first().single { it.id == id }

    @Test
    fun save_newTask_storesStepsInOrder() = runTest {
        val id = tasks.save(TaskDraft(null, "Fazer IR", steps = listOf(step("abrir site"), step("baixar programa"))))

        val draft = tasks.draftFor(id)!!
        assertEquals(listOf("abrir site", "baixar programa"), draft.steps.map { it.title })
    }

    @Test
    fun save_withoutDate_dropsTheDueKind() = runTest {
        val id = tasks.save(TaskDraft(null, "Ligar", dueKind = DueKind.Hard))

        assertNull(task(id).dueKind)
    }

    @Test
    fun toggleStep_lastStep_finishesTask_andUndoReopensIt() = runTest {
        val id = tasks.save(TaskDraft(null, "IR", steps = listOf(step("abrir site"))))
        val stepId = tasks.draftFor(id)!!.steps.single().id!!

        tasks.toggleStep(stepId)
        assertNotNull(task(id).doneAt)

        tasks.toggleStep(stepId)
        assertNull(task(id).doneAt)
    }

    @Test
    fun save_removingLastOpenStep_finishesTask() = runTest {
        val id = tasks.save(TaskDraft(null, "IR", steps = listOf(step("abrir site"), step("enviar"))))
        val (first, _) = tasks.draftFor(id)!!.steps
        tasks.toggleStep(first.id!!)

        tasks.save(TaskDraft(id, "IR", steps = listOf(first)))

        assertNotNull(task(id).doneAt)
    }

    @Test
    fun save_addingStepToDoneTask_reopensIt() = runTest {
        val id = tasks.save(TaskDraft(null, "IR", steps = listOf(step("abrir site"))))
        val only = tasks.draftFor(id)!!.steps.single()
        tasks.toggleStep(only.id!!)

        tasks.save(TaskDraft(id, "IR", steps = listOf(only, step("enviar"))))

        assertNull(task(id).doneAt)
    }

    @Test
    fun delete_softDeletesTaskAndSteps() = runTest {
        val id = tasks.save(TaskDraft(null, "IR", dueDate = LocalDate(2026, 10, 20), steps = listOf(step("abrir site"))))

        tasks.delete(id)

        assertTrue(tasks.observeTasks().first().isEmpty())
        assertTrue(tasks.observeAllSteps().first().isEmpty())
        assertTrue(graph.db.outboxDao().all().all { it.deletedAt != null })
    }
}
```

`shared/data/src/jvmTest/kotlin/com/verto/data/repository/RoutineRepositoryTest.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.data.localAt
import com.verto.domain.model.AnchorTrigger
import com.verto.domain.model.Plan
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.model.StepEnergy
import com.verto.domain.model.StepOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineRepositoryTest {

    private val graph = DataGraph()
    private val routines = graph.routines
    private val oct15 = LocalDate(2026, 10, 15)

    @AfterTest
    fun close() = graph.db.close()

    private suspend fun anchor(name: String, start: Int) =
        routines.saveAnchor(AnchorDraft(null, name, LocalTime(start, 0), LocalTime(start + 2, 0)))

    private suspend fun exercise(anchorId: String) = routines.saveRoutine(
        RoutineDraft(
            id = null,
            name = "Exercício",
            anchorId = anchorId,
            weekdays = DayOfWeek.entries.toSet(),
            stepsA = listOf(StepDraft(null, "treino", StepEnergy.Medium)),
            stepsC = listOf(StepDraft(null, "alongar", StepEnergy.Low)),
        ),
    )

    private suspend fun stepId(routineId: String, plan: Plan) = graph.tasks.observeAllSteps().first()
        .single { it.owner == StepOwner.OfRoutine(routineId, plan) }.id

    private suspend fun outcome(routineId: String) =
        routines.observeDay(oct15).first().logs.singleOrNull { it.routineId == routineId }?.outcome

    @Test
    fun saveRoutine_roundTripsThroughTheDraft() = runTest {
        val routineId = exercise(anchor("Acordei", 6))

        val draft = routines.routineDraft(routineId)!!
        assertEquals(listOf("treino"), draft.stepsA.map { it.title })
        assertEquals(listOf("alongar"), draft.stepsC.map { it.title })
    }

    @Test
    fun tapAnchor_recordsOnlyOneEventPerDay() = runTest {
        val anchorId = anchor("Almocei", 12)

        routines.tapAnchor(anchorId, oct15)
        routines.tapAnchor(anchorId, oct15)

        val event = routines.observeDay(oct15).first().events.single()
        assertEquals(AnchorTrigger.Tap, event.source)
    }

    @Test
    fun recordOpenedWindows_recordsOnlyOpenedUntriggeredWindows() = runTest {
        val breakfast = anchor("Acordei", 6)
        anchor("Jantei", 19)
        graph.clock.now = localAt(15, 12, 30)

        routines.recordOpenedWindows(oct15)

        val events = routines.observeDay(oct15).first().events
        assertEquals(listOf(breakfast), events.map { it.anchorId })
        assertEquals(AnchorTrigger.Window, events.single().source)
    }

    @Test
    fun toggleStep_completingPlanC_logsC_thenPlanA_upgradesToA() = runTest {
        val routineId = exercise(anchor("Acordei", 6))

        routines.toggleStep(stepId(routineId, Plan.C), oct15)
        assertEquals(RoutineOutcome.C, outcome(routineId))

        routines.toggleStep(stepId(routineId, Plan.A), oct15)
        assertEquals(RoutineOutcome.A, outcome(routineId))
    }

    @Test
    fun toggleStep_uncheckingTheOnlyCompletePlan_removesTheLog() = runTest {
        val routineId = exercise(anchor("Acordei", 6))
        val minimal = stepId(routineId, Plan.C)

        routines.toggleStep(minimal, oct15)
        routines.toggleStep(minimal, oct15)

        assertNull(outcome(routineId))
    }

    @Test
    fun toggleStep_afterMidnight_countsForTheLogicalDay() = runTest {
        val routineId = exercise(anchor("Jantei", 19))
        graph.clock.now = localAt(16, 1)

        routines.toggleStep(stepId(routineId, Plan.C), oct15)

        assertEquals(RoutineOutcome.C, outcome(routineId))
    }

    @Test
    fun skip_logsSkipped_andClearLogUndoesIt() = runTest {
        val routineId = exercise(anchor("Acordei", 6))

        routines.skip(routineId, oct15)
        assertEquals(RoutineOutcome.Skipped, outcome(routineId))

        routines.clearLog(routineId, oct15)
        assertNull(outcome(routineId))
    }

    @Test
    fun deleteAnchor_inUse_isRefused() = runTest {
        val anchorId = anchor("Acordei", 6)
        exercise(anchorId)

        assertFalse(routines.deleteAnchor(anchorId))
        assertEquals(1, routines.observeAnchors().first().size)
    }

    @Test
    fun deleteAnchor_unused_removesIt() = runTest {
        val anchorId = anchor("Acordei", 6)

        assertTrue(routines.deleteAnchor(anchorId))
        assertTrue(routines.observeAnchors().first().isEmpty())
    }
}
```

`shared/data/src/jvmTest/kotlin/com/verto/data/repository/DayRepositoryTest.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.DataGraph
import com.verto.domain.model.EnergyLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayRepositoryTest {

    private val graph = DataGraph()
    private val oct15 = LocalDate(2026, 10, 15)

    @AfterTest
    fun close() = graph.db.close()

    @Test
    fun energy_isNullUntilSet() = runTest {
        assertNull(graph.days.observeEnergy(oct15).first())
    }

    @Test
    fun setEnergy_twiceTheSameDay_keepsOneRecordWithTheLatestLevel() = runTest {
        graph.days.setEnergy(oct15, EnergyLevel.High)
        graph.days.setEnergy(oct15, EnergyLevel.Low)

        assertEquals(EnergyLevel.Low, graph.days.observeEnergy(oct15).first())
        assertEquals(1, graph.db.outboxDao().all().count { it.entityType == "day_energy" })
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:data:jvmTest`
Expected: FAIL — `Unresolved reference 'IdGenerator'` / `'InboxRepository'`.

- [ ] **Step 3: Write the seams, mappers and drafts**

`shared/data/src/commonMain/kotlin/com/verto/data/Platform.kt`:

```kotlin
package com.verto.data

import kotlinx.datetime.TimeZone
import kotlin.uuid.Uuid

fun interface IdGenerator {
    fun newId(): String
}

object UuidIdGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}

/** Read on every use: the phone's zone can change while the app is open (travel). */
fun interface ZoneProvider {
    fun current(): TimeZone
}

object SystemZone : ZoneProvider {
    override fun current(): TimeZone = TimeZone.currentSystemDefault()
}
```

`shared/data/src/commonMain/kotlin/com/verto/data/Mappers.kt`:

```kotlin
package com.verto.data

import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.InboxItemEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import com.verto.data.sync.toEnum
import com.verto.data.sync.wire
import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.DueKind
import com.verto.domain.model.InboxItem
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineLog
import com.verto.domain.model.Step
import com.verto.domain.model.StepOwner
import com.verto.domain.model.Task
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlin.time.Instant

private fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

internal fun InboxItemEntity.toDomain() =
    InboxItem(id, text, createdAt.toInstant(), source.toEnum(), state.toEnum())

internal fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    notes = notes,
    dueDate = dueDate?.let { LocalDate.parse(it) },
    dueKind = dueKind?.toEnum<DueKind>(),
    pinnedToToday = pinnedToToday,
    doneAt = doneAt?.toInstant(),
)

internal fun Task.toEntity(updatedAt: Long, deletedAt: Long? = null) = TaskEntity(
    id = id,
    title = title,
    notes = notes,
    dueDate = dueDate?.toString(),
    dueKind = dueKind?.wire(),
    pinnedToToday = pinnedToToday,
    doneAt = doneAt?.toEpochMilliseconds(),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun StepEntity.toDomain(): Step {
    val owner = if (taskId != null) {
        StepOwner.OfTask(taskId)
    } else {
        StepOwner.OfRoutine(checkNotNull(routineId), checkNotNull(plan).toEnum())
    }
    return Step(id, owner, order, title, energy.toEnum(), doneAt?.toInstant())
}

internal fun AnchorEntity.toDomain() =
    Anchor(id, name, LocalTime.parse(windowStart), LocalTime.parse(windowEnd))

internal fun AnchorEventEntity.toDomain() =
    AnchorEvent(id, anchorId, LocalDate.parse(day), triggeredAt.toInstant(), source.toEnum())

internal fun RoutineEntity.toDomain() = Routine(id, name, anchorId, weekdaysOf(weekdays))

internal fun RoutineLogEntity.toDomain() = RoutineLog(id, routineId, LocalDate.parse(day), outcome.toEnum())

internal fun Set<DayOfWeek>.toWire(): String = map { it.isoDayNumber }.sorted().joinToString(",")

internal fun weekdaysOf(wire: String): Set<DayOfWeek> =
    wire.split(',').filter { it.isNotBlank() }.map { DayOfWeek(it.toInt()) }.toSet()
```

`shared/data/src/commonMain/kotlin/com/verto/data/repository/Drafts.kt`:

```kotlin
package com.verto.data.repository

import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorEvent
import com.verto.domain.model.DueKind
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineLog
import com.verto.domain.model.StepEnergy
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class StepDraft(val id: String?, val title: String, val energy: StepEnergy)

data class TaskDraft(
    val id: String?,
    val title: String,
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    val dueKind: DueKind? = null,
    val pinnedToToday: Boolean = false,
    val steps: List<StepDraft> = emptyList(),
    /** Read-only on load; finishing a task goes through [TaskRepository.setDone]. */
    val done: Boolean = false,
)

data class AnchorDraft(val id: String?, val name: String, val windowStart: LocalTime, val windowEnd: LocalTime)

data class RoutineDraft(
    val id: String?,
    val name: String,
    val anchorId: String,
    val weekdays: Set<DayOfWeek>,
    val stepsA: List<StepDraft>,
    val stepsC: List<StepDraft>,
)

data class RoutineDay(
    val anchors: List<Anchor>,
    val routines: List<Routine>,
    val events: List<AnchorEvent>,
    val logs: List<RoutineLog>,
)
```

- [ ] **Step 4: Write the four repositories**

`shared/data/src/commonMain/kotlin/com/verto/data/repository/InboxRepository.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.IdGenerator
import com.verto.data.db.InboxItemEntity
import com.verto.data.sync.LocalStore
import com.verto.data.sync.wire
import com.verto.data.toDomain
import com.verto.domain.VertoSettings
import com.verto.domain.model.InboxItem
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import com.verto.domain.rules.InboxRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class InboxRepository(
    private val store: LocalStore,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val dao = store.db.inboxDao()

    fun observe(): Flow<List<InboxItem>> = dao.observeActive().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: String): InboxItem? = dao.get(id)?.takeIf { it.deletedAt == null }?.toDomain()

    /** Returns null for blank text: a mis-tap on "Guardar" must not leave an empty card behind. */
    suspend fun capture(text: String, source: InboxSource): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val now = clock.now().toEpochMilliseconds()
        val id = ids.newId()
        store.write {
            put(InboxItemEntity(id, trimmed, now, source.wire(), InboxState.Raw.wire(), now, null))
        }
        return id
    }

    suspend fun setState(id: String, state: InboxState) {
        val now = clock.now().toEpochMilliseconds()
        store.write {
            val row = dao.get(id) ?: return@write
            put(row.copy(state = state.wire(), updatedAt = now))
        }
    }

    suspend fun incubateStale(settings: VertoSettings) {
        val now = clock.now()
        store.write {
            val rows = dao.active()
            val stale = InboxRules.toIncubate(rows.map { it.toDomain() }, now, settings.incubationAfter)
                .map { it.id }
                .toSet()
            rows.filter { it.id in stale }.forEach {
                put(it.copy(state = InboxState.Incubated.wire(), updatedAt = now.toEpochMilliseconds()))
            }
        }
    }
}
```

`shared/data/src/commonMain/kotlin/com/verto/data/repository/TaskRepository.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.IdGenerator
import com.verto.data.db.StepEntity
import com.verto.data.db.TaskEntity
import com.verto.data.sync.LocalStore
import com.verto.data.sync.toEnum
import com.verto.data.sync.wire
import com.verto.data.toDomain
import com.verto.data.toEntity
import com.verto.domain.model.DueKind
import com.verto.domain.model.Step
import com.verto.domain.model.Task
import com.verto.domain.rules.TaskRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

class TaskRepository(
    private val store: LocalStore,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val taskDao = store.db.taskDao()
    private val stepDao = store.db.stepDao()

    fun observeTasks(): Flow<List<Task>> = taskDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    /** Task and routine steps alike; callers filter by owner. */
    fun observeAllSteps(): Flow<List<Step>> = stepDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    suspend fun draftFor(id: String): TaskDraft? {
        val row = taskDao.get(id)?.takeIf { it.deletedAt == null } ?: return null
        return TaskDraft(
            id = row.id,
            title = row.title,
            notes = row.notes,
            dueDate = row.dueDate?.let { LocalDate.parse(it) },
            dueKind = row.dueKind?.toEnum<DueKind>(),
            pinnedToToday = row.pinnedToToday,
            steps = stepDao.forTask(id).map { StepDraft(it.id, it.title, it.energy.toEnum()) },
            done = row.doneAt != null,
        )
    }

    suspend fun save(draft: TaskDraft): String {
        require(draft.title.isNotBlank()) { "A task needs a title" }
        val id = draft.id ?: ids.newId()
        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()
        store.write {
            val existing = taskDao.get(id)
            val current = stepDao.forTask(id)
            val kept = draft.steps.mapNotNull { it.id }.toSet()
            current.filter { it.id !in kept }.forEach { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }

            val steps = draft.steps.mapIndexed { index, step ->
                StepEntity(
                    id = step.id ?: ids.newId(),
                    taskId = id,
                    routineId = null,
                    plan = null,
                    order = index,
                    title = step.title.trim(),
                    energy = step.energy.wire(),
                    doneAt = current.firstOrNull { it.id == step.id }?.doneAt,
                    updatedAt = nowMs,
                    deletedAt = null,
                )
            }
            steps.forEach { put(it) }

            val task = Task(
                id = id,
                title = draft.title.trim(),
                notes = draft.notes?.trim()?.ifBlank { null },
                dueDate = draft.dueDate,
                dueKind = draft.dueKind?.takeIf { draft.dueDate != null },
                pinnedToToday = draft.pinnedToToday,
                doneAt = existing?.doneAt?.let { Instant.fromEpochMilliseconds(it) },
            )
            val doneAt = TaskRules.doneAtAfterStepChange(task, steps.map { it.toDomain() }, now)
            put(task.copy(doneAt = doneAt).toEntity(nowMs))
        }
        return id
    }

    suspend fun setPinned(id: String, pinned: Boolean) = update(id) { it.copy(pinnedToToday = pinned) }

    suspend fun setDone(id: String, done: Boolean) {
        val nowMs = clock.now().toEpochMilliseconds()
        update(id) { it.copy(doneAt = if (done) it.doneAt ?: nowMs else null) }
    }

    suspend fun toggleStep(stepId: String) {
        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()
        store.write {
            val step = stepDao.get(stepId) ?: return@write
            val taskId = step.taskId ?: return@write
            put(step.copy(doneAt = if (step.doneAt == null) nowMs else null, updatedAt = nowMs))
            val task = taskDao.get(taskId) ?: return@write
            val doneAt = TaskRules.doneAtAfterStepChange(task.toDomain(), stepDao.forTask(taskId).map { it.toDomain() }, now)
            if (doneAt?.toEpochMilliseconds() != task.doneAt) {
                put(task.copy(doneAt = doneAt?.toEpochMilliseconds(), updatedAt = nowMs))
            }
        }
    }

    suspend fun delete(id: String) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            taskDao.get(id)?.let { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
            stepDao.forTask(id).forEach { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
        }
    }

    private suspend fun update(id: String, change: (TaskEntity) -> TaskEntity) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            val row = taskDao.get(id) ?: return@write
            put(change(row).copy(updatedAt = nowMs))
        }
    }
}
```

`shared/data/src/commonMain/kotlin/com/verto/data/repository/RoutineRepository.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.IdGenerator
import com.verto.data.ZoneProvider
import com.verto.data.db.AnchorEntity
import com.verto.data.db.AnchorEventEntity
import com.verto.data.db.RoutineEntity
import com.verto.data.db.RoutineLogEntity
import com.verto.data.db.StepEntity
import com.verto.data.sync.LocalStore
import com.verto.data.sync.toEnum
import com.verto.data.sync.wire
import com.verto.data.toDomain
import com.verto.data.toWire
import com.verto.domain.VertoSettings
import com.verto.domain.model.Anchor
import com.verto.domain.model.AnchorTrigger
import com.verto.domain.model.Plan
import com.verto.domain.model.Routine
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.rules.AnchorRules
import com.verto.domain.rules.RoutineRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

class RoutineRepository(
    private val store: LocalStore,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val zones: ZoneProvider,
    private val settings: VertoSettings,
) {
    private val anchorDao = store.db.anchorDao()
    private val routineDao = store.db.routineDao()
    private val stepDao = store.db.stepDao()

    fun observeAnchors(): Flow<List<Anchor>> = anchorDao.observeAnchors().map { rows -> rows.map { it.toDomain() } }

    fun observeRoutines(): Flow<List<Routine>> = routineDao.observeRoutines().map { rows -> rows.map { it.toDomain() } }

    fun observeDay(day: LocalDate): Flow<RoutineDay> = combine(
        observeAnchors(),
        observeRoutines(),
        anchorDao.observeEventsOn(day.toString()),
        routineDao.observeLogsOn(day.toString()),
    ) { anchors, routines, events, logs ->
        RoutineDay(anchors, routines, events.map { it.toDomain() }, logs.map { it.toDomain() })
    }

    suspend fun anchorDraft(id: String): AnchorDraft? =
        anchorDao.getAnchor(id)?.takeIf { it.deletedAt == null }?.toDomain()?.let {
            AnchorDraft(it.id, it.name, it.windowStart, it.windowEnd)
        }

    suspend fun saveAnchor(draft: AnchorDraft): String {
        val id = draft.id ?: ids.newId()
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            put(AnchorEntity(id, draft.name.trim(), draft.windowStart.toString(), draft.windowEnd.toString(), nowMs, null))
        }
        return id
    }

    /** Refused while routines still use the anchor: they would silently vanish from Hoje. */
    suspend fun deleteAnchor(id: String): Boolean {
        val nowMs = clock.now().toEpochMilliseconds()
        return store.write {
            if (routineDao.forAnchor(id).isNotEmpty()) return@write false
            anchorDao.getAnchor(id)?.let { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
            true
        }
    }

    suspend fun routineDraft(id: String): RoutineDraft? {
        val row = routineDao.getRoutine(id)?.takeIf { it.deletedAt == null } ?: return null
        val steps = stepDao.forRoutine(id)
        fun plan(plan: Plan) = steps.filter { it.plan == plan.wire() }
            .sortedBy { it.order }
            .map { StepDraft(it.id, it.title, it.energy.toEnum()) }
        val routine = row.toDomain()
        return RoutineDraft(routine.id, routine.name, routine.anchorId, routine.weekdays, plan(Plan.A), plan(Plan.C))
    }

    suspend fun saveRoutine(draft: RoutineDraft): String {
        val id = draft.id ?: ids.newId()
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            put(RoutineEntity(id, draft.name.trim(), draft.anchorId, draft.weekdays.toWire(), nowMs, null))
            val current = stepDao.forRoutine(id)
            val kept = (draft.stepsA + draft.stepsC).mapNotNull { it.id }.toSet()
            current.filter { it.id !in kept }.forEach { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
            listOf(Plan.A to draft.stepsA, Plan.C to draft.stepsC).forEach { (plan, steps) ->
                steps.forEachIndexed { index, step ->
                    put(
                        StepEntity(
                            id = step.id ?: ids.newId(),
                            taskId = null,
                            routineId = id,
                            plan = plan.wire(),
                            order = index,
                            title = step.title.trim(),
                            energy = step.energy.wire(),
                            doneAt = current.firstOrNull { it.id == step.id }?.doneAt,
                            updatedAt = nowMs,
                            deletedAt = null,
                        ),
                    )
                }
            }
        }
        return id
    }

    suspend fun deleteRoutine(id: String) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            routineDao.getRoutine(id)?.let { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
            stepDao.forRoutine(id).forEach { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
        }
    }

    suspend fun tapAnchor(anchorId: String, day: LocalDate) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            val anchor = anchorDao.getAnchor(anchorId)?.toDomain() ?: return@write
            val events = anchorDao.eventsOn(day.toString()).map { it.toDomain() }
            if (AnchorRules.canTap(anchor, day, events)) {
                put(AnchorEventEntity(ids.newId(), anchorId, day.toString(), nowMs, AnchorTrigger.Tap.wire(), nowMs, null))
            }
        }
    }

    suspend fun recordOpenedWindows(day: LocalDate) {
        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()
        store.write {
            val anchors = anchorDao.activeAnchors().map { it.toDomain() }
            val events = anchorDao.eventsOn(day.toString()).map { it.toDomain() }
            AnchorRules.windowsToRecord(anchors, day, events, now, zones.current(), settings.dayStartsAt).forEach {
                put(AnchorEventEntity(ids.newId(), it.id, day.toString(), nowMs, AnchorTrigger.Window.wire(), nowMs, null))
            }
        }
    }

    /** Checks or unchecks a routine step for [day] and keeps that day's log in step with what is complete. */
    suspend fun toggleStep(stepId: String, day: LocalDate) {
        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()
        val zone = zones.current()
        store.write {
            val row = stepDao.get(stepId) ?: return@write
            val routineId = row.routineId ?: return@write
            val doneToday = RoutineRules.isDoneOn(row.toDomain(), day, zone, settings.dayStartsAt)
            put(row.copy(doneAt = if (doneToday) null else nowMs, updatedAt = nowMs))

            val steps = stepDao.forRoutine(routineId).map { it.toDomain() }
            val outcome = RoutineRules.completedOutcome(routineId, steps, day, zone, settings.dayStartsAt)
            val log = routineDao.logFor(routineId, day.toString())
            when {
                outcome != null && log == null ->
                    put(RoutineLogEntity(ids.newId(), routineId, day.toString(), outcome.wire(), nowMs, null))
                outcome != null && log != null && log.outcome != outcome.wire() ->
                    put(log.copy(outcome = outcome.wire(), updatedAt = nowMs))
                outcome == null && log != null && log.outcome != RoutineOutcome.Skipped.wire() ->
                    put(log.copy(deletedAt = nowMs, updatedAt = nowMs))
            }
        }
    }

    suspend fun skip(routineId: String, day: LocalDate) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            val log = routineDao.logFor(routineId, day.toString())
            val skipped = RoutineOutcome.Skipped.wire()
            put(log?.copy(outcome = skipped, updatedAt = nowMs) ?: RoutineLogEntity(ids.newId(), routineId, day.toString(), skipped, nowMs, null))
        }
    }

    suspend fun clearLog(routineId: String, day: LocalDate) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            routineDao.logFor(routineId, day.toString())?.let { put(it.copy(deletedAt = nowMs, updatedAt = nowMs)) }
        }
    }
}
```

`shared/data/src/commonMain/kotlin/com/verto/data/repository/DayRepository.kt`:

```kotlin
package com.verto.data.repository

import com.verto.data.IdGenerator
import com.verto.data.db.DayEnergyEntity
import com.verto.data.sync.LocalStore
import com.verto.data.sync.toEnum
import com.verto.data.sync.wire
import com.verto.domain.model.EnergyLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

class DayRepository(
    private val store: LocalStore,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val dao = store.db.dayEnergyDao()

    fun observeEnergy(day: LocalDate): Flow<EnergyLevel?> =
        dao.observeLatest(day.toString()).map { row -> row?.level?.toEnum<EnergyLevel>() }

    suspend fun setEnergy(day: LocalDate, level: EnergyLevel) {
        val nowMs = clock.now().toEpochMilliseconds()
        store.write {
            val row = dao.latest(day.toString())
                ?: DayEnergyEntity(ids.newId(), day.toString(), level.wire(), nowMs, nowMs, null)
            put(row.copy(level = level.wire(), setAt = nowMs, updatedAt = nowMs))
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:data:jvmTest`
Expected: PASS — 27 tests (6 from Task 6 + 4 inbox + 6 task + 9 routine + 2 day).

- [ ] **Step 6: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "feat(data): repositórios de inbox, tarefas, rotinas e energia do dia"
```

---
### Task 8: App shell — theme, DI, navigation, Android wiring

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/theme/Theme.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/common/Formats.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/common/Labels.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/common/Ticker.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/common/Components.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/navigation/Routes.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/navigation/VertoNavHost.kt`
- Create (stubs, replaced in Tasks 9–12): `composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayScreen.kt`, `.../inbox/InboxScreen.kt`, `.../plan/PlanScreen.kt`, `.../editor/TaskEditorScreen.kt`, `.../editor/RoutineEditorScreen.kt`, `.../editor/AnchorEditorScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/App.kt` (full replacement)
- Create: `androidApp/src/main/kotlin/com/verto/android/VertoApplication.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml` (add `android:name`)
- Test: `composeApp/src/commonTest/kotlin/com/verto/ui/common/FormatsTest.kt`

**Interfaces:**
- Consumes: repositories, `LocalStore`, `IdGenerator`/`UuidIdGenerator`, `ZoneProvider`/`SystemZone` (Task 7); `vertoDatabase(context)` (Task 6).
- Produces (`com.verto.ui.common`): `LocalDate.longPt()` ("quinta, 15 de outubro"), `LocalDate.shortPt()` ("15/10"), `deadlineLabel(due, today)`, `softDateLabel(due)`, `LocalTime.hhmm()`, `parseClock(text): LocalTime?`, `weekdayShortNames: List<Pair<DayOfWeek, String>>`, `Set<DayOfWeek>.label()`; `EnergyLevel.label()`, `EnergyLevel.next()`, `StepEnergy.label()`; `fun interface Ticker { fun ticks(): Flow<Instant> }`, `ClockTicker(clock, every = 1.minutes)`; composables `SectionHeader(title, actionLabel?, onAction?)`, `Hint(text)`, `ListRow(title, subtitle?, onClick)`, `EditorScaffold(title, onBack, content)`, `StepCheckRow(title, checked, energy, onToggle)`.
- Produces: `vertoModule` (Koin) — later tasks add one ViewModel line each; routes `TodayRoute`, `InboxRoute`, `PlanRoute`, `TaskEditorRoute(taskId: String? = null)`, `RoutineEditorRoute(routineId: String? = null, fromInboxId: String? = null)`, `AnchorEditorRoute(anchorId: String? = null)`; screen signatures `TodayScreen()`, `InboxScreen(onMakeRoutine: (String) -> Unit)`, `PlanScreen(onOpenTask: (String?) -> Unit, onOpenRoutine: (String?) -> Unit, onOpenAnchor: (String?) -> Unit)`, `TaskEditorScreen(taskId: String?, onClose: () -> Unit)`, `RoutineEditorScreen(routineId: String?, fromInboxId: String?, onNewAnchor: () -> Unit, onClose: () -> Unit)`, `AnchorEditorScreen(anchorId: String?, onClose: () -> Unit)`.

- [ ] **Step 1: Write the failing formats test**

`composeApp/src/commonTest/kotlin/com/verto/ui/common/FormatsTest.kt`:

```kotlin
package com.verto.ui.common

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatsTest {

    private val oct15 = LocalDate(2026, 10, 15)

    @Test
    fun longPt_readsLikeACalendar() {
        assertEquals("quinta, 15 de outubro", oct15.longPt())
    }

    @Test
    fun deadlineLabel_isNeutralForEveryCase() {
        assertEquals("prazo era dia 12/10", deadlineLabel(LocalDate(2026, 10, 12), oct15))
        assertEquals("prazo é hoje", deadlineLabel(oct15, oct15))
        assertEquals("prazo dia 18/10", deadlineLabel(LocalDate(2026, 10, 18), oct15))
    }

    @Test
    fun parseClock_acceptsShortForms() {
        assertEquals(LocalTime(7, 0), parseClock("7"))
        assertEquals(LocalTime(7, 30), parseClock(" 07:30 "))
        assertEquals(LocalTime(12, 5), parseClock("12:5"))
    }

    @Test
    fun parseClock_rejectsImpossibleTimes() {
        assertNull(parseClock("24:00"))
        assertNull(parseClock("12:60"))
        assertNull(parseClock("almoço"))
        assertNull(parseClock(""))
    }

    @Test
    fun weekdaysLabel_namesEveryDayOnlyWhenNeeded() {
        assertEquals("todo dia", DayOfWeek.entries.toSet().label())
        assertEquals("seg · qua · sex", setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY).label())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd ~/Repos/VertoApp && ./gradlew :composeApp:jvmTest --tests "com.verto.ui.common.FormatsTest"`
Expected: FAIL — `Unresolved reference 'longPt'`.

- [ ] **Step 3: Write the common UI helpers and theme**

`composeApp/src/commonMain/kotlin/com/verto/ui/common/Formats.kt`:

```kotlin
package com.verto.ui.common

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

private val weekdayNames = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")

private val monthNames = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
)

val weekdayShortNames: List<Pair<DayOfWeek, String>> = listOf(
    DayOfWeek.MONDAY to "seg",
    DayOfWeek.TUESDAY to "ter",
    DayOfWeek.WEDNESDAY to "qua",
    DayOfWeek.THURSDAY to "qui",
    DayOfWeek.FRIDAY to "sex",
    DayOfWeek.SATURDAY to "sáb",
    DayOfWeek.SUNDAY to "dom",
)

private fun Int.twoDigits(): String = toString().padStart(2, '0')

fun LocalDate.longPt(): String = "${weekdayNames[dayOfWeek.isoDayNumber - 1]}, $day de ${monthNames[month.number - 1]}"

fun LocalDate.shortPt(): String = "${day.twoDigits()}/${month.number.twoDigits()}"

/** Neutral on purpose: a missed deadline is information, never an alarm. */
fun deadlineLabel(due: LocalDate, today: LocalDate): String = when {
    due < today -> "prazo era dia ${due.shortPt()}"
    due == today -> "prazo é hoje"
    else -> "prazo dia ${due.shortPt()}"
}

fun softDateLabel(due: LocalDate): String = "seria bom até ${due.shortPt()}"

fun LocalTime.hhmm(): String = "${hour.twoDigits()}:${minute.twoDigits()}"

/** Accepts "7", "07:30" or "12:5" so typing a time never needs exact formatting. */
fun parseClock(text: String): LocalTime? {
    val parts = text.trim().split(":")
    if (parts.size > 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = if (parts.size == 2) parts[1].toIntOrNull() ?: return null else 0
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime(hour, minute)
}

fun Set<DayOfWeek>.label(): String =
    if (size == 7) "todo dia" else weekdayShortNames.filter { it.first in this }.joinToString(" · ") { it.second }
```

`composeApp/src/commonMain/kotlin/com/verto/ui/common/Labels.kt`:

```kotlin
package com.verto.ui.common

import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.StepEnergy

fun EnergyLevel.label(): String = when (this) {
    EnergyLevel.Low -> "baixa"
    EnergyLevel.Medium -> "média"
    EnergyLevel.High -> "alta"
}

fun EnergyLevel.next(): EnergyLevel = when (this) {
    EnergyLevel.Low -> EnergyLevel.Medium
    EnergyLevel.Medium -> EnergyLevel.High
    EnergyLevel.High -> EnergyLevel.Low
}

fun StepEnergy.label(): String = when (this) {
    StepEnergy.Low -> "leve"
    StepEnergy.Medium -> "média"
    StepEnergy.Hyperfocus -> "hiperfoco"
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/common/Ticker.kt`:

```kotlin
package com.verto.ui.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Drives "now" for screens that change with the clock (anchor windows, the 04:00 day turn). */
fun interface Ticker {
    fun ticks(): Flow<Instant>
}

class ClockTicker(private val clock: Clock, private val every: Duration = 1.minutes) : Ticker {
    override fun ticks(): Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(every)
        }
    }
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/common/Components.kt`:

```kotlin
package com.verto.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verto.domain.model.StepEnergy

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ListRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StepCheckRow(title: String, checked: Boolean, energy: StepEnergy, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(energy.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EditorScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/theme/Theme.kt`:

```kotlin
package com.verto.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Calm blue-grey palette. The "error" slots carry a muted amber used only for gentle hints:
// the spec rules out alarm red anywhere in the app.
private val LightColors = lightColorScheme(
    primary = Color(0xFF3B6472),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFE9F9),
    onPrimaryContainer = Color(0xFF001F28),
    secondary = Color(0xFF4C6269),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE6EF),
    onSecondaryContainer = Color(0xFF071E24),
    tertiary = Color(0xFF5A5C7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E0FF),
    onTertiaryContainer = Color(0xFF171A37),
    error = Color(0xFF7A5A00),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDF9E),
    onErrorContainer = Color(0xFF261A00),
    background = Color(0xFFF5FAFC),
    onBackground = Color(0xFF171C1E),
    surface = Color(0xFFF5FAFC),
    onSurface = Color(0xFF171C1E),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF40484B),
    outline = Color(0xFF70787C),
    outlineVariant = Color(0xFFBFC8CB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3CDDD),
    onPrimary = Color(0xFF033542),
    primaryContainer = Color(0xFF214C5A),
    onPrimaryContainer = Color(0xFFBFE9F9),
    secondary = Color(0xFFB3CAD3),
    onSecondary = Color(0xFF1E333A),
    secondaryContainer = Color(0xFF354A51),
    onSecondaryContainer = Color(0xFFCFE6EF),
    tertiary = Color(0xFFC2C3EB),
    onTertiary = Color(0xFF2C2F4D),
    tertiaryContainer = Color(0xFF424465),
    onTertiaryContainer = Color(0xFFE0E0FF),
    error = Color(0xFFF1C048),
    onError = Color(0xFF402D00),
    errorContainer = Color(0xFF5C4300),
    onErrorContainer = Color(0xFFFFDF9E),
    background = Color(0xFF0F1416),
    onBackground = Color(0xFFDEE3E6),
    surface = Color(0xFF0F1416),
    onSurface = Color(0xFFDEE3E6),
    surfaceVariant = Color(0xFF40484B),
    onSurfaceVariant = Color(0xFFBFC8CB),
    outline = Color(0xFF899295),
    outlineVariant = Color(0xFF40484B),
)

@Composable
fun VertoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val base = Typography()
    val typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
    )
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = typography, content = content)
}
```

- [ ] **Step 4: Write routes, the screen stubs, the nav host, DI and `App`**

`composeApp/src/commonMain/kotlin/com/verto/ui/navigation/Routes.kt`:

```kotlin
package com.verto.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object InboxRoute

@Serializable
data object PlanRoute

@Serializable
data class TaskEditorRoute(val taskId: String? = null)

@Serializable
data class RoutineEditorRoute(val routineId: String? = null, val fromInboxId: String? = null)

@Serializable
data class AnchorEditorRoute(val anchorId: String? = null)
```

Stubs (each replaced wholesale in its own task):

`composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayScreen.kt`:

```kotlin
package com.verto.ui.today

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun TodayScreen() {
    Text("Hoje")
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/inbox/InboxScreen.kt`:

```kotlin
package com.verto.ui.inbox

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun InboxScreen(onMakeRoutine: (String) -> Unit) {
    Text("Inbox")
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanScreen.kt`:

```kotlin
package com.verto.ui.plan

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun PlanScreen(onOpenTask: (String?) -> Unit, onOpenRoutine: (String?) -> Unit, onOpenAnchor: (String?) -> Unit) {
    Text("Planejar")
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/editor/TaskEditorScreen.kt`:

```kotlin
package com.verto.ui.editor

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun TaskEditorScreen(taskId: String?, onClose: () -> Unit) {
    Text("Tarefa")
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/editor/RoutineEditorScreen.kt`:

```kotlin
package com.verto.ui.editor

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun RoutineEditorScreen(routineId: String?, fromInboxId: String?, onNewAnchor: () -> Unit, onClose: () -> Unit) {
    Text("Rotina")
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/editor/AnchorEditorScreen.kt`:

```kotlin
package com.verto.ui.editor

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun AnchorEditorScreen(anchorId: String?, onClose: () -> Unit) {
    Text("Âncora")
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/navigation/VertoNavHost.kt`:

```kotlin
package com.verto.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.verto.ui.editor.AnchorEditorScreen
import com.verto.ui.editor.RoutineEditorScreen
import com.verto.ui.editor.TaskEditorScreen
import com.verto.ui.inbox.InboxScreen
import com.verto.ui.plan.PlanScreen
import com.verto.ui.today.TodayScreen
import kotlin.reflect.KClass

private enum class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector) {
    Today(TodayRoute, TodayRoute::class, "Hoje", Icons.Filled.Home),
    Inbox(InboxRoute, InboxRoute::class, "Inbox", Icons.Filled.Email),
    Plan(PlanRoute, PlanRoute::class, "Planejar", Icons.AutoMirrored.Filled.List),
}

@Composable
fun VertoNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showTabs = Tab.entries.any { destination?.hasRoute(it.routeClass) == true }

    // Editors bring their own Scaffold; tab screens pad the status bar themselves.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hasRoute(tab.routeClass) == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = TodayRoute,
            modifier = Modifier.padding(padding),
            // Low-stimulus: screens swap without motion.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable<TodayRoute> { TodayScreen() }
            composable<InboxRoute> {
                InboxScreen(onMakeRoutine = { inboxId -> nav.navigate(RoutineEditorRoute(fromInboxId = inboxId)) })
            }
            composable<PlanRoute> {
                PlanScreen(
                    onOpenTask = { nav.navigate(TaskEditorRoute(it)) },
                    onOpenRoutine = { nav.navigate(RoutineEditorRoute(routineId = it)) },
                    onOpenAnchor = { nav.navigate(AnchorEditorRoute(it)) },
                )
            }
            composable<TaskEditorRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<TaskEditorRoute>()
                TaskEditorScreen(route.taskId, onClose = { nav.popBackStack() })
            }
            composable<RoutineEditorRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<RoutineEditorRoute>()
                RoutineEditorScreen(
                    routineId = route.routineId,
                    fromInboxId = route.fromInboxId,
                    onNewAnchor = { nav.navigate(AnchorEditorRoute()) },
                    onClose = { nav.popBackStack() },
                )
            }
            composable<AnchorEditorRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<AnchorEditorRoute>()
                AnchorEditorScreen(route.anchorId, onClose = { nav.popBackStack() })
            }
        }
    }
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt`:

```kotlin
package com.verto.ui.di

import com.verto.data.IdGenerator
import com.verto.data.SystemZone
import com.verto.data.UuidIdGenerator
import com.verto.data.ZoneProvider
import com.verto.data.repository.DayRepository
import com.verto.data.repository.InboxRepository
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.TaskRepository
import com.verto.data.sync.LocalStore
import com.verto.domain.VertoSettings
import com.verto.ui.common.ClockTicker
import com.verto.ui.common.Ticker
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.time.Clock

/** Expects a `VertoDatabase` from the platform module (androidApp). */
val vertoModule = module {
    single { VertoSettings() }
    single<Clock> { Clock.System }
    single<IdGenerator> { UuidIdGenerator }
    single<ZoneProvider> { SystemZone }
    single<Ticker> { ClockTicker(get()) }
    single { LocalStore(get()) }
    singleOf(::InboxRepository)
    singleOf(::TaskRepository)
    singleOf(::RoutineRepository)
    singleOf(::DayRepository)
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/App.kt` (full replacement):

```kotlin
package com.verto.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.verto.ui.navigation.VertoNavHost
import com.verto.ui.theme.VertoTheme

@Composable
fun App() {
    VertoTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            VertoNavHost()
        }
    }
}
```

`androidApp/src/main/kotlin/com/verto/android/VertoApplication.kt`:

```kotlin
package com.verto.android

import android.app.Application
import com.verto.data.db.vertoDatabase
import com.verto.ui.di.vertoModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class VertoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@VertoApplication)
            modules(
                module { single { vertoDatabase(androidContext()) } },
                vertoModule,
            )
        }
    }
}
```

In `androidApp/src/main/AndroidManifest.xml`, add `android:name=".VertoApplication"` to `<application>`:

```xml
    <application
        android:name=".VertoApplication"
        android:allowBackup="true"
        android:label="Verto"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
```

- [ ] **Step 5: Run the tests and build the APK**

Run: `cd ~/Repos/VertoApp && ./gradlew :composeApp:jvmTest :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`, `FormatsTest` 5/5.

- [ ] **Step 6: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "feat(ui): casca do app com tema calmo, Koin e navegação em 3 abas"
```

---

### Task 9: Inbox screen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/inbox/InboxViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/inbox/InboxScreen.kt` (full replacement)
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt` (add one line)
- Create: `composeApp/src/jvmTest/kotlin/com/verto/ui/UiTestGraph.kt`
- Test: `composeApp/src/jvmTest/kotlin/com/verto/ui/inbox/InboxViewModelTest.kt`

**Interfaces:**
- Consumes: `InboxRepository`, `TaskRepository`, `TaskDraft`, `VertoSettings` (Task 7); `Hint` (Task 8).
- Produces: `InboxUiState(draft, raw, incubated, showIncubated)`; `InboxViewModel(inbox, tasks, settings)` with `state`, `onDraftChange`, `capture`, `toTask(item)`, `incubate(id)`, `discard(id)`, `restore(id)`, `toggleIncubated`.
- Produces (test-only, `com.verto.ui`): `TestClock`, `SequentialIds`, `TestZone`, `uiAt(day, hour, minute = 0)`, `ManualTicker(start)` with `now: MutableStateFlow<Instant>`, `UiTestGraph(start)` exposing `clock, ids, settings, ticker, db, store, inbox, tasks, routines, days`, and `abstract class ViewModelTest` (sets `Dispatchers.Main` to an `UnconfinedTestDispatcher`).

The spec says "Gestos" for processing braindumps. This plan uses a row menu (…) instead of swipes: Material3 1.9's swipe API is in flux and a menu is harder to trigger by accident. Swipes can be layered on later without changing the ViewModel.

- [ ] **Step 1: Write the test graph and the failing ViewModel test**

`composeApp/src/jvmTest/kotlin/com/verto/ui/UiTestGraph.kt`:

```kotlin
package com.verto.ui

import com.verto.data.IdGenerator
import com.verto.data.db.inMemoryVertoDatabase
import com.verto.data.repository.DayRepository
import com.verto.data.repository.InboxRepository
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.TaskRepository
import com.verto.data.sync.LocalStore
import com.verto.domain.VertoSettings
import com.verto.ui.common.Ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Clock
import kotlin.time.Instant

class TestClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

class SequentialIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "id-${++next}"
}

val TestZone: TimeZone = TimeZone.of("America/Sao_Paulo")

fun uiAt(day: Int, hour: Int, minute: Int = 0): Instant =
    LocalDateTime(2026, 10, day, hour, minute).toInstant(TestZone)

class ManualTicker(start: Instant) : Ticker {
    val now = MutableStateFlow(start)
    override fun ticks(): Flow<Instant> = now
}

class UiTestGraph(start: Instant = uiAt(15, 10)) {
    val clock = TestClock(start)
    val ids = SequentialIds()
    val settings = VertoSettings()
    val ticker = ManualTicker(start)
    val db = inMemoryVertoDatabase()
    val store = LocalStore(db)
    val inbox = InboxRepository(store, clock, ids)
    val tasks = TaskRepository(store, clock, ids)
    val routines = RoutineRepository(store, clock, ids, { TestZone }, settings)
    val days = DayRepository(store, clock, ids)

    /** Moves both the repositories' clock and the screen's ticker. */
    fun advanceTo(instant: Instant) {
        clock.now = instant
        ticker.now.value = instant
    }
}

abstract class ViewModelTest {
    protected val graph = UiTestGraph()

    @BeforeTest
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        graph.db.close()
    }
}
```

`composeApp/src/jvmTest/kotlin/com/verto/ui/inbox/InboxViewModelTest.kt`:

```kotlin
package com.verto.ui.inbox

import com.verto.domain.model.InboxSource
import com.verto.ui.ViewModelTest
import com.verto.ui.uiAt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InboxViewModelTest : ViewModelTest() {

    private fun viewModel() = InboxViewModel(graph.inbox, graph.tasks, graph.settings)

    @Test
    fun capture_showsTheItemAndClearsTheField() = runTest {
        val vm = viewModel()

        vm.onDraftChange("comprar pão")
        vm.capture()

        val state = vm.state.first { it.raw.isNotEmpty() }
        assertEquals("comprar pão", state.raw.single().text)
        assertEquals("", state.draft)
    }

    @Test
    fun toTask_createsATaskAndLeavesTheRawList() = runTest {
        graph.inbox.capture("fazer IR", InboxSource.App)
        val vm = viewModel()
        val item = vm.state.first { it.raw.isNotEmpty() }.raw.single()

        vm.toTask(item)

        vm.state.first { it.raw.isEmpty() }
        assertEquals("fazer IR", graph.tasks.observeTasks().first().single().title)
    }

    @Test
    fun opening_incubatesStaleItemsSilently() = runTest {
        graph.clock.now = uiAt(10, 10)
        graph.inbox.capture("ideia antiga", InboxSource.App)
        graph.clock.now = uiAt(15, 10)

        val state = viewModel().state.first { it.incubated.isNotEmpty() }

        assertEquals("ideia antiga", state.incubated.single().text)
        assertEquals(emptyList(), state.raw)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd ~/Repos/VertoApp && ./gradlew :composeApp:jvmTest --tests "com.verto.ui.inbox.InboxViewModelTest"`
Expected: FAIL — `Unresolved reference 'InboxViewModel'`.

- [ ] **Step 3: Write the ViewModel, the screen and the DI line**

`composeApp/src/commonMain/kotlin/com/verto/ui/inbox/InboxViewModel.kt`:

```kotlin
package com.verto.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.InboxRepository
import com.verto.data.repository.TaskDraft
import com.verto.data.repository.TaskRepository
import com.verto.domain.VertoSettings
import com.verto.domain.model.InboxItem
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxUiState(
    val draft: String = "",
    val raw: List<InboxItem> = emptyList(),
    val incubated: List<InboxItem> = emptyList(),
    val showIncubated: Boolean = false,
)

class InboxViewModel(
    private val inbox: InboxRepository,
    private val tasks: TaskRepository,
    settings: VertoSettings,
) : ViewModel() {

    private val draft = MutableStateFlow("")
    private val showIncubated = MutableStateFlow(false)

    val state: StateFlow<InboxUiState> = combine(inbox.observe(), draft, showIncubated) { items, text, show ->
        InboxUiState(
            draft = text,
            raw = items.filter { it.state == InboxState.Raw },
            incubated = items.filter { it.state == InboxState.Incubated },
            showIncubated = show,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    init {
        viewModelScope.launch { inbox.incubateStale(settings) }
    }

    fun onDraftChange(text: String) {
        draft.value = text
    }

    fun capture() {
        val text = draft.value
        draft.value = ""
        viewModelScope.launch { inbox.capture(text, InboxSource.App) }
    }

    fun toTask(item: InboxItem) {
        viewModelScope.launch {
            tasks.save(TaskDraft(id = null, title = item.text))
            inbox.setState(item.id, InboxState.Processed)
        }
    }

    fun incubate(id: String) = setState(id, InboxState.Incubated)

    fun discard(id: String) = setState(id, InboxState.Discarded)

    fun restore(id: String) = setState(id, InboxState.Raw)

    fun toggleIncubated() {
        showIncubated.update { !it }
    }

    private fun setState(id: String, state: InboxState) {
        viewModelScope.launch { inbox.setState(id, state) }
    }
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/inbox/InboxScreen.kt` (full replacement):

```kotlin
package com.verto.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.ui.common.Hint
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InboxScreen(onMakeRoutine: (String) -> Unit, viewModel: InboxViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Despeja aqui o que tá na cabeça") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.capture() }),
                trailingIcon = {
                    TextButton(onClick = viewModel::capture, enabled = state.draft.isNotBlank()) { Text("Guardar") }
                },
            )
        }
        if (state.raw.isEmpty()) {
            item { Hint("Nada por aqui. Quando algo passar pela cabeça, joga aí em cima.") }
        }
        items(state.raw, key = { it.id }) { item ->
            InboxRow(
                text = item.text,
                muted = false,
                actions = listOf(
                    "Virar tarefa" to { viewModel.toTask(item) },
                    "Virar rotina" to { onMakeRoutine(item.id) },
                    "Incubar" to { viewModel.incubate(item.id) },
                    "Descartar" to { viewModel.discard(item.id) },
                ),
            )
        }
        item {
            TextButton(onClick = viewModel::toggleIncubated) {
                Text(if (state.showIncubated) "Esconder incubados" else "Ver incubados")
            }
        }
        if (state.showIncubated) {
            items(state.incubated, key = { "incubated-${it.id}" }) { item ->
                InboxRow(
                    text = item.text,
                    muted = true,
                    actions = listOf(
                        "Trazer de volta" to { viewModel.restore(item.id) },
                        "Descartar" to { viewModel.discard(item.id) },
                    ),
                )
            }
        }
    }
}

@Composable
private fun InboxRow(text: String, muted: Boolean, actions: List<Pair<String, () -> Unit>>) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Ações")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    actions.forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            menuOpen = false
                            action()
                        })
                    }
                }
            }
        }
    }
}
```

In `VertoModule.kt`, add the import `import com.verto.ui.inbox.InboxViewModel` and `import org.koin.core.module.dsl.viewModelOf`, then append inside `module { }`:

```kotlin
    viewModelOf(::InboxViewModel)
```

- [ ] **Step 4: Run the tests and build**

Run: `cd ~/Repos/VertoApp && ./gradlew :composeApp:jvmTest :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`; `InboxViewModelTest` 3/3, `FormatsTest` 5/5.

- [ ] **Step 5: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "feat(inbox): despejo rápido com incubação silenciosa"
```

---

### Task 10: Planejar list and the task editor

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/editor/StepList.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/editor/TaskEditorViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/editor/TaskEditorScreen.kt` (full replacement)
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanScreen.kt` (full replacement)
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt` (two lines)
- Test: `composeApp/src/commonTest/kotlin/com/verto/ui/editor/StepListOpsTest.kt`
- Test: `composeApp/src/jvmTest/kotlin/com/verto/ui/editor/TaskEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `TaskRepository`, `TaskDraft`, `StepDraft`, `RoutineRepository` (Task 7); common components and formats (Task 8); `UiTestGraph`/`ViewModelTest` (Task 9).
- Produces: `EditableStep(key, id: String?, title, energy)`; `KeyFactory.next(): String`; `StepListOps.add/update/remove/move/toDrafts`; composable `StepListEditor(steps, onAdd, onTitle: (key, String) -> Unit, onEnergy: (key, StepEnergy) -> Unit, onMove: (key, Int) -> Unit, onRemove: (key) -> Unit)`; composable `DueDatePicker(initial, onPick, onDismiss)`; `TaskEditorArgs(taskId)`, `TaskEditorState`, `TaskEditorViewModel(taskId, tasks)`; `PlanUiState`, `RoutineRow`, `PlanViewModel(tasks, routines)`.

- [ ] **Step 1: Write the failing tests**

`composeApp/src/commonTest/kotlin/com/verto/ui/editor/StepListOpsTest.kt`:

```kotlin
package com.verto.ui.editor

import com.verto.domain.model.StepEnergy
import kotlin.test.Test
import kotlin.test.assertEquals

class StepListOpsTest {

    private fun step(key: String, title: String = key) = EditableStep(key, null, title, StepEnergy.Low)

    @Test
    fun move_swapsWithTheNeighbour_andIgnoresTheEdges() {
        val steps = listOf(step("a"), step("b"), step("c"))

        assertEquals(listOf("a", "c", "b"), StepListOps.move(steps, "c", -1).map { it.key })
        assertEquals(steps, StepListOps.move(steps, "a", -1))
        assertEquals(steps, StepListOps.move(steps, "c", 1))
    }

    @Test
    fun toDrafts_dropsBlankSteps_andTrims() {
        val drafts = StepListOps.toDrafts(listOf(step("a", " abrir site "), step("b", "  ")))

        assertEquals(listOf("abrir site"), drafts.map { it.title })
    }
}
```

`composeApp/src/jvmTest/kotlin/com/verto/ui/editor/TaskEditorViewModelTest.kt`:

```kotlin
package com.verto.ui.editor

import com.verto.data.repository.StepDraft
import com.verto.data.repository.TaskDraft
import com.verto.domain.model.StepEnergy
import com.verto.ui.ViewModelTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TaskEditorViewModelTest : ViewModelTest() {

    @Test
    fun newTask_save_storesNonBlankStepsInTheChosenOrder() = runTest {
        val vm = TaskEditorViewModel(null, graph.tasks)
        vm.setTitle("Fazer IR")
        repeat(3) { vm.addStep() }
        val (first, second, third) = vm.state.value.steps.map { it.key }
        vm.setStepTitle(first, "baixar programa")
        vm.setStepTitle(second, "   ")
        vm.setStepTitle(third, "abrir site")
        vm.moveStep(third, -2)

        vm.save()

        vm.state.first { it.closed }
        val id = graph.tasks.observeTasks().first().single().id
        assertEquals(listOf("abrir site", "baixar programa"), graph.tasks.draftFor(id)!!.steps.map { it.title })
    }

    @Test
    fun existingTask_loadsItsDraft() = runTest {
        val id = graph.tasks.save(TaskDraft(null, "Ligar pro dentista", steps = listOf(StepDraft(null, "achar número", StepEnergy.Low))))

        val state = TaskEditorViewModel(id, graph.tasks).state.first { it.loaded }

        assertEquals("Ligar pro dentista", state.title)
        assertEquals(listOf("achar número"), state.steps.map { it.title })
    }

    @Test
    fun blankTitle_cannotBeSaved() = runTest {
        val vm = TaskEditorViewModel(null, graph.tasks)

        vm.setTitle("   ")
        vm.save()

        assertFalse(vm.state.value.canSave)
        assertEquals(emptyList(), graph.tasks.observeTasks().first())
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd ~/Repos/VertoApp && ./gradlew :composeApp:jvmTest --tests "com.verto.ui.editor.*"`
Expected: FAIL — `Unresolved reference 'EditableStep'` / `'TaskEditorViewModel'`.

- [ ] **Step 3: Write the step list editor**

`composeApp/src/commonMain/kotlin/com/verto/ui/editor/StepList.kt`:

```kotlin
package com.verto.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verto.data.repository.StepDraft
import com.verto.domain.model.StepEnergy
import com.verto.ui.common.label

/** [key] is a UI-only identity so rows keep focus while titles change; [id] is the stored step, if any. */
data class EditableStep(val key: String, val id: String?, val title: String, val energy: StepEnergy)

class KeyFactory {
    private var next = 0
    fun next(): String = "k${next++}"
}

object StepListOps {

    fun add(steps: List<EditableStep>, key: String): List<EditableStep> =
        steps + EditableStep(key, null, "", StepEnergy.Low)

    fun update(steps: List<EditableStep>, key: String, change: (EditableStep) -> EditableStep): List<EditableStep> =
        steps.map { if (it.key == key) change(it) else it }

    fun remove(steps: List<EditableStep>, key: String): List<EditableStep> = steps.filterNot { it.key == key }

    fun move(steps: List<EditableStep>, key: String, delta: Int): List<EditableStep> {
        val from = steps.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in steps.indices) return steps
        return steps.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun toDrafts(steps: List<EditableStep>): List<StepDraft> =
        steps.filter { it.title.isNotBlank() }.map { StepDraft(it.id, it.title.trim(), it.energy) }
}

@Composable
fun StepListEditor(
    steps: List<EditableStep>,
    onAdd: () -> Unit,
    onTitle: (String, String) -> Unit,
    onEnergy: (String, StepEnergy) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = step.title,
                        onValueChange = { onTitle(step.key, it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Passo ${index + 1}") },
                        singleLine = true,
                    )
                    IconButton(onClick = { onMove(step.key, -1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir")
                    }
                    IconButton(onClick = { onMove(step.key, 1) }, enabled = index < steps.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Descer")
                    }
                    IconButton(onClick = { onRemove(step.key) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remover")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepEnergy.entries.forEach { energy ->
                        FilterChip(
                            selected = step.energy == energy,
                            onClick = { onEnergy(step.key, energy) },
                            label = { Text(energy.label()) },
                        )
                    }
                }
            }
        }
        TextButton(onClick = onAdd) { Text("Adicionar passo") }
    }
}
```

- [ ] **Step 4: Write the task editor**

`composeApp/src/commonMain/kotlin/com/verto/ui/editor/TaskEditorViewModel.kt`:

```kotlin
package com.verto.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.TaskDraft
import com.verto.data.repository.TaskRepository
import com.verto.domain.model.DueKind
import com.verto.domain.model.StepEnergy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class TaskEditorArgs(val taskId: String?)

data class TaskEditorState(
    val loaded: Boolean = false,
    val id: String? = null,
    val title: String = "",
    val notes: String = "",
    val dueDate: LocalDate? = null,
    val dueKind: DueKind = DueKind.Soft,
    val pinned: Boolean = false,
    val done: Boolean = false,
    val steps: List<EditableStep> = emptyList(),
    val closed: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank()
}

class TaskEditorViewModel(taskId: String?, private val tasks: TaskRepository) : ViewModel() {

    private val keys = KeyFactory()
    private val _state = MutableStateFlow(TaskEditorState(loaded = taskId == null))
    val state: StateFlow<TaskEditorState> = _state.asStateFlow()

    init {
        if (taskId != null) {
            viewModelScope.launch {
                val draft = tasks.draftFor(taskId)
                _state.value = if (draft == null) {
                    TaskEditorState(loaded = true, closed = true)
                } else {
                    TaskEditorState(
                        loaded = true,
                        id = draft.id,
                        title = draft.title,
                        notes = draft.notes.orEmpty(),
                        dueDate = draft.dueDate,
                        dueKind = draft.dueKind ?: DueKind.Soft,
                        pinned = draft.pinnedToToday,
                        done = draft.done,
                        steps = draft.steps.map { EditableStep(keys.next(), it.id, it.title, it.energy) },
                    )
                }
            }
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value) }

    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    fun setDueDate(value: LocalDate?) = _state.update { it.copy(dueDate = value) }

    fun setDueKind(value: DueKind) = _state.update { it.copy(dueKind = value) }

    fun setPinned(value: Boolean) = _state.update { it.copy(pinned = value) }

    fun addStep() = _state.update { it.copy(steps = StepListOps.add(it.steps, keys.next())) }

    fun setStepTitle(key: String, title: String) =
        _state.update { it.copy(steps = StepListOps.update(it.steps, key) { step -> step.copy(title = title) }) }

    fun setStepEnergy(key: String, energy: StepEnergy) =
        _state.update { it.copy(steps = StepListOps.update(it.steps, key) { step -> step.copy(energy = energy) }) }

    fun moveStep(key: String, delta: Int) = _state.update { it.copy(steps = StepListOps.move(it.steps, key, delta)) }

    fun removeStep(key: String) = _state.update { it.copy(steps = StepListOps.remove(it.steps, key)) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            tasks.save(
                TaskDraft(
                    id = current.id,
                    title = current.title,
                    notes = current.notes,
                    dueDate = current.dueDate,
                    dueKind = current.dueKind.takeIf { current.dueDate != null },
                    pinnedToToday = current.pinned,
                    steps = StepListOps.toDrafts(current.steps),
                ),
            )
            _state.update { it.copy(closed = true) }
        }
    }

    fun toggleDone() {
        val id = _state.value.id ?: return
        val done = !_state.value.done
        viewModelScope.launch {
            tasks.setDone(id, done)
            _state.update { it.copy(done = done) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            tasks.delete(id)
            _state.update { it.copy(closed = true) }
        }
    }
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/editor/TaskEditorScreen.kt` (full replacement):

```kotlin
package com.verto.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.domain.model.DueKind
import com.verto.ui.common.EditorScaffold
import com.verto.ui.common.Hint
import com.verto.ui.common.shortPt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

@Composable
fun TaskEditorScreen(
    taskId: String?,
    onClose: () -> Unit,
    viewModel: TaskEditorViewModel = koinViewModel(parameters = { parametersOf(TaskEditorArgs(taskId)) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingDate by remember { mutableStateOf(false) }
    LaunchedEffect(state.closed) { if (state.closed) onClose() }

    EditorScaffold(title = if (state.id == null) "Nova tarefa" else "Tarefa", onBack = onClose) {
        if (!state.loaded) return@EditorScaffold
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::setTitle,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("O que é") },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::setNotes,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notas (opcional)") },
            minLines = 2,
        )
        FilterChip(selected = state.pinned, onClick = { viewModel.setPinned(!state.pinned) }, label = { Text("Pra hoje") })

        Text("Data", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { pickingDate = true }, label = { Text(state.dueDate?.shortPt() ?: "Escolher data") })
            if (state.dueDate != null) {
                TextButton(onClick = { viewModel.setDueDate(null) }) { Text("Sem data") }
            }
        }
        if (state.dueDate != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.dueKind == DueKind.Hard, { viewModel.setDueKind(DueKind.Hard) }, label = { Text("Prazo real") })
                FilterChip(state.dueKind == DueKind.Soft, { viewModel.setDueKind(DueKind.Soft) }, label = { Text("Seria bom até") })
            }
        }

        Text("Passos", style = MaterialTheme.typography.titleSmall)
        Hint("Quebra até o menor passo possível. Ex.: \"abrir o site da Receita\".")
        StepListEditor(
            steps = state.steps,
            onAdd = viewModel::addStep,
            onTitle = viewModel::setStepTitle,
            onEnergy = viewModel::setStepEnergy,
            onMove = viewModel::moveStep,
            onRemove = viewModel::removeStep,
        )

        Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
        if (state.id != null) {
            OutlinedButton(onClick = viewModel::toggleDone, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.done) "Reabrir" else "Concluir")
            }
            TextButton(onClick = viewModel::delete) { Text("Apagar tarefa") }
        }
    }

    if (pickingDate) {
        DueDatePicker(
            initial = state.dueDate,
            onPick = {
                viewModel.setDueDate(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
fun DueDatePicker(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The Material picker speaks UTC-midnight millis; converting through UTC keeps the calendar date intact.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis == null) onDismiss() else onPick(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
            }) { Text("Ok") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = pickerState)
    }
}
```

- [ ] **Step 5: Write the Planejar list**

`composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanViewModel.kt`:

```kotlin
package com.verto.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.TaskRepository
import com.verto.domain.model.Anchor
import com.verto.domain.model.Routine
import com.verto.domain.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineRow(val routine: Routine, val anchorName: String?)

data class PlanUiState(
    val openTasks: List<Task> = emptyList(),
    val doneTasks: List<Task> = emptyList(),
    val showDone: Boolean = false,
    val routines: List<RoutineRow> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
)

class PlanViewModel(private val tasks: TaskRepository, routines: RoutineRepository) : ViewModel() {

    private val showDone = MutableStateFlow(false)

    val state: StateFlow<PlanUiState> = combine(
        tasks.observeTasks(),
        routines.observeRoutines(),
        routines.observeAnchors(),
        showDone,
    ) { taskList, routineList, anchors, show ->
        val anchorNames = anchors.associate { it.id to it.name }
        PlanUiState(
            openTasks = taskList.filter { it.doneAt == null },
            doneTasks = taskList.filter { it.doneAt != null },
            showDone = show,
            routines = routineList.map { RoutineRow(it, anchorNames[it.anchorId]) },
            anchors = anchors,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun togglePinned(task: Task) {
        viewModelScope.launch { tasks.setPinned(task.id, !task.pinnedToToday) }
    }

    fun toggleShowDone() {
        showDone.update { !it }
    }
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanScreen.kt` (full replacement):

```kotlin
package com.verto.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.domain.model.DueKind
import com.verto.domain.model.Task
import com.verto.ui.common.Hint
import com.verto.ui.common.ListRow
import com.verto.ui.common.SectionHeader
import com.verto.ui.common.hhmm
import com.verto.ui.common.label
import com.verto.ui.common.shortPt
import com.verto.ui.common.softDateLabel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlanScreen(
    onOpenTask: (String?) -> Unit,
    onOpenRoutine: (String?) -> Unit,
    onOpenAnchor: (String?) -> Unit,
    viewModel: PlanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Tarefas", "Nova tarefa") { onOpenTask(null) } }
        if (state.openTasks.isEmpty()) item { Hint("Nenhuma tarefa aberta.") }
        items(state.openTasks, key = { it.id }) { task ->
            TaskRow(task, muted = false, onClick = { onOpenTask(task.id) }, onTogglePinned = { viewModel.togglePinned(task) })
        }
        if (state.doneTasks.isNotEmpty()) {
            item {
                TextButton(onClick = viewModel::toggleShowDone) { Text(if (state.showDone) "Esconder feitas" else "Ver feitas") }
            }
            if (state.showDone) {
                items(state.doneTasks, key = { "done-${it.id}" }) { task ->
                    TaskRow(task, muted = true, onClick = { onOpenTask(task.id) }, onTogglePinned = null)
                }
            }
        }

        item { SectionHeader("Rotinas", "Nova rotina") { onOpenRoutine(null) } }
        if (state.routines.isEmpty()) {
            item {
                Hint(
                    if (state.anchors.isEmpty()) "Crie uma âncora primeiro: é ela que diz quando a rotina acontece."
                    else "Nenhuma rotina ainda.",
                )
            }
        }
        items(state.routines, key = { "routine-${it.routine.id}" }) { row ->
            ListRow(
                title = row.routine.name,
                subtitle = listOfNotNull(row.anchorName?.let { "depois de \"$it\"" }, row.routine.weekdays.label()).joinToString(" · "),
                onClick = { onOpenRoutine(row.routine.id) },
            )
        }

        item { SectionHeader("Âncoras", "Nova âncora") { onOpenAnchor(null) } }
        if (state.anchors.isEmpty()) item { Hint("Âncoras são momentos do seu dia, tipo \"Acordei\" ou \"Almocei\".") }
        items(state.anchors, key = { "anchor-${it.id}" }) { anchor ->
            ListRow(anchor.name, "${anchor.windowStart.hhmm()}–${anchor.windowEnd.hhmm()}", onClick = { onOpenAnchor(anchor.id) })
        }
    }
}

@Composable
private fun TaskRow(task: Task, muted: Boolean, onClick: () -> Unit, onTogglePinned: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                task.dueDate?.let { due ->
                    Text(
                        if (task.dueKind == DueKind.Hard) "prazo ${due.shortPt()}" else softDateLabel(due),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onTogglePinned != null) {
                FilterChip(selected = task.pinnedToToday, onClick = onTogglePinned, label = { Text("Hoje") })
            }
        }
    }
}
```

In `VertoModule.kt`, add imports `com.verto.ui.plan.PlanViewModel`, `com.verto.ui.editor.TaskEditorArgs`, `com.verto.ui.editor.TaskEditorViewModel`, `org.koin.core.module.dsl.viewModel`, and append:

```kotlin
    viewModelOf(::PlanViewModel)
    viewModel { (args: TaskEditorArgs) -> TaskEditorViewModel(args.taskId, get()) }
```

- [ ] **Step 6: Run the tests and build**

Run: `cd ~/Repos/VertoApp && ./gradlew :composeApp:jvmTest :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`; composeApp tests 13/13 (5 formats + 3 inbox + 2 step list + 3 task editor).

- [ ] **Step 7: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "feat(plan): lista do Planejar e editor de tarefa com micro-passos"
```

---

### Task 11: Anchor and routine editors

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/editor/AnchorEditorViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/editor/AnchorEditorScreen.kt` (full replacement)
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/editor/RoutineEditorViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/editor/RoutineEditorScreen.kt` (full replacement)
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt` (two lines)
- Test: `composeApp/src/jvmTest/kotlin/com/verto/ui/editor/AnchorEditorViewModelTest.kt`
- Test: `composeApp/src/jvmTest/kotlin/com/verto/ui/editor/RoutineEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `RoutineRepository`, `AnchorDraft`, `RoutineDraft`, `InboxRepository` (Task 7); `parseClock`, `hhmm`, `weekdayShortNames` (Task 8); `EditableStep`, `KeyFactory`, `StepListOps`, `StepListEditor` (Task 10).
- Produces: `AnchorEditorArgs(anchorId)`, `AnchorEditorState`, `AnchorEditorViewModel(anchorId, routines)`; `RoutineEditorArgs(routineId, fromInboxId)`, `RoutineEditorState`, `RoutineEditorViewModel(args, routines, inbox)` with step operations taking a `Plan`.

Validation messages use the theme's muted amber (`colorScheme.error` slot), never red.

- [ ] **Step 1: Write the failing tests**

`composeApp/src/jvmTest/kotlin/com/verto/ui/editor/AnchorEditorViewModelTest.kt`:

```kotlin
package com.verto.ui.editor

import com.verto.data.repository.AnchorDraft
import com.verto.data.repository.RoutineDraft
import com.verto.data.repository.StepDraft
import com.verto.domain.model.StepEnergy
import com.verto.ui.ViewModelTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnchorEditorViewModelTest : ViewModelTest() {

    @Test
    fun save_acceptsShortTimes() = runTest {
        val vm = AnchorEditorViewModel(null, graph.routines)
        vm.setName("Almocei")
        vm.setStart("12")
        vm.setEnd("14:30")

        vm.save()

        vm.state.first { it.closed }
        val anchor = graph.routines.observeAnchors().first().single()
        assertEquals(LocalTime(12, 0), anchor.windowStart)
        assertEquals(LocalTime(14, 30), anchor.windowEnd)
    }

    @Test
    fun save_windowEndingBeforeItStarts_explainsInsteadOfSaving() = runTest {
        val vm = AnchorEditorViewModel(null, graph.routines)
        vm.setName("Jantei")
        vm.setStart("20:00")
        vm.setEnd("19:00")

        vm.save()

        assertNotNull(vm.state.value.message)
        assertTrue(graph.routines.observeAnchors().first().isEmpty())
    }

    @Test
    fun delete_anchorInUse_keepsItAndExplains() = runTest {
        val anchorId = graph.routines.saveAnchor(AnchorDraft(null, "Acordei", LocalTime(6, 0), LocalTime(9, 0)))
        graph.routines.saveRoutine(
            RoutineDraft(null, "Exercício", anchorId, DayOfWeek.entries.toSet(), listOf(StepDraft(null, "treino", StepEnergy.Medium)), listOf(StepDraft(null, "alongar", StepEnergy.Low))),
        )
        val vm = AnchorEditorViewModel(anchorId, graph.routines)
        vm.state.first { it.loaded }

        vm.delete()

        vm.state.first { it.message != null }
        assertEquals(1, graph.routines.observeAnchors().first().size)
    }
}
```

`composeApp/src/jvmTest/kotlin/com/verto/ui/editor/RoutineEditorViewModelTest.kt`:

```kotlin
package com.verto.ui.editor

import com.verto.data.repository.AnchorDraft
import com.verto.domain.model.InboxSource
import com.verto.domain.model.InboxState
import com.verto.domain.model.Plan
import com.verto.ui.ViewModelTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutineEditorViewModelTest : ViewModelTest() {

    private suspend fun anchor() = graph.routines.saveAnchor(AnchorDraft(null, "Acordei", LocalTime(6, 0), LocalTime(9, 0)))

    private fun RoutineEditorViewModel.step(plan: Plan, title: String) {
        addStep(plan)
        val key = (if (plan == Plan.A) state.value.stepsA else state.value.stepsC).last().key
        setStepTitle(plan, key, title)
    }

    @Test
    fun save_withoutMinimalPlan_explainsWhatIsMissing() = runTest {
        anchor()
        val vm = RoutineEditorViewModel(RoutineEditorArgs(null, null), graph.routines, graph.inbox)
        vm.state.first { it.loaded && it.anchorId != null }
        vm.setName("Exercício")
        vm.step(Plan.A, "treino de 30 min")

        vm.save()

        assertEquals("O plano mínimo precisa de pelo menos um passo.", vm.state.value.message)
        assertTrue(graph.routines.observeRoutines().first().isEmpty())
    }

    @Test
    fun save_fromInbox_prefillsTheName_andMarksTheItemProcessed() = runTest {
        anchor()
        val inboxId = graph.inbox.capture("alongar de manhã", InboxSource.App)!!
        val vm = RoutineEditorViewModel(RoutineEditorArgs(null, inboxId), graph.routines, graph.inbox)
        val loaded = vm.state.first { it.loaded && it.anchorId != null }
        assertEquals("alongar de manhã", loaded.name)
        vm.step(Plan.A, "10 min de alongamento")
        vm.step(Plan.C, "1 alongamento")

        vm.save()

        vm.state.first { it.closed }
        assertEquals("alongar de manhã", graph.routines.observeRoutines().first().single().name)
        assertEquals(InboxState.Processed, graph.inbox.get(inboxId)?.state)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd ~/Repos/VertoApp && ./gradlew :composeApp:jvmTest --tests "com.verto.ui.editor.*"`
Expected: FAIL — `Unresolved reference 'AnchorEditorViewModel'` / `'RoutineEditorViewModel'`.

- [ ] **Step 3: Write the anchor editor**

`composeApp/src/commonMain/kotlin/com/verto/ui/editor/AnchorEditorViewModel.kt`:

```kotlin
package com.verto.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.AnchorDraft
import com.verto.data.repository.RoutineRepository
import com.verto.ui.common.hhmm
import com.verto.ui.common.parseClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnchorEditorArgs(val anchorId: String?)

data class AnchorEditorState(
    val loaded: Boolean = false,
    val id: String? = null,
    val name: String = "",
    val start: String = "",
    val end: String = "",
    val message: String? = null,
    val closed: Boolean = false,
)

class AnchorEditorViewModel(anchorId: String?, private val routines: RoutineRepository) : ViewModel() {

    private val _state = MutableStateFlow(AnchorEditorState(loaded = anchorId == null))
    val state: StateFlow<AnchorEditorState> = _state.asStateFlow()

    init {
        if (anchorId != null) {
            viewModelScope.launch {
                val draft = routines.anchorDraft(anchorId)
                _state.value = if (draft == null) {
                    AnchorEditorState(loaded = true, closed = true)
                } else {
                    AnchorEditorState(true, draft.id, draft.name, draft.windowStart.hhmm(), draft.windowEnd.hhmm())
                }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, message = null) }

    fun setStart(value: String) = _state.update { it.copy(start = value, message = null) }

    fun setEnd(value: String) = _state.update { it.copy(end = value, message = null) }

    fun save() {
        val current = _state.value
        val start = parseClock(current.start)
        val end = parseClock(current.end)
        val problem = when {
            current.name.isBlank() -> "Dá um nome pra âncora, tipo \"Almocei\"."
            start == null || end == null -> "Use horários como 12:00."
            start >= end -> "A janela precisa terminar depois de começar."
            else -> null
        }
        if (problem != null || start == null || end == null) {
            _state.update { it.copy(message = problem) }
            return
        }
        viewModelScope.launch {
            routines.saveAnchor(AnchorDraft(current.id, current.name, start, end))
            _state.update { it.copy(closed = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            if (routines.deleteAnchor(id)) {
                _state.update { it.copy(closed = true) }
            } else {
                _state.update { it.copy(message = "Essa âncora ainda tem rotinas. Mude ou apague as rotinas antes.") }
            }
        }
    }
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/editor/AnchorEditorScreen.kt` (full replacement):

```kotlin
package com.verto.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.ui.common.EditorScaffold
import com.verto.ui.common.Hint
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AnchorEditorScreen(
    anchorId: String?,
    onClose: () -> Unit,
    viewModel: AnchorEditorViewModel = koinViewModel(parameters = { parametersOf(AnchorEditorArgs(anchorId)) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }

    EditorScaffold(title = if (state.id == null) "Nova âncora" else "Âncora", onBack = onClose) {
        if (!state.loaded) return@EditorScaffold
        Hint("Um momento do seu dia que puxa as rotinas, tipo \"Acordei\" ou \"Cheguei em casa\".")
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome") },
            singleLine = true,
        )
        Text("Janela de horário", style = MaterialTheme.typography.titleSmall)
        Hint("Se você não tocar na âncora, ela dispara sozinha quando a janela abre.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(state.start, viewModel::setStart, Modifier.weight(1f), label = { Text("De") }, placeholder = { Text("12:00") }, singleLine = true)
            OutlinedTextField(state.end, viewModel::setEnd, Modifier.weight(1f), label = { Text("Até") }, placeholder = { Text("14:00") }, singleLine = true)
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
        if (state.id != null) {
            TextButton(onClick = viewModel::delete) { Text("Apagar âncora") }
        }
    }
}
```

- [ ] **Step 4: Write the routine editor**

`composeApp/src/commonMain/kotlin/com/verto/ui/editor/RoutineEditorViewModel.kt`:

```kotlin
package com.verto.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.repository.InboxRepository
import com.verto.data.repository.RoutineDraft
import com.verto.data.repository.RoutineRepository
import com.verto.domain.model.Anchor
import com.verto.domain.model.InboxState
import com.verto.domain.model.Plan
import com.verto.domain.model.StepEnergy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

data class RoutineEditorArgs(val routineId: String?, val fromInboxId: String?)

data class RoutineEditorState(
    val loaded: Boolean = false,
    val id: String? = null,
    val name: String = "",
    val anchorId: String? = null,
    val anchors: List<Anchor> = emptyList(),
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val stepsA: List<EditableStep> = emptyList(),
    val stepsC: List<EditableStep> = emptyList(),
    val message: String? = null,
    val closed: Boolean = false,
)

class RoutineEditorViewModel(
    private val args: RoutineEditorArgs,
    private val routines: RoutineRepository,
    private val inbox: InboxRepository,
) : ViewModel() {

    private val keys = KeyFactory()
    private val _state = MutableStateFlow(RoutineEditorState())
    val state: StateFlow<RoutineEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            routines.observeAnchors().collect { anchors ->
                // A single anchor is the obvious choice; preselecting it saves a tap.
                _state.update { it.copy(anchors = anchors, anchorId = it.anchorId ?: anchors.singleOrNull()?.id) }
            }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val routineId = args.routineId
        if (routineId != null) {
            val draft = routines.routineDraft(routineId)
            if (draft == null) {
                _state.update { it.copy(loaded = true, closed = true) }
                return
            }
            _state.update {
                it.copy(
                    loaded = true,
                    id = draft.id,
                    name = draft.name,
                    anchorId = draft.anchorId,
                    weekdays = draft.weekdays,
                    stepsA = draft.stepsA.map { s -> EditableStep(keys.next(), s.id, s.title, s.energy) },
                    stepsC = draft.stepsC.map { s -> EditableStep(keys.next(), s.id, s.title, s.energy) },
                )
            }
        } else {
            val prefill = args.fromInboxId?.let { inbox.get(it)?.text }.orEmpty()
            _state.update { it.copy(loaded = true, name = prefill) }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, message = null) }

    fun setAnchor(anchorId: String) = _state.update { it.copy(anchorId = anchorId, message = null) }

    fun toggleWeekday(day: DayOfWeek) =
        _state.update { it.copy(weekdays = if (day in it.weekdays) it.weekdays - day else it.weekdays + day, message = null) }

    fun addStep(plan: Plan) = updateSteps(plan) { StepListOps.add(it, keys.next()) }

    fun setStepTitle(plan: Plan, key: String, title: String) =
        updateSteps(plan) { StepListOps.update(it, key) { step -> step.copy(title = title) } }

    fun setStepEnergy(plan: Plan, key: String, energy: StepEnergy) =
        updateSteps(plan) { StepListOps.update(it, key) { step -> step.copy(energy = energy) } }

    fun moveStep(plan: Plan, key: String, delta: Int) = updateSteps(plan) { StepListOps.move(it, key, delta) }

    fun removeStep(plan: Plan, key: String) = updateSteps(plan) { StepListOps.remove(it, key) }

    fun save() {
        val current = _state.value
        val stepsA = StepListOps.toDrafts(current.stepsA)
        val stepsC = StepListOps.toDrafts(current.stepsC)
        val anchorId = current.anchorId
        val problem = when {
            current.name.isBlank() -> "Dá um nome pra rotina."
            anchorId == null -> "Escolhe a âncora: depois de quê essa rotina acontece?"
            current.weekdays.isEmpty() -> "Escolhe pelo menos um dia."
            stepsA.isEmpty() -> "O plano ideal precisa de pelo menos um passo."
            stepsC.isEmpty() -> "O plano mínimo precisa de pelo menos um passo."
            else -> null
        }
        if (problem != null || anchorId == null) {
            _state.update { it.copy(message = problem) }
            return
        }
        viewModelScope.launch {
            routines.saveRoutine(RoutineDraft(current.id, current.name, anchorId, current.weekdays, stepsA, stepsC))
            args.fromInboxId?.let { inbox.setState(it, InboxState.Processed) }
            _state.update { it.copy(closed = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            routines.deleteRoutine(id)
            _state.update { it.copy(closed = true) }
        }
    }

    private fun updateSteps(plan: Plan, change: (List<EditableStep>) -> List<EditableStep>) = _state.update {
        if (plan == Plan.A) it.copy(stepsA = change(it.stepsA), message = null) else it.copy(stepsC = change(it.stepsC), message = null)
    }
}
```

`composeApp/src/commonMain/kotlin/com/verto/ui/editor/RoutineEditorScreen.kt` (full replacement):

```kotlin
package com.verto.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.domain.model.Plan
import com.verto.ui.common.EditorScaffold
import com.verto.ui.common.Hint
import com.verto.ui.common.weekdayShortNames
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RoutineEditorScreen(
    routineId: String?,
    fromInboxId: String?,
    onNewAnchor: () -> Unit,
    onClose: () -> Unit,
    viewModel: RoutineEditorViewModel = koinViewModel(parameters = { parametersOf(RoutineEditorArgs(routineId, fromInboxId)) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }

    EditorScaffold(title = if (state.id == null) "Nova rotina" else "Rotina", onBack = onClose) {
        if (!state.loaded) return@EditorScaffold
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nome") },
            singleLine = true,
        )

        Text("Depois de quê?", style = MaterialTheme.typography.titleSmall)
        if (state.anchors.isEmpty()) Hint("Você ainda não tem âncoras.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.anchors.forEach { anchor ->
                FilterChip(selected = state.anchorId == anchor.id, onClick = { viewModel.setAnchor(anchor.id) }, label = { Text(anchor.name) })
            }
            TextButton(onClick = onNewAnchor) { Text("Nova âncora") }
        }

        Text("Dias", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            weekdayShortNames.forEach { (day, name) ->
                FilterChip(selected = day in state.weekdays, onClick = { viewModel.toggleWeekday(day) }, label = { Text(name) })
            }
        }

        Text("Plano A · ideal", style = MaterialTheme.typography.titleSmall)
        Hint("O que você faria num dia bom.")
        StepListEditor(
            steps = state.stepsA,
            onAdd = { viewModel.addStep(Plan.A) },
            onTitle = { key, title -> viewModel.setStepTitle(Plan.A, key, title) },
            onEnergy = { key, energy -> viewModel.setStepEnergy(Plan.A, key, energy) },
            onMove = { key, delta -> viewModel.moveStep(Plan.A, key, delta) },
            onRemove = { key -> viewModel.removeStep(Plan.A, key) },
        )

        Text("Plano C · mínimo", style = MaterialTheme.typography.titleSmall)
        Hint("O mínimo que ainda conta. Ex.: 5 minutos de alongamento.")
        StepListEditor(
            steps = state.stepsC,
            onAdd = { viewModel.addStep(Plan.C) },
            onTitle = { key, title -> viewModel.setStepTitle(Plan.C, key, title) },
            onEnergy = { key, energy -> viewModel.setStepEnergy(Plan.C, key, energy) },
            onMove = { key, delta -> viewModel.moveStep(Plan.C, key, delta) },
            onRemove = { key -> viewModel.removeStep(Plan.C, key) },
        )

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
        if (state.id != null) {
            TextButton(onClick = viewModel::delete) { Text("Apagar rotina") }
        }
    }
}
```

In `VertoModule.kt`, add imports for `AnchorEditorArgs`, `AnchorEditorViewModel`, `RoutineEditorArgs`, `RoutineEditorViewModel`, and append:

```kotlin
    viewModel { (args: AnchorEditorArgs) -> AnchorEditorViewModel(args.anchorId, get()) }
    viewModel { (args: RoutineEditorArgs) -> RoutineEditorViewModel(args, get(), get()) }
```

- [ ] **Step 5: Run the tests and build**

Run: `cd ~/Repos/VertoApp && ./gradlew :composeApp:jvmTest :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`; composeApp tests 18/18.

- [ ] **Step 6: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "feat(plan): editores de âncora e de rotina com planos A e C"
```

---

### Task 12: Hoje screen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayScreen.kt` (full replacement)
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/di/VertoModule.kt` (one line)
- Test: `composeApp/src/jvmTest/kotlin/com/verto/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: `TodayBuilder`, `TodayInput`, `Today`, `LogicalDay` (domain); `RoutineRepository`, `TaskRepository`, `DayRepository`, `ZoneProvider` (data); `Ticker`, formats, labels, components (Task 8); `UiTestGraph` (Task 9).
- Produces: `TodayUiState.Loading` / `TodayUiState.Content(today, shownPlans: Map<String, Plan>, expandedIds: Set<String>)`; `TodayViewModel(routines, tasks, days, ticker, zones, settings)` with `cycleEnergy()`, `tapAnchor(anchorId)`, `toggleRoutineStep(stepId)`, `skip(routineId)`, `undo(routineId)`, `showPlan(routineId, plan)`, `toggleSection(anchorId)`, `toggleTaskStep(stepId)`, `completeTask(taskId)`.

Energy not set for the day reads as Medium (no Plan C suggestion): the spec is silent, and a neutral default avoids implying a bad day.

- [ ] **Step 1: Write the failing test**

`composeApp/src/jvmTest/kotlin/com/verto/ui/today/TodayViewModelTest.kt`:

```kotlin
package com.verto.ui.today

import com.verto.data.ZoneProvider
import com.verto.data.repository.AnchorDraft
import com.verto.data.repository.RoutineDraft
import com.verto.data.repository.StepDraft
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.model.StepEnergy
import com.verto.ui.TestZone
import com.verto.ui.ViewModelTest
import com.verto.ui.uiAt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodayViewModelTest : ViewModelTest() {

    private fun viewModel() = TodayViewModel(
        graph.routines,
        graph.tasks,
        graph.days,
        graph.ticker,
        ZoneProvider { TestZone },
        graph.settings,
    )

    private suspend fun lunchRoutine(): String {
        val anchorId = graph.routines.saveAnchor(AnchorDraft(null, "Almocei", LocalTime(12, 0), LocalTime(14, 0)))
        graph.routines.saveRoutine(
            RoutineDraft(
                id = null,
                name = "Caminhada",
                anchorId = anchorId,
                weekdays = DayOfWeek.entries.toSet(),
                stepsA = listOf(StepDraft(null, "20 min de caminhada", StepEnergy.Medium)),
                stepsC = listOf(StepDraft(null, "ir até a esquina", StepEnergy.Low)),
            ),
        )
        return anchorId
    }

    private suspend fun TodayViewModel.content(predicate: (TodayUiState.Content) -> Boolean = { true }) =
        state.first { it is TodayUiState.Content && predicate(it) } as TodayUiState.Content

    @Test
    fun lowEnergy_suggestsTheMinimalPlan() = runTest {
        lunchRoutine()
        graph.days.setEnergy(LocalDate(2026, 10, 15), EnergyLevel.Low)

        val content = viewModel().content { it.today.sections.isNotEmpty() }

        assertEquals(Plan.C, content.today.sections.single().routines.single().suggestedPlan)
    }

    @Test
    fun cycleEnergy_movesFromTheNeutralDefault() = runTest {
        val vm = viewModel()
        assertEquals(EnergyLevel.Medium, vm.content().today.energy)

        vm.cycleEnergy()

        vm.content { it.today.energy == EnergyLevel.High }
    }

    @Test
    fun tapAnchor_marksItTriggered() = runTest {
        val anchorId = lunchRoutine()
        val vm = viewModel()
        assertTrue(vm.content { it.today.sections.isNotEmpty() }.today.sections.none { it.triggered })

        vm.tapAnchor(anchorId)

        vm.content { it.today.sections.single().triggered }
    }

    @Test
    fun dayRollover_movesToNewDay_andResetsPlanChoices() = runTest {
        lunchRoutine()
        graph.advanceTo(uiAt(16, 3, 59))
        val vm = viewModel()
        val before = vm.content { it.today.sections.isNotEmpty() }
        assertEquals(LocalDate(2026, 10, 15), before.today.day)
        vm.showPlan(before.today.sections.single().routines.single().routine.id, Plan.C)
        vm.content { it.shownPlans.isNotEmpty() }

        graph.advanceTo(uiAt(16, 4, 1))

        val after = vm.content { it.today.day == LocalDate(2026, 10, 16) }
        assertTrue(after.shownPlans.isEmpty())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd ~/Repos/VertoApp && ./gradlew :composeApp:jvmTest --tests "com.verto.ui.today.TodayViewModelTest"`
Expected: FAIL — `Unresolved reference 'TodayViewModel'`.

- [ ] **Step 3: Write the ViewModel**

`composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayViewModel.kt`:

```kotlin
package com.verto.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verto.data.ZoneProvider
import com.verto.data.repository.DayRepository
import com.verto.data.repository.RoutineRepository
import com.verto.data.repository.TaskRepository
import com.verto.domain.VertoSettings
import com.verto.domain.model.EnergyLevel
import com.verto.domain.model.Plan
import com.verto.domain.rules.LogicalDay
import com.verto.domain.rules.Today
import com.verto.domain.rules.TodayBuilder
import com.verto.domain.rules.TodayInput
import com.verto.ui.common.Ticker
import com.verto.ui.common.next
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Content(
        val today: Today,
        val shownPlans: Map<String, Plan>,
        val expandedIds: Set<String>,
    ) : TodayUiState
}

class TodayViewModel(
    private val routines: RoutineRepository,
    private val tasks: TaskRepository,
    private val days: DayRepository,
    ticker: Ticker,
    private val zones: ZoneProvider,
    private val settings: VertoSettings,
) : ViewModel() {

    private val shownPlans = MutableStateFlow<Map<String, Plan>>(emptyMap())
    private val expandedOverride = MutableStateFlow<Set<String>?>(null)

    private val currentDay: StateFlow<LocalDate?> = ticker.ticks()
        .onEach { routines.recordOpenedWindows(dayOf(it)) }
        .map { dayOf(it) }
        .distinctUntilChanged()
        .onEach {
            // Plan choices and opened sections belong to one day; yesterday's must not leak into today.
            shownPlans.value = emptyMap()
            expandedOverride.value = null
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<TodayUiState> = currentDay.filterNotNull().flatMapLatest { day ->
        combine(
            routines.observeDay(day),
            tasks.observeTasks(),
            tasks.observeAllSteps(),
            days.observeEnergy(day),
            combine(shownPlans, expandedOverride) { plans, expanded -> plans to expanded },
        ) { routineDay, taskList, steps, energy, (plans, expanded) ->
            val today = TodayBuilder.build(
                TodayInput(
                    day = day,
                    zone = zones.current(),
                    settings = settings,
                    energy = energy ?: EnergyLevel.Medium,
                    anchors = routineDay.anchors,
                    routines = routineDay.routines,
                    steps = steps,
                    logs = routineDay.logs,
                    events = routineDay.events,
                    tasks = taskList,
                ),
            )
            TodayUiState.Content(today, plans, expanded ?: setOfNotNull(today.expandedAnchorId))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    fun cycleEnergy() {
        val content = state.value as? TodayUiState.Content ?: return
        viewModelScope.launch { days.setEnergy(content.today.day, content.today.energy.next()) }
    }

    fun tapAnchor(anchorId: String) = withDay { routines.tapAnchor(anchorId, it) }

    fun toggleRoutineStep(stepId: String) = withDay { routines.toggleStep(stepId, it) }

    fun skip(routineId: String) = withDay { routines.skip(routineId, it) }

    fun undo(routineId: String) = withDay { routines.clearLog(routineId, it) }

    fun showPlan(routineId: String, plan: Plan) {
        shownPlans.update { it + (routineId to plan) }
    }

    fun toggleSection(anchorId: String) {
        val content = state.value as? TodayUiState.Content ?: return
        val current = content.expandedIds
        expandedOverride.value = if (anchorId in current) current - anchorId else current + anchorId
    }

    fun toggleTaskStep(stepId: String) {
        viewModelScope.launch { tasks.toggleStep(stepId) }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch { tasks.setDone(taskId, true) }
    }

    private fun dayOf(instant: Instant): LocalDate = LogicalDay.of(instant, zones.current(), settings.dayStartsAt)

    private fun withDay(action: suspend (LocalDate) -> Unit) {
        val day = currentDay.value ?: return
        viewModelScope.launch { action(day) }
    }
}
```

- [ ] **Step 4: Write the screen and the DI line**

`composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayScreen.kt` (full replacement):

```kotlin
package com.verto.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.domain.model.DueKind
import com.verto.domain.model.Plan
import com.verto.domain.model.RoutineOutcome
import com.verto.domain.rules.AnchorSection
import com.verto.domain.rules.RoutineCard
import com.verto.domain.rules.TaskCard
import com.verto.ui.common.Hint
import com.verto.ui.common.SectionHeader
import com.verto.ui.common.StepCheckRow
import com.verto.ui.common.deadlineLabel
import com.verto.ui.common.label
import com.verto.ui.common.longPt
import com.verto.ui.common.softDateLabel
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TodayScreen(viewModel: TodayViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val content = state as? TodayUiState.Content ?: return
    val today = content.today

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(today.day.longPt().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall)
                AssistChip(onClick = viewModel::cycleEnergy, label = { Text("Energia: ${today.energy.label()}") })
            }
        }
        if (today.sections.isEmpty() && today.pinned.isEmpty() && today.deadlines.isEmpty()) {
            item { Hint("Nada marcado pra hoje. Tá tudo bem.") }
        }
        items(today.sections, key = { it.anchor.id }) { section ->
            AnchorSectionCard(
                section = section,
                expanded = section.anchor.id in content.expandedIds,
                shownPlan = { card -> content.shownPlans[card.routine.id] ?: card.suggestedPlan },
                onToggle = { viewModel.toggleSection(section.anchor.id) },
                onTap = { viewModel.tapAnchor(section.anchor.id) },
                onShowPlan = viewModel::showPlan,
                onToggleStep = viewModel::toggleRoutineStep,
                onSkip = viewModel::skip,
                onUndo = viewModel::undo,
            )
        }
        if (today.pinned.isNotEmpty()) {
            item { SectionHeader("Pra hoje") }
            if (today.pinnedOverSoftLimit) {
                item { Hint("Mais de três coisas pra hoje. Tudo bem, só vai com calma.") }
            }
            items(today.pinned, key = { "pinned-${it.task.id}" }) { card ->
                TaskCardView(card, today.day, viewModel::toggleTaskStep, viewModel::completeTask)
            }
        }
        if (today.deadlines.isNotEmpty()) {
            item { SectionHeader("Prazos chegando") }
            items(today.deadlines, key = { "deadline-${it.task.id}" }) { card ->
                TaskCardView(card, today.day, viewModel::toggleTaskStep, viewModel::completeTask)
            }
        }
    }
}

@Composable
private fun AnchorSectionCard(
    section: AnchorSection,
    expanded: Boolean,
    shownPlan: (RoutineCard) -> Plan,
    onToggle: () -> Unit,
    onTap: () -> Unit,
    onShowPlan: (String, Plan) -> Unit,
    onToggleStep: (String) -> Unit,
    onSkip: (String) -> Unit,
    onUndo: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(section.anchor.name, style = MaterialTheme.typography.titleMedium)
                    if (!expanded) {
                        Text(
                            section.routines.joinToString(" · ") { it.routine.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!section.triggered) {
                    OutlinedButton(onClick = onTap) { Text("Já foi") }
                }
            }
            if (expanded) {
                section.routines.forEach { card ->
                    RoutineBlock(card, shownPlan(card), onShowPlan, onToggleStep, onSkip, onUndo)
                }
            }
        }
    }
}

@Composable
private fun RoutineBlock(
    card: RoutineCard,
    plan: Plan,
    onShowPlan: (String, Plan) -> Unit,
    onToggleStep: (String) -> Unit,
    onSkip: (String) -> Unit,
    onUndo: (String) -> Unit,
) {
    val routineId = card.routine.id
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(card.routine.name, style = MaterialTheme.typography.titleSmall)
        if (card.outcome == RoutineOutcome.Skipped) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pulado hoje", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onUndo(routineId) }) { Text("Desfazer") }
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = plan == Plan.A, onClick = { onShowPlan(routineId, Plan.A) }, label = { Text("Ideal") })
            FilterChip(
                selected = plan == Plan.C,
                onClick = { onShowPlan(routineId, Plan.C) },
                label = { Text(if (card.suggestedPlan == Plan.C) "Mínimo · sugerido" else "Mínimo") },
            )
        }
        card.steps(plan).forEach { state ->
            StepCheckRow(state.step.title, state.done, state.step.energy) { onToggleStep(state.step.id) }
        }
        when (card.outcome) {
            null -> TextButton(onClick = { onSkip(routineId) }) { Text("Pular hoje") }
            RoutineOutcome.A -> Text("Feito.", color = MaterialTheme.colorScheme.primary)
            RoutineOutcome.C -> Text("Feito no mínimo. Conta igual.", color = MaterialTheme.colorScheme.primary)
            RoutineOutcome.Skipped -> Unit
        }
    }
}

@Composable
private fun TaskCardView(card: TaskCard, day: LocalDate, onToggleStep: (String) -> Unit, onComplete: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.task.title, style = MaterialTheme.typography.titleMedium)
            card.task.dueDate?.let { due ->
                Text(
                    if (card.task.dueKind == DueKind.Hard) deadlineLabel(due, day) else softDateLabel(due),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val next = card.nextStep
            if (next != null) {
                StepCheckRow("Próximo: ${next.title}", checked = false, energy = next.energy) { onToggleStep(next.id) }
            } else if (card.totalSteps == 0) {
                TextButton(onClick = { onComplete(card.task.id) }) { Text("Concluir") }
            }
        }
    }
}
```

In `VertoModule.kt`, add the import `com.verto.ui.today.TodayViewModel` and append:

```kotlin
    viewModelOf(::TodayViewModel)
```

- [ ] **Step 5: Run every test and build**

Run: `cd ~/Repos/VertoApp && ./gradlew :shared:domain:jvmTest :shared:data:jvmTest :composeApp:jvmTest :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`; domain 35, data 27, composeApp 22 tests passing.

- [ ] **Step 6: Commit**

```bash
cd ~/Repos/VertoApp
git add .
git commit -m "feat(today): tela Hoje com âncoras, planos A/C e prazos neutros"
```

---

### Task 13: Run it on a phone and walk the flows

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: the whole app.
- Produces: an installed debug build verified by hand; a README with run instructions.

This task has no new unit test: it checks what tests cannot (layout, insets, keyboard, Koin wiring, Room on a real device).

- [ ] **Step 1: Write `README.md`**

````markdown
# VertoApp

App do Verto (KMP + Compose Multiplatform, Android primeiro). Design: `~/Repos/Verto/docs/superpowers/specs/2026-10-15-verto-design.md`.

## Módulos

- `shared/domain`: regras do planner em Kotlin puro (a maior parte dos testes).
- `shared/data`: Room KMP + outbox para o sync.
- `composeApp`: telas, ViewModels e Koin.
- `androidApp`: o app Android.

## Testes

```bash
./gradlew :shared:domain:jvmTest :shared:data:jvmTest :composeApp:jvmTest
```

## Rodar no emulador

```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone &
./gradlew :androidApp:installDebug
```
````

- [ ] **Step 2: Install on the emulator**

Start the emulator in the background (it keeps running), then install and launch:

```bash
cd ~/Repos/VertoApp
~/Library/Android/sdk/platform-tools/adb wait-for-device
./gradlew :androidApp:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.verto.android/.MainActivity
```

Expected: `Installed on 1 device`; the app opens on **Hoje** showing today's date in Portuguese and "Nada marcado pra hoje. Tá tudo bem.".

- [ ] **Step 3: Walk every flow and compare with the expected result**

| # | Do | Expected |
|---|---|---|
| 1 | Inbox → type "ligar pro dentista" → Done on the keyboard | Card appears, field clears |
| 2 | Inbox → tap "Guardar" with only spaces | Button disabled, nothing created |
| 3 | Card menu (…) → "Virar tarefa" | Card leaves the inbox; Planejar lists the task |
| 4 | Planejar → "Nova âncora" → "Almocei", 12 / 14 → Salvar | Anchor listed as `12:00–14:00` |
| 5 | Planejar → "Nova rotina" → "Caminhada", Almocei preselected, A "20 min", C "até a esquina" → Salvar | Routine listed: `depois de "Almocei" · todo dia` |
| 6 | Planejar → open "Almocei" → "Apagar âncora" | Amber message about routines; anchor stays |
| 7 | Hoje | "Almocei" section expanded, "Já foi" visible (before 12:00) or absent (after) |
| 8 | Tap "Energia" until "baixa" | "Mínimo · sugerido" chip selected, C steps shown |
| 9 | Check "até a esquina" | "Feito no mínimo. Conta igual." |
| 10 | Planejar → task → "Pra hoje" chip, add steps, set a date as "Prazo real" in the past | Hoje shows it under "Pra hoje" with "prazo era dia DD/MM" (no red anywhere) |
| 11 | Hoje → check "Próximo: …" until the last step | Task disappears from Hoje; Planejar → "Ver feitas" lists it |
| 12 | Toggle the phone to dark mode | Calm dark palette, text readable |
| 13 | Kill the app and reopen | Everything is still there (Room) |

Screenshot step 7 and step 10 (`adb exec-out screencap -p > hoje.png`) for the PR.

If any row fails, debug with superpowers:systematic-debugging, add a unit test that reproduces it where possible, fix, and rerun the full suite before ticking the step.

- [ ] **Step 4: Commit**

```bash
cd ~/Repos/VertoApp
git add README.md
git commit -m "docs: README com testes e como rodar no emulador"
```
