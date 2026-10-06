create table empresa (
    id bigserial primary key,
    nome varchar(180) not null,
    documento varchar(30),
    ativa boolean not null default true,
    criado_em timestamp not null default current_timestamp
);

create table unidade (
    id bigserial primary key,
    empresa_id bigint not null references empresa(id),
    nome varchar(180) not null,
    codigo_externo varchar(80),
    ativa boolean not null default true,
    criado_em timestamp not null default current_timestamp
);

create table usuario (
    id bigserial primary key,
    empresa_id bigint not null references empresa(id),
    nome varchar(180) not null,
    email varchar(180) not null,
    perfil varchar(40) not null,
    ativo boolean not null default true,
    criado_em timestamp not null default current_timestamp,
    constraint uk_usuario_email unique (email)
);

create table layout_importacao (
    id bigserial primary key,
    empresa_id bigint not null references empresa(id),
    nome varchar(180) not null,
    tipo varchar(30) not null,
    delimitador_csv varchar(5),
    primeira_linha_cabecalho boolean not null default true,
    ativo boolean not null default true,
    criado_em timestamp not null default current_timestamp
);

create table layout_campo (
    id bigserial primary key,
    layout_importacao_id bigint not null references layout_importacao(id),
    campo_padronizado varchar(80) not null,
    coluna_origem varchar(20) not null,
    obrigatorio boolean not null default false,
    formato varchar(80)
);

create table importacao (
    id bigserial primary key,
    empresa_id bigint not null references empresa(id),
    unidade_id bigint references unidade(id),
    layout_importacao_id bigint references layout_importacao(id),
    usuario_id bigint references usuario(id),
    tipo varchar(30) not null,
    nome_arquivo varchar(255) not null,
    status varchar(30) not null,
    total_linhas integer not null default 0,
    total_processadas integer not null default 0,
    total_erros integer not null default 0,
    criado_em timestamp not null default current_timestamp,
    finalizado_em timestamp
);

create table registro_importado (
    id bigserial primary key,
    importacao_id bigint not null references importacao(id),
    numero_linha integer not null,
    origem varchar(30) not null,
    paciente_nome varchar(180),
    paciente_documento varchar(40),
    atendimento_codigo varchar(80),
    guia_codigo varchar(80),
    data_atendimento date,
    medico_nome varchar(180),
    medico_documento varchar(40),
    procedimento_codigo varchar(80),
    procedimento_nome varchar(255),
    quantidade numeric(14, 4),
    valor_unitario numeric(14, 2),
    valor_total numeric(14, 2),
    chave_cruzamento varchar(500),
    dados_originais jsonb,
    criado_em timestamp not null default current_timestamp
);

create index idx_registro_importado_chave on registro_importado(chave_cruzamento);
create index idx_registro_importado_paciente_data on registro_importado(paciente_documento, data_atendimento);
create index idx_registro_importado_procedimento on registro_importado(procedimento_codigo);

create table rodada_conferencia (
    id bigserial primary key,
    empresa_id bigint not null references empresa(id),
    unidade_id bigint references unidade(id),
    importacao_producao_id bigint references importacao(id),
    importacao_faturamento_id bigint references importacao(id),
    usuario_id bigint references usuario(id),
    periodo_inicio date,
    periodo_fim date,
    status varchar(30) not null,
    total_analisado integer not null default 0,
    total_conforme integer not null default 0,
    total_divergencia integer not null default 0,
    valor_em_risco numeric(14, 2) not null default 0,
    criado_em timestamp not null default current_timestamp,
    finalizado_em timestamp
);

create table divergencia (
    id bigserial primary key,
    rodada_conferencia_id bigint not null references rodada_conferencia(id),
    registro_producao_id bigint references registro_importado(id),
    registro_faturamento_id bigint references registro_importado(id),
    tipo varchar(60) not null,
    severidade varchar(30) not null,
    status varchar(30) not null default 'PENDENTE',
    descricao text,
    valor_em_risco numeric(14, 2) not null default 0,
    criado_em timestamp not null default current_timestamp
);

create index idx_divergencia_rodada on divergencia(rodada_conferencia_id);
create index idx_divergencia_tipo_status on divergencia(tipo, status);

create table tratamento_divergencia (
    id bigserial primary key,
    divergencia_id bigint not null references divergencia(id),
    usuario_id bigint references usuario(id),
    decisao varchar(40) not null,
    motivo text,
    valor_corrigido numeric(14, 2),
    criado_em timestamp not null default current_timestamp
);
