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
mvn test
mvn spring-boot:run
```

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
   - Criar suíte de testes de integração (`ChamadoControllerTest` e `ChamadoServiceTest`) para cobrir regras de SLA, transições no Kanban e permissões de perfil (`DEV`, `ADMIN`, `MEDICO`).
2. **Piloto Operacional com Dados Reais:**
   - Realizar homologação de ponta a ponta com os arquivos reais da clínica/hospital utilizando os cenários de teste em `examples/cenarios/`.
   - Convidar grupo piloto de médicos para validar usabilidade do portal médico e do widget de suporte.
3. **Notificações Ativas:**
   - Implementar envio automático de e-mail/notificação quando o SLA de um chamado estiver em risco (< 25% do tempo restante).
   - Disparo de aviso ao médico quando uma nova competência de pagamento/extrato for liberada pelo faturamento.
4. **Deploy em Ambiente de Staging / Produção:**
   - Configuração de pipeline CI/CD, certificado HTTPS/TLS e backups automatizados do banco PostgreSQL.
