-- Arquivo da nota fiscal anexado ao fechamento (o hospital baixa pelo portal).
alter table producao_fechamento add column nf_arquivo bytea;
alter table producao_fechamento add column nf_arquivo_nome varchar(200);
alter table producao_fechamento add column nf_arquivo_tipo varchar(80);
