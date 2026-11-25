-- Conciliacao unica, etapa 1: a producao medica passa a ser a base. Cada linha vira uma visita com ID
-- permanente e competencia derivada da data do atendimento; o hospital pode chegar antes ou depois.
-- Tabelas separadas do fluxo em duas etapas, que continua funcionando sem alteracao.
create table producao_arquivo (
    id bigserial primary key,
    hospital_id bigint not null references hospital_cadastro(id),
    medico_id bigint references medico_cadastro(id),
    nome varchar(255) not null,
    hash varchar(64) not null,
    conteudo bytea not null,
    usuario_id bigint not null references usuario_acesso(id),
    criado_em timestamp not null default current_timestamp
);
create unique index producao_arquivo_hash on producao_arquivo(hospital_id, coalesce(medico_id,0), hash);

create table producao_visita (
    id bigserial primary key,
    arquivo_id bigint not null references producao_arquivo(id),
    hospital_id bigint not null references hospital_cadastro(id),
    linha integer not null,
    atendimento text not null,
    data_visita date not null,
    medico_id bigint references medico_cadastro(id),
    medico_nome text not null default '',
    campos jsonb not null,
    competencia varchar(7) not null check (competencia ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    competencia_manual varchar(7) check (competencia_manual ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    anterior_id bigint references producao_visita(id),
    decisao varchar(20) not null default 'ORIGINAL' check (decisao in ('ORIGINAL','EM_ANALISE')),
    criado_em timestamp not null default current_timestamp,
    unique (arquivo_id, linha)
);
create index producao_visita_busca on producao_visita(hospital_id, atendimento, data_visita);
create index producao_visita_competencia on producao_visita(hospital_id, competencia);
