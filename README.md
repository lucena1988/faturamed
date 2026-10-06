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
- [Schema SQL inicial](database/schema-inicial.sql)

## Backend

O backend Spring Boot foi iniciado em `src/main/java/br/com/faturamed`.

Componentes iniciais:

- `FaturamedApplication`: entrada da aplicacao.
- `HealthController`: endpoint `GET /api/health`.
- `MotorConferencia`: compara registros de producao e faturamento.
- `V1__schema_inicial.sql`: migration Flyway com o schema inicial.

Quando Java 21 e Maven estiverem instalados/configurados, os comandos principais serao:

```bash
docker compose up -d
mvn test
mvn spring-boot:run
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
