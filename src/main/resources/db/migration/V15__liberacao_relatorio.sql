create table liberacao_relatorio (
 id bigserial primary key,
 relatorio_id bigint not null references conciliacao_visitas(id),
 relatorio_raiz_id bigint not null references conciliacao_visitas(id),
 versao_revisao bigint not null,
 hospital varchar(180) not null,
 dados jsonb not null,
 observacao varchar(2000) not null,
 usuario_id bigint not null,
 responsavel varchar(500) not null,
 criado_em timestamptz not null default now(),
 unique (relatorio_id,versao_revisao)
);
create index liberacao_relatorio_cadeia on liberacao_relatorio(relatorio_raiz_id,id desc);
