-- Chat interno, fase 2: conversas em grupo
alter table chat_conversa add column nome varchar(80);
alter table chat_conversa add column criado_por bigint references usuario_acesso(id);
alter table chat_conversa add constraint ck_chat_conversa_grupo_nome check (tipo <> 'GRUPO' or nome is not null);
