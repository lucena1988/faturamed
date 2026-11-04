-- Empresas clientes (grupos medicos) que reunem medicos. Um medico pode atender por varias empresas;
-- cada producao importada fica atribuida a uma empresa para o dashboard da empresa.
create table empresa_cliente (
 id bigserial primary key,
 nome varchar(180) not null,
 documento varchar(30),
 documento_normalizado varchar(30) unique,
 ativo boolean not null default true,
 versao bigint not null default 0
);

create table medico_empresa (
 medico_id bigint not null references medico_cadastro(id),
 empresa_id bigint not null references empresa_cliente(id),
 primary key (medico_id, empresa_id)
);
create index medico_empresa_empresa on medico_empresa(empresa_id);

alter table conciliacao_visitas add column empresa_id bigint references empresa_cliente(id);
alter table liberacao_relatorio add column empresa_id bigint references empresa_cliente(id);
create index liberacao_relatorio_empresa on liberacao_relatorio(empresa_id, id desc);

alter table usuario_acesso add column empresa_id bigint references empresa_cliente(id);
alter table usuario_acesso drop constraint usuario_acesso_perfil_check;
alter table usuario_acesso add constraint usuario_acesso_perfil_check check (perfil in ('ADMIN','DEV','MEDICO','OPERADOR','EMPRESA'));
alter table usuario_acesso drop constraint usuario_acesso_check;
alter table usuario_acesso add constraint usuario_acesso_check check (
 (perfil='MEDICO' and medico_id is not null and empresa_id is null)
 or (perfil='EMPRESA' and empresa_id is not null and medico_id is null)
 or (perfil in ('ADMIN','DEV','OPERADOR') and medico_id is null and empresa_id is null));
