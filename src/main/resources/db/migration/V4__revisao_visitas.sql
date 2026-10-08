create table revisao_visita (
    id bigserial primary key,
    conciliacao_id bigint not null references conciliacao_visitas(id),
    linha_medico integer not null,
    acao varchar(40) not null,
    responsavel varchar(180) not null,
    justificativa varchar(2000) not null,
    antes jsonb not null,
    depois jsonb not null,
    criado_em timestamptz not null default current_timestamp
);

create index idx_revisao_visita_conciliacao on revisao_visita(conciliacao_id, id);
