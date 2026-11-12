-- MASSA DE TESTE do Fechamento Faturado (exames, consultas, visitas e outros) para validar o card da tela de Inicio.
-- Uso local apenas. Tudo fica marcado pelo arquivo "MASSA-TESTE-EXAMES"; para remover rode massa-teste-exames-remover.sql.
-- Situacoes geradas: ~55% conciliado (vinculo ficticio), ~10% divergente (EM_ANALISE), resto aguardando producao.
begin;

-- um arquivo de base hospitalar por hospital cadastrado
insert into arquivo_base_hospitalar(hospital_id, tipo, competencia, nome, hash, conteudo, usuario_id, quantidade_registros)
select h.id, 'HOSPITAL', to_char(current_date, 'YYYY-MM'), 'MASSA-TESTE-EXAMES', 'massa-teste-exames-' || h.id, '\x00'::bytea,
       (select id from usuario_acesso where perfil = 'ADMIN' order by id limit 1), 0
from hospital_cadastro h
where not exists (select 1 from arquivo_base_hospitalar a where a.hospital_id = h.id and a.nome = 'MASSA-TESTE-EXAMES');

with procs(codigo, produto, tipo, valor, peso) as (values
    ('40101010', 'ECG convencional de até 12 derivações', 'EXAME', 45.00, 6),
    ('40901777', 'Ecocardiograma transtorácico tridimensional', 'EXAME', 380.00, 4),
    ('40304361', 'Hemograma com contagem de plaquetas', 'EXAME', 18.50, 6),
    ('41001320', 'Tomossíntese digital mamária', 'EXAME', 290.00, 2),
    ('40901831', 'Elastografia da próstata', 'EXAME', 210.00, 2),
    ('10101012', 'Consulta em consultório', 'CONSULTA', 150.00, 4),
    ('10102019', 'Visita hospitalar (paciente internado)', 'VISITA', 103.84, 5),
    ('60000775', 'Taxa de sala de procedimento', 'OUTRO', 80.00, 1)
), sorteio as (
    select p.*, row_number() over () as n from procs p, generate_series(1, p.peso)
), medicos as (
    select coalesce(nullif(array_agg(nome order by id), '{}'), array['Dra. Teste Massa']) as nomes from medico_cadastro
), linhas as (
    select a.id as arquivo_id, a.hospital_id, g as linha,
           current_date - (floor(random() * random() * 120))::int as data,
           1 + floor(random() * (select count(*) from sorteio))::int as n,
           random() as sorte
    from arquivo_base_hospitalar a, generate_series(1, 140) g
    where a.nome = 'MASSA-TESTE-EXAMES'
      and not exists (select 1 from visita_base_hospitalar v where v.arquivo_id = a.id)
)
insert into visita_base_hospitalar(arquivo_id, hospital_id, linha, atendimento, data_visita, medico_nome, campos, decisao, arquivo_atual_id, linha_atual, tipo_procedimento)
select l.arquivo_id, l.hospital_id, l.linha, 'MT' || l.hospital_id || lpad(l.linha::text, 5, '0'), l.data,
       m.nomes[1 + (l.linha % array_length(m.nomes, 1))],
       jsonb_build_object(
         'atendimento', 'MT' || l.hospital_id || lpad(l.linha::text, 5, '0'),
         'data consumo', l.data::text,
         'paciente', 'Paciente Teste ' || l.linha,
         'operadora', (array['Bradesco Saúde', 'SulAmérica', 'Amil', 'Unimed'])[1 + l.linha % 4],
         'medico', m.nomes[1 + (l.linha % array_length(m.nomes, 1))],
         'cod produto', s.codigo,
         'produto', s.produto,
         'conta paciente', 'CT' || l.linha,
         'setor', case when l.sorte < 0.85 then 'Faturado' else 'Em analise' end,
         'valor tot', s.valor::text,
         -- metade traz o repasse na planilha, um quarto so a Regra, o resto fica para o repasse cadastrado
         'regra', case when l.linha % 4 = 1 then '' else '0.70' end,
         'vl. a repassar', case when l.linha % 2 = 0 then round(s.valor * 0.70, 2)::text else '' end),
       case when l.sorte < 0.10 then 'EM_ANALISE' else 'ORIGINAL' end,
       l.arquivo_id, l.linha, s.tipo
from linhas l join sorteio s on s.n = l.n, medicos m;

-- conciliados: vinculo ficticio com o relatorio mais recente (linhas 900000+ nao existem no relatorio)
insert into vinculo_base_hospitalar(visita_id, relatorio_id, linha_producao)
select v.id, (select max(id) from conciliacao_visitas), 900000 + v.id
from visita_base_hospitalar v join arquivo_base_hospitalar a on a.id = v.arquivo_id
where a.nome = 'MASSA-TESTE-EXAMES' and v.decisao = 'ORIGINAL' and random() < 0.6
  and exists (select 1 from conciliacao_visitas)
  and not exists (select 1 from vinculo_base_hospitalar b where b.visita_id = v.id);

update arquivo_base_hospitalar a set quantidade_registros = (select count(*) from visita_base_hospitalar v where v.arquivo_id = a.id)
where a.nome = 'MASSA-TESTE-EXAMES';

commit;

select h.nome, v.tipo_procedimento, count(*) as qtd, min(v.data_visita) as de, max(v.data_visita) as ate
from visita_base_hospitalar v join arquivo_base_hospitalar a on a.id = v.arquivo_id join hospital_cadastro h on h.id = v.hospital_id
where a.nome = 'MASSA-TESTE-EXAMES' group by 1, 2 order by 1, 2;
