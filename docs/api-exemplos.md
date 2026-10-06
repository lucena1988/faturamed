# Exemplos da API

## Criar empresa

```http
POST /api/empresas
Content-Type: application/json
```

```json
{
  "nome": "Clinica Exemplo",
  "documento": "12345678000199"
}
```

## Criar layout de faturamento

```http
POST /api/layouts
Content-Type: application/json
```

```json
{
  "empresaId": 1,
  "nome": "Layout faturamento padrao",
  "tipo": "FATURAMENTO",
  "delimitadorCsv": ";",
  "primeiraLinhaCabecalho": true,
  "campos": [
    {
      "campoPadronizado": "data_atendimento",
      "colunaOrigem": "A",
      "obrigatorio": true,
      "formato": "dd/MM/yyyy"
    },
    {
      "campoPadronizado": "paciente_nome",
      "colunaOrigem": "B",
      "obrigatorio": true,
      "formato": null
    },
    {
      "campoPadronizado": "procedimento_codigo",
      "colunaOrigem": "D",
      "obrigatorio": true,
      "formato": null
    }
  ]
}
```

## Criar importacao

```http
POST /api/importacoes
Content-Type: application/json
```

```json
{
  "empresaId": 1,
  "unidadeId": null,
  "layoutImportacaoId": 1,
  "tipo": "FATURAMENTO",
  "nomeArquivo": "faturamento-setembro-2026.xlsx"
}
```

## Adicionar registro importado

```http
POST /api/importacoes/1/registros
Content-Type: application/json
```

```json
{
  "numeroLinha": 2,
  "origem": "FATURAMENTO",
  "pacienteNome": "Paciente Teste",
  "pacienteDocumento": "12345678900",
  "atendimentoCodigo": "ATD-001",
  "guiaCodigo": "GUIA-001",
  "dataAtendimento": "2026-09-01",
  "medicoNome": "Dra. Teste",
  "medicoDocumento": null,
  "procedimentoCodigo": "PROC-001",
  "procedimentoNome": "Consulta",
  "quantidade": 1,
  "valorUnitario": 150.00,
  "valorTotal": 150.00,
  "dadosOriginais": {
    "A": "01/09/2026",
    "B": "Paciente Teste",
    "D": "PROC-001"
  }
}
```
