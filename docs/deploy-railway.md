# Deploy no Railway (CD)

O workflow [.github/workflows/deploy.yml](../.github/workflows/deploy.yml) entrega no
Railway somente commits que passaram na CI:

| Origem | Destino | Aprovacao |
|---|---|---|
| push na `develop` com CI verde | homologacao | automatico |
| push na `main` (merge de PR) com CI verde | producao | manual, no GitHub |
| **Actions > Deploy > Run workflow** | escolhido na tela | conforme o ambiente |

O Railway constroi a imagem a partir do `Dockerfile` do commit testado (`railway up`).
Depois do deploy, a pipeline consulta `APP_URL/api/health` por ate 5 minutos.

## 1. Railway

1. Crie um projeto (ex.: `faturamed`). Ele ja vem com o ambiente `production`;
   crie tambem `homologacao` em **Settings > Environments**.
2. Em **cada** ambiente:
   - **+ New > Database > PostgreSQL**.
   - **+ New > Empty Service**, nome `faturamed`.
   - Variaveis do servico `faturamed` (**Variables > Raw Editor**):
     ```
     DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
     DB_USER=${{Postgres.PGUSER}}
     DB_PASSWORD=${{Postgres.PGPASSWORD}}
     SERVER_PORT=8080
     SESSION_COOKIE_SECURE=true
     ADMIN_INITIAL_EMAIL=admin@seudominio.com.br
     ADMIN_INITIAL_PASSWORD=<senha forte, minimo 12 caracteres>
     ```
   - **Settings > Networking > Generate Domain** (porta 8080). Anote a URL.
   - **Settings > Tokens** do projeto: gere um **Project Token** para esse ambiente.
     O token so funciona no ambiente em que foi criado.

## 2. GitHub

Em **Settings > Environments**, crie dois ambientes com estes nomes exatos:

| Environment | Secret | Variables | Protecao |
|---|---|---|---|
| `homologacao` | `RAILWAY_TOKEN` = token do ambiente homologacao | `RAILWAY_SERVICE=faturamed`, `APP_URL=https://...` | nenhuma |
| `producao` | `RAILWAY_TOKEN` = token do ambiente production | `RAILWAY_SERVICE=faturamed`, `APP_URL=https://...` | **Required reviewers**: voce; **Deployment branches**: `main` |

Com **Required reviewers**, o deploy de producao fica parado em "Waiting" ate alguem
aprovar em **Actions > Deploy > Review deployments**. Nao marque "Prevent self-review"
enquanto voce for o unico mantenedor.

## 3. Primeiro deploy

1. Rode **Actions > Deploy > Run workflow** com `homologacao`.
2. Confira o job **Railway (homologacao)**: build no Railway e `/api/health` respondendo.
3. Acesse a URL e entre com `ADMIN_INITIAL_EMAIL` / `ADMIN_INITIAL_PASSWORD`.

## Voltar uma versao

No Railway, abra o servico, **Deployments**, escolha um deploy anterior e use
**Redeploy**. Ou rode **Run workflow** a partir de um commit anterior.
