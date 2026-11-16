-- FaturaChat: status de acompanhamento em uma mensagem (vira um mini-kanban da conversa).
create table chat_mensagem_status (
    mensagem_id bigint primary key references chat_mensagem(id) on delete cascade,
    status varchar(20) not null check (status in ('PENDENTE','EM_ANALISE','RESOLVIDO')),
    responsavel_id bigint references usuario_acesso(id),
    alterado_por bigint not null references usuario_acesso(id),
    alterado_em timestamp with time zone not null default now()
);
create index chat_mensagem_status_aberto on chat_mensagem_status(status) where status <> 'RESOLVIDO';
