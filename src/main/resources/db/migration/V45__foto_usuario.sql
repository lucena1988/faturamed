-- Foto de perfil de cada login (exibida no menu do usuario e no chat da equipe)
create table usuario_foto (
    usuario_id bigint primary key references usuario_acesso(id) on delete cascade,
    tipo varchar(40) not null,
    conteudo bytea not null,
    atualizado_em timestamptz not null default now(),
    check (octet_length(conteudo) between 1 and 1048576)
);
