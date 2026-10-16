# VertoApp

App do Verto (KMP + Compose Multiplatform, Android primeiro). Design: `~/Repos/Verto/docs/superpowers/specs/2026-10-15-verto-design.md`.

## Módulos

- `shared/domain`: regras do planner em Kotlin puro (a maior parte dos testes).
- `shared/data`: Room KMP + outbox para o sync.
- `composeApp`: telas, ViewModels e Koin.
- `androidApp`: o app Android.

## Identidade

As três áreas são **Agora** (o presente e o próximo passo), **Rascunho** (despejo sem decidir nada) e **Trajeto** (tarefas, rotinas e âncoras, em abas). As rotas internas continuam `TodayRoute`, `InboxRoute` e `PlanRoute`.

- Fontes empacotadas no app (licença OFL, sem download): Average Sans 400 é a voz (títulos, nomes, mensagens); Encode Sans Condensed 400/500/600 é a interface (navegação, botões, metadados).
- Caminho só onde há sequência real (fluxo do dia na Agora, abas do Trajeto); dobra forte no foco, pequena nas listas.
- Spec: `docs/superpowers/specs/2026-10-16-verto-visual-identity-design.md`.

## Testes

```bash
./gradlew :shared:domain:jvmTest :shared:data:jvmTest :composeApp:jvmTest
```

## Rodar no emulador

```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone &
./gradlew :androidApp:installDebug
```

## Sync

O app sincroniza com a API em `https://verto-api.billfolder.app` (repo `verto-api`). Login em Trajeto → engrenagem (Ajustes) → "Conta e sync".

- Toda mudança vai pro Room e pro `outbox` na mesma transação; o sync manda o outbox e depois puxa o que mudou desde o último cursor.
- Roda ao abrir o app, uns segundos depois de cada mudança, com espera crescente sem rede e de hora em hora pelo WorkManager.
- Se um registro tem mudança local pendente, a versão do servidor é ignorada até o próximo push.
- Os tokens ficam em SharedPreferences privadas, fora do backup do Android.

```bash
./gradlew :shared:data:jvmTest --tests '*Sync*'
```

## Só 1 coisa, dia limpo e Ajustes

- **Só 1 coisa**: o card do topo da Agora mostra um passo só, escolhido pela energia do dia (baixa = só passos leves; média = leves e médios; alta = todos), na ordem: rotina de âncora que já disparou → tarefa com prazo real → tarefa fixada. Tocar no card abre em tela cheia com Feito, Outro, Tá grande demais e Sair.
- **Dia limpo**: tira tudo de Pra hoje e incuba os despejos soltos. Nada é apagado nem marcado como atrasado. Fica em Ajustes e na tela "Que bom te ver", que aparece depois de alguns dias sem abrir o app.
- **Ajustes** (engrenagem no Trajeto): virada do dia, dias de ausência, horário de silêncio e Conta e sync. Os ajustes ficam neste aparelho.

```bash
./gradlew :shared:domain:jvmTest --tests '*TunnelRules*'
```
