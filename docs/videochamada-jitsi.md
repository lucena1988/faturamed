# Videochamada do chat (Jitsi próprio)

O botão **📹 Vídeo** do FaturaChat abre uma sala do **Jitsi Meet** instalado num servidor nosso.
As imagens e o áudio não passam por serviços de terceiros (ponto importante para a LGPD).

Enquanto `VIDEO_DOMINIO` estiver vazio, o botão não aparece e o resto do chat funciona normalmente.

## O que é preciso

| Item | Para quê | Custo aproximado |
|---|---|---|
| **VPS** (Ubuntu 22.04 ou 24.04, 4 GB de RAM, 2 vCPU) | computador ligado 24 h onde o Jitsi roda | R$ 50 a 100/mês |
| **Subdomínio** apontando para a VPS, ex.: `video.suaempresa.com.br` | endereço da sala de vídeo | já incluso no domínio que vocês têm |
| **HTTPS** (cadeado) | o navegador só libera câmera e microfone com HTTPS | grátis (Let's Encrypt, automático) |

Sugestões de VPS: Hostinger KVM 2 (mais barato, datacenter no Brasil) ou Magalu Cloud (empresa e dados no Brasil).

## Passo a passo na VPS

1. **DNS:** no painel do domínio, crie um registro `A` de `video` apontando para o IP da VPS
   (no Cloudflare, deixe a nuvem cinza, "DNS only"). Aguarde propagar.
2. **Portas liberadas no firewall da VPS:** `80/tcp`, `443/tcp` e `10000/udp` (o vídeo trafega por essa porta UDP).
3. **Instale o Docker:**
   ```bash
   curl -fsSL https://get.docker.com | sh
   ```
4. **Baixe o Jitsi (versão estável oficial):**
   ```bash
   wget $(curl -s https://api.github.com/repos/jitsi/docker-jitsi-meet/releases/latest | grep 'zip' | cut -d\" -f4) -O jitsi.zip
   unzip jitsi.zip && cd jitsi-docker-jitsi-meet-*
   cp env.example .env && ./gen-passwords.sh
   mkdir -p ~/.jitsi-meet-cfg/{web,transcripts,prosody/config,prosody/prosody-plugins-custom,jicofo,jvb,jigasi,jibri}
   ```
5. **Edite o `.env`** e ajuste:
   ```ini
   PUBLIC_URL=https://video.suaempresa.com.br
   HTTP_PORT=80
   HTTPS_PORT=443
   ENABLE_LETSENCRYPT=1
   LETSENCRYPT_DOMAIN=video.suaempresa.com.br
   LETSENCRYPT_EMAIL=ti@suaempresa.com.br
   TZ=America/Sao_Paulo
   # nao gravar nem transcrever chamadas
   ENABLE_RECORDING=0
   ENABLE_TRANSCRIPTIONS=0
   ```
6. **Suba:** `docker compose up -d`. Abra `https://video.suaempresa.com.br` e confira se uma sala de teste abre com o cadeado.

## Ligar no FaturaMed

No Railway (serviço `faturamed`, aba **Variables**), primeiro em homologação e depois em produção:

```ini
VIDEO_DOMINIO=video.suaempresa.com.br
```

O Railway reinicia sozinho. A aplicação passa a liberar na CSP apenas esse endereço
(`script-src` para o `external_api.js` e `frame-src` para a sala).

## Como funciona para o usuário

- Na conversa (1:1 ou grupo), **📹 Vídeo** cria uma sala com nome aleatório e impossível de adivinhar, registra
  "📹 Iniciou uma chamada de vídeo" no histórico e faz "tocar" para os demais participantes que estão com o chat aberto.
- Quem recebe vê o aviso **Entrar / Agora não** (e uma notificação do navegador, se a aba estiver em segundo plano).
- A sala abre em tela cheia dentro do FaturaMed; **Sair da chamada** fecha.

## LGPD e segurança

- Chamadas **não são gravadas** nem transcritas; nada da chamada fica guardado no FaturaMed (só a linha no histórico do chat).
- O nome da sala é sorteado a cada chamada (96 bits aleatórios) e só circula entre os participantes da conversa.
- Para restringir ainda mais (ninguém fora do FaturaMed cria salas), dá para ligar a autenticação por token (JWT) do Jitsi
  numa etapa futura.
