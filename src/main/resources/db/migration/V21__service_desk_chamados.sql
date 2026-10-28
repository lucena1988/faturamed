alter table usuario_acesso drop constraint if exists usuario_acesso_perfil_check;
alter table usuario_acesso drop constraint if exists usuario_acesso_check;

alter table usuario_acesso add constraint usuario_acesso_perfil_check
    check (perfil in ('ADMIN', 'MEDICO', 'DEV'));

alter table usuario_acesso add constraint usuario_acesso_check
    check ((perfil = 'MEDICO' and medico_id is not null) or (perfil in ('ADMIN', 'DEV') and medico_id is null));

create table chamado_tipo_sla (
    codigo varchar(40) primary key,
    nome varchar(120) not null,
    descricao varchar(300) not null,
    fila_padrao varchar(10) not null check (fila_padrao in ('DEV', 'ADMIN')),
    prioridade_padrao varchar(15) not null check (prioridade_padrao in ('CRITICA', 'ALTA', 'MEDIA', 'BAIXA')),
    sla_horas integer not null check (sla_horas >= 1 and sla_horas <= 720),
    ativo boolean not null default true,
    ordem integer not null default 0
);

insert into chamado_tipo_sla (codigo, nome, descricao, fila_padrao, prioridade_padrao, sla_horas, ativo, ordem) values
    ('ERRO_BLOQUEANTE', 'Erro Bloqueante / Sistema Indisponivel', 'Falha critica que impede o uso de uma tela, importacao ou conferencia.', 'DEV', 'CRITICA', 4, true, 1),
    ('BUG_INCONSISTENCIA', 'Bug / Inconsistencia no Sistema', 'Comportamento inesperado, erro de calculo, layout ou divergencia tecnica.', 'DEV', 'ALTA', 12, true, 2),
    ('DUVIDA_FINANCEIRA', 'Duvida Operacional / Faturamento / Repasse', 'Duvidas sobre valores de repasse, conciliacao, relatorios ou regras de negocio.', 'ADMIN', 'MEDIA', 24, true, 3),
    ('CADASTRO_ACESSO', 'Dificuldade de Acesso / Cadastro / Permissao', 'Problemas com login, vinculo de medico, cadastro de hospital ou permissoes.', 'ADMIN', 'MEDIA', 24, true, 4),
    ('MELHORIA_SUGESTAO', 'Sugestao / Melhoria de Usabilidade', 'Ideias de novos recursos, ajustes visuais ou melhorias no fluxo de trabalho.', 'DEV', 'BAIXA', 48, true, 5);

create table chamado_suporte (
    id bigserial primary key,
    protocolo varchar(24) not null unique,
    solicitante_id bigint not null references usuario_acesso(id),
    solicitante_nome varchar(180) not null,
    solicitante_email varchar(180) not null,
    solicitante_perfil varchar(10) not null check (solicitante_perfil in ('ADMIN', 'MEDICO', 'DEV')),
    tipo_codigo varchar(40) not null references chamado_tipo_sla(codigo),
    fila_destino varchar(10) not null check (fila_destino in ('DEV', 'ADMIN')),
    prioridade varchar(15) not null check (prioridade in ('CRITICA', 'ALTA', 'MEDIA', 'BAIXA')),
    status varchar(25) not null check (status in ('ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'RESOLVIDO', 'FECHADO')),
    titulo varchar(180) not null,
    descricao varchar(4000) not null,
    pagina_origem varchar(250),
    responsavel_id bigint references usuario_acesso(id),
    responsavel_nome varchar(180),
    sla_horas integer not null check (sla_horas >= 1),
    prazo_sla_em timestamptz not null,
    primeiro_atendimento_em timestamptz,
    resolvido_em timestamptz,
    fechado_em timestamptz,
    nota_avaliacao integer check (nota_avaliacao is null or (nota_avaliacao >= 1 and nota_avaliacao <= 5)),
    comentario_avaliacao varchar(1500),
    avaliado_em timestamptz,
    criado_em timestamptz not null default now(),
    atualizado_em timestamptz not null default now(),
    versao bigint not null default 0
);

create index idx_chamado_suporte_solicitante on chamado_suporte(solicitante_id, atualizado_em desc);
create index idx_chamado_suporte_fila_status on chamado_suporte(fila_destino, status, prazo_sla_em asc);
create index idx_chamado_suporte_status on chamado_suporte(status, atualizado_em desc);

create table chamado_mensagem (
    id bigserial primary key,
    chamado_id bigint not null references chamado_suporte(id) on delete cascade,
    autor_id bigint not null references usuario_acesso(id),
    autor_nome varchar(180) not null,
    autor_perfil varchar(10) not null check (autor_perfil in ('ADMIN', 'MEDICO', 'DEV')),
    tipo varchar(20) not null check (tipo in ('MENSAGEM', 'EVENTO_SISTEMA')),
    conteudo varchar(4000) not null,
    criado_em timestamptz not null default now()
);

create index idx_chamado_mensagem_chamado on chamado_mensagem(chamado_id, criado_em asc, id asc);

create table chamado_anexo (
    id bigserial primary key,
    chamado_id bigint not null references chamado_suporte(id) on delete cascade,
    mensagem_id bigint references chamado_mensagem(id) on delete cascade,
    usuario_id bigint not null references usuario_acesso(id),
    usuario_nome varchar(180) not null,
    nome_arquivo varchar(180) not null,
    content_type varchar(40) not null check (content_type in ('image/png', 'image/jpeg', 'image/webp', 'application/pdf')),
    tamanho integer not null check (tamanho > 0 and tamanho <= 5242880),
    conteudo bytea not null,
    criado_em timestamptz not null default now(),
    check (octet_length(conteudo) = tamanho)
);

create index idx_chamado_anexo_chamado on chamado_anexo(chamado_id, id asc);
