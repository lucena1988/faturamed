-- Exclusao definitiva de relatorio (com todas as versoes), confirmada com a senha de quem excluiu.
-- Sem chaves estrangeiras: o registro sobrevive aos dados apagados.
create table relatorio_exclusao_auditoria (
 id bigserial primary key,
 relatorio_raiz_id bigint not null,
 relatorios varchar(500) not null,
 hospital varchar(180) not null,
 arquivo_medico varchar(500),
 medico_producao_id bigint,
 empresa_id bigint,
 publicacoes integer not null,
 solicitacoes integer not null,
 recebimentos integer not null,
 usuario_id bigint not null,
 responsavel varchar(500) not null,
 criado_em timestamptz not null default now()
);
