alter table arquivo_base_hospitalar add column quantidade_registros integer;

update arquivo_base_hospitalar a set quantidade_registros = (
    select count(*) from visita_base_hospitalar v
    where v.arquivo_id = a.id or v.arquivo_atual_id = a.id
) where a.tipo = 'HOSPITAL';
