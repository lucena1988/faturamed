# Manual do usuário FaturaMed

Orientações para administradores e médicos  
Versão 1.0 · Atualizado em 15 de outubro de 2026

O FaturaMed ajuda a comparar a produção médica com o faturamento informado pelo hospital, revisar diferenças e acompanhar os recebimentos. Este manual ensina a executar essas tarefas e a consultar os relatórios publicados para cada médico.

O administrador prepara os dados, confere as visitas, libera os relatórios e registra os recebimentos. O médico consulta suas informações, acompanha o extrato e solicita revisão quando identifica algo que precisa ser esclarecido.

> **Antes de começar:** uma visita marcada como **PAGA** significa que seu faturamento foi identificado no hospital. O recebimento do dinheiro é registrado separadamente. Da mesma forma, confirmar ciência de um relatório não significa aprovar valores ou confirmar um pagamento.

## Como consultar este manual

- [1 Primeiro acesso e segurança](#1-primeiro-acesso-e-segurança)
- [2 Entenda as informações do sistema](#2-entenda-as-informações-do-sistema)
- [3 Guia do administrador](#3-guia-do-administrador)
- [4 Guia do médico](#4-guia-do-médico)
- [5 Exemplos práticos](#5-exemplos-práticos)
- [6 Dúvidas e solução de problemas](#6-dúvidas-e-solução-de-problemas)
- [7 Checklists de rotina](#7-checklists-de-rotina)
- [8 Glossário](#8-glossário)

Se você é administrador e vai preparar o primeiro relatório, comece pelos cadastros da seção 3. Se você é médico, leia as seções 1 e 2 e siga para a seção 4.

## 1 Primeiro acesso e segurança

### 1.1 Entrar no sistema

1. Abra no navegador o endereço fornecido pela equipe responsável pelo FaturaMed.
2. Informe seu e-mail e sua senha na tela de login.
3. Entre no sistema. O administrador acessa o ambiente de gestão; o médico acessa **Meu painel**.

No computador em que o projeto está em execução local, o endereço é `http://localhost:8080/login.html`. Para acessar de outro computador, solicite à equipe responsável o endereço disponibilizado para sua organização.

O acesso do médico é criado pelo administrador. Caso ainda não tenha recebido suas credenciais, procure o responsável pelo sistema.

### 1.2 Alterar a senha

1. Abra **Minha conta**.
2. Preencha **Senha atual**.
3. Digite a **Nova senha** e repita em **Confirmar nova senha**.
4. Clique em **Alterar senha** e confira a mensagem da tela.

Use uma senha exclusiva com pelo menos 12 caracteres. O sistema também limita o tamanho em bytes; caso uma senha com muitos caracteres especiais seja recusada, escolha outra combinação longa dentro do limite indicado.

Recomenda-se alterar a senha inicial no primeiro acesso. Se esquecer a senha, peça a redefinição ao administrador. A recuperação automática por e-mail ainda não está disponível.

### 1.3 Proteger o acesso

- Use um login individual e mantenha a senha em sigilo.
- Clique em **Sair** ao terminar, principalmente em computadores compartilhados.
- Depois de 30 minutos sem atividade, a sessão expira e será necessário entrar novamente.
- Cinco tentativas de login malsucedidas para uma conta causam bloqueio de 15 minutos. Aguarde esse período antes de tentar novamente.
- Compartilhe relatórios financeiros apenas com o destinatário correto. Evite enviar dados de pacientes ao pedir ajuda.

## 2 Entenda as informações do sistema

### 2.1 O que cada perfil pode fazer

| Atividade | Administrador | Médico |
| --- | --- | --- |
| Cadastrar hospitais e médicos e gerenciar acessos | Sim | Não |
| Importar planilhas e revisar a conciliação | Sim | Não |
| Liberar relatórios para consulta | Sim | Não |
| Registrar recebimentos e estornos | Sim | Não |
| Acompanhar cobranças e responder pedidos de revisão | Sim | Não |
| Consultar informações médicas e financeiras | Nos relatórios administrativos | Somente as próprias informações publicadas |
| Confirmar ciência e solicitar revisão pelo portal | Acompanha os registros | Sim |
| Alterar a própria senha | Sim | Sim |

### 2.2 Situação das visitas

| Situação | Significado | Próximo passo habitual |
| --- | --- | --- |
| PAGA | A visita possui correspondência com um registro faturado do hospital | Conferir o recebimento financeiro separadamente |
| PENDENTE | Não foi encontrada correspondência ou o registro hospitalar ainda não está faturado | Verificar o motivo e acompanhar o hospital quando necessário |
| DIVERGENTE | Há conflito, dados incompletos ou mais de uma correspondência possível | Revisar os dados e os registros candidatos |

Leia sempre o **Motivo** da visita. Duas visitas com a mesma situação podem precisar de providências diferentes. Uma pendência também pode ser faturada em outra competência.

### 2.3 Valores financeiros

| Informação | Como interpretar |
| --- | --- |
| Faturado pelo hospital | Repasse das visitas com faturamento confirmado no escopo consultado |
| Recebido | Recebimentos registrados no sistema, descontados os estornos |
| A receber | Diferença entre o faturado confirmado e o recebido; não inclui estimativas de pendências e divergências |
| Aguardando faturamento | Valores conhecidos associados a visitas pendentes |
| Valor em conferência | Valores ou informações que ainda precisam ser verificados |
| Sem valor definido ou Não informado | O valor não é conhecido; não significa zero |
| Não confirmado | Os dados financeiros exigem conferência e não devem ser interpretados como saldo validado |

Os totais dependem do hospital, relatório e período selecionados. Relatórios independentes podem conter a mesma produção: não some seus totais sem conferir se existe repetição.

### 2.4 Produção, versões e publicação

A **produção** informa as visitas realizadas pelo médico. O **faturamento do hospital** informa os registros hospitalares usados na comparação. A **conciliação** é o resultado desse cruzamento.

Uma atualização pode criar uma **nova versão** do mesmo relatório. O administrador trabalha na versão mais recente e preserva o histórico. O médico consulta a **publicação liberada**, que é uma cópia dos dados conferidos para consulta.

Se o administrador fizer ajustes depois da liberação, o médico continua vendo a publicação anterior até a próxima liberação. Recebimentos e estornos podem atualizar a consulta financeira, desde que compatíveis com as visitas publicadas.

## 3 Guia do administrador

### 3.1 Ordem recomendada de trabalho

1. Cadastre os hospitais e os médicos.
2. Crie os acessos e confira o vínculo de cada médico pelo CRM e pela UF.
3. Separe as planilhas de produção e faturamento do hospital.
4. Analise as planilhas e faça a conciliação.
5. Revise divergências, pendências e visitas sem médico identificado.
6. Libere o relatório para o médico.
7. Acompanhe cobranças e solicitações de revisão.
8. Registre os recebimentos efetivos e consulte o saldo.

### 3.2 Cadastrar hospitais

**Onde acessar:** menu **Hospitais**.

1. Preencha o nome do hospital.
2. Informe documento, cidade e UF, quando disponíveis.
3. Mantenha o cadastro ativo para utilizá-lo nas importações.
4. Salve e confira se aparece na listagem.

Para corrigir um cadastro, use **Editar**. A inativação retira o hospital das opções para novas seleções; os relatórios anteriores mantêm o nome salvo na importação. O preenchimento de CNPJ não realiza consulta oficial de regularidade.

### 3.3 Cadastrar médicos

**Onde acessar:** menu **Médicos**.

1. Preencha nome, CRM e UF.
2. Se necessário, informe as variações de nome usadas nas planilhas, uma por linha, no campo de aliases.
3. Mantenha o cadastro ativo e salve.
4. Confira o CRM e a UF antes de criar o acesso.

**Exemplo de alias:** se o cadastro é “Ana Maria Souza” e o hospital usa “Dra Ana M Souza”, essa variação pode ajudar a identificar os registros. Só associe nomes que realmente pertençam à mesma pessoa.

CRM e UF, juntos, identificam um cadastro único. Nomes iguais podem pertencer a pessoas diferentes; nesses casos, revise as visitas e selecione explicitamente o médico cadastrado. O sistema não consulta o conselho profissional para validar o CRM.

### 3.4 Criar e administrar acessos

**Onde acessar:** menu **Acessos**.

1. Em **Novo acesso**, informe nome e e-mail.
2. Selecione o perfil **Médico** ou **Administrador**.
3. Para um médico, selecione o **Médico cadastrado** correto, conferindo CRM e UF.
4. Defina uma senha inicial com pelo menos 12 caracteres.
5. Clique em **Criar acesso** e confira a listagem.
6. Entregue as credenciais ao usuário por um canal apropriado e oriente a troca da senha.

Cada cadastro médico pode estar associado a, no máximo, um usuário. O usuário não altera o próprio perfil ou vínculo médico.

Na lista de acessos, utilize as ações disponíveis para redefinir a senha ou ativar e desativar um usuário. A redefinição invalida as sessões existentes na próxima requisição protegida. O sistema impede desativar seu próprio acesso e o último administrador ativo.

### 3.5 Preparar e analisar as planilhas

**Onde acessar:** **Importações** ou **Conciliação de visitas**.

Antes de enviar os arquivos, confira:

- Se produção e faturamento correspondem ao hospital escolhido.
- Se o arquivo hospitalar está completo para a conferência pretendida.
- Se as datas e os números dos atendimentos estão corretos.
- Se a produção é de um médico ou reúne vários profissionais.
- Se os arquivos estão em um formato aceito pela tela de conciliação: `.xlsx` ou `.xlsb`.

Na produção, **Dt.** representa a data da visita; no modelo hospitalar, a referência é **Data Consumo**. Quando a data não está informada, mais de um registro possível pode exigir revisão manual. Não misture hospitais em um único arquivo.

1. Escolha **Novo relatório** no campo **Importação**.
2. Selecione o hospital cadastrado. Se usar a alternativa de nome manual, confira cuidadosamente o nome informado.
3. Selecione **Um único médico** ou **Vários médicos / identificação por linha**.
4. Para produção individual, selecione o médico ativo pelo nome e CRM/UF.
5. Anexe a produção médica e o faturamento hospitalar.
6. Use **Analisar planilhas** e leia o diagnóstico. O envio para conciliação também solicita essa conferência.

O diagnóstico apresenta períodos dos arquivos, cobertura dos atendimentos, dados ausentes, repetições e correspondências ambíguas. Essa análise ainda não salva um relatório.

Corrija arquivos selecionados por engano antes de prosseguir. Uma repetição pode representar visitas distintas; não exclua linhas apenas porque o atendimento aparece mais de uma vez. Os alertas podem se sobrepor e não devem ser somados como se fossem visitas diferentes.

### 3.6 Conciliar e consultar o resultado

1. Depois de conferir o diagnóstico, confirme o envio e clique em **Conciliar**, conforme a tela utilizada.
2. Aguarde a conclusão e confira a mensagem de resultado.
3. Abra o relatório gerado e verifique hospital, versão e quantidade de visitas.
4. Examine os totais e os motivos de pendência e divergência.
5. Comece a revisão pelas situações que exigem conferência.

O sistema usa atendimento e data na comparação e considera outros dados disponíveis para distinguir os registros. Quando não há correspondência segura, a visita permanece para análise. Um registro hospitalar não pode ser usado para confirmar duas visitas na mesma rodada.

### 3.7 Revisar uma visita

**Onde acessar:** **Conciliação de visitas**, ação **Revisar**, ou **Revisar próxima visita**.

1. Selecione o relatório mais recente.
2. Use os filtros de médico, status, origem, categoria e atendimento para localizar o caso.
3. Abra a visita e compare os dados da produção com os registros do hospital.
4. Observe as outras visitas do mesmo atendimento e o histórico existente.
5. Escolha a decisão adequada, informe responsável e justificativa e clique em **Salvar revisão**.
6. Confira o novo resultado e as demais visitas do atendimento, pois o sistema pode resolver correspondências que se tornaram únicas após a revisão.

| Decisão | Quando usar |
| --- | --- |
| Ajustar dados da visita | Corrigir atendimento, data, médico, procedimento, código, valores, status ou motivo com base em informação conferida |
| Confirmar correspondência | Associar o registro hospitalar correto à visita |
| Manter pendente | Registrar por que a visita continua aguardando tratamento |
| Manter divergente | Registrar um conflito que ainda não pôde ser resolvido |
| Restaurar resultado automático | Retornar ao resultado original da importação daquela versão, mantendo o histórico |

O filtro **Médico no hospital** ajuda a examinar os candidatos. Selecionar um candidato ou usar **Preencher dados para revisão** apenas prepara o formulário; a decisão só é gravada em **Salvar revisão**.

Ao corrigir a identificação, prefira selecionar **Médico cadastrado**. Campos monetários vazios continuam desconhecidos. Não preencha zero só para remover um aviso.

Se outra pessoa alterar o relatório enquanto você revisa, atualize os detalhes e confira novamente antes de salvar. O histórico preserva as decisões e permite entender o que mudou.

### 3.8 Atualizar um relatório existente

Use a atualização quando receber novos arquivos da mesma produção. **Novo relatório** inicia uma rodada independente e pode repetir informações já importadas.

1. No campo **Importação**, selecione a opção **Atualizar** referente ao relatório desejado.
2. Escolha **Produção e faturamento completos** ou **Somente faturamento completo do hospital**.
3. No primeiro caso, envie as duas planilhas completas. No segundo, envie o faturamento completo atualizado; a produção salva será reutilizada.
4. Analise o diagnóstico e confirme a criação da versão.
5. Confira o resumo de visitas preservadas, novas visitas faturadas, pendentes e divergentes.
6. Revise o resultado e libere novamente quando estiver pronto para o médico.

Não envie somente as linhas novas do hospital. A atualização precisa preservar os registros já conciliados ou envolvidos em revisões. Se houver conflito com dados protegidos, confira os arquivos e a versão selecionada antes de tentar novamente.

Arquivos idênticos podem manter a versão existente. A atualização herda a identificação da produção e o hospital da base. Somente a versão mais recente permite novas revisões e registros financeiros.

### 3.9 Liberar o relatório para o médico

**Onde acessar:** **Conciliação de visitas**, seção **Liberação para o médico**.

1. Confira se está na versão mais recente.
2. Revise as visitas sem médico identificado e os vínculos com os cadastros.
3. Confira pendências, divergências e valores desconhecidos.
4. Registre uma observação de liberação, se necessário.
5. Quando solicitado, marque **Pendências, divergências e visitas sem médico conferidas**.
6. Clique em **Liberar para o médico** e confirme a situação da publicação.

É possível liberar um relatório com pendências depois de conferi-las e reconhecê-las expressamente. Pelo menos uma visita deve ter médico identificado; visitas sem vínculo seguro não são publicadas para um médico.

A liberação torna a cópia publicada disponível no portal. Alterações posteriores precisam de nova liberação para aparecerem ao médico. A observação administrativa da liberação não é uma mensagem enviada ao profissional.

### 3.10 Acompanhar a fila de cobrança

**Onde acessar:** menu **Fila de cobrança**.

A fila reúne visitas pendentes sem correspondência e sem candidatos no arquivo hospitalar. Visitas com ambiguidades ou registros ainda não faturados devem ser tratadas na revisão e não necessariamente aparecem nessa fila.

1. Selecione hospital e relatório.
2. Filtre por médico, dias desde a visita ou prioridade de contato.
3. Use **Baixar cobrança XLSX** se precisar preparar uma cobrança externa.
4. Faça o contato com o hospital pelo canal adotado pela organização.
5. Na visita, clique em **Acompanhar**.
6. Registre situação, data da cobrança, resposta, observações e próximo contato.
7. Clique em **Salvar acompanhamento**.

Para o mesmo acompanhamento em várias visitas, selecione as linhas e use **Registrar lote**. Confira a seleção antes de confirmar; os campos informados serão aplicados a todas as visitas escolhidas.

**Contato para hoje** e **Contatos atrasados** consideram a data do próximo contato, e não a idade da visita ou um vencimento financeiro. As visitas que saíram da fila podem continuar com o histórico disponível em **Cobranças acompanhadas fora da fila**.

Registrar **Enviada** documenta um contato realizado pelo operador. O sistema não envia a cobrança automaticamente nem altera o faturamento ao salvar o acompanhamento.

### 3.11 Registrar recebimentos

**Onde acessar:** menu **Recebimentos**.

Registre apenas dinheiro efetivamente recebido e conferido. A visita precisa estar faturada, com médico cadastrado e saldo confirmado. Recebimentos podem ser parciais e não podem ultrapassar o saldo disponível.

**Para uma visita**

1. Selecione hospital, relatório e médico.
2. Localize a visita e abra a ação de recebimento.
3. Informe data, valor recebido e observações, quando necessárias.
4. Clique em **Registrar recebimento**.
5. Confira o histórico e o saldo restante.

**Para um pagamento que abrange várias visitas**

1. Selecione um médico cadastrado e marque as visitas disponíveis.
2. Clique em **Registrar pagamento**.
3. Informe data, valor total recebido e uma referência que permita identificar o pagamento.
4. Distribua manualmente o valor entre as visitas. **Usar saldo** preenche somente a visita escolhida.
5. Confira se a diferença entre o total recebido e o distribuído é zero.
6. Confira médico, hospital, relatório, referência e valores e clique em **Confirmar pagamento**.

A data não pode ser futura. A referência não pode se repetir para o mesmo médico na mesma sequência de versões, inclusive depois de estorno. O sistema não consulta o banco para confirmar a transferência e não escolhe automaticamente como distribuir o dinheiro.

Se aparecer um aviso de saldo desatualizado, atualize e confira os registros antes de reenviar. Isso evita repetir um recebimento já salvo.

### 3.12 Consultar pagamentos e anexar comprovantes

1. Em **Recebimentos**, vá a **Pagamentos por referência**.
2. Filtre por referência, período do pagamento e situação.
3. Use **Ver pagamento** para consultar visitas vinculadas, valores, estornos e histórico.
4. Em **Comprovantes**, selecione o arquivo e clique em **Anexar comprovante**.

São aceitos PDF, PNG e JPEG de até 5 MB, com limite de dez documentos ativos por pagamento. Arquivos idênticos já ativos no mesmo pagamento são recusados.

Se anexar um arquivo incorreto, use a retirada com justificativa. O documento deixa de ficar disponível para download, mas o histórico é preservado. Anexar ou retirar um comprovante não registra nem estorna valores. Os comprovantes administrativos não são disponibilizados no portal do médico.

Os totais de **Pagamentos por referência** consideram os pagamentos distribuídos. Eles não incluem recebimentos individuais sem referência de pagamento comum.

### 3.13 Corrigir um recebimento com estorno

1. Abra o histórico financeiro da visita.
2. Localize e confira o lançamento incorreto.
3. Utilize a ação de estorno e informe uma justificativa clara.
4. Confira o evento de estorno e o novo saldo.
5. Se necessário, registre o recebimento correto. Para um novo pagamento distribuído, use uma nova referência identificável.

O estorno é integral para o lançamento escolhido e preserva o original. Estornar uma parte distribuída para uma visita não estorna automaticamente as demais visitas do pagamento. Os lançamentos não são editados nem apagados.

Se uma visita com recebimentos tiver médico, data, atendimento, código ou valores alterados, o financeiro pode exigir conferência e bloquear novos registros. Verifique a causa e o histórico antes de decidir qualquer estorno.

### 3.14 Responder solicitações dos médicos

**Onde acessar:** menu **Solicitações dos médicos**.

1. Filtre a situação **Abertas** e abra o pedido desejado.
2. Leia o motivo e confira médico, publicação e visita.
3. Consulte a conciliação e apure a informação.
4. Se for necessária uma correção, revise a visita na versão atual e libere novamente o relatório.
5. Use **Responder**, escreva uma resposta clara e envie.

A resposta precisa ter de 5 a 2.000 caracteres e encerra o pedido. Ela não pode ser sobrescrita. Solicitações de publicações anteriores continuam respondíveis.

Responder ao médico não altera automaticamente dados, valores ou recebimentos. Explique o resultado da apuração e, quando houver alteração, qual foi a correção realizada.

Na seção **Ciência das publicações atuais**, acompanhe quem já confirmou ciência. **Aguardando ciência** significa que essa ação ainda não foi registrada para a publicação atual.

### 3.15 Consultar indicadores e exportar

No **Dashboard**, selecione hospital, relatório e período. Examine visitas analisadas, pendências, divergências e valores. O período acompanha a data das visitas, não a data bancária do recebimento. Rótulos como **Pagas** e **Valor das pagas** se referem ao faturamento conciliado.

Use **Divergências** para localizar os casos que exigem conferência e abrir a conciliação correspondente. **Painel do médico** permite consultar o resumo do profissional selecionado no ambiente administrativo.

Em **Conciliação de visitas**, **Baixar relatório geral XLSX** exporta o relatório completo, inclusive informações administrativas. O filtro de médico na tela não transforma esse download geral em um arquivo individual. Confira o conteúdo e o destinatário antes de compartilhar.

Para gerar um demonstrativo individual:

1. Abra **Recebimentos** e selecione hospital, relatório e um médico cadastrado.
2. Confira se o relatório está liberado para esse médico.
3. Em **Demonstrativo do médico**, ajuste os períodos de visitas e recebimentos, quando necessário.
4. Clique em **Baixar PDF** e confira a identificação do destinatário.

Se houver uma versão nova em revisão, revise e libere essa versão antes de gerar seu demonstrativo pela tela administrativa. O PDF é um acompanhamento financeiro; não substitui comprovante bancário, recibo de quitação ou nota fiscal.

### 3.16 Reconhecer áreas demonstrativas

A seção **Layouts** da tela principal apresenta um quadro demonstrativo de mapeamento de colunas. Ela não deve ser tratada como confirmação de que qualquer planilha passou a ser compatível com a conciliação.

Para usar um novo modelo de arquivo, peça à equipe responsável que valide sua compatibilidade antes de realizar a importação operacional.

## 4 Guia do médico

### 4.1 Consultar seus relatórios

1. Entre com seu e-mail e senha.
2. Em **Meu painel**, selecione o **Hospital**.
3. Escolha o **Relatório** disponível.
4. Selecione o **Período das visitas do painel** que deseja consultar.
5. Confira os indicadores e a lista de pendências.

Seu portal mostra somente as informações vinculadas ao seu cadastro e liberadas pelo administrador. A ausência de um relatório não significa que não houve produção ou que o saldo é zero: pode faltar importação, identificação ou liberação.

### 4.2 Entender o painel e as pendências

Observe os campos **Faturado pelo hospital**, **Recebido**, **A receber**, **Aguardando faturamento**, **Valor em conferência** e **Visitas sem valor definido** conforme as definições da seção 2.

Em **Pendências da produção**, use os filtros **Situação** e **Atendimento** para encontrar uma visita. Leia o motivo antes de solicitar esclarecimento. Pendências e valores em conferência não devem ser somados ao saldo confirmado como se já fossem valores faturados.

### 4.3 Conferir o extrato financeiro

**Onde acessar:** seção **Extrato financeiro**, no seu portal.

1. Confira o hospital e o relatório selecionados.
2. Defina o período das visitas, se desejar restringir quais visitas serão consultadas.
3. Defina o período dos recebimentos e estornos, se desejar restringir os movimentos exibidos.
4. Clique em **Filtrar**.
5. Compare **Posição atual das visitas** com **Recebimentos e estornos do período**.
6. Use **Limpar filtros** para retornar à consulta sem os intervalos informados.

Os filtros do extrato são independentes do período mensal do painel. As datas inicial e final são incluídas na consulta.

O período das visitas seleciona as visitas e seus movimentos. O período dos recebimentos restringe os movimentos dessas visitas, mas não recalcula o saldo como ele era no passado: **Recebido acumulado** e **Saldo atual** continuam considerando todo o histórico autorizado.

Um estorno aparece na data em que foi registrado e pode deixar o líquido dos movimentos de um período negativo. Isso não significa, por si só, que o médico possui uma dívida. Valores não confirmados exigem esclarecimento com o administrador.

### 4.4 Baixar extrato e demonstrativo

1. Na seção **Extrato financeiro**, aplique os filtros desejados.
2. Use **Baixar extrato XLSX** para consultar os dados em planilha ou **Baixar demonstrativo PDF** para obter o documento de acompanhamento.
3. Confira nome, hospital, relatório e períodos no arquivo baixado.

O XLSX apresenta resumo, visitas e movimentos. O PDF e o XLSX seguem os filtros do extrato. Nenhum deles substitui um comprovante bancário ou confirma quitação automaticamente.

### 4.5 Confirmar ciência

1. Leia o relatório e suas informações.
2. Acesse **Conferência do relatório**, pelo atalho **Conferir relatório** ou pela própria seção.
3. Clique em **Confirmar ciência**.
4. Confira a confirmação exibida na tela.

Ciência registra que você tomou conhecimento da publicação. Não significa concordar com os valores, desistir de uma revisão ou confirmar recebimento. Uma nova publicação exige nova ciência.

### 4.6 Solicitar revisão de uma visita

1. Em **Conferência do relatório**, localize **Solicitar revisão de visita**.
2. Selecione a visita correta.
3. Descreva o motivo com clareza, entre 5 e 2.000 caracteres.
4. Clique em **Solicitar revisão**.
5. Acompanhe a resposta em **Minhas solicitações**. Use **Atualizar conferência** para recarregar.

**Exemplo de motivo:** “A visita do atendimento 12345, realizada em 10/09/2026, consta como pendente. Solicito verificar o faturamento enviado pelo hospital.”

Evite incluir dados de pacientes que não sejam necessários à apuração. Existe apenas um pedido aberto por visita e publicação para seu cadastro. Depois de respondido, é possível abrir outro pedido, se ainda houver necessidade.

Pedidos de publicações anteriores continuam identificados no histórico. A resposta não modifica automaticamente o relatório: uma correção precisa ser feita e liberada pelo administrador.

### 4.7 Consultar notificações

Abra o sino de notificações para consultar novas publicações e respostas. Use os filtros de todas ou não lidas e abra o aviso para acessar o item relacionado.

Marcar uma notificação como lida apenas organiza seus avisos. Para registrar ciência, utilize **Confirmar ciência** no relatório. As notificações acontecem dentro do sistema; não há envio automático por e-mail ou WhatsApp.

O administrador também recebe avisos de novos pedidos de revisão. A leitura é individual: a ação de um usuário não marca o aviso como lido para os demais.

## 5 Exemplos práticos

### 5.1 Uma visita faturada com recebimento parcial

Uma visita tem repasse confirmado de R$ 200,00 e situação **PAGA**. O administrador registra recebimento de R$ 120,00.

| Informação | Valor |
| --- | --- |
| Faturado | R$ 200,00 |
| Recebido | R$ 120,00 |
| A receber | R$ 80,00 |

A visita continua faturada; os R$ 80,00 representam o saldo financeiro restante. Se houver outras visitas pendentes com valores desconhecidos, elas não são convertidas em zero nem incorporadas a esse saldo confirmado.

### 5.2 Uma visita pendente que foi esclarecida

O médico identifica uma visita pendente e solicita revisão. O administrador apura o caso. Se o hospital ainda não faturou, mantém a pendência e informa a conclusão na resposta. Se encontrar dados que permitem corrigir a visita, registra a revisão e libera uma nova publicação.

O médico atualiza o relatório, lê a resposta e confere a publicação disponível. A resposta e a liberação são ações diferentes.

### 5.3 Um relatório atualizado depois da liberação

O relatório foi liberado para o médico. Dias depois, o administrador importa uma atualização do faturamento e cria outra versão.

O médico continua consultando a cópia já publicada enquanto o administrador revisa o novo resultado. Depois da nova liberação, o portal passa a mostrar a publicação atualizada e o médico pode registrar nova ciência.

## 6 Dúvidas e solução de problemas

### Não consigo entrar

Confira o endereço e o e-mail e verifique se a senha foi digitada corretamente. Depois de cinco falhas, aguarde 15 minutos. Se persistir, peça ao administrador que confira se o acesso está ativo e, se necessário, redefina sua senha.

### O sistema voltou para a tela de login

A sessão pode ter expirado por inatividade ou o acesso pode ter sido invalidado após uma alteração de senha ou desativação. Entre novamente. Se o acesso continuar indisponível, procure o administrador.

### Sou médico e não vejo meu relatório

Confira os filtros de hospital e relatório. Peça ao administrador que verifique o cadastro associado ao seu login, a identificação das visitas e a liberação. Uma visita sem identificação segura não é atribuída automaticamente a um médico.

### O administrador corrigiu os dados e continuo vendo o resultado anterior

Peça que ele confira se a nova versão foi liberada. Atualize a página depois da liberação. Revisar dados e publicar para o médico são etapas separadas.

### A visita está PAGA mas o dinheiro não entrou

**PAGA** significa faturamento identificado. Consulte **Recebido**, **A receber** e o extrato. Se o dinheiro já foi recebido e ainda não aparece, solicite ao administrador a conferência do registro financeiro.

### O total do painel é diferente dos movimentos do extrato

Confira relatório e períodos. O painel usa a data das visitas. O extrato também possui um filtro próprio para a data dos recebimentos e estornos. O saldo atual considera o histórico acumulado e pode diferir do líquido de movimentos de apenas um mês.

### Um botão está desabilitado

Confira os filtros e as informações obrigatórias. A operação pode exigir uma versão atual, médico cadastrado, relatório liberado ou visita com saldo confirmado. Leia a mensagem exibida antes de tentar novamente.

### O sistema informa que os dados foram alterados por outro usuário

Atualize os detalhes e compare as informações atuais com o que pretendia salvar. Refaça a operação apenas depois dessa conferência. Não repita cliques de confirmação enquanto aguarda uma resposta.

### Não consigo atualizar o faturamento

Confirme que escolheu a versão mais recente e enviou o faturamento completo. Confira se os arquivos mantêm a produção e os registros protegidos das versões anteriores. Se houver conflito, peça ajuda à equipe responsável em vez de criar várias rodadas independentes para contornar o aviso.

### Não consigo registrar um recebimento

Verifique se a visita está faturada, o médico está cadastrado, a data não é futura e o valor cabe no saldo. Em pagamentos distribuídos, confira a soma das parcelas e se a referência já foi utilizada. Avisos de financeiro em conferência precisam ser apurados antes do registro.

### Um comprovante foi anexado mas o saldo não mudou

Anexos apenas documentam um pagamento. Para atualizar o financeiro, é necessário registrar o recebimento correspondente. Não registre novamente um valor que já consta no histórico.

### Não consigo excluir um relatório

Relatórios com histórico financeiro ou de liberação possuem restrições de exclusão. Preserve a rastreabilidade e procure a equipe responsável para definir o tratamento adequado.

### Como pedir ajuda

Médicos devem procurar o administrador ou usar a solicitação de revisão quando a dúvida for sobre uma visita. Administradores devem acionar a equipe responsável pela implantação para problemas de funcionamento.

Informe a tela, a mensagem exibida, o hospital, o número do relatório e o que estava tentando fazer. Quando necessário, informe a linha da visita. Nunca envie sua senha; oculte dados de pacientes e informações financeiras de terceiros em capturas de tela.

## 7 Checklists de rotina

### Administrador antes de liberar um relatório

- [ ] Hospital e relatório corretos selecionados.
- [ ] Planilhas e diagnóstico conferidos.
- [ ] Médicos e vínculos com CRM/UF verificados.
- [ ] Visitas sem identificação segura examinadas.
- [ ] Pendências, divergências e valores desconhecidos conferidos.
- [ ] Revisões salvas e resultado final consultado.
- [ ] Liberação realizada e situação da publicação confirmada.

### Administrador na rotina financeira

- [ ] Pedidos de revisão e contatos agendados consultados.
- [ ] Recebimentos confrontados com a documentação disponível.
- [ ] Médico, relatório, data e referência conferidos antes de registrar.
- [ ] Distribuição de pagamentos igual ao total recebido.
- [ ] Histórico e saldo conferidos após cada registro ou estorno.
- [ ] Destinatário e conteúdo conferidos antes de compartilhar arquivos.

### Médico ao consultar uma publicação

- [ ] Hospital, relatório e período corretos selecionados.
- [ ] Faturado, recebido e saldo interpretados separadamente.
- [ ] Pendências e valores em conferência examinados.
- [ ] Filtros de visitas e de movimentos do extrato conferidos.
- [ ] Revisões solicitadas quando necessário.
- [ ] Ciência registrada para a publicação consultada.
- [ ] Saída do sistema realizada ao terminar em computador compartilhado.

## 8 Glossário

| Termo | Significado |
| --- | --- |
| Alias | Variação de nome que ajuda a identificar um médico nas planilhas |
| Atendimento | Identificador usado para localizar os registros relacionados a uma visita |
| Candidato hospitalar | Registro do hospital que pode corresponder à visita e precisa ser examinado |
| Ciência | Registro de que o médico tomou conhecimento de uma publicação |
| Competência | Período de referência de uma produção ou de um faturamento |
| Conciliação | Comparação da produção com os registros hospitalares |
| Estorno | Lançamento que reverte um recebimento preservando seu histórico |
| Linha da visita | Identificador da visita no relatório; em atualizações, pode diferir da posição na nova planilha |
| Publicação | Cópia do relatório liberada pelo administrador para consulta médica |
| Repasse | Valor atribuído ao médico no contexto da visita |
| Versão | Atualização de um relatório que preserva a relação com o anterior |

Este manual acompanha as funcionalidades disponíveis na data indicada. Ao alterar telas ou regras operacionais, atualize as orientações correspondentes antes de distribuí-lo novamente.
