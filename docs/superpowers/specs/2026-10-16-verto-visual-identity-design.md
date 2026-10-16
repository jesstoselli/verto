# Identidade visual e navegação principal do Verto

Data: 16 de outubro de 2026

## Intenção

O Verto é um planner que organiza sem punir. Ele deve ajudar a pessoa a voltar, perceber o que cabe no dia real e encontrar somente o próximo passo, sem transformar ausência, mudança de energia ou acúmulo em culpa.

A identidade precisa comunicar duas ideias ao mesmo tempo:

- existe orientação, mas não rigidez;
- existe ação, mas não cobrança.

A frase que guia as decisões é **“Só o próximo passo.”** A personalidade é a combinação de **instrumento silencioso** com **guia gentil**.

## Objetivos

- Dar ao app uma identidade reconhecível e coerente nas três áreas principais.
- Manter a interface calma, legível e funcional em telas pequenas.
- Diferenciar orientação, foco, energia e adaptação sem usar decoração gratuita.
- Tornar a área de planejamento mais compreensível e menos parecida com um cadastro longo.
- Substituir a linguagem de produtividade genérica por um vocabulário próprio e acolhedor.
- Preservar os fluxos e regras de domínio já existentes.

## Não objetivos

- Redesenhar as regras de seleção do “Só 1 coisa”.
- Alterar sincronização, persistência, conta, dia limpo, virada do dia ou regras de ausência.
- Reestruturar os modelos de tarefa, rotina e âncora.
- Criar gamificação, métricas de produtividade, sequências ou punições por atraso.
- Introduzir animações contínuas ou estímulos visuais que disputem atenção com o conteúdo.

## Antiobjetivos

O resultado não pode parecer:

- um painel corporativo;
- um aplicativo de hábitos que cobra sequências perfeitas;
- um conjunto genérico de cartões arredondados;
- um sistema excessivamente fofo que enfraqueça a clareza funcional;
- três produtos diferentes unidos apenas pela barra inferior.

## Conceito: caminho maleável

O conceito visual é **“Estrutura que cede.”** O Verto oferece um caminho, mas esse caminho muda de forma para acomodar o dia real.

### Caminho

O caminho é uma linha discreta de orientação. Ele aparece somente quando existe sequência verdadeira, especialmente em Agora: âncoras, rotinas, passos e o que vem depois.

- Fica atrás do conteúdo.
- Pode desaparecer sob o foco presente e reaparecer depois.
- Nunca deve atravessar texto nem virar ornamento espalhado por todas as telas.
- Elementos decorativos do caminho não participam da árvore de acessibilidade.

### Dobra

A dobra marca o presente, uma adaptação ou uma ação disponível.

- O cartão de foco em Agora recebe a expressão mais forte da dobra.
- Cartões de lista podem ter apenas um pequeno corte no canto.
- A aba ativa de Trajeto usa um ponto com pequena dobra como indicador.
- A forma pode ser assimétrica, mas não deve comprometer área de toque ou leitura.

### Silêncio

Espaço, contraste e hierarquia tipográfica fazem mais trabalho que contornos, sombras ou ilustrações. A assinatura visual deve ser concentrada; a dobra é o elemento memorável e o restante permanece disciplinado.

## Paleta e funções

| Papel | Cor | Uso |
| --- | --- | --- |
| Profundo | `#11111F` | Fundo canônico da experiência principal em modo escuro |
| Superfície baixa | `#191927` | Navegação e separação discreta |
| Superfície | `#242437` a `#27273B` | Cartões e campos |
| Foco | `#9C79F4` | Estado ativo, rotina e orientação primária |
| Orientação | `#6EB7CC` | Caminho, tarefas e ações contextuais |
| Energia | `#FEC4C6` | Estado de energia e ação acolhedora |
| Adaptação | `#E8A2F5` | Dobra, âncoras e mudança de percurso |
| Apoio | `#6473CA` | Linha de caminho e profundidade secundária |
| Texto principal | `#F6F2F9` | Conteúdo principal no fundo profundo |
| Texto secundário | `#C9C5D8` | Explicações e contexto |

As três áreas principais devem sempre compartilhar o mesmo universo de cor. Nenhuma delas pode trocar isoladamente para um fundo claro. O modo escuro é a referência visual aprovada. O suporte de tema claro existente pode continuar, desde que as três áreas também permaneçam coerentes entre si e preservem os mesmos papéis semânticos; removê-lo não faz parte deste trabalho.

## Tipografia

### Average Sans

Average Sans é a voz humana do Verto. Usar o peso regular 400, sem síntese de negrito.

Aplicações:

- títulos de tela e de seção;
- nome da tarefa ou passo em foco;
- mensagens acolhedoras e estados vazios;
- textos corridos que conversam com a pessoa;
- nomes de tarefas, rotinas e âncoras.

A hierarquia é construída com escala, espaço, posição e cor, não com peso artificial.

### Encode Sans Condensed

Encode Sans Condensed é a infraestrutura da interface. Usar pesos 400, 500 e 600.

Aplicações:

- navegação;
- datas e horários;
- metadados, estados e contagens;
- botões e ações;
- campos, chips e abas;
- informações compactas.

Evitar pesos 700 a 900, caixa alta e parágrafos longos. A fonte de apoio informa; ela não assume a personalidade do produto.

### Distribuição e licença

As duas famílias usam SIL Open Font License. Os arquivos necessários devem ser empacotados nos recursos do app; a interface não depende de download de fonte em tempo de execução. Incluir apenas os pesos usados: Average Sans 400 e Encode Sans Condensed 400, 500 e 600.

## Vocabulário principal

Os rótulos visíveis das três áreas mudam para:

| Nome anterior | Nome aprovado | Significado |
| --- | --- | --- |
| Hoje | **Agora** | O que merece atenção no presente, sem limitar a experiência ao calendário |
| Caçamba | **Rascunho** | Um lugar provisório; o que entra ainda não virou obrigação |
| Planejar | **Trajeto** | Onde tarefas, rotinas e âncoras dão forma ao caminho |

Os nomes internos de rotas, pacotes e modelos podem permanecer como estão. A mudança obrigatória é a linguagem exibida à pessoa.

Toda a interface usa sentence case. Ações têm nomes diretos e mantêm o mesmo verbo até a confirmação do resultado.

## Estrutura das três áreas

### Agora

Agora é o lugar do presente e do próximo passo.

- Cabeçalho com “Agora”, data e energia atual.
- O caminho discreto passa atrás da sequência real do dia.
- O cartão do “Só 1 coisa” é o foco principal e recebe a dobra mais expressiva.
- O conteúdo que já aconteceu fica visualmente reduzido; o que vem depois permanece disponível sem competir com o foco.
- O fluxo de túnel, as âncoras, planos ideal/mínimo, pular, desfazer e demais comportamentos existentes continuam funcionando.
- A identidade não deve esconder informação necessária nem transformar toda a tela num único mockup simplificado.

Exemplos de voz:

- “Seu próximo passo”
- “A caminhada começa pequena. O resto pode aparecer depois.”
- “Marcar como feito”

### Rascunho

Rascunho é o lugar de descarregar sem decidir.

- Entrada de captura no topo, dentro de uma superfície com pequena dobra.
- Texto de convite: “O que apareceu?”
- Placeholder: “Escreve do jeito que vier.”
- Os itens brutos e incubados existentes permanecem acessíveis, assim como converter, incubar, descartar e restaurar.
- Quando não há itens brutos, usar o estado vazio “A cabeça respirou.” com explicação de que nada virou obrigação.
- O conteúdo deve reforçar provisoriedade, não lixo ou fracasso.

### Trajeto

Trajeto organiza tarefas, rotinas e âncoras sem juntá-las numa lista vertical longa.

#### Cabeçalho

- Título “Trajeto”.
- Subtítulo “Dá forma ao que vem depois.”
- Resumo persistente com as quantidades atuais, por exemplo: “No caminho: 4 tarefas, 2 rotinas e 3 âncoras”.
- Ajustes continua acessível a partir de Trajeto. A posição exata pode ser resolvida de forma responsiva, mas não pode desaparecer; a preferência é um ícone secundário no cabeçalho ou em overflow ao lado da criação contextual.

#### Abas internas

As divisões **Tarefas**, **Rotinas** e **Âncoras** são abas no topo do conteúdo.

- A linha das abas é uma versão discreta do caminho.
- A aba ativa é indicada por texto, ponto maior e pequena dobra; a diferença não depende apenas de cor.
- A troca é imediata e de baixo estímulo, sem animação automática.
- A aba inicial é Tarefas.
- A seleção pode ser estado local salvável da tela; não exige persistência de domínio.
- Ao retornar pela navegação com estado restaurado, preservar a aba que estava aberta quando possível.

#### Conteúdo contextual

- Cada aba mostra somente seu tipo de conteúdo.
- A ação de adicionar cria o tipo da aba ativa.
- Tarefas preserva itens abertos, feitas e o controle de itens fixados em Agora.
- Rotinas preserva âncora associada, dias da semana e edição dos planos ideal e mínimo.
- Âncoras preserva janela de horário e a relação com rotinas.
- Os estados vazios explicam o papel do tipo e oferecem a ação adequada.

Exemplos de subtítulos internos:

- Tarefas: “Por decidir”
- Rotinas: “Seus ritmos”
- Âncoras: “Pontos do dia”

## Navegação

A barra inferior passa a exibir **Agora**, **Rascunho** e **Trajeto**. A área ativa usa texto principal e um indicador pequeno na cor de foco. Ícones, se mantidos, são apoio e não a única forma de reconhecer o destino.

As abas internas de Trajeto são visualmente secundárias à barra inferior:

- ficam abaixo do cabeçalho e do resumo;
- não usam o mesmo tratamento de seleção da barra principal;
- não alteram a rota principal;
- não escondem a barra inferior.

## Componentes e superfícies

- Cartões de foco: fundo entre `#2D2C47` e `#25243A`, dobra expressiva e sombra de profundidade curta.
- Cartões de lista: superfície `#242437`, faixa semântica lateral e dobra pequena no canto.
- Campos: fundo próximo da superfície, contorno discreto e texto legível; o foco usa orientação ou foco conforme a função.
- Botões primários acolhedores podem usar Energia (`#FEC4C6`) com texto `#4A1A1E`.
- Ações contextuais usam Orientação (`#6EB7CC`) ou Foco (`#9C79F4`).
- Bordas e sombras não devem ser aplicadas igualmente a todos os componentes.

Editores, Ajustes, Conta e telas auxiliares adotam a mesma tipografia, paleta e linguagem de superfícies. Caminhos e dobras completas ficam reservados às áreas onde tenham função; telas auxiliares podem usar apenas pequenos cortes ou nenhuma assinatura gráfica.

## Movimento e resposta

- Manter a troca das áreas principais sem transição, conforme o comportamento de baixo estímulo já existente.
- A troca de abas internas é imediata; uma mudança curta no indicador é aceitável somente se respeitar redução de movimento.
- Confirmações respondem à ação da pessoa, sem celebração automática ou partículas.
- Nenhum elemento se move continuamente.

## Acessibilidade

- Alvos de toque com pelo menos 48 dp.
- Contraste verificado nos pares de texto e superfície.
- Suporte a aumento de fonte sem cortar títulos, abas ou ações.
- Abas expõem papel, nome e estado selecionado para tecnologias assistivas.
- Caminhos e dobras decorativos ficam fora da semântica.
- Estado selecionado nunca depende somente de cor.
- A ordem de leitura segue cabeçalho, resumo, abas, conteúdo e navegação.

## Restrições de implementação

- O projeto continua KMP/CMP, Android primeiro.
- Reutilizar os ViewModels, repositórios, rotas e regras existentes sempre que o comportamento não muda.
- Preferir componentes Compose pequenos e focados para caminho, dobra, cartão e abas internas.
- Não criar dependência de rede para fontes, cores ou recursos visuais.
- Não alterar API ou banco para sustentar mudanças puramente visuais.
- Preservar alterações existentes da usuária no repositório.

## Verificação e critérios de aceite

### Funcional

- Agora, Rascunho e Trajeto aparecem na barra inferior e abrem os fluxos já correspondentes.
- Nenhuma capacidade existente das três telas fica inacessível.
- Em Trajeto, cada aba mostra o conjunto correto e a ação de adicionar abre o editor correto.
- Ajustes permanece acessível.
- Estado e restauração de navegação continuam funcionando.

### Visual

- As três áreas principais compartilham o mesmo universo de fundo e superfícies.
- Average Sans e Encode Sans Condensed aparecem apenas nos papéis definidos.
- Não há negrito sintético de Average Sans nem ações em caixa alta.
- O caminho aparece somente onde existe sequência real.
- A dobra é mais forte no foco e menor em listas ou indicadores.
- Trajeto não volta a ser uma lista única de três seções empilhadas.

### Técnica

- Executar `./gradlew :shared:domain:jvmTest :shared:data:jvmTest :composeApp:jvmTest`.
- Compilar e instalar o debug Android.
- Validar visualmente no Pixel 9 em largura normal e com escala de fonte aumentada.
- Validar os três temas principais no modo escuro, referência canônica, e confirmar que eventual tema claro continua coerente nas três áreas.

## Decisões deixadas ao implementador

- Organização exata dos novos componentes Compose em arquivos, respeitando os limites atuais do projeto.
- Técnica de desenho do caminho e da dobra, desde que preserve semântica, desempenho e redimensionamento.
- Posicionamento responsivo final de Ajustes ao lado da ação contextual em Trajeto.
- Ajustes finos de espaçamento e escala tipográfica necessários para telas menores ou fonte ampliada.
