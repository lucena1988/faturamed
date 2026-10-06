# Modelo de Negocio

## Conceito

O Faturamed compara registros de producao medica com registros de faturamento para identificar se o que foi faturado corresponde ao que foi efetivamente produzido.

O sistema deve preservar o arquivo original importado, criar registros padronizados e gerar uma trilha de auditoria para cada decisao tomada sobre divergencias.

## Entidades principais

### Empresa

Representa o cliente que usa o sistema.

Exemplos:

- clinica
- hospital
- grupo de unidades
- empresa de auditoria

### Unidade

Local operacional onde a producao ou faturamento ocorre.

Uma empresa pode ter varias unidades.

### Usuario

Pessoa que acessa o sistema.

Perfis iniciais:

- administrador
- auditor
- operador
- visualizador

### Layout de importacao

Modelo que informa como interpretar uma planilha.

Exemplo:

| Coluna | Campo padronizado |
| --- | --- |
| A | data_atendimento |
| B | paciente_nome |
| C | paciente_documento |
| D | procedimento_codigo |
| E | procedimento_nome |
| F | medico_nome |
| G | quantidade |
| H | valor_total |

### Importacao

Execucao de upload e processamento de um arquivo.

Cada importacao possui:

- empresa
- unidade
- tipo de arquivo
- nome do arquivo
- layout usado
- data/hora
- usuario responsavel
- status

Tipos iniciais:

- producao
- faturamento

### Registro padronizado

Linha importada e convertida para o formato interno do sistema.

Campos principais:

- paciente
- documento do paciente
- atendimento
- data
- medico
- procedimento
- codigo do procedimento
- quantidade
- valor unitario
- valor total
- origem

### Rodada de conferencia

Processamento que compara um conjunto de registros de producao contra um conjunto de registros de faturamento.

### Divergencia

Resultado encontrado pelo motor de regras.

Tipos iniciais:

- conforme
- nao_encontrado
- duplicidade
- quantidade_divergente
- valor_divergente
- medico_divergente
- data_divergente
- procedimento_divergente
- faturado_sem_producao
- produzido_sem_faturamento

### Tratamento de divergencia

Decisao humana sobre uma divergencia.

Status iniciais:

- pendente
- em_analise
- corrigido
- contestado
- aceito
- ignorado

## Chave de cruzamento inicial

Enquanto nao houver uma chave unica confiavel, a conferencia pode usar uma chave composta:

```text
paciente_documento + data + procedimento_codigo + medico + quantidade
```

Na pratica, o sistema deve permitir ajustar essa estrategia por cliente, porque algumas empresas podem nao receber CPF, carteirinha, atendimento ou codigo de procedimento em todos os relatorios.

## Indicadores do dashboard

- total de itens analisados
- total de conformes
- total de divergencias
- total de duplicidades
- total de nao encontrados
- percentual de inconsistencias
- valor em risco
- valor corrigido
- valor contestado

## Fluxo do MVP

```text
Arquivo de producao
        |
        v
Importacao -> Padronizacao
        |
        v
Arquivo de faturamento
        |
        v
Importacao -> Padronizacao
        |
        v
Rodada de conferencia
        |
        v
Motor de regras
        |
        v
Divergencias e conformidades
        |
        v
Analise do usuario
        |
        v
Relatorio final
```
