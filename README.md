# Faturamed

Sistema de auditoria e conferencia de faturamento medico.

O objetivo do MVP e receber planilhas de producao e faturamento, padronizar os dados importados, cruzar os registros e apontar inconsistencias como faltantes, duplicidades, quantidade divergente, valor divergente e medico divergente.

## MVP 1.0

1. Cadastro de empresas, unidades e usuarios.
2. Configuracao de layouts de importacao por cliente.
3. Importacao de arquivos `.xlsx` e `.csv`.
4. Padronizacao dos campos principais.
5. Motor de regras de conferencia e conciliacao em duas etapas.
6. Tela/listagem de divergencias e duplicidades.
7. Dashboard com indicadores de conferencia e valor em risco.
8. Registro da decisao tomada para cada divergencia e fluxo de revisao.
9. Extrato financeiro, demonstrativo e portal individual do medico.
10. Service Desk integrado com Kanban, SLA, perfil DEV e widget Liquid Glass.

## Documentos

- [Módulo de Service Desk & Suporte Integrado](docs/service-desk.md)
- [Novo fluxo de base hospitalar e conciliação em duas etapas](docs/base-hospitalar.md)
- [Manual do usuário para administradores e médicos](docs/manual-do-usuario-v2.md)
- [Conciliação de Visitas](docs/conciliacao-visitas.md)
- [Login e Permissões](docs/login-e-permissoes.md)
- [Modelo de negocio](docs/modelo-negocio.md)
- [Regras de conferencia](docs/regras-conferencia.md)
- [Exemplos da API](docs/api-exemplos.md)
- [Schema SQL inicial](database/schema-inicial.sql)

## Service Desk & Suporte Integrado

O sistema possui um Service Desk nativo para comunicação entre médicos, equipe administrativa e desenvolvimento:

- **Painel Kanban (`/chamados.html`):** Gestão visual em colunas (`Aberto`, `Em Atendimento`, `Aguardando Usuário`, `Resolvido`, `Fechado`), filtros rápidos e atribuição de responsáveis.
- **Widget Flutuante (Liquid Glass):** Presente em todas as telas principais (`chamados-widget.js`), permitindo abertura rápida de chamados sem sair da página de trabalho e com captura automática da URL de origem.
- **Controle de SLA por Criticidade:** Contagem regressiva em tempo real com alertas visuais (`No Prazo`, `Alerta`, `Vencido`):
  - `ERRO_BLOQUEANTE`: Fila **DEV** / Crítica / SLA 4h
  - `BUG_INCONSISTENCIA`: Fila **DEV** / Alta / SLA 12h
  - `DUVIDA_FINANCEIRA`: Fila **ADMIN** / Média / SLA 24h
  - `CADASTRO_ACESSO`: Fila **ADMIN** / Média / SLA 24h
  - `MELHORIA_SUGESTAO`: Fila **DEV** / Baixa / SLA 48h
- **Perfis de Acesso:** Suporte a `MEDICO`, `ADMIN` e `DEV` (técnico responsável por bugs e erros bloqueantes).
- **Anexos e Avaliação:** Envio de múltiplos arquivos (até 5MB cada) e avaliação com estrelas (1 a 5) após a resolução.

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
- `ChamadoController` e `ChamadoService`: endpoints, SLA e historico do Service Desk.
- `V1__schema_inicial.sql` a `V21__service_desk_chamados.sql`: migrations Flyway evolutivas.

Para rodar a aplicacao e o PostgreSQL com Docker:

Antes do primeiro inicio, configure `ADMIN_INITIAL_EMAIL` e uma senha exclusiva em
`ADMIN_INITIAL_PASSWORD` no arquivo local `.env` (modelo em `.env.example`).
As credenciais criam somente o primeiro administrador. O `.env` nao deve ser commitado.

```bash
docker compose up -d --build
```

Acesse http://localhost:8080/login.html. O administrador gerencia os usuarios em
**Acessos** e vincula cada login de medico ao respectivo cadastro CRM/UF.
Detalhes: [Login e permissoes](docs/login-e-permissoes.md).
Para novas importacoes, acesse http://localhost:8080/base-hospitalar.html.
Para suporte e chamados, abra http://localhost:8080/chamados.html.
Para parar os containers, execute `docker compose down`. Os dados do PostgreSQL ficam persistidos no volume `postgres_data`.

Para executar o backend fora do Docker, com Java 21 e Maven instalados:

```bash
docker compose up -d postgres
mvn spring-boot:run
```

### Testes automatizados: execucao padrao

Use **um unico fluxo completo**, com Docker em execucao e Python 3.10+ no PATH.
Java, Maven, Node.js e PostgreSQL sao executados em containers.
Na primeira utilizacao, instale as dependencias Python:

```bash
python -m pip install -r requirements-test.txt
```

Na raiz do projeto, execute:

```bash
python test.py
```

No macOS/Linux, use `python3 test.py`. Os atalhos `./test.sh` e
`powershell -NoProfile -ExecutionPolicy Bypass -File ./test.ps1` chamam esse mesmo
executor. O argumento opcional `all` e equivalente; os antigos modos parciais
foram retirados para manter uma execucao padrao.

A execucao inclui, uma vez por rodada:

1. Cenarios HTTP de homologacao (importacao, conciliacao, financeiro, permissoes,
   Service Desk, exportacoes, reinicio e restauracao de backup).
2. Testes Java, incluindo integracao com PostgreSQL exclusivo de testes.
3. Testes JavaScript com DOM simulado e verificacao do dashboard gerado.

Os bancos sao temporarios e isolados do PostgreSQL usado em `localhost:8080`.
As planilhas ficticias sao geradas em memoria a partir dos cenarios e salvas como
evidencia da rodada. Nao e necessario copiar arquivos locais para `outputs/`:
a execucao funciona em um checkout novo, como o runner do GitHub Actions.
A homologacao usa a porta local **18081**, que precisa estar livre. Os containers
e redes criados pela rodada sao removidos ao final; as evidencias ficam salvas.
Uma falha de teste retorna codigo diferente de zero, mas as outras camadas
continuam e o relatorio registra os resultados disponiveis.

**Dashboard principal:** [outputs/dashboard-regressao.html](outputs/dashboard-regressao.html).
Cada rodada preserva seu proprio `outputs/evidencias-auto-*/dashboard.html`, logs,
resultados HTTP, relatorios Java e exportacoes. Falhas anteriores permanecem no
historico e so sao reavaliadas com uma nova execucao completa.

Para reconstruir somente o dashboard de uma rodada existente, sem executar testes:

```bash
python scripts/gerar_dashboard_regressao.py outputs/evidencias-auto-NOME-DA-RODADA
```

`dashboard-testes.html` e `outputs/dashboard-testes.html` sao atalhos de
compatibilidade para o dashboard principal. O antigo gerador
`gerar_dashboard_testes.py` apenas delega ao gerador unico; nao presume aprovacao
de testes. Testes com DOM simulado e HTTP nao substituem validacao visual em navegador.

### Pipeline em cada Pull Request (GitHub Actions)

**Cenario temporariamente desativado:** AUTO27 (visitas ambiguas resolvidas por
revisao e exclusividade). O vinculo `medicoCadastroId` ainda nao e propagado em
uma das visitas. O teste permanece no codigo e aparece como **IGNORADO**, sem
bloquear a pipeline. Para reativar apos a correcao, remova `skip_reason` da chamada
AUTO27 em `scripts/homologacao_cenarios.py`. Isso nao corrige o comportamento da aplicacao.

O workflow [.github/workflows/ci.yml](.github/workflows/ci.yml) roda em todo PR,
em push para `main` e `develop`, e manualmente em **Actions > CI > Run workflow**.
Ele tem quatro jobs:

| Job | O que faz |
|---|---|
| **Java (build e testes)** | `mvn -B verify` com PostgreSQL de servico; falhas viram anotacoes no PR |
| **Frontend e scripts** | testes `node --test` e `scripts.test_qa_fixtures` |
| **Seguranca** | gitleaks (segredos), dependency-review (so PR) e CodeQL (Java, JS, Python) |
| **Regressao completa** | `python test.py`; so roda se Java e Frontend passarem |

Depois de uma CI verde em push para `main` ou `develop`, o workflow
[.github/workflows/imagem.yml](.github/workflows/imagem.yml) constroi a imagem Docker,
bloqueia vulnerabilidades criticas com Trivy e publica em
`ghcr.io/lucena1988/faturamed` com as tags do commit, da branch e `latest` (so `main`).

O runner Ubuntu prepara Python 3.12 e usa Docker para os ambientes exclusivos de QA.
Nao precisa de `.env`, banco externo ou secrets de producao. Uma nova atualizacao do
mesmo PR cancela a execucao anterior. O limite de cada job e 40 minutos.

Os resultados aparecem nos checks do PR. Mesmo com testes reprovados, a pipeline
publica os relatorios disponiveis em **Actions > execucao > Artifacts > regressao-...**,
com retencao de 14 dias. Extraia o ZIP completo e abra `dashboard-regressao.html`;
as subpastas contem os logs e evidencias vinculados. Backups em `privado/` e arquivos
`.dump` ficam fora do artefato. Os resultados nao sao commitados no repositorio.

Para ativar, inclua o workflow, `requirements-test.txt` e as alteracoes do projeto
no commit e envie a branch ao GitHub. Actions precisa estar habilitado no repositorio;
PRs de forks podem exigir aprovacao de um mantenedor para iniciar a execucao.

Para impedir merge com testes reprovados, configure em **Settings > Rules > Rulesets**
uma regra para a branch de destino e exija os checks **Java (build e testes)**,
**Frontend e scripts**, **Seguranca** e **Regressao completa** depois da primeira execucao. Criar o workflow sozinho nao torna esse check obrigatorio para merge.
Falhas de cenarios sao mantidas como falhas na pipeline; nao ha `continue-on-error`.

Se `python test.py` terminar com codigo 1, consulte o **Resumo da execucao** no
log: ele informa a etapa reprovada, as ultimas linhas do log e os cenarios HTTP
com erro. O mesmo texto fica em `resumo-execucao.txt` no artefato. `execucao.json`
registra o codigo de cada etapa; `PENDENCIAS.md` descreve as falhas HTTP.
O codigo 1 sozinho nao identifica a causa e nao significa necessariamente uma
falha de configuracao do GitHub Actions.

## Frontend

Arquivos em `src/main/resources/static`:

- `index.html`: dashboard e visão geral.
- `chamados.html`, `chamados.css`, `chamados.js`: painel Kanban de chamados e SLAs do Service Desk.
- `chamados-widget.js`, `chamados-widget.css`: widget flutuante de abertura rápida de chamados.
- `base-hospitalar.html`: importação hospitalar e conciliação em 2 etapas.
- `conciliacao.html`: conferência detalhada e revisão de inconsistências.
- `recebimentos.html`, `cobrancas.html`: acompanhamento de faturamento e pagamentos.
- `portal.html`, `painel-medico.js`: portal de consulta do médico e extrato financeiro.
- `usuarios.html`: gestão de acessos e perfis (ADMIN, MEDICO, DEV).

## Próximos Passos

1. **Testes Automatizados do Service Desk:**
   - Ampliar a cobertura existente de SLA e transicoes no Kanban; permissoes, avisos e fluxos HTTP ja integram a execucao padrao.
2. **Piloto Operacional com Dados Reais:**
   - Realizar homologação de ponta a ponta com os arquivos reais da clínica/hospital utilizando os cenários de teste em `examples/cenarios/`.
   - Convidar grupo piloto de médicos para validar usabilidade do portal médico e do widget de suporte.
3. **Notificações Ativas:**
   - Implementar envio automático de e-mail/notificação quando o SLA de um chamado estiver em risco (< 25% do tempo restante).
   - Disparo de aviso ao médico quando uma nova competência de pagamento/extrato for liberada pelo faturamento.
4. **Deploy em Ambiente de Staging / Produção:**
   - Configuração de pipeline CI/CD, certificado HTTPS/TLS e backups automatizados do banco PostgreSQL.
