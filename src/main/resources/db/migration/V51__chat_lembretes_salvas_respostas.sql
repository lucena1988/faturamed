-- FaturaChat: lembretes em mensagens, mensagens salvas e respostas rapidas (tudo por pessoa).
create table chat_lembrete (
    id bigserial primary key,
    usuario_id bigint not null references usuario_acesso(id) on delete cascade,
    mensagem_id bigint not null references chat_mensagem(id) on delete cascade,
    quando timestamp with time zone not null,
    criado_em timestamp with time zone not null default now(),
    entregue_em timestamp with time zone
);
create index chat_lembrete_pendente on chat_lembrete(quando) where entregue_em is null;

create table chat_mensagem_salva (
    usuario_id bigint not null references usuario_acesso(id) on delete cascade,
    mensagem_id bigint not null references chat_mensagem(id) on delete cascade,
    salvo_em timestamp with time zone not null default now(),
    primary key (usuario_id, mensagem_id)
);

create table chat_resposta_rapida (
    id bigserial primary key,
    usuario_id bigint not null references usuario_acesso(id) on delete cascade,
    atalho varchar(30) not null,
    texto varchar(1000) not null,
    criado_em timestamp with time zone not null default now(),
    unique (usuario_id, atalho)
);
