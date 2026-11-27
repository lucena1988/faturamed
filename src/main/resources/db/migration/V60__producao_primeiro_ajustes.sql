-- Conciliacao unica: decisao sobre repeticoes em analise e correcao manual da competencia, com historico.
-- DUPLICADA tira a visita da conciliacao e dos relatorios; a visita continua rastreavel.
alter table producao_visita drop constraint producao_visita_decisao_check;
alter table producao_visita add constraint producao_visita_decisao_check check (decisao in ('ORIGINAL','EM_ANALISE','DUPLICADA'));
alter table producao_visita drop constraint producao_visita_situacao_check;
alter table producao_visita add constraint producao_visita_situacao_check check (situacao in ('PAGA','PENDENTE','DIVERGENTE','EM_ANALISE','DUPLICADA'));

create table producao_visita_historico (
    id bigserial primary key,
    visita_id bigint not null references producao_visita(id),
    acao varchar(30) not null check (acao in ('CORRIGIR_COMPETENCIA','RESTAURAR_COMPETENCIA','VISITA_DISTINTA','DUPLICADA')),
    antes varchar(120) not null,
    depois varchar(120) not null,
    justificativa varchar(2000) not null,
    usuario_id bigint not null references usuario_acesso(id),
    responsavel varchar(500) not null,
    criado_em timestamptz not null default now()
);
create index producao_visita_historico_visita on producao_visita_historico(visita_id, id);
