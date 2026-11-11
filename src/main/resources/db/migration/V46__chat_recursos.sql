-- Chat interno: responder, editar/apagar, reacoes, mensagem fixada, grupos oficiais, status e retencao (LGPD)
alter table chat_mensagem add column responde_a bigint references chat_mensagem(id) on delete set null;
alter table chat_mensagem add column editada_em timestamptz;
alter table chat_mensagem add column apagada_em timestamptz;

create table chat_reacao (
    mensagem_id bigint not null references chat_mensagem(id) on delete cascade,
    usuario_id bigint not null references usuario_acesso(id) on delete cascade,
    emoji varchar(16) not null,
    criado_em timestamptz not null default now(),
    primary key (mensagem_id, usuario_id, emoji)
);

alter table chat_conversa add column fixada_mensagem_id bigint references chat_mensagem(id) on delete set null;
alter table chat_conversa add column oficial boolean not null default false;

create table chat_status (
    usuario_id bigint primary key references usuario_acesso(id) on delete cascade,
    status varchar(12) not null default 'DISPONIVEL' check (status in ('DISPONIVEL','OCUPADO','REUNIAO','AUSENTE')),
    recado varchar(80),
    atualizado_em timestamptz not null default now()
);

-- configuracao unica do chat; retencao_meses null = guardar sem prazo
create table chat_configuracao (
    id integer primary key default 1 check (id = 1),
    retencao_meses integer check (retencao_meses is null or retencao_meses between 1 and 120),
    atualizado_por bigint references usuario_acesso(id),
    atualizado_em timestamptz not null default now()
);
insert into chat_configuracao(id) values (1);

-- "Robo FaturaMed": autor das mensagens automaticas do sistema no chat (conta inativa, nao faz login)
insert into usuario_acesso(email, nome, senha_hash, perfil, ativo)
values ('robo@faturamed.sistema', 'Robô FaturaMed', '!sem-login', 'ADMIN', false)
on conflict (email) do nothing;
