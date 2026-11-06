-- Codigo de uso unico enviado por e-mail para redefinir a senha. Guarda so o hash do codigo.
create table recuperacao_senha (
 id bigserial primary key,
 usuario_id bigint not null references usuario_acesso(id),
 codigo_hash varchar(100) not null,
 expira_em timestamptz not null,
 tentativas integer not null default 0,
 usado_em timestamptz,
 criado_em timestamptz not null default now()
);
create index recuperacao_senha_usuario on recuperacao_senha(usuario_id, criado_em desc);
