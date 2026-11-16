alter table chamado_suporte drop constraint if exists chamado_suporte_status_check;
alter table chamado_suporte add constraint chamado_suporte_status_check
    check (status in ('ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'RESOLVIDO', 'FECHADO', 'CANCELADO'));

alter table chamado_suporte add column if not exists cancelado_em timestamptz;
alter table chamado_suporte add column if not exists motivo_cancelamento varchar(60);
alter table chamado_suporte add column if not exists justificativa_cancelamento varchar(2000);

alter table chamado_aviso drop constraint if exists chamado_aviso_tipo_check;
alter table chamado_aviso add constraint chamado_aviso_tipo_check
    check (tipo in ('RETORNO_SOLICITADO', 'RESPOSTA_SOLICITANTE', 'MENSAGEM_SUPORTE', 'CHAMADO_CANCELADO'));
