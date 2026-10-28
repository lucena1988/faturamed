create table chamado_aviso (
 id bigserial primary key,
 chamado_id bigint not null references chamado_suporte(id),
 usuario_id bigint not null references usuario_acesso(id),
 tipo varchar(30) not null check (tipo in ('RETORNO_SOLICITADO','RESPOSTA_SOLICITANTE','MENSAGEM_SUPORTE')),
 criado_em timestamptz not null default now(),
 lido_em timestamptz
);
create index chamado_aviso_pendente on chamado_aviso(usuario_id,id desc) where lido_em is null;
