-- Conciliacao unica, etapa 4: ponte para o relatorio do medico. Ao gerar o relatorio de uma competencia, as
-- visitas passam a pertencer a um arquivo de producao no formato atual (arquivo_base_hospitalar + relatorio),
-- de onde seguem revisao, liberacao, portal do medico e recebimentos sem alteracao.
alter table producao_visita add column arquivo_relatorio_id bigint references arquivo_base_hospitalar(id) on delete set null;
alter table producao_visita add column linha_relatorio integer;
create index producao_visita_relatorio on producao_visita(arquivo_relatorio_id);

-- Excluir a planilha do hospital nao pode ser barrado pelo vinculo da conciliacao unica.
alter table producao_visita drop constraint producao_visita_hospital_visita_id_fkey;
alter table producao_visita add constraint producao_visita_hospital_visita_id_fkey
    foreign key (hospital_visita_id) references visita_base_hospitalar(id) on delete set null;
