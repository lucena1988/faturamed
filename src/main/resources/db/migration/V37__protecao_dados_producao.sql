-- Protecao de dados sensiveis da producao (LGPD):
-- 1) campos criptografados (AES-GCM, prefixo "enc:v1:") precisam de mais espaco
alter table producao_item alter column carteirinha type varchar(200);
alter table producao_item alter column guia type varchar(200);
alter table producao_item alter column senha type varchar(200);

-- 2) registro de acesso a dados de pacientes (quem abriu, baixou ou revelou)
create table acesso_dados (
    id bigserial primary key,
    usuario varchar(180),
    perfil varchar(12),
    acao varchar(30) not null,
    entidade varchar(20) not null,
    entidade_id bigint,
    detalhe varchar(300) not null default '',
    ip varchar(64),
    quando timestamp not null default now()
);
create index idx_acesso_dados_entidade on acesso_dados(entidade, entidade_id, quando desc);
create index idx_acesso_dados_quando on acesso_dados(quando desc);

-- 3) prazo de guarda: quando a foto da folha foi descartada
alter table producao_lote add column foto_removida_em timestamp;
