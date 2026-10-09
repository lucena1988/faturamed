create table catalogo_procedimento (
    id bigserial primary key,
    tabela varchar(20) not null check (tabela in ('TUSS', 'AMB_92')),
    versao varchar(80) not null,
    fonte varchar(1000) not null,
    consultado_em date not null,
    criado_em timestamp not null default current_timestamp,
    unique (tabela, versao)
);

create table procedimento_referencia (
    id bigserial primary key,
    catalogo_id bigint not null references catalogo_procedimento(id),
    codigo varchar(40) not null,
    descricao varchar(2000) not null,
    inicio_vigencia date,
    fim_vigencia date,
    unique (catalogo_id, codigo),
    check (fim_vigencia is null or inicio_vigencia is null or fim_vigencia >= inicio_vigencia)
);

create index idx_procedimento_referencia_codigo on procedimento_referencia(codigo);
