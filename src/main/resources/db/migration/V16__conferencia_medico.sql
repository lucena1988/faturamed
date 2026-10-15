create table ciencia_relatorio (
    liberacao_id bigint not null references liberacao_relatorio(id),
    medico_id bigint not null references medico_cadastro(id),
    usuario_id bigint not null,
    criado_em timestamptz not null default now(),
    primary key (liberacao_id, medico_id)
);

create table solicitacao_medico (
    id bigserial primary key,
    liberacao_id bigint not null references liberacao_relatorio(id),
    medico_id bigint not null references medico_cadastro(id),
    linha integer not null,
    motivo varchar(2000) not null,
    usuario_id bigint not null,
    criado_em timestamptz not null default now(),
    resposta varchar(2000),
    respondido_por bigint,
    respondido_em timestamptz,
    check ((resposta is null and respondido_por is null and respondido_em is null)
        or (resposta is not null and respondido_por is not null and respondido_em is not null))
);
create unique index solicitacao_medico_aberta on solicitacao_medico(liberacao_id, medico_id, linha) where resposta is null;
create index solicitacao_medico_fila on solicitacao_medico(criado_em desc, id desc);
