-- Repeticoes hospitalares em analise que sao, na verdade, procedimentos diferentes
-- (mesmo atendimento, data e medico, mas codigo ou nome do procedimento distintos).
-- Mesma regra de BaseHospitalarService.mesmoProcedimento: com os dois codigos, decide o codigo;
-- sem codigo em algum deles, decide o nome. Equivale a decisao manual "E um procedimento distinto".
create temporary table exame_distinto on commit drop as
select v.id, v.anterior_id, a.usuario_id
from visita_base_hospitalar v
join visita_base_hospitalar o on o.id = v.anterior_id
join arquivo_base_hospitalar a on a.id = v.arquivo_id
where v.decisao = 'EM_ANALISE'
  and case
        when regexp_replace(coalesce(v.campos->>'cod produto', ''), '[^0-9A-Za-z]', '', 'g') <> ''
         and regexp_replace(coalesce(o.campos->>'cod produto', ''), '[^0-9A-Za-z]', '', 'g') <> ''
          then regexp_replace(v.campos->>'cod produto', '[^0-9A-Za-z]', '', 'g')
            <> regexp_replace(o.campos->>'cod produto', '[^0-9A-Za-z]', '', 'g')
        when lower(trim(coalesce(v.campos->>'produto', ''))) <> ''
         and lower(trim(coalesce(o.campos->>'produto', ''))) <> ''
          then lower(trim(v.campos->>'produto')) <> lower(trim(o.campos->>'produto'))
        else false
      end;

insert into revisao_base_hospitalar(visita_id, anterior_id, decisao, justificativa, evidencia, usuario_id)
select id, null, 'VISITA_DISTINTA',
       'Reclassificacao automatica: codigo ou nome do procedimento diferente da visita ' || anterior_id || ' (exames distintos no mesmo atendimento e data)',
       '', usuario_id
from exame_distinto;

update visita_base_hospitalar v set decisao = 'VISITA_DISTINTA', anterior_id = null
from exame_distinto e where v.id = e.id;
