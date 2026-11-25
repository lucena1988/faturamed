-- Conciliacao unica, etapa 2: vinculo entre a visita da producao e o registro do hospital, em qualquer ordem
-- de importacao. Uma visita hospitalar atende no maximo uma visita da producao.
alter table producao_visita add column situacao varchar(20) not null default 'PENDENTE'
    check (situacao in ('PAGA','PENDENTE','DIVERGENTE','EM_ANALISE'));
alter table producao_visita add column motivo text not null default 'Aguardando registro do hospital';
alter table producao_visita add column hospital_visita_id integer references visita_base_hospitalar(id);
alter table producao_visita add column medico_inferido boolean not null default false;
alter table producao_visita add column conciliado_em timestamp;

update producao_visita set situacao='EM_ANALISE', motivo='Repeticao de visita ja importada em outro arquivo; aguarda decisao'
 where decisao='EM_ANALISE';

create unique index producao_visita_hospital on producao_visita(hospital_visita_id) where hospital_visita_id is not null;
