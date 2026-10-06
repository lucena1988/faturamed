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

Quando Java 21 e Maven estiverem instalados/configurados, os comandos principais serao:

```bash
docker compose up -d
mvn test
mvn spring-boot:run
```

Endpoints iniciais:

- `POST /api/empresas`
- `GET /api/empresas`
- `POST /api/layouts`
- `GET /api/layouts?empresaId=1`
- `POST /api/importacoes`
- `POST /api/importacoes/{id}/registros`
- `GET /api/importacoes/{id}/registros`

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
