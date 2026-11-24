-- Tipo informado na importacao (null = classificacao automatica por linha).
alter table arquivo_base_hospitalar add column tipo_procedimento varchar(20)
    check (tipo_procedimento in ('VISITA','EXAME','CONSULTA','OUTRO'));
