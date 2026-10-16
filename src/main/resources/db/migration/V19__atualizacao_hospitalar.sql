alter table arquivo_base_hospitalar add column modo_hospital varchar(12) not null default 'NOVO'
    check (modo_hospital in ('NOVO','ATUALIZAR'));
alter table visita_base_hospitalar add column arquivo_atual_id bigint references arquivo_base_hospitalar(id);
alter table visita_base_hospitalar add column linha_atual integer;
update visita_base_hospitalar set arquivo_atual_id=arquivo_id,linha_atual=linha;
create table atualizacao_visita_hospitalar (
    id bigserial primary key,
    visita_id integer not null references visita_base_hospitalar(id),
    arquivo_id bigint not null references arquivo_base_hospitalar(id),
    linha integer not null,
    usuario_id bigint not null references usuario_acesso(id),
    antes jsonb not null,
    depois jsonb not null,
    criado_em timestamp not null default current_timestamp,
    unique (arquivo_id,linha)
);
create index atualizacao_hospital_visita on atualizacao_visita_hospitalar(visita_id,id);
