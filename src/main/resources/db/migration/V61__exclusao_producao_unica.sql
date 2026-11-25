-- Registro das importacoes de producao da conciliacao unica que foram excluidas (ex.: planilha enviada por engano).
create table producao_arquivo_exclusao_auditoria (
    id bigserial primary key,
    arquivo_id bigint not null,
    hospital_id bigint not null,
    nome text not null,
    visitas integer not null,
    repeticoes_promovidas integer not null,
    usuario_id bigint not null references usuario_acesso(id),
    responsavel text not null,
    criado_em timestamp not null default current_timestamp
);
