create table pagamento_visitas (
 id bigserial primary key,
 relatorio_raiz_id bigint not null references conciliacao_visitas(id),
 relatorio_id bigint not null references conciliacao_visitas(id),
 medico_id bigint not null references medico_cadastro(id),
 data_recebimento date not null,
 valor_total numeric(16,2) not null check (valor_total > 0),
 referencia varchar(120) not null check (length(btrim(referencia)) > 0),
 referencia_chave varchar(120) generated always as (lower(btrim(referencia))) stored,
 observacoes varchar(2000) not null,
 responsavel varchar(500) not null,
 usuario_id bigint not null,
 criado_em timestamptz not null default now(),
 unique (relatorio_raiz_id,medico_id,referencia_chave)
);
alter table recebimento_visita add column pagamento_id bigint references pagamento_visitas(id);
create index recebimento_visita_pagamento on recebimento_visita(pagamento_id);
