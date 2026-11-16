-- Repasse cadastrado: usado somente quando a planilha do hospital nao traz o repasse
-- (nem "Vl. a Repassar" nem "Regra"). Busca do mais especifico para o mais generico:
-- hospital+codigo, codigo, hospital+tipo, tipo.
create table repasse_procedimento (
    id bigserial primary key,
    hospital_id bigint references hospital_cadastro(id),
    codigo_procedimento varchar(40),
    tipo_procedimento varchar(20) check (tipo_procedimento in ('VISITA','EXAME','CONSULTA','OUTRO')),
    percentual numeric(7,4) check (percentual >= 0 and percentual <= 100),
    valor_fixo numeric(12,2) check (valor_fixo >= 0),
    vigencia_inicio date not null default date '2000-01-01',
    vigencia_fim date,
    ativo boolean not null default true,
    observacao varchar(300) not null default '',
    criado_em timestamp not null default current_timestamp,
    check ((percentual is null) <> (valor_fixo is null)),
    check (codigo_procedimento is not null or tipo_procedimento is not null),
    check (vigencia_fim is null or vigencia_fim >= vigencia_inicio)
);
create index repasse_procedimento_busca on repasse_procedimento(codigo_procedimento, tipo_procedimento, hospital_id);
