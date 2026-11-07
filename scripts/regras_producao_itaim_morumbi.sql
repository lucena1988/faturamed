-- Cadastra as regras de valor de producao dos hospitais Sao Luiz Itaim e Sao Luiz Morumbi
-- (tabelas recebidas do cliente em 06/11/2026). Idempotente: apaga e recria apenas as regras
-- desses dois hospitais que ainda nao foram usadas em nenhum fechamento.
-- Requer a migracao V31 aplicada. Uso:
--   docker exec -i faturamed-postgres psql -U faturamed -d faturamed -v ON_ERROR_STOP=1 < scripts/regras_producao_itaim_morumbi.sql
-- Vigencia inicial assumida: 01/01/2026 (ajuste na tela se for outra).
-- Nao cadastrado: "Coordenacao teste ergometrico" (R$ 1.500/mes, Dr. Marcos Esteves) -> medico ainda nao existe no cadastro.

begin;

create temp table _h(chave text primary key, id bigint) on commit drop;

insert into hospital_cadastro(nome, nome_normalizado, cidade, uf)
select 'Hospital São Luiz Itaim', 'hospital sao luiz itaim', 'São Paulo', 'SP'
where not exists (select 1 from hospital_cadastro where nome_normalizado like '%itaim%');
insert into hospital_cadastro(nome, nome_normalizado, cidade, uf)
select 'Hospital São Luiz Morumbi', 'hospital sao luiz morumbi', 'São Paulo', 'SP'
where not exists (select 1 from hospital_cadastro where nome_normalizado like '%morumbi%');
insert into _h select 'ITAIM', min(id) from hospital_cadastro where nome_normalizado like '%itaim%';
insert into _h select 'MORUMBI', min(id) from hospital_cadastro where nome_normalizado like '%morumbi%';

delete from producao_regra_valor r using _h
where r.hospital_id = _h.id and not exists (select 1 from producao_fechamento_calculo c where c.regra_id = r.id);

-- regra(hospital, nome, tipo, valor, minimo, incremento, exames 'NOME:peso,...', faixas 'qtd:valor,...')
create or replace function pg_temp.regra(h text, nome text, tipo text, valor numeric, minimo int, incremento numeric, exames text, faixas text)
returns void language plpgsql as $$
declare rid bigint; e text; f text;
begin
  insert into producao_regra_valor(hospital_id, nome, tipo, valor, minimo_por_agenda, incremento, vigencia_inicio, observacao)
  values ((select id from _h where chave = h), nome, tipo, valor, minimo, incremento, date '2026-01-01', 'Tabela recebida em 06/11/2026')
  returning id into rid;
  foreach e in array string_to_array(exames, ',') loop
    insert into producao_regra_exame(regra_id, exame_id, peso)
    select rid, x.id, split_part(e, ':', 2)::int from producao_exame_tipo x where x.nome_normalizado = split_part(e, ':', 1);
    if not found then raise exception 'Exame % nao cadastrado', split_part(e, ':', 1); end if;
  end loop;
  if faixas is not null then
    foreach f in array string_to_array(faixas, ',') loop
      insert into producao_regra_faixa(regra_id, quantidade, valor) values (rid, split_part(f, ':', 1)::int, split_part(f, ':', 2)::numeric);
    end loop;
  end if;
end $$;

-- ===== ITAIM =====
select pg_temp.regra('ITAIM', 'AngioTC eletivo', 'POR_EXAME', 400.00, 1, 0, 'ANGIOTC ELETIVO:1', null);
select pg_temp.regra('ITAIM', 'RMC eletivo', 'POR_EXAME', 420.00, 1, 0, 'RMC ELETIVO:1', null);
select pg_temp.regra('ITAIM', 'AngioTC internado', 'POR_EXAME', 400.00, 1, 0, 'ANGIOTC INTERNADO:1', null);
select pg_temp.regra('ITAIM', 'RMC internado', 'POR_EXAME', 420.00, 1, 0, 'RMC INTERNADO:1', null);
-- planilha pula de "26" para "28": os valores seguem +106,40 por exame, entao 2.872,61 = 27 exames (ver observacao ao cliente)
select pg_temp.regra('ITAIM', 'Eco cardio', 'TABELA_AGENDA', 0, 0, 106.40,
  'ECOCARDIOGRAMA:1,ECO ESTRESSE:2,ECO TRANSESOFAGICO:2,ECO COM STRAIN:2',
  '11:1170.21,12:1276.61,13:1383.01,14:1489.41,15:1595.81,16:1702.21,17:1808.61,18:1915.01,19:2021.41,20:2127.81,21:2234.21,22:2340.61,23:2447.01,24:2553.41,25:2659.81,26:2766.21,27:2872.61,28:2979.01,29:3085.41,30:3191.81');
select pg_temp.regra('ITAIM', 'BIP', 'POR_EXAME', 350.00, 0, 0, 'BIP:1', null);
select pg_temp.regra('ITAIM', 'Intraoperatório', 'POR_EXAME', 1170.21, 0, 0, 'INTRAOPERATORIO:1', null);
select pg_temp.regra('ITAIM', 'Teste ergométrico', 'POR_AGENDA', 1120.00, 0, 0, 'TESTE ERGOMETRICO:1', null);
select pg_temp.regra('ITAIM', 'MAPA', 'POR_EXAME', 48.00, 0, 0, 'MAPA:1', null);
select pg_temp.regra('ITAIM', 'ECG', 'POR_EXAME', 17.00, 0, 0, 'ELETROCARDIOGRAMA:1', null);

-- ===== MORUMBI (valores brutos) =====
select pg_temp.regra('MORUMBI', 'AngioTC eletivo', 'POR_EXAME', 400.00, 1, 0, 'ANGIOTC ELETIVO:1', null);
select pg_temp.regra('MORUMBI', 'RMC eletivo', 'POR_EXAME', 420.00, 1, 0, 'RMC ELETIVO:1', null);
select pg_temp.regra('MORUMBI', 'AngioTC/RMC internado', 'POR_EXAME', 420.00, 1, 0, 'ANGIOTC INTERNADO:1,RMC INTERNADO:1', null);
-- pesos do eco assumidos iguais aos do Itaim (a planilha do Morumbi nao informa)
select pg_temp.regra('MORUMBI', 'Eco cardio', 'TABELA_AGENDA', 0, 0, 106.38,
  'ECOCARDIOGRAMA:1,ECO ESTRESSE:2,ECO TRANSESOFAGICO:2,ECO COM STRAIN:2',
  '10:1157.89,11:1263.16,12:1368.42,13:1473.68,14:1578.95,15:1684.21,16:1789.47,17:1894.74,18:2000.00,19:2105.26,20:2210.53,21:2316.91,22:2423.29,23:2529.67,24:2636.05,25:2742.43');
select pg_temp.regra('MORUMBI', 'BIP', 'POR_EXAME', 315.79, 0, 0, 'BIP:1', null);
select pg_temp.regra('MORUMBI', 'Intraoperatório', 'POR_EXAME', 1157.89, 0, 0, 'INTRAOPERATORIO:1', null);
select pg_temp.regra('MORUMBI', 'Teste ergométrico', 'POR_AGENDA', 1000.00, 0, 0, 'TESTE ERGOMETRICO:1', null);
select pg_temp.regra('MORUMBI', 'MAPA', 'POR_EXAME', 47.37, 0, 0, 'MAPA:1', null);
select pg_temp.regra('MORUMBI', 'ECG', 'POR_EXAME', 15.79, 0, 0, 'ELETROCARDIOGRAMA:1', null);

select h.nome hospital, count(r.id) regras from hospital_cadastro h join _h on _h.id = h.id
left join producao_regra_valor r on r.hospital_id = h.id group by h.nome order by h.nome;

commit;
