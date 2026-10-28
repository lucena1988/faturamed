alter table visita_base_hospitalar drop constraint visita_base_hospitalar_decisao_check;
alter table visita_base_hospitalar add constraint visita_base_hospitalar_decisao_check
    check (decisao in ('ORIGINAL','EM_ANALISE','VISITA_DISTINTA','DUPLICADO_PAGO_OUTRO_MES','DUPLICADO_MESMA_COMPETENCIA'));
