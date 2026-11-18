-- Foto dos grupos do FaturaChat (grupo comum: qualquer participante troca; grupo oficial: so o ADMIN)
create table chat_grupo_foto (
    conversa_id bigint primary key references chat_conversa(id) on delete cascade,
    tipo varchar(40) not null,
    conteudo bytea not null,
    atualizado_em timestamptz not null default now(),
    check (octet_length(conteudo) between 1 and 1048576)
);
