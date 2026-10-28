create table leitura_notificacao (
    usuario_id bigint not null references usuario_acesso(id) on delete cascade,
    escopo varchar(50) not null,
    tipo varchar(15) not null check (tipo in ('LIBERACAO','PEDIDO','RESPOSTA')),
    evento_id bigint not null,
    lido_em timestamptz not null default now(),
    primary key (usuario_id, escopo, tipo, evento_id)
);
