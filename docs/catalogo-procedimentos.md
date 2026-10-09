# Catalogo de procedimentos

Referencias iniciais: TUSS 22 da ANS e AMB 92, esta ultima pendente de confirmacao e de arquivo de referencia da empresa.

O catalogo identifica procedimentos. Valores contratados, percentuais de repasse e correspondencias TUSS/AMB dependem de confirmacao separada. Descricoes iguais nao autorizam equivalencia automatica entre codigos.

## Carga TUSS

Com o backend iniciado, execute:

```powershell
./scripts/importar-tuss.ps1
```

A carga consulta a API indicada pela ANS, percorre as paginas, verifica a contagem e grava uma fotografia identificada pela data de consulta. Esse identificador nao e uma versao oficial do Padrao TISS. As datas de vigencia de cada procedimento sao preservadas. Nova carga exige uma nova versao; catalogos anteriores nao sao sobrescritos.

Fonte oficial: https://www.gov.br/ans/pt-br/assuntos/prestadores/padrao-para-troca-de-informacao-de-saude-suplementar-2013-tiss/codigos-da-tuss

Em 08/10/2026, a ANS indica o componente TUSS `202609` como vigente no Padrao TISS de setembro/2026: https://www.gov.br/ans/pt-br/assuntos/prestadores/padrao-para-troca-de-informacao-de-saude-suplementar-2013-tiss/padrao-tiss-setembro-2026

A primeira tentativa de carga via API expirou durante a paginacao. Nenhum catalogo parcial foi persistido. A disponibilidade da API e necessaria para executar o script.

## API

- `GET /api/catalogos`: lista fontes, versoes e contagens.
- `GET /api/catalogos/{id}/procedimentos?codigo=10102019`: consulta o codigo preservado como texto.
- O parametro opcional `data=2026-01-15` filtra a vigencia informada pela fonte.
- `POST /api/catalogos`: importa uma tabela completa em uma transacao. Campos: `tabela` (`TUSS` ou `AMB_92`), `versao`, `fonte`, `consultadoEm` e `procedimentos`.
- Cada procedimento recebe `codigo`, `descricao`, `inicioVigencia` e `fimVigencia` (datas opcionais). Codigos repetidos e intervalos invalidos sao rejeitados.

A AMB 92 ainda nao foi carregada. O codigo `20010` observado no hospital nao foi classificado automaticamente como AMB 92. O catalogo ainda nao esta integrado ao motor de conciliacao.
