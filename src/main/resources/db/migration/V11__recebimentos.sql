create table recebimento_visita (
 id bigserial primary key,
 relatorio_raiz_id bigint not null references conciliacao_visitas(id),
 relatorio_id bigint not null references conciliacao_visitas(id),
 linha_medico integer not null check (linha_medico > 0),
 medico_id bigint not null references medico_cadastro(id),
 valor numeric(16,2) not null check (valor <> 0),
 data_recebimento date not null,
 observacoes varchar(2000) not null,
 responsavel varchar(500) not null,
 usuario_id bigint not null,
 criado_em timestamptz not null default now(),
 estorno_de bigint unique references recebimento_visita(id),
 check ((estorno_de is null and valor > 0) or (estorno_de is not null and valor < 0))
);
create index recebimento_visita_linha on recebimento_visita(relatorio_raiz_id,linha_medico,id);
