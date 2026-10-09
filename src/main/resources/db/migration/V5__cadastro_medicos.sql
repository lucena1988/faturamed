create table medico_cadastro (
    id bigserial primary key,
    nome varchar(180) not null,
    crm varchar(20) not null,
    uf varchar(2) not null,
    ativo boolean not null default true,
    versao bigint not null default 0,
    unique (crm, uf)
);
create table medico_alias (
    medico_id bigint not null references medico_cadastro(id),
    nome varchar(180) not null,
    normalizado varchar(180) not null unique,
    primary key (medico_id, normalizado)
);
