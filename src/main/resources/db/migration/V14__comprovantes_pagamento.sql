create table pagamento_comprovante (
 id bigserial primary key,
 pagamento_id bigint not null references pagamento_visitas(id),
 nome varchar(180) not null,
 tipo varchar(40) not null check (tipo in ('application/pdf','image/png','image/jpeg')),
 tamanho integer not null check (tamanho > 0 and tamanho <= 5242880),
 sha256 varchar(64) not null,
 conteudo bytea not null,
 usuario_id bigint not null,
 responsavel varchar(500) not null,
 criado_em timestamptz not null default now(),
 retirado_em timestamptz,
 retirado_por varchar(500),
 motivo_retirada varchar(2000),
 check (octet_length(conteudo)=tamanho)
);
create unique index pagamento_comprovante_ativo_hash on pagamento_comprovante(pagamento_id,sha256) where retirado_em is null;
