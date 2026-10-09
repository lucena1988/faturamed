create table usuario_acesso (
    id bigserial primary key,
    email varchar(180) not null unique,
    nome varchar(180) not null,
    senha_hash varchar(100) not null,
    perfil varchar(10) not null check (perfil in ('ADMIN', 'MEDICO')),
    medico_id bigint references medico_cadastro(id),
    ativo boolean not null default true,
    versao bigint not null default 0,
    falhas integer not null default 0,
    bloqueado_ate timestamptz,
    criado_em timestamptz not null default now(),
    check ((perfil = 'MEDICO' and medico_id is not null) or (perfil = 'ADMIN' and medico_id is null))
);
create unique index usuario_medico_unico on usuario_acesso(medico_id) where medico_id is not null;
