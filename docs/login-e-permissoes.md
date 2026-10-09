# Login e permissoes

## Acesso local

- Abra `http://localhost:8080/login.html`.
- O administrador inicial usa o email `ADMIN_INITIAL_EMAIL` e a senha `ADMIN_INITIAL_PASSWORD` do arquivo local `.env`.
- As credenciais sao utilizadas somente quando nao existem usuarios no banco. Alterar `.env` nao redefine uma senha existente.
- O arquivo `.env` esta excluido do Git e do contexto de build do Docker. Nao publique nem compartilhe suas credenciais.
- Altere a senha em **Minha conta** apos o primeiro acesso.

## Perfis

**ADMIN:** dashboard, conciliacao, importacao, revisao, exportacao, cadastros e gestao de acessos.

**MEDICO:** portal somente de leitura com seus relatorios, indicadores e pendencias. Nao tem acesso a APIs administrativas, importacoes, revisoes, exportacoes administrativas ou cadastros de terceiros.

Em **Acessos**, o administrador cria um usuario e vincula o perfil MEDICO a um cadastro ativo, identificado por CRM/UF. Um cadastro medico possui no maximo um usuario. O perfil e o vinculo nao sao alteraveis pelo proprio usuario.

O servidor determina o medico a partir da sessao, nao de parametros enviados pelo navegador. Visitas com vinculo explicito usam o ID cadastrado; visitas sem vinculo usam somente correspondencia unica de nome/alias entre medicos ativos. Homonimos ou visitas sem medico identificado nao aparecem no portal ate serem revisados pelo administrador.

O portal lista as ultimas versoes entre os 100 relatorios mais recentes e mostra apenas relatorios com visitas desse medico. Os indicadores abrangem o relatorio e o periodo selecionados, nao uma soma de todas as importacoes. As respostas nao incluem candidatos hospitalares, planilhas originais, pacientes, auditoria ou registros sem producao. Faturado nao comprova recebimento financeiro.

## Senhas e sessoes

- Senhas sao armazenadas com BCrypt (custo 12), nunca em texto no banco ou nas respostas das APIs.
- Minimo de 12 caracteres; maximo de 72 bytes UTF-8.
- Sessao via cookie HttpOnly e SameSite=Lax, com expiracao apos 30 minutos sem atividade.
- Login, logout e alteracoes exigem token CSRF. O frontend consulta `/api/auth/csrf` e envia o token no header.
- Cinco falhas de login em uma conta causam bloqueio de 15 minutos. Mensagens nao revelam se o email existe.
- Desativar o usuario/medico ou redefinir a senha invalida os acessos existentes na proxima requisicao protegida.
- Nao e permitido desativar o proprio acesso ou o ultimo administrador ativo.
- Redefinicao de senha e feita pelo administrador; recuperacao automatica por email, convites e MFA nao estao implementados.

## Antes de publicar

Esta configuracao foi preparada para teste local. Antes de liberar para medicos externos, configure HTTPS e `SESSION_COOKIE_SECURE=true`, credenciais exclusivas do banco, protecao contra tentativas por IP no proxy, backup e monitoramento. Restrinja as portas do banco e use segredos do ambiente de hospedagem em vez de `.env` compartilhado. Remova as credenciais de bootstrap do ambiente apos provisionar o administrador. Revise a politica de privacidade, os vinculos e a liberacao dos relatorios.

Protecao CSRF segue a [documentacao do Spring Security](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html).

## Verificacao

```sh
docker build --target build -t faturamed-verificacao .
docker run --rm faturamed-verificacao mvn -B test
```

Os testes cobrem permissoes ADMIN/MEDICO, API anonima, CSRF, sessao revogada, senha ausente nas respostas, sanitizacao do portal e restricao ao ID do medico autenticado.
