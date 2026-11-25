-- Conciliacao unica: tipo do procedimento na producao (visita, exame, consulta, outro), como na planilha do hospital.
-- Em producao_arquivo guarda o que o operador informou (null = automatico por linha); em producao_visita, o tipo de cada
-- linha (null = sem codigo nem nome de procedimento para classificar).
alter table producao_arquivo add column tipo_procedimento varchar(20)
    check (tipo_procedimento in ('VISITA','EXAME','CONSULTA','OUTRO'));
alter table producao_visita add column tipo_procedimento varchar(20)
    check (tipo_procedimento in ('VISITA','EXAME','CONSULTA','OUTRO'));
