-- Chat interno da equipe (fase 1: conversas 1:1)
create table chat_conversa (
    id bigserial primary key,
    tipo varchar(10) not null default 'DIRETA' check (tipo in ('DIRETA', 'GRUPO')),
    -- chave "menorId:maiorId" garante uma unica conversa direta por par de usuarios
    chave_direta varchar(50) unique,
    criado_em timestamptz not null default now(),
    ultima_mensagem_em timestamptz
);

create table chat_participante (
    conversa_id bigint not null references chat_conversa(id) on delete cascade,
    usuario_id bigint not null references usuario_acesso(id),
    ultima_leitura_id bigint not null default 0,
    primary key (conversa_id, usuario_id)
);
create index idx_chat_participante_usuario on chat_participante(usuario_id);

create table chat_mensagem (
    id bigserial primary key,
    conversa_id bigint not null references chat_conversa(id) on delete cascade,
    autor_id bigint not null references usuario_acesso(id),
    texto varchar(4000) not null,
    criado_em timestamptz not null default now()
);
create index idx_chat_mensagem_conversa on chat_mensagem(conversa_id, id desc);
