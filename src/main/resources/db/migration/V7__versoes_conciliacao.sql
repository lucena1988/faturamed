alter table conciliacao_visitas add column anterior_id bigint references conciliacao_visitas(id);
alter table conciliacao_visitas add column numero_versao integer not null default 1;
alter table conciliacao_visitas add column hash_producao varchar(64);
alter table conciliacao_visitas add column hash_hospital varchar(64);
alter table conciliacao_visitas add column linhas_producao jsonb;
create unique index uk_conciliacao_sucessora on conciliacao_visitas(anterior_id) where anterior_id is not null;
