alter table chamado_suporte drop constraint if exists chamado_suporte_status_check;
alter table chamado_suporte add constraint chamado_suporte_status_check
    check (status in ('ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'BLOCK', 'RESOLVIDO', 'FECHADO', 'CANCELADO'));
