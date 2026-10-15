# Conciliacao de visitas hospitalares

Tela: http://localhost:8080/conciliacao.html

## Fila de cobranca ao hospital

### Acompanhamento manual

#### Prioridades de contato

A fila possui filtros Todas, Nao cobradas, Contato para hoje, Contatos atrasados e Aguardando retorno. Hoje/atrasados usam o proximo contato agendado, com referencia America/Sao_Paulo, e nao a idade da visita ou prazo financeiro. Sem data de proximo contato nao ha atraso presumido. Visitas nao cobradas com contato agendado podem aparecer tambem nos grupos de data: os contadores nao sao exclusivos.

Contadores respeitam medico e dias desde a visita, antes do filtro de prioridade. A ordem usa o proximo contato mais antigo, deixando visitas sem agenda por ultimo; empates usam data da visita e linha. O XLSX respeita a prioridade selecionada, mantendo os dados internos de contato fora da exportacao. A tabela identifica o responsavel pelo ultimo acompanhamento. Nao ha notificacoes automaticas nem alteracao dos status da conciliacao.

`Acompanhar` registra situacao (Nao cobrada, Enviada, Aguardando retorno ou Retorno recebido), data da cobranca, resposta, observacoes e proximo contato. O responsavel e o usuario autenticado, registrado pelo servidor. Cada salvamento acrescenta um evento ao historico; nao modifica a conciliacao nem envia emails.

O historico e compartilhado pela familia de versoes e pela linha interna estavel da visita, nao pelo numero de atendimento ou pelo ID da planilha. Assim, visitas repetidas mantem acompanhamentos distintos. Eventos ficam vinculados tambem a versao em que foram registrados: excluir essa versao apaga seus contatos, evitando reaproveitar historicos de visitas removidas por exclusao de relatorio.

Uma nova versao herda os contatos sem copiar eventos. Visitas que saem da fila continuam acessiveis em `Cobrancas acompanhadas fora da fila`, com historico somente para consulta. Edicoes concorrentes, versoes historicas e visitas que ja sairam da fila sao bloqueadas. Os registros internos de contato nao sao incluidos no XLSX destinado ao hospital.

Tela: http://localhost:8080/cobrancas.html. Acesso administrativo pelo menu principal ou pelo relatorio de conciliacao. Selecione hospital e relatorio; somente a versao mais recente de cada cadeia aparece na lista. Relatorios independentes nao sao somados nem deduplicados entre si nesta etapa.

A fila inclui somente visitas PENDENTE sem registro hospitalar vinculado e sem candidatos. Ambiguidades, divergencias e correspondencias aguardando faturamento ficam na revisao, nao na cobranca por ausencia de registro. Identificacoes manuais e de producao sao respeitadas. Linhas repetidas representam visitas distintas e permanecem na fila.

Filtros de medico e dias desde a visita tambem se aplicam ao XLSX. O tempo e calculado pela data efetiva da visita, com referencia no fuso America/Sao_Paulo; nao representa dias desde uma cobranca enviada. Visitas sem data aparecem apenas no filtro de tempo Todos. Valores desconhecidos sao exibidos como nao informados e exportados em branco. Nao ha estimativa baseada em Valor Orig, nem comprovacao de recebimento ou inadimplencia. Dados de paciente nao constam deste relatorio.

O link Revisar abre a visita especifica na conciliacao. A consulta e a exportacao nao alteram status nem enviam cobrancas. Registro de contato, responsavel e retorno do hospital fica para a proxima etapa.

## Atualizacao somente do faturamento hospitalar

Ao selecionar um relatorio para atualizar, escolha `Somente faturamento completo do hospital`. A producao original, inclusive linhas repetidas e sua identificacao, e reutilizada do relatorio base. O servidor herda hospital e medico da versao salva, valida a versao de revisao e cria uma nova versao sem alterar a anterior.

Envie o faturamento completo atualizado: registros previamente conciliados ou envolvidos em revisoes devem permanecer presentes e inalterados. Arquivos identicos mantem a versao existente. Uma janela de conclusao informa visitas preservadas, novas visitas faturadas, pendentes, divergentes e seus repasses conhecidos. Visitas sem valor sao contadas separadamente; faturamento nao comprova recebimento financeiro.

## Fila de revisao por categoria

Na revisao, o filtro `Medico no hospital` restringe os candidatos sem selecionar uma correspondencia automaticamente. Cada registro exibe o cadastro ativo sugerido por nome/alias e CRM/UF, ou avisa sobre homonimos e ausencia de cadastro. A previa do registro selecionado mostra procedimento, data, valores e disponibilidade. `Preencher dados para revisao` copia os dados para o formulario; somente `Salvar revisao` persiste a decisao. Trocar o filtro limpa selecoes ocultas, e registros reservados continuam indisponiveis.

A fila mostra contadores das visitas que ainda precisam de conferencia, respeitando o filtro de medico. Ao selecionar uma categoria, a tabela mostra apenas os casos a conferir dessa categoria e limpa os filtros de status, origem e atendimento. O seletor Categoria permite voltar a consultar todas as visitas, inclusive resolvidas.

`Revisar proxima visita` abre a revisao manual dentro dos filtros atuais, priorizando ambiguidades. Cliques seguintes avancam pelas visitas ainda na fila. Ao salvar, o acompanhamento recalcula categorias e contadores; versoes historicas nao permitem iniciar revisao. A fila nao modifica correspondencias automaticamente.

## Identificacao da producao na importacao

As telas de Conciliacao e Importacoes permitem selecionar **Varios medicos / identificacao por linha** (padrao) ou **Um unico medico**.

- Em producao individual, selecione um cadastro ativo por nome e CRM/UF. O servidor preenche apenas as identificacoes ausentes, armazenando ID e nome do cadastro em metadados separados. A planilha e os campos originais permanecem intactos.
- Nome/alias preenchido na linha deve identificar unicamente o mesmo cadastro. Outro medico, nome desconhecido ou homonimo fica DIVERGENTE para conferencia, sem sobrescrever a fonte ou atribuir pagamento automaticamente.
- O preenchimento da importacao nao e uma revisao manual. Visitas sem correspondente hospitalar continuam pendentes, mas passam a ter o medico identificado e podem aparecer no seu painel/portal.
- Hospital de outro medico ou identificacao hospitalar ambigua nao confirma a visita do medico selecionado.
- Producao coletiva mantem a identificacao por linha, sem aplicar um medico a todos os registros. As identificacoes ausentes continuam visiveis no acompanhamento e no filtro de medico nao identificado.
- Novas versoes herdam o modo e o medico da importacao base. Trocar essa configuracao exige **Novo relatorio**. As visitas conciliadas/revisadas e a fonte original continuam preservadas.
- A resposta da importacao informa quantas linhas tiveram identificacao preenchida e quantas informaram medico diferente. Isso ainda nao e a pre-conferencia das planilhas, prevista para o proximo passo.

## Regra do modelo corrigido (13/10/2026)

- `Dt.` representa a data real da visita e deve corresponder a `Data Consumo` do hospital.
- `Valor Orig = 0` na producao significa valor ainda nao informado. Nao gera divergencia monetaria; o resultado e a exportacao usam o valor do hospital quando ha correspondencia segura. A fonte original permanece intacta.
- Valores nao zero informados na producao continuam sendo conferidos.
- Havendo varios registros para atendimento/data, medico, `Cod Produto` e procedimento preenchidos refinam os candidatos. Se nenhum candidato corresponde aos campos informados, os registros permanecem disponiveis para revisao, sem escolher um arbitrariamente.
- As disputas sao contadas por linha da producao depois desse refinamento. Um registro hospitalar nao pode ser atribuido automaticamente a duas visitas.
- Sem data, permanece a compatibilidade com o modelo anterior; datas ou medicos ambiguos exigem revisao.
- Relatorios salvos e revisoes manuais nao sao alterados automaticamente. Para testar a nova regra com os mesmos arquivos, importe como **Novo relatorio**, pois arquivos identicos em uma atualizacao retornam a versao existente.

## Dashboard e importacoes

### Novas versoes da mesma producao

O campo Importacao permite Novo relatorio ou Atualizar uma versao mais recente.
Uma atualizacao recebe as duas planilhas completas e cria outro relatorio com
anterior_id e numero_versao; nao sobrescreve a versao antiga. Cada versao pode
ser consultada, mas somente a ultima aceita revisoes. Exclua versoes da mais
recente para a mais antiga. O dashboard continua exibindo uma rodada por vez.

Visitas pagas e linhas com historico de revisao sao preservadas. Pendencias
sem revisao sao reavaliadas e novas ocorrencias sao acrescentadas. A producao
deve manter todas as ocorrencias anteriores, incluindo os campos preenchidos;
reordenar linhas e permitido. A correspondencia usa os campos originais e a
quantidade de ocorrencias, nao considera um atendimento repetido como duplicata.
As linhas do relatorio sao IDs estaveis, nao necessariamente a posicao da nova
planilha. linhas_producao registra o mapa ID -> linha Excel da nova versao.

Registros hospitalares preservados sao associados pelo conteudo, nao por numero
da linha. Alteracao ou ausencia de registros protegidos, reducao da producao,
mudanca de hospital, versao base desatualizada ou revisao concorrente retornam
conflito/erro antes de gravar uma nova versao. Mudancas que nao podem ser
identificadas com seguranca precisam de conferencia; nao sao descartadas.

SHA-256 dos dois arquivos permite reconhecer reenvio identico da versao base
e retornar o mesmo relatorio, sem gerar nova versao. A deteccao e restrita ao
relatorio escolhido; Novo relatorio continua sendo uma rodada independente.
Relatorios antigos sem hashes podem gerar uma primeira versao ao reenviar.

POST aceita relatorioBaseId e versaoBase (versao de revisao retornada pelo
acompanhamento). Historicos sao copiados para a nova versao mantendo antes/depois,
responsavel e horario. Restaurar automatico usa a base daquela versao.

### Cadastro manual de hospitais

Secao `/#hospitais`, no menu lateral da tela principal. Cadastro com nome,
CNPJ/documento opcional, cidade e UF opcionais e ativo/inativo. Nomes sao
unicos sem diferenciar caixa, acentos e espacos; documento, quando preenchido,
tambem e unico apos remover pontuacao. Nao ha consulta ou validacao oficial de
CNPJ nesta etapa. Edicoes usam versao para evitar sobrescrita concorrente.

As duas telas de importacao oferecem a selecao de hospital ativo e a alternativa
de nome manual para compatibilidade e testes. Se enviado `hospitalId`, o servidor
valida o cadastro ativo e usa seu nome, mesmo que outro nome tenha sido enviado.
O relatorio guarda ID e nome da importacao. Revisoes e filtros por medico
preservam o vinculo; renomear ou inativar nao altera relatorios antigos.
O dashboard continua agrupando as rodadas pelo nome salvo no relatorio.

API: GET/POST `/api/hospitais`, PUT `/api/hospitais/{id}`. Nao ha exclusao fisica
de hospitais. Atualize a pagina de conciliacao para carregar mudancas feitas
no cadastro em outra aba.

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

### Cadastro manual de medicos

Secao `/#medicos`, dentro da tela principal, acessivel pelo menu Medicos e pela
conciliacao. O endereco antigo `/medicos.html` redireciona para essa secao. Cadastro
com nome, CRM numerico, UF, ativo/inativo e ate 30 aliases. CRM e UF juntos sao
unicos; zeros iniciais do CRM sao removidos. Os registros nao sao excluidos.
Edicoes possuem versao para impedir sobrescrita concorrente. Cada alias
normalizado pertence a um unico cadastro, e conflitos retornam HTTP 409.

Na revisao, Ajustar dados permite selecionar Medico cadastrado. O servidor
valida o cadastro ativo, aplica seu nome e persiste o ID no historico da visita.
O nome original do arquivo nao e alterado. Restaurar automatico remove esse
vinculo manual junto dos demais ajustes. Digitar outro nome limpa a selecao.

O filtro Medico agrupa aliases quando nome/alias corresponde a um unico cadastro
ativo; nomes ambiguos nao recebem ID presumido. Nesses casos, vincule cada visita
na revisao. Exportacao por `medicoId` agrupa as visitas identificadas e inclui
CRM/UF no Resumo. Varios cadastros podem compartilhar um nome, mas nao CRM/UF.
Cadastros inativos deixam de gerar sugestoes; vinculos manuais antigos continuam
disponiveis em relatorios. Atualize a conciliacao depois de editar cadastros em
outra aba para carregar os novos nomes e aliases. Ainda nao ha validacao junto
ao conselho profissional nem cadastro de multiplos CRMs para o mesmo registro.

API: GET/POST `/api/medicos`, PUT `/api/medicos/{id}`.

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

### Acompanhamento

A tela inclui categorias e origem por visita, filtros e contagens no escopo do
medico selecionado. Sem medico identificado e contado no relatorio geral.
`GET /api/conciliacoes/visitas/{id}/acompanhamento` retorna o relatorio e seus
metadados de acompanhamento calculados na mesma leitura, com a versao.

Categorias distinguem ambiguidade, dados faltantes, valor divergente, ausencia
de correspondencia, aguardando faturamento, dados divergentes e conferencia
manual. A categoria principal usa dados estruturados; nao interpreta o texto
livre da justificativa. Ambiguidade tem prioridade sobre falta de dados nas
visitas sem correspondencia escolhida. O motivo detalhado permanece na tabela.

Origens: nao revisada (pendente/divergente sem decisao vigente), automatica na
importacao (paga), automatica apos revisao e manual. Restaurar o resultado
automatico retorna a origem apropriada do resultado inicial, sem apagar o
historico. A classificacao considera a ultima decisao de cada linha.

O aviso de conferencia nao altera status e nao bloqueia o download. Uma visita
divergente ou sem data, medico, procedimento, codigo, valor ou repasse exige
conferencia. Pendentes sem revisao tambem exigem conferencia; uma pendente
completa e revisada pode constar no envio como pendente de faturamento.
O aviso avalia todas as visitas do medico, independentemente dos filtros de
status, categoria, origem e atendimento. Nao representa aprovacao financeira.

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
# Diagnostico antes da importacao

Nas telas de Importacoes e Conciliacao, `Analisar planilhas` consulta os arquivos sem salvar relatorio. O envio para conciliacao tambem abre o diagnostico para confirmacao.

O diagnostico informa cobertura por atendimento/data, periodo de cada arquivo, IDs ausentes/repetidos, medico nao identificado e correspondencias ambiguas ou com dados divergentes. Alertas podem se sobrepor; exemplos mostram ate 20 linhas por categoria, mantendo os totais completos. A ausencia de medico no arquivo original e distinta da identificacao efetiva por hospital ou cadastro selecionado.

A coluna ID e analisada, mas nao substitui a regra de correspondencia: um ID composto de data e atendimento nao distingue medicos diferentes. Nenhuma planilha ou revisao anterior e alterada pela consulta. Em atualizacoes, os numeros descrevem os arquivos enviados; a preservacao das revisoes ocorre somente ao criar a nova versao.

## Acompanhamento de cobranca em lote

Na Fila de cobranca, o administrador pode selecionar de 1 a 500 visitas visiveis do mesmo relatorio e registrar situacao, data da cobranca, resposta, observacoes e proximo contato comuns. A confirmacao identifica hospital, relatorio e quantidade. Trocar filtros ou atualizar a fila limpa a selecao.

`POST /api/conciliacoes/visitas/{id}/cobranca/lote` recebe `visitas` com `linha` e `versao` do ultimo contato (zero quando inexistente), alem dos campos comuns de acompanhamento. Retorna `registradas`. A transacao valida todas as visitas sob bloqueio do relatorio; selecao duplicada, versao historica, visita fora da fila ou contato desatualizado impedem a gravacao integral. Cada visita recebe seu proprio evento com o usuario autenticado. Nao envia email nem altera status de conciliacao ou valores.

## Recebimento financeiro real

`recebimentos.html` permite ao administrador selecionar hospital, relatorio e medico e registrar manualmente recebimentos por visita, inclusive parciais. Tambem permite pagamentos distribuidos manualmente entre visitas, descritos abaixo. Nao existe rateio automatico, conciliacao bancaria ou envio de email.

- Faturado: repasse efetivo das visitas PAGA, arredondado por visita para centavos (HALF_UP).
- Recebido: soma dos lancamentos menos os estornos.
- A receber: faturado menos recebido; nao inclui estimativas de pendencias ou divergencias.
- Em conferencia: valores/identidade financeira alterados ou faturamento nao confirmado. Valores desconhecidos nao sao tratados como divida zero.

Registro exige visita faturada, medico identificado no cadastro, valor positivo de ate duas casas decimais, data nao futura e valor dentro do saldo. A versao financeira e o ID do ultimo evento da visita (zero quando nenhum). Bloqueio transacional do relatorio impede duplicacao por concorrencia. `PAGA` continua significando faturamento, nao recebimento.

O estorno integral de um lancamento exige justificativa e cria evento negativo independente, preservando o original, data, observacao e usuario autenticado. Lancamentos nao podem ser apagados ou editados. Relatorios com historico financeiro nao podem ser excluidos, mesmo apos estorno. Historico e saldo acompanham as versoes do mesmo relatorio; relatorios independentes nao sao consolidados automaticamente, pois podem conter a mesma producao.

Mudancas de medico, atendimento, data ou codigo de uma visita com recebimentos ativos sinalizam conferencia e bloqueiam novos recebimentos. Tambem sao sinalizados saldo negativo e visita que deixou de estar faturada. O administrador pode conferir/estornar o registro. Identidade nao capturada em registros legados exige conferencia conservadora.

APIs administrativas: GET `/api/conciliacoes/visitas/{id}/recebimentos`, POST `/visitas/{linha}` com `valor`, `data`, `observacoes`, `versao`, e POST `/visitas/{linha}/{evento}/estorno` com `justificativa`, `versao` (ambos relativos ao caminho de recebimentos). Somente a ultima versao permite gravacao.

Dashboard e Painel do medico mostram Recebido e A receber respeitando o periodo da visita selecionado, nao o mes bancario do recebimento. O portal consulta GET `/api/medico/relatorios/{id}/recebimentos`: usa o cadastro do login e retorna apenas saldos das visitas autorizadas, sem historico interno ou dados de outros medicos. Financeiro inconsistente aparece como nao confirmado.

## Extrato financeiro do medico

No portal, a secao Extrato financeiro usa o hospital e relatorio selecionados. Os filtros De/Ate de visitas e de recebimentos sao independentes do filtro mensal do painel. Datas limite sao inclusivas; limites invertidos sao rejeitados. Visitas sem data aparecem sem filtros de visita, mas nao sao presumidas dentro de um intervalo.

O periodo de visitas define as visitas do extrato e os movimentos vinculados a elas. O periodo dos recebimentos filtra somente recebimentos/estornos dessas visitas: nao altera o saldo atual nem o recebido acumulado. Entradas representam eventos positivos, estornos representam o total positivo dos eventos negativos e liquido e entradas menos estornos. Um recebimento estornado permanece no mes em que foi registrado; o evento negativo aparece na data do estorno. Isso permite periodos com liquido negativo.

Faturado confirmado, recebido acumulado e saldo atual usam os valores efetivos das visitas e todo o historico financeiro. Aguardando faturamento e divergencias sao apresentados separadamente com valores informados e contagem de visitas sem valor. Totais monetarios sao subtotais conhecidos; dados desconhecidos ou financeiros inconsistentes permanecem nao confirmados nos detalhes. Movimentos de visitas com financeiro inconsistente nao sao divulgados no extrato enquanto exigem conferencia.

GET `/api/medico/relatorios/{id}/extrato` e `/extrato.xlsx` aceitam `visitaDe`, `visitaAte`, `recebimentoDe`, `recebimentoAte` no formato yyyy-MM-dd. O cadastro do login define o medico, nao um parametro. Movimentos tambem sao filtrados pelo medico gravado no lancamento, alem das linhas atualmente autorizadas. Observacoes internas, justificativas de estorno, usuario responsavel, pacientes e candidatos hospitalares nao fazem parte do extrato.

A exportacao tem Resumo (escopo, datas, totais e criterio), Visitas (posicao atual, inclusive pendencias/divergencias) e Movimentos (recebimentos e estornos do periodo). Valores sao numericos com formato monetario, datas sao nativas do Excel e valores desconhecidos ficam em branco. O download aplica os mesmos filtros da consulta. Nao consolida relatorios independentes ou versoes, evitando soma duplicada de producoes possivelmente repetidas.

## Pagamento distribuido entre visitas

Na tela Recebimentos, selecione um medico cadastrado, marque de 1 a 500 visitas faturadas com saldo confirmado do relatorio selecionado e use Registrar pagamento. Informe data, valor total, referencia e observacoes. Distribua manualmente um valor positivo por visita; Usar saldo preenche apenas a visita escolhida, sem decidir automaticamente quais visitas receberam dinheiro. Remover retira a visita da distribuicao preservando os demais valores. Mudar hospital, relatorio, medico ou atualizar limpa a selecao.

A tela mostra total, valor distribuido e diferenca em centavos exatos. A confirmacao identifica medico, hospital, relatorio, quantidade, referencia e valor total. Cada alocacao pode ser parcial, mas nao pode exceder o saldo e a soma deve corresponder exatamente ao total recebido. Nao existe saldo solto ou rateio presumido.

POST `/api/conciliacoes/visitas/{id}/recebimentos/pagamentos` recebe `medicoId`, `data`, `valorTotal`, `referencia` (obrigatoria, ate 120 caracteres), `observacoes` (ate 2000), `versaoConciliacao` e `visitas` com `linha`, `versao`, `valor`. Retorna `pagamentoId`, `visitas` (quantidade), `valorTotal`. A versao por visita e o ultimo evento financeiro. `versaoConciliacao` vem do GET de recebimentos e bloqueia a gravacao quando uma revisao alterou os dados, mesmo sem novos eventos financeiros.

Somente a ultima versao do relatorio aceita gravacao. O servico bloqueia o relatorio e valida toda a distribuicao antes de inserir o cabecalho e os eventos. Visita desatualizada, fora do faturamento, de outro medico, saldo excedido, linha duplicada ou soma incorreta impedem a gravacao integral. Falhas posteriores tambem revertem a transacao. O usuario responsavel vem da sessao, nao do corpo da requisicao.

Uma referencia nao pode ser repetida para o mesmo medico na mesma cadeia de relatorios, desconsiderando espacos nas extremidades e maiusculas/minusculas. Isso bloqueia repeticao do pagamento, inclusive tentativas apos estorno. A referencia nao verifica transferencias bancarias nem impede duplicidade entre relatorios independentes. Correcao de lancamento requer estorno e nova referencia identificavel.

Cada visita recebe seu proprio evento ligado ao pagamento comum. Historico administrativo, extrato do medico e Excel exibem pagamento e referencia. Estornar uma alocacao cria evento negativo independente, ligado ao mesmo pagamento; nao estorna as outras visitas automaticamente nem apaga o cabecalho. Referencia e vinculos acompanham as versoes preservadas. Observacoes e justificativas internas continuam fora do extrato do medico.
