# Planilhas sinteticas de conciliacao

Dados ficticios para testes manuais. Nao incluem pacientes ou medicos reais.
Os valores nao representam precos contratados ou uma tabela TUSS/AMB validada.

## Importacao

Na tela `/conciliacao.html` ou na aba Importacoes do dashboard:

- Hospital: `Analia Franco - TESTE`.
- Producao: `producao-medico-teste-1500.xlsx` (1.500 visitas, somente Atendimento).
- Faturamento: `hospital-analia-franco-teste-1700.xlsx` (1.700 registros, layout Analia Franco).

Cada linha da producao representa uma visita. Os registros hospitalares cobrem
janeiro a abril de 2026, com 12 medicos ficticios. O codigo de visita usado e
`10102019`. A aba `Cenarios de teste` do arquivo hospitalar detalha os casos.

## Resultado esperado

Antes de qualquer revisao manual, sem filtro de periodo:

| Indicador | Resultado |
| --- | ---: |
| Visitas analisadas | 1.500 |
| Pagas | 900 |
| Pendentes | 200 |
| Divergentes | 400 |
| Registros hospitalares sem producao | 250 |
| Valor das pagas | R$ 119.712,60 |
| Repasse de visitas informado pelo hospital | R$ 224.159,316 |

O card do repasse hospitalar exibe R$ 224.159,32, arredondado para duas casas.
Existem 25 registros hospitalares sem repasse; o total informado soma apenas
valores conhecidos. Paga significa constar no setor Faturado do hospital,
nao comprova recebimento financeiro.

## Cenarios

As linhas abaixo sao os numeros de linha do Excel da producao, incluindo o cabecalho:

| Linhas | Visitas | Cenario | Status inicial |
| --- | ---: | --- | --- |
| 2-901 | 900 | Correspondencia unica faturada | PAGA |
| 902-1001 | 100 | Correspondencia unica em analise | PENDENTE |
| 1002-1101 | 100 | Sem registro hospitalar | PENDENTE |
| 1102-1201 | 100 | Repasse diferente de Valor Tot x Regra | DIVERGENTE |
| 1202-1251 | 50 | Medico ou repasse ausente no hospital | DIVERGENTE |
| 1252-1351 | 100 | Duas linhas disputando um registro hospitalar | DIVERGENTE |
| 1352-1451 | 100 | Uma linha com candidatos de datas distintas | DIVERGENTE |
| 1452-1501 | 50 | Duas linhas com candidatos de medicos diferentes | DIVERGENTE |

## Conferencia manual

1. Importar os dois arquivos e conferir os indicadores acima.
2. Conferir o preenchimento automatico de uma visita paga.
3. Revisar uma visita com candidatos de datas ou medicos diferentes.
4. Confirmar que o registro hospitalar escolhido nao pode ser usado em outra linha.
5. Conferir a atualizacao dos valores no dashboard e exportar o Excel.
6. Restaurar o resultado automatico e verificar o historico da revisao.

Reimportacoes geram relatorios independentes. Exclua apenas relatorios de teste
quando precisar limpar o ambiente; a exclusao atual e definitiva.
