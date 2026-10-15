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
