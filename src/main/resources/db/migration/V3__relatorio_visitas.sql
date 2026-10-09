create table conciliacao_visitas (
    id bigserial primary key,
    criado_em timestamp not null default current_timestamp,
    relatorio jsonb not null
);
