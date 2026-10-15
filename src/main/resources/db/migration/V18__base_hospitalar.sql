alter table conciliacao_visitas add column base_hospitalar boolean not null default false;

create table arquivo_base_hospitalar (
    id bigserial primary key,
    hospital_id bigint not null references hospital_cadastro(id),
    tipo varchar(10) not null check (tipo in ('HOSPITAL','PRODUCAO')),
    medico_id bigint references medico_cadastro(id),
    competencia varchar(7) not null,
    nome varchar(255) not null,
    hash varchar(64) not null,
    conteudo bytea not null,
    usuario_id bigint not null references usuario_acesso(id),
    criado_em timestamp not null default current_timestamp,
    relatorio_id bigint unique references conciliacao_visitas(id)
);
create unique index arquivo_base_hash on arquivo_base_hospitalar(hospital_id,tipo,competencia,coalesce(medico_id,0),hash);

create table visita_base_hospitalar (
    id serial primary key,
    arquivo_id bigint not null references arquivo_base_hospitalar(id),
    hospital_id bigint not null references hospital_cadastro(id),
    linha integer not null,
    atendimento text not null,
    data_visita date not null,
    medico_nome text not null,
    campos jsonb not null,
    anterior_id integer references visita_base_hospitalar(id),
    decisao varchar(40) not null check (decisao in ('ORIGINAL','EM_ANALISE','VISITA_DISTINTA','DUPLICADO_PAGO_OUTRO_MES')),
    unique (arquivo_id, linha)
);
create index visita_base_busca on visita_base_hospitalar(hospital_id, atendimento, data_visita);

create table revisao_base_hospitalar (
    id bigserial primary key,
    visita_id integer not null references visita_base_hospitalar(id),
    anterior_id integer references visita_base_hospitalar(id),
    decisao varchar(40) not null,
    justificativa varchar(2000) not null,
    evidencia varchar(2000) not null,
    usuario_id bigint not null references usuario_acesso(id),
    criado_em timestamp not null default current_timestamp
);

create table vinculo_base_hospitalar (
    visita_id integer primary key references visita_base_hospitalar(id),
    relatorio_id bigint not null references conciliacao_visitas(id),
    linha_producao integer not null,
    unique (relatorio_id, linha_producao)
);

create table reserva_base_hospitalar (
    visita_id integer primary key references visita_base_hospitalar(id),
    relatorio_raiz_id bigint not null references conciliacao_visitas(id),
    linha_producao integer not null
);
