# Verto Visual Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aplicar a identidade “caminho maleável” ao Verto, com Average Sans + Encode Sans Condensed, navegação Agora/Rascunho/Trajeto e Trajeto dividido em abas, sem alterar as regras de domínio existentes.

**Architecture:** A implementação começa no tema e em primitivas visuais reutilizáveis, depois migra cada destino principal separadamente. O estado da aba interna de Trajeto pertence ao `PlanViewModel`, para sobreviver à restauração do destino sem persistência em banco; tarefas, rotinas, âncoras e seus editores continuam usando os repositórios e rotas atuais.

**Tech Stack:** Kotlin 2.4.21, Compose Multiplatform 1.12.2, Material 3 1.9.0, Navigation Compose 2.9.2, Koin 4.2.2, Kotlin Test, Gradle, Android API 26+.

**Spec:** `docs/superpowers/specs/2026-10-16-verto-visual-identity-design.md`

## Global Constraints

- O projeto continua KMP/CMP e Android primeiro; nenhuma mudança exige API ou banco novos.
- Empacotar Average Sans 400 e Encode Sans Condensed 400/500/600 no app, com as licenças OFL; não carregar fontes pela rede.
- Average Sans é a voz em peso 400; nunca sintetizar negrito.
- Encode Sans Condensed é a interface em pesos 400/500/600; não usar caixa alta nem pesos 700–900.
- Preservar todos os fluxos atuais: Só 1 coisa, túnel, planos ideal/mínimo, itens incubados, tarefas feitas, dia limpo, Ajustes, Conta e sync.
- As três áreas principais compartilham o mesmo tema; nenhuma pode mudar isoladamente para fundo claro.
- O modo escuro é a referência de aceite, mas o tema claro existente deve continuar coerente e funcional.
- Caminho somente onde existe sequência real; dobra forte no foco, pequena nas listas e no indicador de aba.
- Manter navegação e troca de abas sem movimento automático; respeitar redução de movimento.
- Alvos de toque têm pelo menos 48 dp e seleção não depende somente de cor.
- Não alterar arquivos alheios à tarefa nem sobrescrever mudanças existentes da usuária.

## Review Focus

- **Texto longo e fonte ampliada:** títulos, nomes e ações devem quebrar sem sobreposição; Task 2 define componentes flexíveis e Task 8 valida Pixel 9 com fonte ampliada.
- **Coleções vazias ou mistas:** Rascunho e cada aba de Trajeto devem manter ação e explicação corretas; Tasks 5, 6 e 7 cobrem estados vazios, itens feitos e contagens.
- **Restauração da aba de Trajeto:** sair e voltar deve preservar a lente quando o destino for restaurado; Task 6 testa o estado e Task 7 valida o fluxo real de navegação.
- **Coerência claro/escuro:** Agora, Rascunho e Trajeto nunca podem parecer produtos diferentes; Tasks 1 e 8 verificam ambos os temas.
- **Regressão de ações existentes:** concluir/pular/desfazer, converter/incubar/restaurar, editar/criar e abrir Ajustes devem continuar alcançáveis; Tasks 4, 5, 7 e 8 executam testes e smoke tests correspondentes.

---

### Task 1: Empacotar fontes e definir a tipografia da marca

**Files:**
- Create: `composeApp/src/commonMain/composeResources/font/average_sans_regular.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/encode_sans_condensed_regular.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/encode_sans_condensed_medium.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/encode_sans_condensed_semibold.ttf`
- Create: `composeApp/src/commonMain/composeResources/files/OFL-average-sans.txt`
- Create: `composeApp/src/commonMain/composeResources/files/OFL-encode-sans.txt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/theme/Typography.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/theme/Theme.kt:17-145`
- Test: `composeApp/src/commonTest/kotlin/com/verto/ui/theme/TypographyContractTest.kt`

**Interfaces:**
- Consumes: recursos oficiais de `google/fonts/ofl/averagesans` e `google/fonts/ofl/encodesanscondensed`.
- Produces: `internal fun buildVertoTypography(voice: FontFamily, interfaceFont: FontFamily): Typography` e `@Composable internal fun rememberVertoTypography(): Typography`.

- [ ] **Step 1: Escrever o teste do contrato tipográfico**

```kotlin
class TypographyContractTest {
    @Test
    fun voiceAndInterfaceRoles_useApprovedFamiliesAndWeights() {
        val typography = buildVertoTypography(FontFamily.Serif, FontFamily.Monospace)

        assertEquals(FontFamily.Serif, typography.headlineMedium.fontFamily)
        assertEquals(FontWeight.Normal, typography.headlineMedium.fontWeight)
        assertEquals(FontFamily.Serif, typography.bodyLarge.fontFamily)
        assertEquals(FontFamily.Monospace, typography.bodySmall.fontFamily)
        assertEquals(FontWeight.SemiBold, typography.labelLarge.fontWeight)
        assertEquals(FontWeight.Medium, typography.labelMedium.fontWeight)
    }
}
```

- [ ] **Step 2: Executar o teste e confirmar a falha**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.theme.TypographyContractTest'`

Expected: FAIL porque `buildVertoTypography` ainda não existe.

- [ ] **Step 3: Adicionar fontes e licenças oficiais aos recursos**

Baixar somente os quatro TTFs aprovados e os respectivos `OFL.txt` dos diretórios oficiais do Google Fonts. Não adicionar pesos não usados. Conferir que o Gradle gera `Res.font.average_sans_regular` e os três recursos de Encode.

- [ ] **Step 4: Implementar o construtor puro e o carregador Compose**

Em `Typography.kt`, `buildVertoTypography` recebe famílias já resolvidas e mapeia:

- `display*`, `headline*`, `title*`, `bodyLarge` e `bodyMedium` para `voice`, sempre `FontWeight.Normal`;
- `bodySmall` e `label*` para `interfaceFont`, usando 400/500/600 conforme o contrato;
- escala atual como ponto de partida, removendo pesos sintéticos de Average Sans.

`rememberVertoTypography()` cria as famílias a partir de `Res.font.*` e delega ao construtor puro. `VertoTheme` chama essa função e mantém os dois esquemas de cor existentes, atualizando os tokens de superfície para os valores do spec onde necessário.

- [ ] **Step 5: Executar teste e compilação dos recursos**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.theme.TypographyContractTest' :androidApp:assembleDebug`

Expected: PASS e `BUILD SUCCESSFUL`, sem fallback ou erro de recurso de fonte.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/composeResources composeApp/src/commonMain/kotlin/com/verto/ui/theme composeApp/src/commonTest/kotlin/com/verto/ui/theme
git commit -m "feat(ui): add Verto brand typography"
```

### Task 2: Criar primitivas de dobra, caminho e lista

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/common/FoldedSurface.kt`
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/common/PathRail.kt`
- Create: `composeApp/src/commonTest/kotlin/com/verto/ui/common/FoldGeometryTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/common/Components.kt:41-167`

**Interfaces:**
- Consumes: paleta e tipografia do Task 1.
- Produces:
  - `internal fun foldTriangle(width: Float, height: Float, requestedSize: Float): List<Offset>`
  - `@Composable fun FoldedSurface(modifier: Modifier = Modifier, foldColor: Color, foldSize: Dp = 24.dp, onClick: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit)`
  - `enum class PathNodeState { Past, Current, Future }`
  - `@Composable fun PathRail(nodeState: PathNodeState, drawBefore: Boolean, drawAfter: Boolean, modifier: Modifier = Modifier)`
  - `@Composable fun BrandListRow(title: String, subtitle: String?, accent: Color, onClick: () -> Unit)`

- [ ] **Step 1: Escrever o teste da geometria segura da dobra**

```kotlin
class FoldGeometryTest {
    @Test
    fun foldTriangle_clampsToSurfaceBounds() {
        val points = foldTriangle(width = 20f, height = 16f, requestedSize = 40f)

        assertEquals(3, points.size)
        assertTrue(points.all { it.x in 0f..20f && it.y in 0f..16f })
        assertEquals(Offset(20f, 16f), points.last())
    }
}
```

- [ ] **Step 2: Executar o teste e confirmar a falha**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.common.FoldGeometryTest'`

Expected: FAIL porque `foldTriangle` ainda não existe.

- [ ] **Step 3: Implementar `foldTriangle` e `FoldedSurface`**

Manter o desenho da dobra dentro dos limites do componente, atrás do conteúdo e fora da semântica. `FoldedSurface` não impõe altura, permite quebra de texto e mantém o `onClick` em toda a superfície com alvo mínimo de 48 dp.

- [ ] **Step 4: Implementar `PathRail` e migrar `ListRow` para `BrandListRow`**

`PathRail` desenha apenas linha e nó; a tela decide se há trecho antes/depois. `BrandListRow` mantém a faixa semântica lateral, usa uma dobra pequena e não restringe o título a uma linha. Manter `ListRow` temporariamente como delegação compatível ou migrar todos os usos dentro deste task para não quebrar chamadas existentes.

- [ ] **Step 5: Executar teste e compilação**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.common.FoldGeometryTest' :androidApp:assembleDebug`

Expected: PASS e `BUILD SUCCESSFUL`.

- [ ] **Step 6: Verificar manualmente os componentes isolados**

No preview ou numa tela existente, conferir texto de duas linhas, fonte do sistema ampliada e superfície estreita; nenhuma dobra pode cobrir texto ou reduzir o alvo de toque.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/verto/ui/common composeApp/src/commonTest/kotlin/com/verto/ui/common
git commit -m "feat(ui): add path and fold primitives"
```

### Task 3: Renomear e alinhar a navegação principal

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/navigation/VertoNavHost.kt:41-96`
- Test: `composeApp/src/commonTest/kotlin/com/verto/ui/navigation/NavigationLabelsTest.kt`

**Interfaces:**
- Consumes: tipografia do Task 1.
- Produces: `internal enum class PrimaryTab(..., val label: String, ...)` com rótulos `Agora`, `Rascunho`, `Trajeto`.

- [ ] **Step 1: Escrever o teste dos nomes aprovados**

```kotlin
class NavigationLabelsTest {
    @Test
    fun primaryTabs_useApprovedLabelsInOrder() {
        assertEquals(listOf("Agora", "Rascunho", "Trajeto"), PrimaryTab.entries.map { it.label })
    }
}
```

- [ ] **Step 2: Executar o teste e confirmar a falha**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.navigation.NavigationLabelsTest'`

Expected: FAIL porque `PrimaryTab` e os novos rótulos ainda não existem.

- [ ] **Step 3: Renomear o enum e aplicar o tratamento visual**

Manter as rotas `TodayRoute`, `InboxRoute` e `PlanRoute`. Usar Encode Sans Condensed via `labelMedium`, fundo `surfaceContainerLow`, texto selecionado principal e um indicador curto na cor de foco; ícone continua apoio, não o único estado.

- [ ] **Step 4: Executar teste e compilação**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.navigation.NavigationLabelsTest' :androidApp:assembleDebug`

Expected: PASS e `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/verto/ui/navigation/VertoNavHost.kt composeApp/src/commonTest/kotlin/com/verto/ui/navigation/NavigationLabelsTest.kt
git commit -m "feat(ui): rename Verto primary navigation"
```

### Task 4: Aplicar a identidade à tela Agora

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/today/TodayScreen.kt:83-413`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/today/TunnelOverlay.kt:1-126`
- Test: `composeApp/src/jvmTest/kotlin/com/verto/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: `FoldedSurface`, `PathRail`, `BrandListRow` e tema dos Tasks 1–2.
- Produces: Agora com cabeçalho, energia, cartão de foco dobrado e fluxo real usando caminho atrás do conteúdo; não produz APIs para outras tasks.

- [ ] **Step 1: Executar a regressão comportamental antes da mudança**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.today.TodayViewModelTest'`

Expected: PASS; guardar o total de testes como baseline.

- [ ] **Step 2: Renomear o cabeçalho e transformar o cartão de foco**

Trocar somente o título de tela para “Agora”. Manter a data e o seletor de energia. `FocusCard` usa `FoldedSurface`, rótulo “Seu próximo passo”, Average Sans para o título e ação “Marcar como feito”; toque no cartão ainda abre o túnel e o botão ainda conclui diretamente.

- [ ] **Step 3: Integrar o caminho ao fluxo existente**

Substituir o trilho reto interno de `AnchorFlowCard` por `PathRail`, mantendo exatamente os callbacks `onToggle`, `onTap`, `onShowPlan`, `onToggleStep`, `onSkip` e `onUndo`. Passado fica reduzido, atual ganha nó/dobra e futuro permanece legível; não esconder seções, tarefas fixadas, prazos ou feitas recentes.

- [ ] **Step 4: Alinhar o túnel ao tema sem mudar ações**

Aplicar tipografia e superfícies da marca em `TunnelOverlay`; preservar Feito, Outro, Tá grande demais e Sair, inclusive estados de habilitação.

- [ ] **Step 5: Executar regressão e compilação**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.today.TodayViewModelTest' :androidApp:assembleDebug`

Expected: o mesmo conjunto de testes PASS e `BUILD SUCCESSFUL`.

- [ ] **Step 6: Smoke test no Pixel 9**

Verificar: mudança de energia, abrir/fechar túnel, concluir, pedir Outro, dividir passo, pular/desfazer rotina, alternar ideal/mínimo, abrir tarefa e rolar até prazos/feitas. O caminho nunca atravessa texto.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/verto/ui/today
git commit -m "feat(ui): reshape the Agora experience"
```

### Task 5: Aplicar a identidade à tela Rascunho

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/inbox/InboxScreen.kt:44-171`
- Test: `composeApp/src/jvmTest/kotlin/com/verto/ui/inbox/InboxViewModelTest.kt`

**Interfaces:**
- Consumes: `FoldedSurface`, `BrandListRow` e tema dos Tasks 1–2.
- Produces: Rascunho com captura dobrada, estado vazio e listas bruta/incubada preservadas.

- [ ] **Step 1: Executar a regressão comportamental antes da mudança**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.inbox.InboxViewModelTest'`

Expected: PASS; guardar o total de testes como baseline.

- [ ] **Step 2: Renomear e redesenhar a captura**

Título “Rascunho”, subtítulo “Tira da cabeça sem decidir.”, convite “O que apareceu?” e placeholder “Escreve do jeito que vier.”. A captura usa `FoldedSurface`; Guardar continua desabilitado quando o rascunho está vazio e a ação do IME continua chamando `capture()`.

- [ ] **Step 3: Atualizar vazio e listas sem perder ações**

Usar “A cabeça respirou.” e “Nada aqui virou obrigação. Você escolhe o que ganha caminho quando estiver pronta.” quando `raw` estiver vazio. Aplicar a nova lista a itens brutos e incubados, preservando Virar tarefa, Virar rotina, Incubar, Descartar e Trazer de volta.

- [ ] **Step 4: Executar regressão e compilação**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.inbox.InboxViewModelTest' :androidApp:assembleDebug`

Expected: o mesmo conjunto de testes PASS e `BUILD SUCCESSFUL`.

- [ ] **Step 5: Smoke test vazio, bruto e incubado**

No Pixel 9, validar rascunho vazio, item capturado, menu de ações, seção incubada vazia, incubar/restaurar/descartar e teclado aberto. O estado vazio não pode empurrar o campo para fora da primeira tela.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/verto/ui/inbox/InboxScreen.kt
git commit -m "feat(ui): reshape the Rascunho experience"
```

### Task 6: Adicionar o estado das abas de Trajeto

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanTab.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanViewModel.kt:18-76`
- Create: `composeApp/src/jvmTest/kotlin/com/verto/ui/plan/PlanViewModelTest.kt`

**Interfaces:**
- Consumes: repositórios e `PlanUiState` atuais.
- Produces:
  - `enum class PlanTab(val label: String) { Tasks("Tarefas"), Routines("Rotinas"), Anchors("Âncoras") }`
  - `PlanUiState.selectedTab: PlanTab`
  - `PlanUiState.taskCount`, `routineCount`, `anchorCount`
  - `fun PlanViewModel.selectTab(tab: PlanTab)`

- [ ] **Step 1: Escrever os testes do estado de apresentação**

```kotlin
class PlanViewModelTest : ViewModelTest() {
    @Test fun initialTab_isTasks()
    @Test fun selectTab_updatesOnlyPresentationState()
    @Test fun counts_includeOpenAndDoneTasks_routinesAndAnchors()
    @Test fun togglingDone_keepsTheSelectedTab()
}
```

Nos testes, criar pelo menos uma tarefa aberta, uma concluída, uma rotina e uma âncora; afirmar contagens exatas e que repositórios não mudam ao selecionar aba.

- [ ] **Step 2: Executar os testes e confirmar a falha**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.plan.PlanViewModelTest'`

Expected: FAIL porque `PlanTab`, contagens e `selectTab` ainda não existem.

- [ ] **Step 3: Implementar estado e seleção**

Adicionar `MutableStateFlow(PlanTab.Tasks)` ao ViewModel e incluí-lo no `combine`. As contagens são derivadas das listas já carregadas; tarefas contam abertas + feitas. Selecionar aba não escreve em repositório e não reinicia `showDone`.

- [ ] **Step 4: Executar os testes**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.plan.PlanViewModelTest'`

Expected: quatro testes PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/verto/ui/plan composeApp/src/jvmTest/kotlin/com/verto/ui/plan
git commit -m "feat(ui): add Trajeto tab state"
```

### Task 7: Organizar Trajeto em três abas internas

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanTabBar.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/plan/PlanScreen.kt:51-174`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/navigation/VertoNavHost.kt:102-108`
- Test: `composeApp/src/jvmTest/kotlin/com/verto/ui/plan/PlanViewModelTest.kt`

**Interfaces:**
- Consumes: `PlanTab`, `PlanUiState` e `selectTab` do Task 6; `FoldedSurface` e `BrandListRow` do Task 2.
- Produces: `@Composable internal fun PlanTabBar(selected: PlanTab, onSelect: (PlanTab) -> Unit, modifier: Modifier = Modifier)` e Trajeto contextual completo.

- [ ] **Step 1: Completar o contrato de restauração no teste do ViewModel**

```kotlin
@Test
fun refreshedRepositoryContent_keepsTheSelectedTab() = runTest {
    val vm = viewModel()
    vm.selectTab(PlanTab.Anchors)
    graph.tasks.save(TaskDraft(null, "Comprar ração"))

    vm.state.first { it.taskCount == 1 }
    assertEquals(PlanTab.Anchors, vm.state.value.selectedTab)
}
```

- [ ] **Step 2: Executar o contrato de estado antes da mudança visual**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.plan.PlanViewModelTest.refreshedRepositoryContent_keepsTheSelectedTab'`

Expected: PASS; este é o baseline que a nova composição não pode quebrar.

- [ ] **Step 3: Implementar `PlanTabBar` com semântica acessível**

Distribuir três rótulos em largura igual sobre uma linha de caminho. Cada aba tem alvo de 48 dp, papel de tab, estado selecionado e indicador por texto + ponto maior + pequena dobra; não animar automaticamente.

- [ ] **Step 4: Reestruturar `PlanScreen`**

Cabeçalho “Trajeto”, subtítulo “Dá forma ao que vem depois.”, resumo pluralizado a partir das três contagens, ação contextual de adicionar e Ajustes sempre alcançável. Renderizar apenas a lista da aba ativa:

- `Tasks`: abertas, controle Ver feitas/Esconder feitas e feitas quando expandido;
- `Routines`: rotinas ou instrução para criar âncora primeiro;
- `Anchors`: âncoras e seu vazio explicativo.

O `+` chama `onOpenTask(null)`, `onOpenRoutine(null)` ou `onOpenAnchor(null)` conforme `selectedTab`. O ícone de Ajustes continua chamando `onOpenSettings`.

- [ ] **Step 5: Executar testes e compilação**

Run: `./gradlew :composeApp:jvmTest --tests 'com.verto.ui.plan.PlanViewModelTest' :androidApp:assembleDebug`

Expected: todos os testes de `PlanViewModelTest` PASS e `BUILD SUCCESSFUL`.

- [ ] **Step 6: Smoke test de todas as abas e rotas**

No Pixel 9: alternar abas; sair para Agora e voltar; confirmar aba preservada; criar e editar cada tipo; abrir Ajustes; remover tarefa de Agora; alternar feitas; validar vazios. A barra inferior permanece visível e visualmente primária.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/verto/ui/plan composeApp/src/commonMain/kotlin/com/verto/ui/navigation/VertoNavHost.kt composeApp/src/jvmTest/kotlin/com/verto/ui/plan
git commit -m "feat(ui): organize Trajeto into tabs"
```

### Task 8: Fechar consistência, documentação e validação integrada

**Files:**
- Modify: `README.md:1-35`
- Modify: `composeApp/src/commonMain/kotlin/com/verto/ui/common/Components.kt:93-220`
- Verify: `composeApp/src/commonMain/kotlin/com/verto/ui/FreshStartScreen.kt`
- Verify: `composeApp/src/commonMain/kotlin/com/verto/ui/settings/SettingsScreen.kt`
- Verify: `composeApp/src/commonMain/kotlin/com/verto/ui/account/AccountScreen.kt`
- Verify: `composeApp/src/commonMain/kotlin/com/verto/ui/editor/TaskEditorScreen.kt`
- Verify: `composeApp/src/commonMain/kotlin/com/verto/ui/editor/RoutineEditorScreen.kt`
- Verify: `composeApp/src/commonMain/kotlin/com/verto/ui/editor/AnchorEditorScreen.kt`

**Interfaces:**
- Consumes: todas as entregas anteriores.
- Produces: sistema integrado, documentação com os nomes atuais e evidência final de regressão/visual.

- [ ] **Step 1: Fazer a varredura de linguagem antiga**

Run: `rg -n '"Hoje"|"Caçamba"|"Planejar"|Hoje →|Caçamba|Planejar' composeApp/src README.md`

Expected: encontrar apenas referências que realmente descrevem o conceito temporal “hoje” ou documentação histórica; rótulos de destinos ainda antigos são falha.

- [ ] **Step 2: Atualizar README e componentes auxiliares compartilhados**

Atualizar a documentação para Agora/Rascunho/Trajeto e o novo acesso a Ajustes. Em `Components.kt`, confirmar que `EmptyState`, `EditorSection` e `EditorScaffold` usam o novo sistema tipográfico e superfícies coerentes; não adicionar caminhos ou dobras onde não tenham função.

- [ ] **Step 3: Executar a suíte completa**

Run: `./gradlew :shared:domain:jvmTest :shared:data:jvmTest :composeApp:jvmTest`

Expected: `BUILD SUCCESSFUL`, zero testes falhando.

- [ ] **Step 4: Montar e instalar o Android debug**

Run: `./gradlew :androidApp:assembleDebug :androidApp:installDebug`

Expected: `BUILD SUCCESSFUL` e instalação concluída no Pixel 9.

- [ ] **Step 5: Validar os cinco focos de revisão no Pixel 9**

Executar e registrar o resultado desta matriz:

1. modo escuro e modo claro nas três áreas;
2. fonte padrão e fonte ampliada, incluindo títulos longos e nomes de duas linhas;
3. Rascunho vazio, bruto e incubado;
4. Trajeto vazio, misto, com feitas expandidas e aba restaurada;
5. Agora com foco, sem foco, âncoras expandidas, túnel e prazos.

Expected: sem corte, sobreposição, perda de ação, quebra de contraste ou regressão funcional. Elementos decorativos não são anunciados pelo TalkBack; abas anunciam nome e seleção.

- [ ] **Step 6: Confirmar o diff e a higiene do repositório**

Run: `git diff --check && git status --short`

Expected: nenhum erro de whitespace; somente arquivos intencionais da identidade e documentação.

- [ ] **Step 7: Commit**

```bash
git add README.md composeApp/src/commonMain/kotlin/com/verto/ui/common/Components.kt
git commit -m "docs: align Verto naming and visual guidance"
```

- [ ] **Step 8: Revisão final da branch**

Comparar a implementação linha a linha com `docs/superpowers/specs/2026-10-16-verto-visual-identity-design.md`. Verificar especialmente que o redesenho não removeu capacidades existentes e que Trajeto não voltou a empilhar as três divisões numa lista única.
