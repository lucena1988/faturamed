-- Cadastro do medico em duas etapas: a FaturaMed cria o cadastro basico (nome e e-mail; CRM e UF opcionais)
-- e envia um convite; o medico completa CRM, CPF, empresa e e-mails e define a propria senha.
alter table medico_cadastro alter column crm drop not null;
alter table medico_cadastro alter column uf drop not null;
alter table medico_cadastro add column email varchar(500);
alter table medico_cadastro add column cpf varchar(11) unique;
alter table medico_cadastro add column empresa_nome varchar(180);
alter table medico_cadastro add column empresa_cnpj varchar(14);
-- Nulo = cadastro ainda nao completado pelo medico (inclui todos os medicos ja existentes).
alter table medico_cadastro add column cadastro_completo_em timestamptz;

create table medico_convite (
 id bigserial primary key,
 medico_id bigint not null references medico_cadastro(id),
 token_hash varchar(64) not null unique,
 expira_em timestamptz not null,
 usado_em timestamptz,
 criado_por bigint not null references usuario_acesso(id),
 criado_em timestamptz not null default now()
);
create index medico_convite_medico on medico_convite(medico_id);
