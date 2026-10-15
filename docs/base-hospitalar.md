# Base hospitalar e conciliação em duas etapas

## Acesso e rotina

Acesse `http://localhost:8080/base-hospitalar.html` ou use Importações > Abrir base hospitalar e importar. Esta é a entrada para novas importações. O envio simultâneo de produção e faturamento foi removido.

1. Cadastre hospital e médicos, incluindo aliases dos nomes usados nas planilhas.
2. Selecione o hospital e importe seu arquivo, informando a competência. As visitas recebem IDs permanentes e aguardam a produção. O original fica armazenado no PostgreSQL.
3. Confira as ocorrências divergentes na tabela. Repetições do mesmo atendimento, data e médico exigem análise; profissionais diferentes no mesmo dia podem representar visitas legítimas.
4. Escolha o tipo de produção. Em **Um único médico**, selecione o cadastro por CRM/UF e envie Atendimento e Dt. Em **Vários médicos na mesma planilha**, Medico é opcional: se vazio, o sistema identifica pelo hospital somente quando atendimento e data encontram um único registro com cadastro médico ativo identificado com segurança. Sem registro hospitalar, fica pendente. Várias correspondências ou identificação desconhecida ou ambígua exigem revisão. Nomes já informados na produção não são substituídos silenciosamente.
5. Abra a conciliação gerada, revise as diferenças e libere o relatório para o médico.
6. Quando houver novos dados hospitalares ou decisões sobre duplicidades, use Atualizar conciliação na produção original em Arquivos armazenados. A atualização cria uma versão, preserva revisões e recebimentos e exige nova liberação.

Os modelos aceitos continuam sendo XLSX e XLSB. O hospital usa Atendimento, Data Consumo, Medico, Cod Produto, Valor Tot, Vl. A Repassar e Setor; os demais campos existentes do modelo continuam sendo lidos. A competência é informada pelo operador e não é deduzida da data da visita.

## Duplicidades

Cada ocorrência hospitalar guarda arquivo, linha original e competência. A identificação compara hospital, atendimento, data e médico, considerando o cadastro e seus aliases quando a correspondência é única. Nomes ausentes exigem análise conservadora. Procedimento e valores permanecem disponíveis para a revisão.

Uma repetição suspeita recebe EM_ANALISE e referência ao ID anterior. Ela não vira uma segunda visita disponível para vinculação automática. Se a visita original ainda não foi conciliada, a suspeita também bloqueia sua associação automática até a decisão.

Em Analisar repetição, o administrador pode:

- Confirmar **Duplicado pago em outro mês**, informando ID original, justificativa e evidência do pagamento anterior. As competências devem ser diferentes, e hospital, atendimento, data e identificação médica precisam ser compatíveis.
- Confirmar **É uma visita distinta**, com justificativa. A ocorrência mantém seu próprio ID e passa a poder ser selecionada na revisão. Se duas visitas do mesmo médico continuarem indistinguíveis por atendimento e data, a conciliação exige escolha manual.
- Cancelar e manter o caso em análise quando não houver evidência suficiente.

A decisão e o responsável ficam no histórico. O sistema não consulta o banco para comprovar pagamento: a evidência é conferida pelo administrador. Classificar a duplicidade não gera recebimento nem estorno. A ocorrência duplicada permanece rastreável e não cria outro vínculo financeiro.

## IDs e proteção contra dupla associação

O ID hospitalar é um inteiro gerado pelo banco e independente de atendimento, data ou posição no Excel. Nos novos relatórios, a linha do registro hospitalar identifica esse ID permanente. A linha da planilha original continua disponível na base.

Uma visita pode ter apenas um vínculo atual com uma linha de produção. A primeira associação também reserva o ID para o histórico daquela linha e cadeia de relatórios. Remover o vínculo numa revisão não disponibiliza a visita para outra produção, evitando reaproveitamento enquanto o histórico permanece.

Importações e revisões são transacionais e serializadas por hospital. Conflitos de associação revertem a operação inteira. O reenvio do mesmo arquivo para hospital, tipo, competência e médico mantém a importação existente. Arquivos iguais de médicos distintos são aceitos porque ambos podem ter a mesma combinação de atendimento e data.

## Atualização e financeiro

Atualizar conciliação usa a produção já armazenada e a base atual. Vínculos existentes e decisões manuais são preservados; visitas ainda sem vínculo e sem revisão são recalculadas. Casos revisados precisam de nova decisão manual quando necessário. A atualização mantém a cadeia usada pelos recebimentos e publicações.

CONCILIADA na base significa vínculo com produção. PAGA no relatório continua significando faturamento hospitalar identificado. Recebimentos reais e estornos continuam sendo registrados em Recebimentos. A publicação anterior continua visível ao médico até uma nova liberação.

## Históricos anteriores

Relatórios anteriores à mudança permanecem disponíveis, com suas revisões, publicações e registros financeiros. Não há migração automática dos registros antigos para a base permanente nem cruzamento automático de duplicidades entre o acervo antigo e a nova base. Antes de iniciar novas importações para períodos já processados, confira o histórico anterior para evitar sobreposição operacional.

Novas produções vinculadas à base não podem ser excluídas pela listagem de relatórios. Os arquivos originais podem ser baixados somente por administradores; não são publicados ao médico. O backup do PostgreSQL passa a incluir também as planilhas originais armazenadas.

## API administrativa

- POST `/api/base-hospitalar/hospital`: multipart `hospitalId`, `competencia` em AAAA-MM e `arquivo`.
- POST `/api/base-hospitalar/producao`: multipart `hospitalId`, `modoProducao` (`UNICO` ou `VARIOS`, padrão `UNICO`), `competencia` e `arquivo`. `medicoId` é obrigatório no modo individual e não deve ser enviado no coletivo.
- GET `/api/base-hospitalar/visitas?hospitalId=...`: ocorrências e vínculos.
- GET `/api/base-hospitalar/arquivos?hospitalId=...`: arquivos e relatório atual de cada produção.
- GET `/api/base-hospitalar/arquivos/{id}/original`: download administrativo do original.
- GET `/api/base-hospitalar/visitas/{id}/historico`: decisões com responsável e evidência.
- POST `/api/base-hospitalar/visitas/{id}/decisao`: `acao`, `anteriorId`, `justificativa`, `evidencia`.
- POST `/api/base-hospitalar/relatorios/{id}/atualizar?versao=...`: cria nova versão a partir da base atual. A versão de revisão é obtida pelo acompanhamento da conciliação.

As APIs de consulta, revisão, publicação e financeiro dos relatórios continuam nos caminhos existentes. O antigo POST simultâneo `/api/conciliacoes/visitas` e seu diagnóstico não são mais expostos.

## Verificação

Os testes de integração usam um banco PostgreSQL exclusivo, habilitados com `BASE_HOSPITALAR_TEST_DB=true` e `DB_URL`, `DB_USER` e `DB_PASSWORD` apontando para esse banco. Nunca aponte esses testes para o banco operacional. Execute `mvn -B test` no ambiente de verificação.

Os cenários cobrem hospital antes da produção, original preservado, reenvio idempotente, dois médicos no mesmo dia, duplicidade entre competências, bloqueio de dupla associação, reserva após revisão, chegada posterior de visita e preservação de IDs e recebimentos entre versões. A produção coletiva também cobre aliases, nomes ausentes ou ambíguos, disputa pela mesma visita e vínculos já consumidos por uma produção individual.
