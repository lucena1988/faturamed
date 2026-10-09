# Faturamed

Sistema de auditoria e conferencia de faturamento medico.

O objetivo do MVP e receber planilhas de producao e faturamento, padronizar os dados importados, cruzar os registros e apontar inconsistencias como faltantes, duplicidades, quantidade divergente, valor divergente e medico divergente.

## MVP 1.0

1. Cadastro de empresas, unidades e usuarios.
2. Configuracao de layouts de importacao por cliente.
3. Importacao de arquivos `.xlsx` e `.csv`.
4. Padronizacao dos campos principais.
5. Motor de regras de conferencia.
6. Tela/listagem de divergencias.
7. Dashboard com indicadores de conferencia e valor em risco.
8. Registro da decisao tomada para cada divergencia.

## Documentos iniciais

- [Modelo de negocio](docs/modelo-negocio.md)
- [Regras de conferencia](docs/regras-conferencia.md)
- [Checklist da planilha real](docs/checklist-planilha-real.md)
- [Exemplos da API](docs/api-exemplos.md)
- [Schema SQL inicial](database/schema-inicial.sql)

## Backend

O backend Spring Boot foi iniciado em `src/main/java/br/com/faturamed`.

Componentes iniciais:

- `FaturamedApplication`: entrada da aplicacao.
- `HealthController`: endpoint `GET /api/health`.
- `EmpresaController`: endpoints iniciais de cadastro/listagem de empresas.
- `LayoutImportacaoController`: cadastro/listagem de layouts de importacao.
- `ImportacaoController`: cadastro de importacoes e registros importados padronizados.
- `MotorConferencia`: compara registros de producao e faturamento.
- `V1__schema_inicial.sql`: migration Flyway com o schema inicial.

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
Para parar os containers, execute `docker compose down`. Os dados do PostgreSQL ficam persistidos no volume `postgres_data`.

Para executar o backend fora do Docker, com Java 21 e Maven instalados:

Configure tambem as variaveis `ADMIN_INITIAL_EMAIL` e `ADMIN_INITIAL_PASSWORD` no
ambiente/IntelliJ para o primeiro inicio. O Spring Boot nao carrega `.env` automaticamente.

```bash
docker compose up -d postgres
mvn test
mvn spring-boot:run
```

No Windows, o perfil Maven `windows-local-build` grava os arquivos gerados em
`${java.io.tmpdir}/faturamed-build`, fora do OneDrive, para evitar falhas do
`mvn clean` causadas por atributos de somente leitura nas pastas sincronizadas.
Recarregue o projeto Maven no IntelliJ depois de alterar o `pom.xml`.
No Docker e no Linux, a saida continua em `target`.

Endpoints iniciais:

- `POST /api/empresas`
- `GET /api/empresas`
- `POST /api/layouts`
- `GET /api/layouts?empresaId=1`
- `POST /api/importacoes`
- `POST /api/importacoes/{id}/registros`
- `GET /api/importacoes/{id}/registros`

## Frontend

O prototipo inicial do sistema esta em `src/main/resources/static`.

Arquivos principais:

- `index.html`
- `styles.css`
- `app.js`

Quando a aplicacao Spring Boot estiver rodando, o frontend ficara disponivel em:

```text
http://localhost:8080/
```

## Campos padronizados

Os arquivos importados devem ser convertidos para um formato interno comum:

- empresa
- unidade
- paciente
- documento do paciente
- atendimento
- data do atendimento/producao
- medico
- procedimento
- codigo do procedimento
- quantidade
- valor unitario
- valor total
- origem do registro

## Proximo passo

Pedir a empresa exemplos reais, preferencialmente anonimizados, das planilhas usadas no processo:

- planilha de faturamento
- relatorio de producao
- relatorio de consultas
- relatorio de exames/procedimentos
- qualquer planilha auxiliar usada na conferencia manual

Com esses arquivos, o modelo de dados e as regras podem ser ajustados ao processo real antes da implementacao em Java/Spring Boot.
