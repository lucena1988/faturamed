# Backup diario do banco de producao (Cloudflare R2)

O workflow [.github/workflows/backup.yml](../.github/workflows/backup.yml) roda todo dia
as 03h (Brasilia), faz `pg_dump` do PostgreSQL de producao, valida o arquivo, criptografa
com [age](https://github.com/FiloSottile/age) e envia ao bucket do Cloudflare R2.
O banco guarda planilhas originais e comprovantes (dados de saude), por isso o arquivo
nunca sai da Railway sem criptografia.

Complementa o backup de volume da Railway; nao o substitui.

## 1. Chave de criptografia (uma vez)

No seu computador, com o `age` instalado (`winget install FiloSottile.age`):

```powershell
age-keygen -o chave-backup-faturamed.txt
```

- A linha `# public key: age1...` e a **chave publica**. Vai para o GitHub (passo 3).
- O arquivo inteiro e a **chave privada**. **Guarde em dois lugares seguros** (gerenciador de
  senhas e um pendrive offline). **Nunca** suba ao GitHub, ao Railway ou ao R2.
  Sem ela, os backups nao podem ser abertos.

## 2. Cloudflare R2

1. Cloudflare > **R2 Object Storage** > **Create bucket** (ex.: `faturamed-backup`).
   Mantenha o bucket **privado** (sem dominio publico nem acesso r2.dev).
2. No bucket > **Settings > Object lifecycle rules** > regra para **excluir objetos apos
   30 dias** (prefixo `producao/`). E assim que o backup antigo e descartado.
3. R2 > **Manage API tokens** > **Create API token** com permissao **Object Read & Write**,
   restrito a esse bucket. Anote **Access Key ID**, **Secret Access Key** e o
   **Account ID** (aparece na pagina do R2).

## 3. Railway e GitHub

**Railway** (ambiente `production`): no servico do PostgreSQL, em **Variables**, copie
`DATABASE_PUBLIC_URL` (conexao pelo proxy TCP publico; a `DATABASE_URL` interna nao e
acessivel pelo GitHub).

**GitHub** > Settings > Environments > **New environment**, nome exato `backup`,
**sem** Required reviewers (o agendamento nao pode esperar aprovacao).

| Tipo | Nome | Valor |
|---|---|---|
| Secret | `BACKUP_DATABASE_URL` | `DATABASE_PUBLIC_URL` do Postgres de producao |
| Secret | `R2_ACCOUNT_ID` | Account ID do Cloudflare |
| Secret | `R2_ACCESS_KEY_ID` | Access Key ID do token do R2 |
| Secret | `R2_SECRET_ACCESS_KEY` | Secret Access Key do token do R2 |
| Variable | `R2_BUCKET` | nome do bucket (ex.: `faturamed-backup`) |
| Variable | `BACKUP_AGE_PUBLIC_KEY` | chave publica `age1...` do passo 1 |
| Variable (opcional) | `BACKUP_PG_VERSION` | versao do PostgreSQL da Railway; padrao `17` |

Se o `pg_dump` reclamar de "server version mismatch", ajuste `BACKUP_PG_VERSION` para a
versao do servidor (ou uma mais nova).

## 4. Primeiro teste

1. **Actions > Backup > Run workflow**.
2. Confira os passos "Conferir o dump" e "Enviar ao Cloudflare R2" e o objeto em
   `producao/` no bucket.
3. **Faca o teste de restauracao abaixo na primeira vez e depois a cada trimestre.**
   Backup que nunca foi restaurado nao e backup.

Se o workflow falhar em algum dia, o GitHub envia e-mail. Nao ignore.

## Restaurar

Baixe o arquivo `.dump.age` do R2 (console da Cloudflare) e, num banco **novo e vazio**
(nunca por cima da producao sem decidir isso conscientemente):

```powershell
age --decrypt -i chave-backup-faturamed.txt -o faturamed.dump faturamed-AAAAMMDD-HHMMSS.dump.age
pg_restore --no-owner --exit-on-error -d "postgresql://usuario:senha@host:5432/banco_novo" faturamed.dump
```

Depois suba a aplicacao apontando para o banco restaurado e confira os dados.
O mesmo procedimento ja e validado automaticamente em QA (caso AUTO10 em
[regressao-homologacao.md](regressao-homologacao.md)).

## Custos e limites

O R2 tem 10 GB gratis por mes. Com retencao de 30 dias, o espaco usado e
(tamanho do dump) x 30; acompanhe quando o banco crescer.
