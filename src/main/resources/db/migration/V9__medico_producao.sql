alter table conciliacao_visitas add column medico_producao_id bigint references medico_cadastro(id);
