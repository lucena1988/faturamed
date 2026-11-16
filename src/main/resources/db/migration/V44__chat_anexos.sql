-- Chat interno: anexos (imagens, PDFs, documentos e planilhas) nas mensagens
create table chat_anexo (
    id bigserial primary key,
    mensagem_id bigint not null references chat_mensagem(id) on delete cascade,
    nome varchar(200) not null,
    tipo varchar(120) not null,
    tamanho integer not null check (tamanho > 0),
    conteudo bytea not null,
    criado_em timestamptz not null default now(),
    check (octet_length(conteudo) = tamanho)
);
create index idx_chat_anexo_mensagem on chat_anexo(mensagem_id);
