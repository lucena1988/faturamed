-- Historico de acoes nas folhas de producao e nos fechamentos (quem fez o que e quando).
create table producao_historico (
    id bigserial primary key,
    entidade varchar(12) not null check (entidade in ('LOTE','FECHAMENTO')),
    entidade_id bigint not null,
    acao varchar(40) not null,
    descricao varchar(300) not null default '',
    usuario varchar(180),
    quando timestamp not null default now()
);
create index idx_producao_historico on producao_historico(entidade, entidade_id, quando);

-- registros existentes ganham ao menos o evento de criacao
insert into producao_historico(entidade, entidade_id, acao, descricao, usuario, quando)
select 'LOTE', id, 'CRIADA', 'Folha criada', criado_por, criado_em from producao_lote;
insert into producao_historico(entidade, entidade_id, acao, descricao, usuario, quando)
select 'FECHAMENTO', id, 'GERADO', 'Fechamento gerado e enviado ao hospital', criado_por, criado_em from producao_fechamento;
