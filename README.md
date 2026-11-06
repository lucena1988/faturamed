# FaturaMed

Aplicacao para conferencia e conciliacao de faturamento medico contra dados hospitalares.

## Modos de Uso

1. **Dashboard web em navegador** para operacao visual dos arquivos, regras e resultados.
2. **API REST / CLI** para automacao e integracao com outros sistemas.
3. **Portal do Medico** com visualizacao restrita de extrato, producao e pendencias por medico.
4. **Service Desk Integrado** para abertura e gestao de chamados com controle de SLA e perfil DEV.

## Documentacao

- [Manual do usuario v2](docs/manual-do-usuario-v2.md)
- [Regras de conferencia](docs/regras-conferencia.md)
- [Exemplos de uso da API REST](docs/api-exemplos.md)
- [Catalogo padrao de procedimentos medicos](docs/catalogo-procedimentos.md)
- [Base hospitalar consolidada](docs/base-hospitalar.md)
- [Conciliacao de visitas e consultas sem internacao](docs/conciliacao-visitas.md)
- [Modelo de negocio e perfis de usuario](docs/modelo-negocio.md)
- [Modulo de Service Desk & Suporte](docs/service-desk.md)
- [Checklist para homologacao com planilha real](docs/checklist-planilha-real.md)
- [Roteiro de testes de regressao](docs/regressao-homologacao.md)
- [Guia de deploy na Railway](docs/deploy-railway.md)
- [Schema SQL inicial](database/schema-inicial.sql)

## Service Desk & Suporte Integrado

O sistema possui um Service Desk nativo para comunicação entre médicos, equipe administrativa e desenvolvimento:

- **Painel Kanban (`/chamados.html`):** Gestão visual em colunas (`Aberto`, `Em Atendimento`, `Aguardando Usuário`, `Resolvido`, `Fechado`), histórico de cancelados/descartados, filtros rápidos e atribuição de responsáveis.
- **Widget Flutuante (Liquid Glass):** Presente em todas as telas principais (`chamados-widget.js`), permitindo abertura rápida de chamados sem sair da página de trabalho e com captura automática da URL de origem.
- **Controle de SLA por Criticidade:** Contagem regressiva em tempo real com alertas visuais (`No Prazo`, `Alerta`, `Vencido`):
  - `ERRO_BLOQUEANTE`: Fila **DEV** / Crítica / SLA 4h
  - `BUG_INCONSISTENCIA`: Fila **DEV** / Alta / SLA 12h
  - `DUVIDA_FINANCEIRA`: Fila **ADMIN** / Média / SLA 24h
  - `CADASTRO_ACESSO`: Fila **ADMIN** / Média / SLA 24h
  - `MELHORIA_SUGESTAO`: Fila **DEV** / Baixa / SLA 48h
- **Perfis de Acesso:** Suporte a `MEDICO`, `ADMIN` e `DEV` (técnico responsável por bugs e erros bloqueantes).
- **Anexos e Avaliação:** Envio de múltiplos arquivos (até 5MB cada) e avaliação com estrelas (1 a 5) após a resolução.
- **Cancelamento com Justificativa:** Encerramento estruturado para chamados indevidos, duplicidades ou solicitações de cancelamento sem poluir o fluxo ativo do Kanban.

Mais detalhes em [docs/service-desk.md](docs/service-desk.md).

## Backend

O backend Spring Boot está em `src/main/java/br/com/faturamed`.

Componentes principais:

- `FaturamedApplication`: entrada da aplicacao.
- `HealthController`: endpoint `GET /api/health`.
- `EmpresaController`: endpoints de cadastro/listagem de empresas.
- `LayoutImportacaoController`: configurador de layouts de importacao.
- `ImportacaoController`: ingestao e padronizacao de planilhas hospitalares e de producao.
- `ConciliadorVisitas` e `MotorConferencia`: comparador e classificador de divergencias.
- `ChamadoController` e `ChamadoService`: endpoints, SLA, cancelamento e historico do Service Desk.
- `V1__schema_inicial.sql` a `V28__cancelamento_chamados.sql`: migrations Flyway evolutivas.

Para rodar a aplicacao e o PostgreSQL com Docker:

Antes do primeiro inicio, configure `ADMIN_INITIAL_EMAIL` e uma senha exclusiva em
`ADMIN_INITIAL_PASSWORD` no arquivo local `.env` (modelo em `.env.example`).
As credenciais criam somente o primeiro administrador. O `.env` nao deve ser commitado.
Apos configurar o arquivo, execute:

```bash
docker compose up -d --build
```

A aplicacao web estara disponivel em `http://localhost:8080`.
Para suporte e chamados, abra `http://localhost:8080/chamados.html`.

Para encerrar os servicos:

```bash
docker compose down
```

Para encerrar os servicos e limpar o volume do banco:

```bash
docker compose down -v
```

## Frontend

O frontend e servido estaticamente pelo Spring Boot a partir de `src/main/resources/static`.
Ele consome a API REST e oferece:

- **Modo escuro** por padrao, com alternador no cabecalho e persistencia em `localStorage` ou preferencia do sistema.
- **Painel de auditoria e exclusao:** visualizacao do historico de planilhas importadas (`/auditoria-exclusao.html`), com metricas consolidadas e fluxo seguro de exclusao completa de importacoes e seus dados vinculados.
- **Painel por Empresa Cliente:** tela exclusiva para perfis `ADMIN` e `EMPRESA` (`/empresa-painel.html`), exibindo totais faturados, repasse retido, taxa administrativa, quebras por medico, hospital e mes, alem de exportacao de relatorios em CSV.
- **Painel gerencial:** totais faturados, pendentes e divergencias agrupadas por competencia e hospital.
- **Relatorio analitico:** tabela de procedimentos com status, valores, repasse e inconsistencias detalhadas.
- **Gestao de layouts:** interface para mapear colunas de planilhas de hospitais com formatos variados.
- **Historico de importacoes:** listagem e detalhamento de processamentos anteriores.
- **Exportacao:** download dos relatorios conferidos em formato CSV.
- **Gestao de usuarios:** cadastro e manutencao de acessos em `/usuarios.html`.
- **Portal do Medico:** extrato financeiro, resumo de producao e pendencias em `/medico.html`.
- **Service Desk:** gestao de incidentes e chamados em `/chamados.html` com suporte ao perfil `DEV`.

Arquivos principais do frontend:

- `index.html`: tela principal do dashboard.
- `app.js`: logica de consumo da API, upload de arquivos e renderizacao de relatorios.
- `styles.css`: estilos gerais da aplicacao com suporte completo a temas claro e escuro.
- `tema.js`: gerenciamento de tema (claro/escuro/sistema) compartilhado entre todas as telas.
- `tokens.css`: variaveis de cor, tipografia e espacamento para consistencia visual.
- `usuarios.html` e `usuarios.js`: gestao de usuarios e permissoes.
- `medico.html` e `medico.js`: portal do medico autenticado.
- `chamados.html`, `chamados.css`, `chamados.js`: painel Kanban de chamados e SLAs do Service Desk.
- `chamados-widget.js`, `chamados-widget.css`: widget flutuante de abertura rápida de chamados.
- `empresa-painel.html`, `empresa-painel.js`: painel analitico consolidado da empresa cliente.

## Estrutura do Projeto

```
faturamed/
├── .env.example
├── Dockerfile
├── README.md
├── docker-compose.yml
├── pom.xml
├── docs/
│   ├── api-exemplos.md
│   ├── base-hospitalar.md
│   ├── catalogo-procedimentos.md
│   ├── checklist-planilha-real.md
│   ├── conciliacao-visitas.md
│   ├── deploy-railway.md
│   ├── manual-do-usuario-v2.md
│   ├── manual-do-usuario.md
│   ├── modelo-negocio.md
│   ├── regras-conferencia.md
│   ├── regressao-homologacao.md
│   └── service-desk.md
├── src/
│   ├── main/
│   │   ├── java/br/com/faturamed/
│   │   │   ├── FaturamedApplication.java
│   │   │   ├── HealthController.java
│   │   │   ├── layout/
│   │   │   ├── importacao/
│   │   │   ├── conciliacao/
│   │   │   └── seguranca/
│   │   │       ├── ChamadoController.java
│   │   │       ├── ChamadoService.java
│   │   │       ├── SegurancaConfig.java
│   │   │       ├── UsuarioService.java
│   │   │       └── ...
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── db/migration/
│   │       │   ├── V1__schema_inicial.sql
│   │       │   ├── ...
│   │       │   ├── V21__service_desk_chamados.sql
│   │       │   ├── ...
│   │       │   └── V28__cancelamento_chamados.sql
│   │       └── static/
│   │           ├── chamados-widget.css
│   │           ├── chamados-widget.js
│   │           ├── chamados.css
│   │           ├── chamados.html
│   │           ├── chamados.js
│   │           └── ...
│   └── test/
└── test-data/
```

## Licenca

Uso interno e restrito. Todos os direitos reservados.
