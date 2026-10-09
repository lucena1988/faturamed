# Conciliacao de visitas hospitalares

Tela: http://localhost:8080/conciliacao.html

## Dashboard e importacoes

O dashboard principal em http://localhost:8080/ usa os relatorios salvos. Os filtros Hospital e Relatorio selecionam uma rodada; Periodo filtra pela data das visitas do medico. Relatorios de reimportacoes nao sao somados entre si.

Indicadores: visitas analisadas, atendimentos distintos, pagas, pendentes, divergentes e repasse das pagas. O grafico permite agrupar por status, medico identificado ou motivo. Valores ausentes permanecem nao informados. Medicos nao identificados ficam em um grupo separado.

A fila de analise mostra ate dez pendencias/divergencias, priorizando divergentes e depois repasses conhecidos. A aba Divergencias lista todas as visitas que exigem conferencia, com filtros de motivo e status. Os links abrem a conciliacao selecionada filtrada por atendimento e status.

A aba Importacoes recebe as duas planilhas pelo endpoint de conciliacao e carrega a nova rodada no dashboard. A listagem de arquivos mostra as contagens completas da rodada, independentemente do filtro mensal aplicado aos indicadores.

Envie a producao do medico (.xlsx ou .xlsb) e o faturamento de um unico hospital. O hospital informado delimita o contexto do cruzamento. A chave e atendimento + data. O modelo do hospital usa Data Consumo; a producao usa Dt.

As linhas do medico sao preservadas. Quando a chave aparece exatamente uma vez em cada origem, os dados do hospital completam os campos vazios do relatorio. Campos originais permanecem registrados. Havendo varias linhas em qualquer origem, nenhuma delas e selecionada automaticamente: todos os candidatos ficam disponiveis para conferencia. Nomes e procedimentos nao sao inferidos nas linhas sem correspondencia.

## Status

- PAGA: correspondencia unica no setor Faturado do hospital, conforme definicao da empresa. Nao representa comprovacao de recebimento financeiro.
- PENDENTE: sem correspondente no arquivo enviado, ou correspondente fora do setor Faturado. A visita pode aparecer em outra competencia.
- DIVERGENTE: chave repetida na producao, varios candidatos no hospital, campos hospitalares incompletos, conflito com dados originalmente preenchidos pelo medico ou repasse inconsistente com Valor Tot x Regra (tolerancia R$ 0,01).

Os valores enriquecidos sao os informados pelo hospital. A tabela de preco contratada ainda nao e validada. O repasse correspondente soma apenas linhas PAGA, sem arredondar cada linha.

## Relatorio

O filtro Medico seleciona as visitas pelo nome efetivo, incluindo correcoes de
revisao. Nomes sao comparados sem diferenciar caixa, acentos e espacos extras;
nao ha identificador CRM cadastrado nesta etapa. Nao atribua um medico apenas
porque ele aparece entre os candidatos de uma visita ambigua.

`Medico nao identificado` permite conferencia interna. A exportacao individual
fica disponivel somente para um medico identificado e inclui todos os status
desse profissional, independentemente dos filtros locais Status e Atendimento.
Os indicadores da tela acompanham o filtro Medico. O download geral permanece
com todas as visitas.

GET `/api/conciliacoes/visitas/{id}/relatorio.xlsx?medico=nome` gera o arquivo
individual com Resumo e Visitas, totais de repasse conhecido e contagens sem
valor por status. Candidatos, hospital sem producao e historico detalhado nao
sao enviados. A coluna Origem da conciliacao diferencia importacao automatica,
automatica apos revisao e revisao manual. Visitas sem medico ficam no relatorio
geral ate serem identificadas. A geracao nao envia emails automaticamente.

O XLSX inclui Resumo, Visitas, Candidatos hospital e Hospital sem producao. A ultima aba inclui inclusive datas de outras competencias presentes no arquivo hospitalar, sem classifica-las como erro. Codigo e atendimento sao texto. Valores e datas sao tipados; a precisao do repasse e preservada. Linha medico e Linha hospital permitem rastrear a origem.

Os relatorios sao persistidos no PostgreSQL e podem ser reabertos na tela.

## Revisao das visitas

A revisao mostra juntas as visitas do mesmo atendimento, com navegacao entre
linhas e datas dos candidatos hospitalares. Ao trocar de visita, o operador
confirma o descarte de alteracoes nao salvas.

Depois de associar um registro na revisao, o sistema reavalia o atendimento na
mesma transacao. Uma visita divergente, ainda nao revisada, pode ser preenchida
quando resta um unico candidato livre e exclusivo. Dados incompletos, conflitos
com a producao e repasse inconsistente impedem essa automacao. Candidatos ainda
disputados permanecem em revisao; a quantidade coincidente nao basta.

Cada preenchimento automatico gera uma revisao `CORRESPONDENCIA_AUTOMATICA`,
com responsavel Sistema, antes/depois e referencia a revisao que o originou.
A versao retornada inclui essas decisoes. Restaurar uma visita nao dispara
automacao nem restaura outras visitas; cada decisao tem seu proprio historico.

Cada linha tem a acao Revisar. Os detalhes exibem os dados da producao, o status automatico, o status atual, os candidatos do hospital e o historico de decisoes.

- Confirmar correspondencia associa um candidato da propria visita. O status passa a PAGA quando o setor e Faturado; caso contrario, fica PENDENTE. Medico, codigo, valor total e repasse precisam estar preenchidos.
- Manter pendente e Manter divergente removem a associacao atual e registram a justificativa.
- Restaurar resultado automatico retorna ao resultado original da importacao, sem apagar o historico.

Todas as decisoes exigem responsavel e justificativa. O responsavel e informado pelo operador; ainda nao ha vinculo com um usuario autenticado. As decisoes sao gravadas separadamente em revisao_visita, com os estados antes/depois e horario. O JSON original da importacao permanece preservado.

Um registro do hospital so pode ser associado a uma linha do medico na mesma rodada. Registros ocupados ficam indisponiveis na tela. As revisoes usam a versao da rodada; uma alteracao concorrente retorna HTTP 409 e exige atualizar os detalhes.

O dashboard e o XLSX consultam o resultado revisado. O XLSX inclui a aba Revisoes quando existem decisoes. Nos cards financeiros, um candidato ja associado a outra visita nao e contado novamente como valor pendente/divergente.

## API

- POST /api/conciliacoes/visitas (multipart: hospital, producao, faturamento).
- GET /api/conciliacoes/visitas (ultimos 100 relatorios).
- GET /api/conciliacoes/visitas/{id}.
- GET /api/conciliacoes/visitas/{id}/relatorio.xlsx.
- GET /api/conciliacoes/visitas/{id}/visitas/{linha}/revisao.
- POST /api/conciliacoes/visitas/{id}/visitas/{linha}/revisao (JSON: acao, linhaHospital quando confirmar, responsavel, justificativa, versao retornada pelos detalhes).

A leitura usa Apache POI. No layout Analia Franco, Planilha2 tem prioridade para evitar importar o mesmo detalhe duas vezes. Nao misture hospitais em um unico arquivo. As tabelas TUSS/AMB ainda nao participam desse cruzamento.
# Ajustes de dados na revisao

A producao pode conter apenas `Atendimento`. Sem `Dt.`, a busca considera todas
as datas do hospital: correspondencia unica e exclusiva preenche os dados,
multiplos candidatos ou disputa entre linhas exigem revisao; ausencia de
correspondente fica pendente. Com `Dt.` preenchida, o filtro de data existente
permanece. Visitas sem correspondencia segura nao recebem data presumida e
podem ser filtradas como `Sem data informada` no dashboard. Relatorios antigos
nao sao conciliados novamente automaticamente.

A decisao `AJUSTAR_DADOS` permite corrigir data, atendimento, medico, procedimento,
codigo TUSS/AMB, valor hospital, repasse, status e motivo. A linha identifica a
origem e nao e editavel. Campos monetarios vazios permanecem desconhecidos,
nao sao convertidos em zero. Atendimento e data corrigidos podem ser usados
para buscar novamente os registros do hospital.

Os ajustes compoem o resultado efetivo, o dashboard e o Excel, com historico
de antes/depois, responsavel e justificativa. O total informado pelo hospital
continua baseado na planilha original. `PAGA` exige uma correspondencia com
registro faturado do hospital e dados completos; nao comprova recebimento.
