-- Portal do Hospital: acesso do hospital para conferir os fechamentos de producao,
-- contestar etiquetas (sem travar o processo) e liberar para emissao da NF.

-- 1) perfil HOSPITAL ligado a um hospital do cadastro
alter table usuario_acesso add column hospital_id bigint references hospital_cadastro(id);
alter table usuario_acesso drop constraint usuario_acesso_perfil_check;
alter table usuario_acesso add constraint usuario_acesso_perfil_check check (perfil in ('ADMIN','DEV','MEDICO','OPERADOR','EMPRESA','HOSPITAL'));
alter table usuario_acesso drop constraint if exists usuario_acesso_check;
alter table usuario_acesso add constraint usuario_acesso_vinculo_check check (
    (perfil='MEDICO' and medico_id is not null and empresa_id is null and hospital_id is null)
 or (perfil='EMPRESA' and empresa_id is not null and medico_id is null and hospital_id is null)
 or (perfil='HOSPITAL' and hospital_id is not null and medico_id is null and empresa_id is null)
 or (perfil in ('ADMIN','DEV','OPERADOR') and medico_id is null and empresa_id is null and hospital_id is null));

-- 2) conferencia do hospital no fechamento
alter table producao_fechamento add column visto_hospital_em timestamp;
alter table producao_fechamento add column valor_enviado numeric(14,2);
update producao_fechamento set valor_enviado = total;

-- conferencia lote a lote dentro do fechamento
create table producao_fechamento_lote (
    fechamento_id bigint not null references producao_fechamento(id) on delete cascade,
    lote_id bigint not null references producao_lote(id),
    conferido_em timestamp,
    conferido_por varchar(180),
    primary key (fechamento_id, lote_id)
);
insert into producao_fechamento_lote(fechamento_id, lote_id)
select distinct fi.fechamento_id, i.lote_id from producao_fechamento_item fi join producao_item i on i.id = fi.item_id;

-- 3) etiquetas contestadas pelo hospital
--    PENDENTE: contestada no fechamento ainda em conferencia
--    EM_ANALISE: fechamento aprovado sem ela; aguarda a FaturaMed decidir
--    REINCLUIR: FaturaMed discordou; volta a ficar disponivel para um proximo fechamento
--    ACEITA: FaturaMed concordou; etiqueta nao sera faturada
alter table producao_item add column contestacao varchar(12) check (contestacao in ('PENDENTE','EM_ANALISE','REINCLUIR','ACEITA'));
alter table producao_item add column contestacao_motivo varchar(500) not null default '';
alter table producao_item add column contestacao_por varchar(180);
alter table producao_item add column contestacao_em timestamp;
alter table producao_item add column contestacao_resposta varchar(500) not null default '';

-- a etiqueta pertence ao fechamento enquanto estiver "INCLUIDA"; as contestadas saem dele na aprovacao
alter table producao_fechamento_item add column situacao varchar(10) not null default 'INCLUIDA' check (situacao in ('INCLUIDA','RETIRADA'));
alter table producao_fechamento_item drop constraint producao_fechamento_item_item_id_key;
create unique index producao_fechamento_item_ativo on producao_fechamento_item(item_id) where situacao = 'INCLUIDA';
