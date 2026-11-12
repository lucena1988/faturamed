-- Remove a massa de teste criada por massa-teste-exames.sql (somente o que esta no arquivo "MASSA-TESTE-EXAMES").
begin;
delete from vinculo_base_hospitalar where visita_id in (
    select v.id from visita_base_hospitalar v join arquivo_base_hospitalar a on a.id = v.arquivo_id where a.nome = 'MASSA-TESTE-EXAMES');
delete from visita_base_hospitalar where arquivo_id in (select id from arquivo_base_hospitalar where nome = 'MASSA-TESTE-EXAMES');
delete from arquivo_base_hospitalar where nome = 'MASSA-TESTE-EXAMES';
commit;
