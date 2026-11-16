-- Uma folha de producao pode envolver varios medicos e varios exames.
create table producao_exame_tipo (
    id bigserial primary key,
    nome varchar(120) not null,
    nome_normalizado varchar(120) not null unique,
    ativo boolean not null default true
);
insert into producao_exame_tipo(nome, nome_normalizado) values
 ('Consulta', 'CONSULTA'), ('Visita hospitalar', 'VISITA HOSPITALAR'), ('Parecer', 'PARECER'),
 ('Ecocardiograma', 'ECOCARDIOGRAMA'), ('Eletrocardiograma', 'ELETROCARDIOGRAMA'), ('Holter 24h', 'HOLTER 24H'),
 ('MAPA', 'MAPA'), ('Teste ergometrico', 'TESTE ERGOMETRICO'), ('Prova de funcao pulmonar', 'PROVA DE FUNCAO PULMONAR'),
 ('Doppler vascular', 'DOPPLER VASCULAR');

create table producao_lote_medico (
    lote_id bigint not null references producao_lote(id) on delete cascade,
    medico_id bigint not null references medico_cadastro(id),
    primary key (lote_id, medico_id)
);
create table producao_lote_exame (
    lote_id bigint not null references producao_lote(id) on delete cascade,
    exame_id bigint not null references producao_exame_tipo(id),
    primary key (lote_id, exame_id)
);
insert into producao_lote_medico(lote_id, medico_id) select id, medico_id from producao_lote where medico_id is not null;
alter table producao_lote drop column medico_id;

alter table producao_item add column medico_id bigint references medico_cadastro(id);
alter table producao_item add column exame_id bigint references producao_exame_tipo(id);
