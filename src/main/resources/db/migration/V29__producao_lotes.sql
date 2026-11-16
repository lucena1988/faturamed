-- Modulo Producao: folhas de etiquetas recebidas por hospital/medico.
-- Cada etiqueta colada na folha vira um item faturavel (etiquetas repetidas = procedimentos distintos).
create table producao_lote (
    id bigserial primary key,
    hospital_id bigint not null references hospital_cadastro(id),
    medico_id bigint not null references medico_cadastro(id),
    data_producao date not null,
    periodo varchar(10) not null default 'MANHA' check (periodo in ('MANHA','TARDE','NOITE','INTEGRAL')),
    tipo_agenda varchar(120) not null default '',
    auxiliar varchar(120) not null default '',
    observacao varchar(500) not null default '',
    status varchar(20) not null default 'RASCUNHO' check (status in ('RASCUNHO','CONFERIDO','ENVIADO')),
    foto bytea,
    foto_tipo varchar(60),
    criado_por varchar(180),
    criado_em timestamp not null default now(),
    atualizado_em timestamp not null default now(),
    versao bigint not null default 0
);
create index idx_producao_lote_data on producao_lote(data_producao desc);

create table producao_item (
    id bigserial primary key,
    lote_id bigint not null references producao_lote(id) on delete cascade,
    ordem int not null default 0,
    nr_atendimento varchar(30) not null,
    data_hora_atendimento varchar(30) not null default '',
    paciente varchar(180) not null default '',
    prontuario varchar(30) not null default '',
    data_nascimento varchar(12) not null default '',
    filiacao varchar(180) not null default '',
    convenio varchar(120) not null default '',
    convenio_codigo varchar(20) not null default '',
    plano varchar(120) not null default '',
    carteirinha varchar(40) not null default '',
    guia varchar(40) not null default '',
    senha varchar(40) not null default '',
    nr_prescricao varchar(30) not null default '',
    procedimento varchar(180) not null default '',
    medico_solicitante varchar(180) not null default '',
    origem varchar(10) not null default 'MANUAL' check (origem in ('MANUAL','OCR','LEITOR')),
    pendente_revisao boolean not null default false
);
create index idx_producao_item_lote on producao_item(lote_id);
create index idx_producao_item_atendimento on producao_item(nr_atendimento);
