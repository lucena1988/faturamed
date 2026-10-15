create table cobranca_contato (
    id bigserial primary key,
    relatorio_raiz_id bigint not null references conciliacao_visitas(id) on delete cascade,
    relatorio_id bigint not null references conciliacao_visitas(id) on delete cascade,
    linha_medico integer not null,
    situacao varchar(30) not null check (situacao in ('NAO_COBRADA','ENVIADA','AGUARDANDO_RETORNO','RETORNO_RECEBIDO')),
    data_cobranca date,
    resposta text not null default '',
    observacoes text not null default '',
    proximo_contato date,
    responsavel varchar(500) not null,
    usuario_id bigint not null,
    criado_em timestamptz not null default now()
);
create index idx_cobranca_visita on cobranca_contato(relatorio_raiz_id,linha_medico,id desc);
