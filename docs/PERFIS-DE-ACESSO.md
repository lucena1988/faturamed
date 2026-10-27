# Perfis de acesso FaturaMed

- **Funcionário FaturaMed (`OPERADOR`)**: cadastros, importações, conciliação, publicação, cobranças, recebimentos e respostas às solicitações médicas. Abre chamados, acompanha os chamados de funcionários e médicos da empresa e envia informações. Administra somente acessos de médicos (criação, ativação, desativação e redefinição de senha). Não administra contas de funcionários/técnicos, atendentes ou SLAs; não assume nem resolve chamados.
- **Médico (`MEDICO`)**: conta obrigatoriamente vinculada a um médico ativo. Consulta apenas seus relatórios publicados e dados financeiros; confirma ciência, solicita revisão e não acessa Help Desk, janela de suporte ou APIs de chamados.
- **Administrador técnico (`ADMIN`)**: operação completa, usuários e gestão do Service Desk. `DEV` permanece como perfil técnico legado, com os mesmos acessos administrativos. Novas contas técnicas são criadas como ADMIN.

As contas ADMIN e DEV existentes não são rebaixadas automaticamente. Para funcionários, crie contas individuais com o novo perfil em Acessos. Funcionários podem abrir Acessos, mas só enxergam e administram contas médicas; as demais contas ficam restritas à equipe técnica.

O Kanban pode ser consultado por funcionários, sem controles de gestão e sem arrastar cartões. Chamados particulares de contas técnicas não são exibidos ao funcionário, salvo os que ele próprio abriu. Médicos não consultam protocolos ou anexos de chamados. O solicitante pode confirmar ou reabrir o próprio chamado depois de resolvido pelo suporte; a avaliação também confirma o encerramento, conforme o fluxo existente.

A migração V22 registra criação, ativação, desativação e redefinição de senha em usuario_acesso_auditoria, com usuário afetado, operador e data, sem armazenar senhas no histórico.

Esta versão é destinada a uma única empresa, FaturaMed. Não implementa isolamento entre múltiplas empresas. As mensagens atuais do chamado são compartilhadas entre as pessoas autorizadas a consultá-lo; não há campo de nota interna privativa do suporte.
