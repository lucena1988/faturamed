# Conciliacao de visitas hospitalares

Tela: http://localhost:8080/conciliacao.html

Envie a producao do medico (.xlsx ou .xlsb) e o faturamento de um unico hospital. O hospital informado delimita o contexto do cruzamento. A chave e atendimento + data. O modelo do hospital usa Data Consumo; a producao usa Dt.

As linhas do medico sao preservadas. Quando a chave aparece exatamente uma vez em cada origem, os dados do hospital completam os campos vazios do relatorio. Campos originais permanecem registrados. Havendo varias linhas em qualquer origem, nenhuma delas e selecionada automaticamente: todos os candidatos ficam disponiveis para conferencia. Nomes e procedimentos nao sao inferidos nas linhas sem correspondencia.

## Status

- PAGA: correspondencia unica no setor Faturado do hospital, conforme definicao da empresa. Nao representa comprovacao de recebimento financeiro.
- PENDENTE: sem correspondente no arquivo enviado, ou correspondente fora do setor Faturado. A visita pode aparecer em outra competencia.
- DIVERGENTE: chave repetida na producao, varios candidatos no hospital, campos hospitalares incompletos, conflito com dados originalmente preenchidos pelo medico ou repasse inconsistente com Valor Tot x Regra (tolerancia R$ 0,01).

Os valores enriquecidos sao os informados pelo hospital. A tabela de preco contratada ainda nao e validada. O repasse correspondente soma apenas linhas PAGA, sem arredondar cada linha.

## Relatorio

O XLSX inclui Resumo, Visitas, Candidatos hospital e Hospital sem producao. A ultima aba inclui inclusive datas de outras competencias presentes no arquivo hospitalar, sem classifica-las como erro. Codigo e atendimento sao texto. Valores e datas sao tipados; a precisao do repasse e preservada. Linha medico e Linha hospital permitem rastrear a origem.

Os relatorios sao persistidos no PostgreSQL e podem ser reabertos na tela.

## API

- POST /api/conciliacoes/visitas (multipart: hospital, producao, faturamento).
- GET /api/conciliacoes/visitas (ultimos 100 relatorios).
- GET /api/conciliacoes/visitas/{id}.
- GET /api/conciliacoes/visitas/{id}/relatorio.xlsx.

A leitura usa Apache POI. No layout Analia Franco, Planilha2 tem prioridade para evitar importar o mesmo detalhe duas vezes. Nao misture hospitais em um unico arquivo. As tabelas TUSS/AMB ainda nao participam desse cruzamento.
