-- Perfis tecnicos existentes sao preservados; novos funcionarios usam OPERADOR.
alter table usuario_acesso drop constraint usuario_acesso_perfil_check;
alter table usuario_acesso add constraint usuario_acesso_perfil_check check (perfil in ('ADMIN','DEV','MEDICO','OPERADOR'));
alter table usuario_acesso drop constraint usuario_acesso_check;
alter table usuario_acesso add constraint usuario_acesso_check check ((perfil='MEDICO' and medico_id is not null) or (perfil in ('ADMIN','DEV','OPERADOR') and medico_id is null));
alter table chamado_suporte drop constraint chamado_suporte_solicitante_perfil_check;
alter table chamado_suporte add constraint chamado_suporte_solicitante_perfil_check check (solicitante_perfil in ('ADMIN','DEV','MEDICO','OPERADOR'));
alter table chamado_mensagem drop constraint chamado_mensagem_autor_perfil_check;
alter table chamado_mensagem add constraint chamado_mensagem_autor_perfil_check check (autor_perfil in ('ADMIN','DEV','MEDICO','OPERADOR'));

create table usuario_acesso_auditoria (
 id bigserial primary key,
 usuario_id bigint not null references usuario_acesso(id),
 operador_id bigint references usuario_acesso(id),
 acao varchar(30) not null,
 criado_em timestamptz not null default now()
);
