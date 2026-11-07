-- Regras de valor por hospital e fechamento de producao por periodo.
-- A unidade de calculo e a AGENDA = folha (lote) x medico executante.
--   POR_EXAME      : valor x quantidade, com minimo garantido de exames por agenda (ex.: AngioTC, MAPA, ECG)
--   POR_AGENDA     : valor fixo por agenda que teve exames da regra (ex.: Teste ergometrico)
--   TABELA_AGENDA  : soma os pontos dos exames da agenda (peso por exame) e busca o valor na tabela de faixas;
--                    acima da ultima faixa soma "incremento" por ponto extra (ex.: Eco cardio)
--   MENSAL_MEDICO  : valor fixo por fechamento para um medico (ex.: coordenacao)
insert into producao_exame_tipo(nome, nome_normalizado) values
 ('AngioTC eletivo', 'ANGIOTC ELETIVO'), ('AngioTC internado', 'ANGIOTC INTERNADO'),
 ('RMC eletivo', 'RMC ELETIVO'), ('RMC internado', 'RMC INTERNADO'),
 ('Eco estresse', 'ECO ESTRESSE'), ('Eco transesofagico', 'ECO TRANSESOFAGICO'), ('Eco com strain', 'ECO COM STRAIN'),
 ('BIP', 'BIP'), ('Intraoperatorio', 'INTRAOPERATORIO')
on conflict (nome_normalizado) do nothing;

create table producao_regra_valor (
    id bigserial primary key,
    hospital_id bigint not null references hospital_cadastro(id),
    nome varchar(120) not null,
    tipo varchar(20) not null check (tipo in ('POR_EXAME','POR_AGENDA','TABELA_AGENDA','MENSAL_MEDICO')),
    valor numeric(12,2) not null default 0 check (valor >= 0),
    minimo_por_agenda int not null default 0 check (minimo_por_agenda >= 0),
    incremento numeric(12,2) not null default 0 check (incremento >= 0),
    medico_id bigint references medico_cadastro(id),
    vigencia_inicio date not null,
    vigencia_fim date,
    observacao varchar(300) not null default '',
    ativo boolean not null default true,
    check (vigencia_fim is null or vigencia_fim >= vigencia_inicio),
    check (tipo <> 'MENSAL_MEDICO' or medico_id is not null)
);
create index idx_producao_regra_hospital on producao_regra_valor(hospital_id);

create table producao_regra_exame (
    regra_id bigint not null references producao_regra_valor(id) on delete cascade,
    exame_id bigint not null references producao_exame_tipo(id),
    peso int not null default 1 check (peso > 0),
    primary key (regra_id, exame_id)
);

create table producao_regra_faixa (
    regra_id bigint not null references producao_regra_valor(id) on delete cascade,
    quantidade int not null check (quantidade >= 0),
    valor numeric(12,2) not null check (valor >= 0),
    primary key (regra_id, quantidade)
);

create table producao_fechamento (
    id bigserial primary key,
    hospital_id bigint not null references hospital_cadastro(id),
    data_inicio date not null,
    data_fim date not null check (data_fim >= data_inicio),
    status varchar(20) not null default 'AGUARDANDO' check (status in ('AGUARDANDO','APROVADO','NF_EMITIDA')),
    total numeric(14,2) not null default 0,
    aprovado_por varchar(180),
    aprovado_em date,
    nf_numero varchar(40),
    nf_data date,
    observacao varchar(500) not null default '',
    criado_por varchar(180),
    criado_em timestamp not null default now()
);

-- valores congelados no momento do fechamento (mudar a regra depois nao altera o que ja foi enviado ao hospital)
create table producao_fechamento_calculo (
    id bigserial primary key,
    fechamento_id bigint not null references producao_fechamento(id) on delete cascade,
    lote_id bigint references producao_lote(id),
    data_producao date,
    periodo varchar(10),
    medico_id bigint references medico_cadastro(id),
    regra_id bigint references producao_regra_valor(id),
    descricao varchar(200) not null,
    exames int not null default 0,
    pontos int not null default 0,
    valor numeric(12,2) not null
);
create table producao_fechamento_item (
    fechamento_id bigint not null references producao_fechamento(id) on delete cascade,
    item_id bigint not null unique references producao_item(id) on delete cascade,
    primary key (fechamento_id, item_id)
);
alter table producao_lote add column fechamento_id bigint references producao_fechamento(id);
