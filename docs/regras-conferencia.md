# Regras de Conferencia

O motor de regras deve ficar separado do restante da aplicacao para facilitar evolucao futura.

## Resultado possivel

- `CONFORME`
- `DIVERGENCIA`
- `NAO_ENCONTRADO`
- `DUPLICIDADE`

## Regras iniciais

### REGRA 001 - Quantidade divergente

Quando um item de faturamento tem correspondente na producao, mas a quantidade e diferente.

```text
se faturamento.quantidade != producao.quantidade
entao QUANTIDADE_DIVERGENTE
```

### REGRA 002 - Valor divergente

Quando um item de faturamento tem correspondente na producao, mas o valor total e diferente do esperado.

```text
se faturamento.valor_total != producao.valor_total_esperado
entao VALOR_DIVERGENTE
```

### REGRA 003 - Faturado sem producao

Quando um item aparece no faturamento, mas nao aparece na producao.

```text
se faturamento nao possui correspondente na producao
entao FATURADO_SEM_PRODUCAO
```

### REGRA 004 - Produzido sem faturamento

Quando um item aparece na producao, mas nao aparece no faturamento.

```text
se producao nao possui correspondente no faturamento
entao PRODUZIDO_SEM_FATURAMENTO
```

### REGRA 005 - Duplicidade

Quando registros equivalentes aparecem mais de uma vez na mesma origem.

```text
se paciente + data + procedimento + medico aparece mais de uma vez
entao DUPLICIDADE
```

### REGRA 006 - Medico divergente

Quando o procedimento e o paciente batem, mas o medico informado e diferente.

```text
se faturamento.medico != producao.medico
entao MEDICO_DIVERGENTE
```

### REGRA 007 - Data divergente

Quando o atendimento/procedimento parece ser o mesmo, mas a data e diferente.

```text
se faturamento.data != producao.data
entao DATA_DIVERGENTE
```

## Prioridade sugerida

1. Duplicidade
2. Faturado sem producao
3. Produzido sem faturamento
4. Quantidade divergente
5. Valor divergente
6. Medico divergente
7. Data divergente
8. Conforme

## Valor em risco

O valor em risco deve considerar o impacto financeiro estimado das divergencias.

Exemplos:

- item faturado sem producao: valor total faturado
- produzido sem faturamento: valor esperado de faturamento
- quantidade divergente: diferenca entre valor faturado e valor esperado
- valor divergente: diferenca absoluta entre os valores
