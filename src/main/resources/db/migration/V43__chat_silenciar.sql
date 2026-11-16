-- Chat interno: cada participante pode silenciar o som de uma conversa
alter table chat_participante add column silenciado boolean not null default false;
