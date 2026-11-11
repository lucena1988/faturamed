-- FaturaChat: pessoas mencionadas com @ em cada mensagem (geram alerta mesmo com a conversa silenciada)
create table chat_mencao (
    mensagem_id bigint not null references chat_mensagem(id) on delete cascade,
    usuario_id bigint not null references usuario_acesso(id) on delete cascade,
    primary key (mensagem_id, usuario_id)
);
create index idx_chat_mencao_usuario on chat_mencao(usuario_id);
