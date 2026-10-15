# Regras de Conferencia

O motor de regras deve ficar separado do restante da aplicacao para facilitar evolucao futura.

## Novo fluxo de conciliacao acordado com a empresa

Definicao de negocio registrada em 15/10/2026. A implementacao da base hospitalar e sua rotina estao descritas em [Base hospitalar](base-hospitalar.md). As regras iniciais abaixo sao referencia do modelo anterior. As novas importacoes usam o fluxo em duas etapas; historicos anteriores sao preservados sem migracao automatica. A producao pode ser individual, com medico selecionado pelo administrador, ou coletiva, com identificacao do medico por linha.

### Base hospitalar antes da producao medica

1. Importar a planilha do hospital sem exigir a producao medica no mesmo momento.
2. Armazenar o arquivo original e os registros importados, com hospital, arquivo de origem, linha e data da importacao. Preservar a competencia informada pelo hospital, quando disponivel, sem deduzi-la da data da visita.
3. Criar um ID interno permanente para cada visita hospitalar identificada como distinta. Uma visita nova comeca como `AGUARDANDO_PRODUCAO`, sem ser considerada divergente apenas porque a producao ainda nao foi enviada.
4. Importar posteriormente a producao medica, que pode conter apenas numero do atendimento e data da visita.
5. Buscar a visita hospitalar correspondente e vincular a linha da producao ao ID interno existente, preservando ambas as fontes.
6. Conciliar automaticamente somente quando houver uma correspondencia unica e segura. Encaminhar ambiguidades, conflitos e possiveis duplicidades para revisao administrativa.

### Identidade da visita

O numero do atendimento identifica o atendimento hospitalar, mas nao identifica sozinho uma visita. Um atendimento pode conter visitas em datas diferentes e de medicos diferentes, inclusive na mesma data.

- O ID interno deve ser independente dos campos da planilha e permanecer fixo apos correcoes ou novas importacoes.
- Hospital, atendimento, data e medico identificado compoem os criterios de busca e comparacao. Procedimento e codigo, quando disponiveis, ajudam a distinguir os registros.
- Mesmo atendimento e mesma data com medicos diferentes podem representar visitas legitimas. Nao classificar esse caso automaticamente como duplicidade.
- Mesmo atendimento, data e medico tambem nao garantem unicidade: a repeticao exige conferir os demais dados e o historico.
- Cada ocorrencia importada deve ter sua propria rastreabilidade. Uma ocorrencia repetida pode ser ligada a uma visita existente sem criar uma segunda visita conciliada ou somar novamente seus valores.
- O numero da linha do Excel nao e o ID permanente da visita.

### Identificacao do medico na producao

Para uma producao individual contendo apenas atendimento e data, o administrador seleciona o cadastro do medico por CRM/UF. Na producao coletiva, o sistema usa o medico informado por linha ou a inferencia hospitalar segura descrita abaixo.

Na opcao Varios medicos na mesma planilha, identificar cada linha pelo campo Medico, usando nome ou alias de um cadastro ativo e unico. Nao receber um medico unico para todo o arquivo. Agrupar as linhas do mesmo cadastro antes de conciliar, inclusive quando usam aliases diferentes, para detectar disputas pela mesma visita.

Quando Medico estiver vazio na producao coletiva, consultar todos os registros hospitalares do mesmo hospital, atendimento e data. Somente um unico registro, com medico identificado por um cadastro ativo e unico, permite inferir a identidade. A linha continua sujeita as regras de dados, valores e exclusividade existentes. Preservar a fonte original e registrar a origem hospitalar da identificacao no motivo e nos metadados.

Sem registro hospitalar para atendimento e data, a linha sem medico fica PENDENTE, sem identidade presumida. Com varios registros, mesmo do mesmo medico, fica DIVERGENTE. Medico hospitalar desconhecido ou ambiguo, duplicidades em analise, visitas reservadas e disputa entre linhas impedem associacao automatica.

Medico informado na producao, mas nao cadastrado ou ambiguo, nao e substituido silenciosamente pelo hospital: continua DIVERGENTE. Atualizacoes preservam vinculos e decisoes manuais; linhas nao revisadas podem ser reavaliadas pela nova regra.

Uma visita hospitalar nao pode consumir automaticamente duas ocorrencias de producao. Repeticoes na producao tambem devem passar por conferencia, preservando as linhas originais.

### Reimportacao e possivel duplicidade entre competencias

A empresa informou que a mesma visita pode reaparecer no arquivo hospitalar, com o mesmo atendimento e a mesma data. A busca por possiveis repeticoes deve consultar o historico do hospital, inclusive importacoes de outras competencias.

1. Ao receber uma nova ocorrencia, comparar com as visitas existentes e seus registros de origem.
2. Se houver indicio de repeticao ou mais de uma correspondencia possivel, registrar a ocorrencia como `DIVERGENTE`, com motivo de possivel duplicidade, e apresentar os registros anteriores para analise.
3. Nao confirmar uma nova visita, um segundo vinculo de producao ou um novo valor financeiro automaticamente enquanto a repeticao estiver em analise.
4. O administrador verifica medico, data da visita, atendimento, procedimento, competencia e historico de faturamento e recebimentos.
5. Se confirmar que se trata da mesma visita ja paga em outra competencia, registrar a decisao **Duplicado pago em outro mes**, vinculando a ocorrencia ao ID da visita original e a evidencia utilizada.
6. Se forem visitas diferentes, registrar a justificativa e manter IDs distintos. Se o caso continuar incerto, manter a divergencia aberta.

A decisao **Duplicado pago em outro mes** e uma classificacao administrativa confirmada na revisao, nao uma conclusao automatica baseada apenas em atendimento e data. Registrar responsavel, data, justificativa e referencia ao registro anterior. Se nao houver evidencia suficiente do pagamento anterior, manter o caso em analise em vez de afirmar que foi pago.

Faturamento identificado e recebimento financeiro sao informacoes diferentes. A etiqueta de duplicidade nao cria recebimento, nao estorna valores e nao altera silenciosamente os lancamentos anteriores. A ocorrencia confirmada como duplicada nao deve aumentar a quantidade de visitas distintas nem duplicar faturamento ou saldo a receber nos totais consolidados.

O reenvio do mesmo arquivo deve ser reconhecido e informado ao operador, sem duplicar automaticamente a base. Isso e diferente de receber um arquivo novo com algumas visitas ja presentes em importacoes anteriores; essas ocorrencias precisam continuar rastreaveis.

### Situacoes e motivos separados

| Situacao de conciliacao proposta | Significado |
| --- | --- |
| `AGUARDANDO_PRODUCAO` | Visita hospitalar armazenada, ainda sem producao medica vinculada |
| `CONCILIADA` | Producao e visita hospitalar vinculadas com seguranca e sem conflito aberto |
| `PRODUCAO_SEM_BASE_HOSPITALAR` | Linha de producao sem visita correspondente na base hospitalar disponivel |
| `DIVERGENTE` | Ambiguidade, conflito de dados ou possivel duplicidade aguardando decisao |

O motivo e a decisao da revisao complementam a situacao. **Duplicado pago em outro mes** deve ficar registrado como decisao sobre a ocorrencia repetida, preservando a visita original. Os nomes tecnicos finais e a apresentacao de casos resolvidos devem ser definidos na implementacao, sem apagar a divergencia e sua resolucao do historico.

Manter separados o estado da conciliacao, o faturamento hospitalar e os recebimentos reais. `CONCILIADA` nao significa faturada ou recebida. O status atual `PAGA` nao deve ser reutilizado como comprovacao de recebimento.

### Exemplos de comportamento esperado

| Caso | Tratamento esperado |
| --- | --- |
| Atendimento 123, 01/10, Dra. Ana; atendimento 123, 02/10, Dra. Ana | Duas visitas com IDs diferentes |
| Atendimento 123, 01/10, Dra. Ana; atendimento 123, 01/10, Dr. Bruno | Podem ser duas visitas legitimas; considerar o medico na associacao |
| Producao individual da Dra. Ana com atendimento 123 e data 01/10 | Buscar a visita desse hospital e dessa medica; vincular somente se unica e segura |
| Producao sem medico identificado e dois medicos no mesmo atendimento e data | Encaminhar para revisao; nao escolher arbitrariamente |
| Visita reaparece em outro arquivo hospitalar e parece repetir uma anterior | Sinalizar divergencia e apresentar o historico para analise |
| Revisao confirma mesma visita e pagamento anterior em outra competencia | Registrar Duplicado pago em outro mes, vincular ao ID original e impedir dupla contagem |
| Arquivo hospitalar importado sem producao disponivel | Armazenar e aguardar producao, sem exigir os dois arquivos juntos |

### Criterios de aceite para a implementacao

- Permitir importar e consultar a base hospitalar antes da producao.
- Preservar arquivo original, ocorrencias, vinculos e historico de decisoes.
- Manter o ID da visita entre importacoes e correcoes, sem depender da posicao na planilha.
- Considerar hospital e medico ao distinguir visitas de mesmo atendimento e data.
- Sinalizar possiveis duplicidades dentro do arquivo e entre importacoes e competencias.
- Permitir confirmar Duplicado pago em outro mes com justificativa e referencia anterior.
- Impedir dupla associacao automatica de producao e dupla contagem de ocorrencias confirmadas como duplicadas.
- Manter conciliacao, faturamento e recebimento separados nos indicadores.
- Preservar as regras de acesso e a liberacao administrativa antes da consulta pelo medico.

### Pontos para validar com exemplos de planilhas

- Como a competencia do hospital e informada e qual evidencia sustenta a confirmacao de pagamento em outro mes.
- Quais campos distinguem duas visitas legitimas do mesmo medico no mesmo atendimento e dia, caso isso ocorra.
- Quais nomes e aliases das planilhas coletivas precisam ser cadastrados para identificar os medicos por linha.
- Como migrar os relatorios existentes para IDs permanentes sem perder revisoes, liberacoes e lancamentos financeiros.

## Regras iniciais do modelo anterior

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
