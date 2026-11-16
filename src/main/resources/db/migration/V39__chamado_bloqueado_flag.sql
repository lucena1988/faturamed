-- Bloqueio passa a ser uma marcacao do chamado (card vermelho na coluna de origem), e nao uma etapa do kanban.
alter table chamado_suporte add column if not exists bloqueado boolean not null default false;

update chamado_suporte set bloqueado = true, status = 'EM_ATENDIMENTO' where status = 'BLOCK';

alter table chamado_suporte drop constraint if exists chamado_suporte_status_check;
alter table chamado_suporte add constraint chamado_suporte_status_check
    check (status in ('ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'RESOLVIDO', 'FECHADO', 'CANCELADO'));
