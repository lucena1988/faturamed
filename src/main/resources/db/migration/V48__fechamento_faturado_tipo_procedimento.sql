-- Fechamento Faturado: a base hospitalar passa a aceitar qualquer procedimento.
-- As linhas existentes eram todas visitas; o historico fica preservado com tipo VISITA.
alter table visita_base_hospitalar add column tipo_procedimento varchar(20) not null default 'VISITA'
    check (tipo_procedimento in ('VISITA','EXAME','CONSULTA','OUTRO'));
create index visita_base_tipo on visita_base_hospitalar(hospital_id, tipo_procedimento);
