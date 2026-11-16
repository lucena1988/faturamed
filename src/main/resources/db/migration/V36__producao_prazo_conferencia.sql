-- Prazo para o hospital conferir o fechamento (dias uteis, configuravel) e controle do lembrete por e-mail.
alter table producao_fechamento add column prazo_conferencia date;
alter table producao_fechamento add column lembrete_prazo_em timestamp;
-- fechamentos ja existentes: aproximacao de 5 dias uteis = 7 dias corridos
update producao_fechamento set prazo_conferencia = criado_em::date + 7 where prazo_conferencia is null;
