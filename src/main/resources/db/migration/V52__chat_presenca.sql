-- Ultima vez que cada pessoa esteve conectada ao chat ("visto por ultimo"), atualizada ao entrar, a cada pulso e ao sair.
create table chat_presenca (
    usuario_id bigint primary key references usuario_acesso(id) on delete cascade,
    visto_em timestamptz not null
);
