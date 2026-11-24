-- Registro das planilhas do hospital excluidas (ex.: importadas na competencia errada).
create table arquivo_base_exclusao_auditoria (
    id bigserial primary key,
    arquivo_id bigint not null,
    hospital_id bigint not null,
    competencia varchar(7) not null,
    nome text not null,
    procedimentos integer not null,
    usuario_id bigint not null references usuario_acesso(id),
    responsavel text not null,
    criado_em timestamp not null default current_timestamp
);
