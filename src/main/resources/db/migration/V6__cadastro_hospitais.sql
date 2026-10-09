create table hospital_cadastro (
    id bigserial primary key,
    nome varchar(180) not null,
    nome_normalizado varchar(180) not null unique,
    documento varchar(30),
    documento_normalizado varchar(30) unique,
    cidade varchar(120) not null default '',
    uf varchar(2) not null default '',
    ativo boolean not null default true,
    versao bigint not null default 0
);
