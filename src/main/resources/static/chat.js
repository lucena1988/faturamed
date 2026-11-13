// Chat interno da equipe: conversas 1:1 e em grupo, mensagens em tempo real (SSE), nao lidas, quem esta online
// e videochamada pelo Jitsi proprio (quando configurado no servidor).
(function () {
  const $ = id => document.getElementById(id);
  const api = (url, init) => window.FaturamedAuth.request(url, init);
  const json = (body, method = 'POST') => ({method, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
  const painel = $('chat-painel');
  let eu = null, conversas = [], atual = null, mensagens = [], leituraContato = 0, temMais = false;
  let fonte = null, digitandoTimer = null, ultimoDigitando = 0;
  // ?janela=1: conversa aberta em janela propria (duplo clique na lista), so com a conversa
  const modoJanela = new URLSearchParams(location.search).get('janela');
  const emJanela = modoJanela === '1';
  // ?janela=avulsa: chat completo (lista + conversa) em janela propria, aberto pelo menu
  if (emJanela) document.documentElement.classList.add('chat-janela');
  if (modoJanela === 'avulsa') document.documentElement.classList.add('chat-avulso');
  // conversa aberta pelo hash (ex.: clique no widget com a janela ja aberta)
  window.addEventListener('hashchange', () => {
    const alvo = Number((location.hash.match(/^#c(\d+)$/) || [])[1]);
    if (alvo && alvo !== atual?.id) abrir(alvo);
  });

  const grupo = c => c?.tipo === 'GRUPO';
  const hora = iso => {
    const d = new Date(iso), hoje = new Date();
    const hm = d.toLocaleTimeString('pt-BR', {hour: '2-digit', minute: '2-digit'});
    return d.toDateString() === hoje.toDateString() ? hm : d.toLocaleDateString('pt-BR', {day: '2-digit', month: '2-digit'}) + ' ' + hm;
  };
  const iniciais = nome => (nome || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join('');
  const primeiroNome = nome => (nome || '').split(/\s+/)[0];
  const el = (tag, cls, texto) => { const e = document.createElement(tag); if (cls) e.className = cls; if (texto != null) e.textContent = texto; return e; };

  function aviso(texto) { const p = $('chat-aviso'); p.textContent = texto || ''; p.hidden = !texto; }

  // cor fixa por nome, para cada pessoa ser reconhecida de relance
  const cor = nome => { let h = 0; for (const ch of nome || '') h = (h * 31 + ch.charCodeAt(0)) >>> 0; return 'cor-' + (h % 6); };
  // foto de perfil quando a pessoa tem uma; senao, iniciais coloridas
  const urlFoto = (id, versao) => `/api/usuarios/${id}/foto?v=${versao}`;
  function avatar(nome, online, ehGrupo, foto, usuarioId, status) {
    const a = el('span', 'chat-avatar ' + (ehGrupo ? 'grupo' : cor(nome)), ehGrupo ? '#' : iniciais(nome));
    a.setAttribute('aria-hidden', 'true');
    a.classList.toggle('online', !!online);
    if (online && status && status !== 'DISPONIVEL') a.dataset.status = status;
    if (!ehGrupo && foto && usuarioId) {
      const img = document.createElement('img'); img.alt = ''; img.src = urlFoto(usuarioId, foto); img.loading = 'lazy';
      img.addEventListener('error', () => { img.remove(); a.classList.remove('com-foto'); });
      a.classList.add('com-foto'); a.append(img);
    }
    return a;
  }
  const avatarConversa = c => avatar(c.nome, c.contato?.online, grupo(c), c.contato?.foto, c.contato?.id, c.contato?.status);

  function desenharLista() {
    const termo = $('chat-busca').value.trim().toLowerCase();
    const ul = $('chat-conversas'); ul.replaceChildren();
    $('chat-vazio').hidden = conversas.length > 0;
    for (const c of conversas.filter(c => !termo || c.nome.toLowerCase().includes(termo))) {
      const b = el('button', 'chat-item' + (atual?.id === c.id ? ' ativo' : '') + (c.naoLidas ? ' nao-lida' : ''));
      b.type = 'button';
      const corpo = el('span', 'chat-item-corpo');
      const linha = el('span', 'chat-item-linha');
      const nome = el('strong', null, c.nome);
      if (c.silenciado) { const s = el('span', 'chat-mudo', ' 🔕'); s.title = 'Som silenciado'; nome.append(s); }
      linha.append(nome, el('time', null, c.ultimaMensagemEm ? hora(c.ultimaMensagemEm) : ''));
      let prev = 'Sem mensagens';
      if (c.ultimaMensagem) {
        const autor = c.ultimoAutorId === eu?.id ? 'Você: ' : grupo(c) ? primeiroNome(c.ultimoAutorNome) + ': ' : '';
        prev = autor + c.ultimaMensagem;
      } else if (grupo(c)) prev = `Grupo com ${c.participantes} pessoas`;
      const linha2 = el('span', 'chat-item-linha');
      linha2.append(el('small', null, prev));
      if (c.mencoesNaoLidas) { const m = el('span', 'chat-mencao-badge', '@'); m.title = 'Você foi mencionado'; linha2.append(m); }
      if (c.naoLidas) linha2.append(el('span', 'nav-count', c.naoLidas > 99 ? '99+' : String(c.naoLidas)));
      corpo.append(linha, linha2);
      b.append(avatarConversa(c), corpo);
      b.title = grupo(c) ? `Grupo · ${c.participantes} pessoas` : c.contato?.online ? 'Online' : 'Offline';
      b.addEventListener('click', () => abrir(c.id));
      b.addEventListener('dblclick', () => abrirJanela(c));
      b.title += ' · duplo clique abre em janela separada';
      const li = el('li'); li.append(b); ul.append(li);
    }
    atualizarContadorMenu();
  }

  function atualizarContadorMenu() {
    const total = conversas.reduce((s, c) => s + c.naoLidas, 0);
    if (emJanela) return;
    if (window.fmChatTotal) window.fmChatTotal(total);
    document.title = (total ? `(${total}) ` : '') + 'FaturaChat | FaturaMed';
  }

  function desenharCabecalho() {
    $('chat-nome').textContent = atual.nome;
    const status = $('chat-status');
    if (atual.sistema) {
      status.textContent = 'avisos automáticos do sistema';
      status.classList.remove('online');
    } else if (grupo(atual)) {
      status.textContent = `${atual.oficial ? '🏛 Grupo oficial · ' : ''}${atual.participantes} participantes`;
      status.classList.remove('online');
    } else {
      const c = atual.contato;
      const rotulo = c?.online ? (STATUS_ROTULO[c.status] || '● online') : 'offline';
      status.textContent = rotulo + (c?.recado ? ` — ${c.recado}` : '');
      status.classList.toggle('online', !!c?.online);
    }
    $('chat-avatar').replaceWith(Object.assign(avatarConversa(atual), {id: 'chat-avatar'}));
    $('chat-participantes-abrir').hidden = !grupo(atual);
    $('chat-video').hidden = !video.ativo;
    const mudo = $('chat-silenciar');
    mudo.textContent = atual.silenciado ? '🔕' : '🔔';
    mudo.title = atual.silenciado ? 'Som desta conversa silenciado: clique para reativar' : 'Silenciar o som desta conversa';
    mudo.setAttribute('aria-label', mudo.title);
    mudo.setAttribute('aria-pressed', String(!!atual.silenciado));
    if (emJanela) document.title = `${atual.nome} | FaturaChat`;
    $('chat-exportar').href = `/api/chat/conversas/${atual.id}/exportar`;
    // conversa com o Robo: so leitura
    $('chat-aviso-sistema').hidden = !atual.sistema;
    $('chat-form').hidden = !!atual.sistema || !!gravador;
    $('chat-silenciar').hidden = false;
    desenharFixada();
  }
  const STATUS_ROTULO = {DISPONIVEL: '● disponível', OCUPADO: '● ocupado', REUNIAO: '● em reunião', AUSENTE: '● ausente'};

  let novasAbaixo = 0;
  const pertoDoFim = () => { const c = $('chat-mensagens'); return c.scrollHeight - c.scrollTop - c.clientHeight < 120; };
  function rolarParaFim(suave) {
    const c = $('chat-mensagens');
    c.scrollTo({top: c.scrollHeight, behavior: suave && !matchMedia('(prefers-reduced-motion: reduce)').matches ? 'smooth' : 'auto'});
    novasAbaixo = 0; atualizarBotaoNovas();
  }
  function atualizarBotaoNovas() {
    const b = $('chat-novas');
    b.hidden = novasAbaixo === 0;
    b.textContent = `↓ ${novasAbaixo} nova${novasAbaixo > 1 ? 's' : ''} mensage${novasAbaixo > 1 ? 'ns' : 'm'}`;
  }

  function desenharMensagens(rolarFim) {
    const caixa = $('chat-mensagens'), ol = $('chat-lista-mensagens');
    const distanciaFim = caixa.scrollHeight - caixa.scrollTop;
    ol.replaceChildren();
    let dia = null, autorAnterior = null;
    const ultimaMinha = [...mensagens].reverse().find(m => m.autorId === eu.id);
    for (const m of mensagens) {
      const d = new Date(m.criadoEm).toLocaleDateString('pt-BR', {weekday: 'long', day: '2-digit', month: 'long'});
      if (d !== dia) { dia = d; autorAnterior = null; ol.append(el('li', 'chat-dia', d)); }
      const minha = m.autorId === eu.id;
      const li = el('li', 'chat-msg' + (minha ? ' minha' : ''));
      // foto de quem escreveu ao lado do balao (na primeira mensagem de cada sequencia)
      if (!minha && autorAnterior !== m.autorId) {
        const av = avatar(m.autorNome, false, false, m.autorFoto, m.autorId);
        av.classList.add('chat-msg-avatar'); av.title = m.autorNome; li.append(av); li.classList.add('com-avatar');
      } else if (!minha) li.classList.add('sequencia');
      if (grupo(atual) && !minha && autorAnterior !== m.autorId) li.append(el('span', 'chat-msg-autor', m.autorNome));
      autorAnterior = m.autorId;
      li.dataset.id = m.id;
      if (m.sistema) li.classList.add('sistema');
      if ((m.mencionados || []).includes(eu.id)) li.classList.add('mencionou-voce');
      if (m.apagada) {
        li.classList.add('apagada');
        li.append(el('p', 'chat-msg-apagada', '🚫 Mensagem apagada'));
      } else {
        if (m.resposta) li.append(blocoResposta(m.resposta));
        if (m.anexos?.length) li.append(desenharAnexos(m.anexos));
        if (m.texto) { const p = el('p'); p.append(textoComLinks(m.texto)); li.append(p); if (window.fmChatRefs?.extrair(m.texto).length) { const c = el('div'); li.append(c); window.fmChatRefs.cartoes(c, m.texto); } }
        if (!atual.sistema) li.append(acoesMensagem(m, minha));
      }
      if (m.reacoes?.length) li.append(desenharReacoes(m));
      const rodape = el('small', null, (m.editada && !m.apagada ? 'editada · ' : '') + new Date(m.criadoEm).toLocaleTimeString('pt-BR', {hour: '2-digit', minute: '2-digit'}));
      if (minha && m === ultimaMinha) {
        const visto = m.id <= leituraContato;
        rodape.append(el('span', 'chat-visto', visto ? (grupo(atual) ? ' ✓✓ visto por todos' : ' ✓✓ visto') : ' ✓'));
      }
      li.append(rodape);
      ol.append(li);
    }
    $('chat-anteriores').hidden = !temMais;
    caixa.scrollTop = rolarFim ? caixa.scrollHeight : caixa.scrollHeight - distanciaFim;
  }

  // ---- texto com links: "#lote 112", "#chamado 42", @menções e endereços web viram links seguros (sem innerHTML)
  const PADRAO_LINKS = /(#lote\s?(\d{1,9}))|(#chamado\s?(\d{1,9}))|(https?:\/\/[^\s<>"']+)|(@[\p{L}][\p{L}.\-]{1,40})|(#(relatorio|div)\s?(\d{1,12}(?:\/[0-9A-Za-z.\-]{1,40})?))/giu;
  function textoComLinks(texto) {
    const frag = document.createDocumentFragment();
    let ultimo = 0;
    for (const m of texto.matchAll(PADRAO_LINKS)) {
      if (m.index > ultimo) frag.append(texto.slice(ultimo, m.index));
      let no;
      if (m[1]) { no = el('a', 'chat-link-ref', `📦 Lote ${m[2]}`); no.href = `producao.html?folha=${m[2]}`; no.target = '_blank'; no.rel = 'noopener'; }
      else if (m[3]) { no = el('a', 'chat-link-ref', `🎫 Chamado ${m[4]}`); no.href = `chamados.html?chamado=${m[4]}`; no.target = '_blank'; no.rel = 'noopener'; }
      else if (m[5]) { no = el('a', 'chat-link', m[5]); no.href = m[5]; no.target = '_blank'; no.rel = 'noopener noreferrer'; }
      else if (m[7]) {
        const [rel, at] = m[9].split('/');
        no = el('a', 'chat-link-ref', m[8].toLowerCase() === 'div' ? `⚠️ Atendimento ${at || rel}` : `📊 Conciliação ${rel}`);
        no.href = `conciliacao.html?relatorio=${rel}${at ? '&atendimento=' + encodeURIComponent(at) : ''}`; no.target = '_blank'; no.rel = 'noopener';
      }
      else {
        no = el('span', 'chat-mencao', m[6]);
        if (eu && souEu(m[6].slice(1))) no.classList.add('voce');
      }
      frag.append(no);
      ultimo = m.index + m[0].length;
    }
    if (ultimo < texto.length) frag.append(texto.slice(ultimo));
    return frag;
  }

  // mencoes: "@Ana" (primeiro nome) ou "@Ana.Souza" (nome completo com pontos); sem acento e sem maiusculas
  const normalizar = s => (s || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim().replace(/\s+/g, ' ');
  const nomeMencao = nome => (nome || '').trim().split(/\s+/).join('.');
  function souEu(token) {
    const t = normalizar(token).replace(/[.\-]+$/, '');
    const nome = normalizar(eu?.nome);
    return t === 'todos' || t === nome.replace(/ /g, '.') || t === nome.split(' ')[0];
  }

  // sugestao de nomes ao digitar "@": participantes da conversa (e @todos em grupo)
  let candidatosMencao = [], mencaoAtiva = -1, cacheParticipantes = {};
  async function pessoasDaConversa() {
    if (!atual) return [];
    if (!grupo(atual)) return atual.contato ? [{id: atual.contato.id, nome: atual.contato.nome, foto: atual.contato.foto}] : [];
    if (!cacheParticipantes[atual.id]) {
      try { cacheParticipantes[atual.id] = (await api(`/api/chat/conversas/${atual.id}/participantes`)).filter(p => p.id !== eu.id); }
      catch (_) { return []; }
    }
    return cacheParticipantes[atual.id];
  }
  function termoMencao() {
    const t = $('chat-texto'), antes = t.value.slice(0, t.selectionStart);
    const m = antes.match(/(^|\s)@([\p{L}.\-]{0,40})$/u);
    return m ? {termo: m[2], inicio: antes.length - m[2].length - 1} : null;
  }
  async function sugerirMencao() {
    const caixa = $('chat-mencoes');
    const alvo = termoMencao();
    if (!alvo || atual?.sistema) { caixa.hidden = true; return; }
    const termo = normalizar(alvo.termo).replace(/\./g, ' ');
    const pessoas = await pessoasDaConversa();
    candidatosMencao = pessoas.filter(p => !termo || normalizar(p.nome).startsWith(termo) || normalizar(p.nome).split(' ').some(parte => parte.startsWith(termo))).slice(0, 8);
    if (grupo(atual) && 'todos'.startsWith(termo)) candidatosMencao.push({id: 0, nome: 'todos', todos: true}); if (grupo(atual)) [['equipe', 'toda a equipe interna'], ['admin', 'administradores'], ['operador', 'operadores'], ['dev', 'desenvolvedores']].filter(([n]) => n.startsWith(termo)).forEach(([n, d]) => candidatosMencao.push({id: 0, nome: n, todos: true, rotulo: '@' + n + ' — ' + d}));
    caixa.replaceChildren();
    if (!candidatosMencao.length) { caixa.hidden = true; return; }
    mencaoAtiva = 0;
    candidatosMencao.forEach((p, i) => {
      const b = el('button', 'chat-mencao-opcao' + (i === 0 ? ' ativo' : '')); b.type = 'button';
      b.setAttribute('role', 'option');
      b.append(p.todos ? el('span', 'chat-avatar grupo', '@') : avatar(p.nome, false, false, p.foto, p.id),
        el('span', null, p.todos ? (p.rotulo || '@todos — avisar todo o grupo') : p.nome));
      b.addEventListener('mousedown', e => { e.preventDefault(); escolherMencao(i); });
      caixa.append(b);
    });
    caixa.hidden = false;
  }
  function escolherMencao(i) {
    const p = candidatosMencao[i], alvo = termoMencao();
    if (!p || !alvo) return;
    const t = $('chat-texto');
    const inserir = '@' + (p.todos ? p.nome : nomeMencao(p.nome)) + ' ';
    t.value = t.value.slice(0, alvo.inicio) + inserir + t.value.slice(t.selectionStart);
    t.selectionStart = t.selectionEnd = alvo.inicio + inserir.length;
    $('chat-mencoes').hidden = true; ajustarAltura(); t.focus();
  }
  function teclaMencao(e) {
    const caixa = $('chat-mencoes');
    if (caixa.hidden) return false;
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      mencaoAtiva = (mencaoAtiva + (e.key === 'ArrowDown' ? 1 : -1) + candidatosMencao.length) % candidatosMencao.length;
      [...caixa.children].forEach((b, i) => b.classList.toggle('ativo', i === mencaoAtiva));
      return true;
    }
    if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); escolherMencao(mencaoAtiva); return true; }
    if (e.key === 'Escape') { caixa.hidden = true; return true; }
    return false;
  }

  function blocoResposta(r) {
    const b = el('button', 'chat-msg-resposta'); b.type = 'button';
    b.title = 'Ir para a mensagem original';
    b.append(el('strong', null, r.autorNome), el('span', null, r.texto));
    b.addEventListener('click', () => irParaMensagem(r.id));
    return b;
  }

  const PRAZO_EDICAO_MS = 15 * 60 * 1000;
  const REACOES = ['👍', '✅', '👀', '❤️', '😂', '🙏', '🎉', '😮'];
  function acoesMensagem(m, minha) {
    const barra = el('div', 'chat-msg-acoes');
    barra.setAttribute('role', 'toolbar'); barra.setAttribute('aria-label', 'Ações da mensagem');
    const botao = (rotulo, titulo, acao) => { const b = el('button', null, rotulo); b.type = 'button'; b.title = titulo; b.setAttribute('aria-label', titulo); b.addEventListener('click', e => { e.stopPropagation(); acao(b); }); barra.append(b); return b; };
    botao('😊', 'Reagir', b => abrirReacoes(b, m));
    botao('↩', 'Responder', () => responder(m));
    if (!(atual.oficial && eu.perfil !== 'ADMIN')) botao('📌', atual.fixada?.id === m.id ? 'Desafixar' : 'Fixar no topo', () => fixar(atual.fixada?.id === m.id ? null : m.id));
    if (minha && Date.now() - new Date(m.criadoEm).getTime() < PRAZO_EDICAO_MS) {
      if (m.texto) botao('✏️', 'Editar (até 15 min)', () => editar(m));
      botao('🗑', 'Apagar (até 15 min)', () => apagar(m));
    }
    return barra;
  }

  function abrirReacoes(ancora, m) {
    document.querySelector('.chat-reacoes-menu')?.remove();
    const menu = el('div', 'chat-reacoes-menu');
    menu.setAttribute('role', 'menu');
    for (const emoji of REACOES) {
      const b = el('button', null, emoji); b.type = 'button'; b.setAttribute('role', 'menuitem'); b.title = `Reagir com ${emoji}`;
      b.addEventListener('click', e => { e.stopPropagation(); menu.remove(); reagir(m.id, emoji); });
      menu.append(b);
    }
    ancora.closest('.chat-msg').append(menu);
    menu.querySelector('button').focus();
    setTimeout(() => document.addEventListener('click', () => menu.remove(), {once: true}));
  }

  function desenharReacoes(m) {
    const linha = el('div', 'chat-reacoes');
    for (const r of m.reacoes) {
      const minhaReacao = r.usuarios.includes(eu.id);
      const b = el('button', 'chat-reacao' + (minhaReacao ? ' minha' : ''), `${r.emoji} ${r.usuarios.length}`); b.type = 'button';
      b.title = r.nomes.join(', ') + (minhaReacao ? ' · clique para remover a sua' : ' · clique para reagir também');
      b.setAttribute('aria-pressed', String(minhaReacao));
      b.disabled = m.apagada;
      b.addEventListener('click', () => reagir(m.id, r.emoji));
      linha.append(b);
    }
    return linha;
  }

  async function reagir(id, emoji) {
    try { trocarMensagem(await api(`/api/chat/mensagens/${id}/reacoes`, json({emoji}))); } catch (e) { aviso(e.message); }
  }

  let respondendo = null;
  function responder(m) {
    respondendo = m;
    const caixa = $('chat-respondendo');
    caixa.querySelector('strong').textContent = `Respondendo a ${m.autorId === eu.id ? 'você mesmo' : m.autorNome}`;
    caixa.querySelector('span').textContent = m.texto || (m.anexos?.length ? '📎 Anexo' : '');
    caixa.hidden = false;
    $('chat-texto').focus();
  }
  function cancelarResposta() { respondendo = null; $('chat-respondendo').hidden = true; }

  async function editar(m) {
    const novo = prompt('Editar mensagem (até 15 minutos após o envio):', m.texto);
    if (novo == null || !novo.trim() || novo.trim() === m.texto) return;
    try { trocarMensagem(await api(`/api/chat/mensagens/${m.id}`, json({texto: novo}, 'PUT'))); } catch (e) { aviso(e.message); }
  }

  async function apagar(m) {
    const ok = window.fmConfirmar ? await window.fmConfirmar('Apagar esta mensagem para todos? Os anexos dela também serão removidos.', {titulo: 'Apagar mensagem', confirmar: 'Apagar', perigo: true}) : confirm('Apagar esta mensagem para todos?');
    if (!ok) return;
    try { trocarMensagem(await api(`/api/chat/mensagens/${m.id}`, {method: 'DELETE'})); } catch (e) { aviso(e.message); }
  }

  async function fixar(mensagemId) {
    if (!atual) return;
    try {
      const c = await api(`/api/chat/conversas/${atual.id}/fixada`, json({mensagemId}, 'PUT'));
      Object.assign(atual, {fixada: c.fixada}); desenharFixada(); desenharMensagens(false);
    } catch (e) { aviso(e.message); }
  }

  function desenharFixada() {
    const faixa = $('chat-fixada');
    const f = atual?.fixada;
    faixa.hidden = !f;
    if (!f) return;
    faixa.querySelector('.chat-fixada-texto').textContent = `${f.autorNome}: ${f.texto}`;
    faixa.querySelector('.chat-fixada-soltar').hidden = !!(atual.oficial && eu.perfil !== 'ADMIN');
  }

  // substitui uma mensagem ja exibida (reacao, edicao ou apagada) sem recarregar a conversa
  function trocarMensagem(msg) {
    const i = mensagens.findIndex(m => m.id === msg.id);
    if (i >= 0 && atual?.id === msg.conversaId) { mensagens[i] = msg; desenharMensagens(false); }
    if (atual?.fixada?.id === msg.id && msg.apagada) { atual.fixada = null; desenharFixada(); }
  }

  // abre a conversa na mensagem (resultado de busca, mensagem fixada ou resposta citada)
  async function irParaMensagem(id, conversaId) {
    if (conversaId && atual?.id !== conversaId) await abrir(conversaId);
    if (!mensagens.some(m => m.id === id)) {
      try {
        const pagina = await api(`/api/chat/conversas/${atual.id}/mensagens?antesDe=${id + 1}`);
        mensagens = pagina.mensagens; temMais = pagina.temMais; desenharMensagens(false);
      } catch (e) { aviso(e.message); return; }
    }
    const li = document.querySelector(`.chat-msg[data-id="${id}"]`);
    if (!li) return;
    li.scrollIntoView({block: 'center', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'});
    li.classList.remove('destaque'); void li.offsetWidth; li.classList.add('destaque');
  }

  function fecharConversa() {
    atual = null;
    $('chat-sem-conversa').hidden = false; $('chat-aberta').hidden = true;
    painel.classList.remove('com-conversa');
    try { history.replaceState(null, '', location.pathname); } catch (_) {}
    desenharLista();
  }

  async function abrir(id) {
    atual = conversas.find(c => c.id === id);
    if (!atual) return;
    try { history.replaceState(null, '', location.search + '#c' + id); } catch (_) {}
    $('chat-sem-conversa').hidden = true; $('chat-aberta').hidden = false;
    painel.classList.add('com-conversa');
    $('chat-digitando').hidden = true;
    desenharCabecalho();
    mensagens = []; temMais = false; novasAbaixo = 0; atualizarBotaoNovas(); desenharMensagens(true);
    try {
      const pagina = await api(`/api/chat/conversas/${id}/mensagens`);
      if (atual?.id !== id) return;
      mensagens = pagina.mensagens; temMais = pagina.temMais; leituraContato = pagina.leituraContato;
      desenharMensagens(true);
      marcarLida();
    } catch (e) { aviso(e.message); }
    desenharLista();
    $('chat-texto').focus();
  }

  async function anteriores() {
    if (!atual || !mensagens.length) return;
    try {
      const pagina = await api(`/api/chat/conversas/${atual.id}/mensagens?antesDe=${mensagens[0].id}`);
      mensagens = pagina.mensagens.concat(mensagens); temMais = pagina.temMais;
      desenharMensagens(false);
    } catch (e) { aviso(e.message); }
  }

  function marcarLida() {
    if (!atual || !mensagens.length || document.hidden) return;
    const ultima = mensagens[mensagens.length - 1].id;
    if (atual.naoLidas || atual.mencoesNaoLidas) { atual.naoLidas = 0; atual.mencoesNaoLidas = 0; desenharLista(); }
    api(`/api/chat/conversas/${atual.id}/leitura`, json({ateId: ultima})).catch(() => {});
  }

  let enviando = false;
  // dados de paciente nao devem circular pelo chat: avisa antes de enviar CPF ou numero longo (carteirinha)
  const PADRAO_SENSIVEL = /\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b|\b\d{12,20}\b/;
  async function confirmarSensivel(texto) {
    if (!PADRAO_SENSIVEL.test(texto)) return true;
    const msg = 'A mensagem parece ter um CPF ou número de carteirinha. Dados de pacientes devem ficar no sistema, não no chat (LGPD). Enviar mesmo assim?';
    return window.fmConfirmar ? window.fmConfirmar(msg, {titulo: 'Possível dado sensível', confirmar: 'Enviar mesmo assim'}) : confirm(msg);
  }

  async function enviar(ev) {
    ev.preventDefault();
    const campo = $('chat-texto'), texto = campo.value.trim();
    if ((!texto && !pendentes.length) || !atual || enviando) return;
    if (texto && !(await confirmarSensivel(texto))) return;
    const arquivos = pendentes.slice();
    const respondeA = respondendo?.id || null;
    campo.value = ''; ajustarAltura(); alternarEmojis(false);
    pendentes = []; desenharPrevia(); cancelarResposta();
    enviando = true;
    try {
      let msg;
      if (arquivos.length) {
        const dados = new FormData();
        if (texto) dados.append('texto', texto);
        if (respondeA) dados.append('respondeA', respondeA);
        arquivos.forEach(a => dados.append('arquivos', a, a.name));
        aviso(`Enviando ${arquivos.length} arquivo(s)…`);
        msg = await api(`/api/chat/conversas/${atual.id}/mensagens`, {method: 'POST', body: dados});
        aviso('');
      } else {
        msg = await api(`/api/chat/conversas/${atual.id}/mensagens`, json({texto, respondeA}));
      }
      receberMensagem(msg);
    } catch (e) {
      campo.value = texto; pendentes = arquivos; desenharPrevia(); aviso(e.message);
    } finally { enviando = false; atualizarBotaoEnvio(); }
  }

  // ---- anexos: clipe, colar (Ctrl+V) e arrastar; previa antes de enviar
  const EXTENSOES = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'odt', 'ods', 'csv', 'txt'];
  const LIMITE_MB = 10, LIMITE_ARQUIVOS = 5;
  let pendentes = [];
  const extensao = nome => (nome.split('.').pop() || '').toLowerCase();
  const tamanhoLegivel = b => b < 1024 ? `${b} B` : b < 1048576 ? `${Math.round(b / 1024)} KB` : `${(b / 1048576).toFixed(1).replace('.', ',')} MB`;
  const ehImagem = nome => ['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(extensao(nome));
  const ICONE_ARQUIVO = {pdf: ['PDF', 'pdf'], doc: ['DOC', 'doc'], docx: ['DOC', 'doc'], odt: ['DOC', 'doc'], xls: ['XLS', 'xls'], xlsx: ['XLS', 'xls'],
    ods: ['XLS', 'xls'], csv: ['CSV', 'xls'], ppt: ['PPT', 'ppt'], pptx: ['PPT', 'ppt'], txt: ['TXT', 'txt']};

  function adicionarArquivos(lista) {
    for (const original of lista) {
      let arquivo = original;
      // imagem colada vem sem nome util ("image.png"): da um nome com data e hora
      if (/^image\.(png|jpe?g|gif|webp)$/i.test(arquivo.name) || !arquivo.name) {
        const ext = (arquivo.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
        const agora = new Date();
        const marca = agora.toLocaleDateString('pt-BR').split('/').reverse().join('-') + '_' + agora.toTimeString().slice(0, 8).replaceAll(':', '');
        arquivo = new File([arquivo], `imagem-colada_${marca}.${ext}`, {type: arquivo.type});
      }
      if (!EXTENSOES.includes(extensao(arquivo.name))) { aviso(`Tipo não permitido: ${arquivo.name}. Use imagem, PDF, Word, Excel, PowerPoint, CSV ou TXT.`); continue; }
      if (arquivo.size > LIMITE_MB * 1048576) { aviso(`${arquivo.name} passa de ${LIMITE_MB} MB.`); continue; }
      if (pendentes.length >= LIMITE_ARQUIVOS) { aviso(`No máximo ${LIMITE_ARQUIVOS} arquivos por mensagem.`); break; }
      pendentes.push(arquivo);
    }
    desenharPrevia();
    $('chat-texto').focus();
  }

  function desenharPrevia() {
    const ul = $('chat-anexos-previa');
    ul.querySelectorAll('img').forEach(i => URL.revokeObjectURL(i.src));
    ul.replaceChildren();
    ul.hidden = pendentes.length === 0;
    atualizarBotaoEnvio();
    pendentes.forEach((a, i) => {
      const li = el('li', 'chat-previa-item');
      if (ehImagem(a.name)) {
        const img = document.createElement('img'); img.src = URL.createObjectURL(a); img.alt = '';
        li.append(img);
      } else {
        const [rotulo, cor] = ICONE_ARQUIVO[extensao(a.name)] || ['ARQ', 'txt'];
        li.append(el('span', `chat-arquivo-icone ${cor}`, rotulo));
      }
      const info = el('span', 'chat-previa-info');
      info.append(el('strong', null, a.name), el('small', null, tamanhoLegivel(a.size)));
      const rm = el('button', 'chat-previa-remover', '✕'); rm.type = 'button';
      rm.title = `Remover ${a.name}`; rm.setAttribute('aria-label', rm.title);
      rm.addEventListener('click', () => { pendentes.splice(i, 1); desenharPrevia(); });
      li.append(info, rm);
      ul.append(li);
    });
  }

  // ---- configuracoes de audio: microfone e saida de som escolhidos (guardados so neste navegador)
  const CHAVE_AUDIO = 'fm-chat-audio';
  const prefsAudio = () => { try { return JSON.parse(localStorage.getItem(CHAVE_AUDIO) || '{}'); } catch (_) { return {}; } };
  const salvarPrefsAudio = p => { try { localStorage.setItem(CHAVE_AUDIO, JSON.stringify(p)); } catch (_) {} };
  const podeEscolherSaida = typeof HTMLMediaElement !== 'undefined' && 'setSinkId' in HTMLMediaElement.prototype;
  let fluxoTeste = null, analisador = null, quadroNivel = 0;

  function aplicarSaida(audio) {
    const saida = prefsAudio().saida;
    if (saida && podeEscolherSaida) audio.setSinkId(saida).catch(() => {});
  }

  async function listarAparelhos() {
    const aparelhos = await navigator.mediaDevices.enumerateDevices();
    const prefs = prefsAudio();
    const semNome = aparelhos.some(a => !a.label);
    $('chat-config-permissao').hidden = !semNome;
    const preencher = (select, tipo, escolhido, rotuloPadrao) => {
      select.replaceChildren(Object.assign(document.createElement('option'), {value: '', textContent: rotuloPadrao}));
      aparelhos.filter(a => a.kind === tipo && a.deviceId !== 'default' && a.deviceId !== 'communications').forEach((a, i) => {
        const o = document.createElement('option'); o.value = a.deviceId;
        o.textContent = a.label || `${tipo === 'audioinput' ? 'Microfone' : 'Saída'} ${i + 1}`;
        select.append(o);
      });
      select.value = [...select.options].some(o => o.value === escolhido) ? escolhido : '';
    };
    preencher($('chat-config-mic'), 'audioinput', prefs.mic, 'Padrão do sistema');
    preencher($('chat-config-saida'), 'audiooutput', prefs.saida, 'Padrão do sistema');
    $('chat-config-saida').disabled = !podeEscolherSaida;
    $('chat-config-sem-saida').hidden = podeEscolherSaida;
  }

  // medidor de nivel do microfone escolhido, enquanto a janela esta aberta
  async function medirMicrofone() {
    pararMedidor();
    const mic = $('chat-config-mic').value;
    try {
      fluxoTeste = await navigator.mediaDevices.getUserMedia({audio: mic ? {deviceId: {exact: mic}} : true});
    } catch (_) { return false; }
    const AC = window.AudioContext || window.webkitAudioContext;
    const ctx = new AC();
    analisador = ctx.createAnalyser(); analisador.fftSize = 256;
    ctx.createMediaStreamSource(fluxoTeste).connect(analisador);
    const dados = new Uint8Array(analisador.frequencyBinCount);
    const barra = $('chat-config-nivel-barra');
    const passo = () => {
      analisador.getByteTimeDomainData(dados);
      let pico = 0; for (const v of dados) pico = Math.max(pico, Math.abs(v - 128));
      barra.style.width = Math.min(100, pico / 128 * 220) + '%';
      quadroNivel = requestAnimationFrame(passo);
    };
    passo();
    fluxoTeste.ctx = ctx;
    return true;
  }

  function pararMedidor() {
    cancelAnimationFrame(quadroNivel);
    fluxoTeste?.getTracks().forEach(t => t.stop());
    fluxoTeste?.ctx?.close().catch(() => {});
    fluxoTeste = null; analisador = null;
    const barra = $('chat-config-nivel-barra'); if (barra) barra.style.width = '0';
  }

  // grava 5 s com as configuracoes atuais e toca de volta na saida escolhida, para comparar a qualidade
  async function gravarTeste() {
    const status = $('chat-config-teste-status'), botao = $('chat-config-gravar-teste');
    const p = prefsAudio();
    let entrada, trat;
    try {
      entrada = await navigator.mediaDevices.getUserMedia({audio: {
        ...(p.mic ? {deviceId: {exact: p.mic}} : {}), echoCancellation: true,
        noiseSuppression: p.reduzirRuido !== false, autoGainControl: p.volumeAuto !== false, channelCount: 1, sampleRate: 48000}});
    } catch (_) { status.textContent = 'Não foi possível usar o microfone.'; return; }
    pararMedidor();
    trat = p.tratarVoz !== false ? tratarVoz(entrada) : null;
    const [tipo] = formatoGravacao();
    const rec = new MediaRecorder(trat ? trat.fluxo : entrada, tipo ? {mimeType: tipo, audioBitsPerSecond: 64000} : undefined);
    const partes = [];
    rec.addEventListener('dataavailable', e => { if (e.data.size) partes.push(e.data); });
    rec.addEventListener('stop', () => {
      entrada.getTracks().forEach(t => t.stop()); trat?.fechar();
      const url = URL.createObjectURL(new Blob(partes, {type: rec.mimeType}));
      const audio = new Audio(url); aplicarSaida(audio);
      status.textContent = '▶️ Tocando o teste…';
      audio.addEventListener('ended', () => { status.textContent = 'Teste concluído. Ajuste as opções e grave de novo para comparar.'; URL.revokeObjectURL(url); botao.disabled = false; medirMicrofone(); });
      audio.play().catch(() => { status.textContent = 'Não foi possível tocar o teste.'; botao.disabled = false; });
    });
    botao.disabled = true;
    rec.start();
    let resta = 5;
    status.textContent = `🔴 Gravando… fale normalmente (${resta} s)`;
    const contagem = setInterval(() => {
      resta--;
      if (resta > 0) status.textContent = `🔴 Gravando… fale normalmente (${resta} s)`;
      else { clearInterval(contagem); rec.stop(); }
    }, 1000);
  }

  async function abrirConfigAudio() {
    if (!navigator.mediaDevices?.enumerateDevices) { aviso('Este navegador não permite escolher aparelhos de áudio.'); return; }
    await listarAparelhos();
    const p = prefsAudio();
    $('chat-config-ruido').checked = p.reduzirRuido !== false;
    $('chat-config-volume').checked = p.volumeAuto !== false;
    $('chat-config-voz').checked = p.tratarVoz !== false;
    $('chat-config-teste-status').textContent = '';
    atualizarBotaoNotificar(); carregarRetencao();
    $('chat-config-audio').showModal();
    // so mede se a permissao ja foi dada; senao espera o clique em "Permitir"
    if ($('chat-config-permissao').hidden) medirMicrofone();
  }

  // ---- mensagem de voz (estilo WhatsApp): microfone aparece quando o campo esta vazio
  const podeGravar = !!(navigator.mediaDevices?.getUserMedia && window.MediaRecorder);
  const LIMITE_AUDIO_S = 300;
  let gravador = null, fluxo = null, pedacos = [], inicioGravacao = 0, timerGravacao = null, descartar = false, tratamento = null;

  /**
   * Tratamento de voz antes de gravar: corta o zumbido grave (ar-condicionado, rede eletrica, mesa),
   * corta o chiado agudo, realca a faixa da fala e nivela o volume com um compressor suave.
   */
  function tratarVoz(entrada) {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    const ctx = new AC({sampleRate: 48000});
    const fonte = ctx.createMediaStreamSource(entrada);
    const graves = ctx.createBiquadFilter(); graves.type = 'highpass'; graves.frequency.value = 90; graves.Q.value = 0.7;
    const chiado = ctx.createBiquadFilter(); chiado.type = 'lowpass'; chiado.frequency.value = 9000; chiado.Q.value = 0.7;
    const presenca = ctx.createBiquadFilter(); presenca.type = 'peaking'; presenca.frequency.value = 3000; presenca.Q.value = 1; presenca.gain.value = 3;
    const compressor = ctx.createDynamicsCompressor();
    compressor.threshold.value = -26; compressor.knee.value = 12; compressor.ratio.value = 3.5;
    compressor.attack.value = 0.005; compressor.release.value = 0.2;
    const ganho = ctx.createGain(); ganho.gain.value = 1.5;
    const destino = ctx.createMediaStreamDestination();
    fonte.connect(graves).connect(chiado).connect(presenca).connect(compressor).connect(ganho).connect(destino);
    return {fluxo: destino.stream, fechar: () => ctx.close().catch(() => {})};
  }

  function atualizarBotaoEnvio() {
    const vazio = !$('chat-texto').value.trim() && !pendentes.length;
    $('chat-gravar').hidden = !(podeGravar && vazio);
    $('chat-enviar').hidden = podeGravar && vazio;
  }

  const formatoGravacao = () => [['audio/webm;codecs=opus', 'webm'], ['audio/ogg;codecs=opus', 'ogg'], ['audio/mp4', 'm4a'], ['audio/webm', 'webm']]
    .find(([tipo]) => MediaRecorder.isTypeSupported?.(tipo)) || ['', 'webm'];
  const minSeg = s => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;

  async function iniciarGravacao() {
    if (!atual || gravador) return;
    const mic = prefsAudio().mic;
    const p = prefsAudio();
    // supressao de ruido, cancelamento de eco e volume automatico do proprio navegador (podem ser desligados nas configuracoes)
    const opcoes = {echoCancellation: true, noiseSuppression: p.reduzirRuido !== false, autoGainControl: p.volumeAuto !== false,
      channelCount: 1, sampleRate: 48000};
    try {
      try {
        fluxo = await navigator.mediaDevices.getUserMedia({audio: mic ? {...opcoes, deviceId: {exact: mic}} : opcoes});
      } catch (e) {
        // microfone escolhido foi desconectado: usa o padrao e avisa
        if (!mic || !['OverconstrainedError', 'NotFoundError'].includes(e.name)) throw e;
        fluxo = await navigator.mediaDevices.getUserMedia({audio: opcoes});
        aviso('O microfone escolhido não foi encontrado; usando o padrão do sistema.');
      }
    } catch (e) {
      aviso(e.name === 'NotAllowedError' ? 'Permita o uso do microfone no navegador para gravar áudio.' : 'Nenhum microfone disponível.');
      return;
    }
    const [tipo, ext] = formatoGravacao();
    tratamento = p.tratarVoz !== false ? tratarVoz(fluxo) : null;
    const origem = tratamento ? tratamento.fluxo : fluxo;
    // 64 kbps em Opus: voz natural, cerca de 0,5 MB por minuto
    gravador = new MediaRecorder(origem, tipo ? {mimeType: tipo, audioBitsPerSecond: 64000} : undefined);
    pedacos = []; descartar = false;
    gravador.addEventListener('dataavailable', e => { if (e.data.size) pedacos.push(e.data); });
    gravador.addEventListener('stop', () => finalizarGravacao(ext));
    gravador.start(250);
    inicioGravacao = Date.now();
    $('chat-form').hidden = true; $('chat-gravacao').hidden = false;
    $('chat-gravacao-tempo').textContent = '0:00';
    timerGravacao = setInterval(() => {
      const s = (Date.now() - inicioGravacao) / 1000;
      $('chat-gravacao-tempo').textContent = minSeg(s);
      if (s >= LIMITE_AUDIO_S) pararGravacao(false);
    }, 250);
    $('chat-gravacao-enviar').focus();
  }

  function pararGravacao(cancelar) {
    if (!gravador) return;
    descartar = cancelar;
    clearInterval(timerGravacao);
    if (gravador.state !== 'inactive') gravador.stop(); else finalizarGravacao('webm');
  }

  async function finalizarGravacao(ext) {
    const segundos = Math.round((Date.now() - inicioGravacao) / 1000);
    fluxo?.getTracks().forEach(t => t.stop());
    tratamento?.fechar(); tratamento = null;
    const tipo = gravador?.mimeType || 'audio/webm';
    gravador = null; fluxo = null;
    $('chat-gravacao').hidden = true; $('chat-form').hidden = false;
    atualizarBotaoEnvio();
    if (descartar || !pedacos.length || !atual) return;
    if (segundos < 1) { aviso('Áudio muito curto. Segure um pouco mais.'); return; }
    const marca = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '');
    const arquivo = new File(pedacos, `audio_${segundos}s_${marca}.${ext}`, {type: tipo.split(';')[0]});
    const dados = new FormData();
    dados.append('arquivos', arquivo, arquivo.name);
    try { receberMensagem(await api(`/api/chat/conversas/${atual.id}/mensagens`, {method: 'POST', body: dados})); }
    catch (e) { aviso(e.message); }
  }

  // player do audio: play/pause, barra de progresso e tempo; um audio toca por vez
  let audioTocando = null;
  function playerAudio(a, url) {
    const caixa = el('div', 'chat-audio');
    const audio = new Audio(); audio.preload = 'metadata'; audio.src = url;
    aplicarSaida(audio);
    const duracaoNome = Number((a.nome.match(/audio_(\d+)s_/) || [])[1]) || 0;
    const botao = el('button', 'chat-audio-play'); botao.type = 'button'; botao.setAttribute('aria-label', 'Ouvir áudio');
    botao.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path class="play" d="M8 5v14l11-7z"/><path class="pausa" d="M7 5h4v14H7zM13 5h4v14h-4z"/></svg>';
    const barra = document.createElement('input');
    barra.type = 'range'; barra.min = 0; barra.max = 1000; barra.value = 0; barra.className = 'chat-audio-barra';
    barra.setAttribute('aria-label', 'Posição do áudio');
    const tempo = el('span', 'chat-audio-tempo', duracaoNome ? minSeg(duracaoNome) : '0:00');
    const duracao = () => (Number.isFinite(audio.duration) && audio.duration > 0) ? audio.duration : duracaoNome;
    const marcar = tocando => { caixa.classList.toggle('tocando', tocando); botao.setAttribute('aria-label', tocando ? 'Pausar áudio' : 'Ouvir áudio'); };
    botao.addEventListener('click', () => {
      if (audio.paused) {
        if (audioTocando && audioTocando !== audio) audioTocando.pause();
        audioTocando = audio;
        audio.play().catch(() => aviso('Não foi possível tocar o áudio.'));
      } else audio.pause();
    });
    audio.addEventListener('play', () => marcar(true));
    audio.addEventListener('pause', () => marcar(false));
    audio.addEventListener('ended', () => { marcar(false); barra.value = 0; tempo.textContent = minSeg(duracao()); });
    audio.addEventListener('loadedmetadata', () => { if (!audio.currentTime) tempo.textContent = minSeg(duracao()); });
    audio.addEventListener('timeupdate', () => {
      const d = duracao();
      if (d) barra.value = Math.min(1000, audio.currentTime / d * 1000);
      tempo.textContent = minSeg(audio.currentTime);
    });
    barra.addEventListener('input', () => { const d = duracao(); if (d) audio.currentTime = barra.value / 1000 * d; });
    const mic = el('span', 'chat-audio-mic'); mic.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/></svg>';
    caixa.append(botao, barra, tempo, mic);
    return caixa;
  }

  function desenharAnexos(anexos) {
    const caixa = el('div', 'chat-anexos');
    for (const a of anexos) {
      const url = `/api/chat/anexos/${a.id}`;
      if (a.tipo.startsWith('audio/')) { caixa.append(playerAudio(a, url)); continue; }
      if (a.tipo.startsWith('image/')) {
        const link = el('a', 'chat-anexo-img'); link.href = url; link.target = '_blank'; link.rel = 'noopener';
        link.title = `${a.nome} · clique para ampliar`;
        const img = document.createElement('img'); img.src = url; img.alt = a.nome; img.loading = 'lazy';
        img.addEventListener('load', () => { const c = $('chat-mensagens'); if (c.scrollHeight - c.scrollTop - c.clientHeight < 400) c.scrollTop = c.scrollHeight; }, {once: true});
        link.append(img); caixa.append(link);
      } else {
        const [rotulo, cor] = ICONE_ARQUIVO[extensao(a.nome)] || ['ARQ', 'txt'];
        const link = el('a', 'chat-anexo-arquivo'); link.href = url + '?download=true'; link.title = `Baixar ${a.nome}`;
        const info = el('span', 'chat-previa-info');
        info.append(el('strong', null, a.nome), el('small', null, tamanhoLegivel(a.tamanho) + ' · baixar'));
        link.append(el('span', `chat-arquivo-icone ${cor}`, rotulo), info);
        caixa.append(link);
      }
    }
    return caixa;
  }

  function receberMensagem(msg) {
    const conversa = conversas.find(c => c.id === msg.conversaId);
    if (!conversa) { carregarConversas(); return; }
    conversa.ultimaMensagem = msg.texto || (msg.anexos?.length
      ? (msg.anexos[0].tipo.startsWith('audio/') ? '🎤 Mensagem de voz' : '📎 ' + msg.anexos[0].nome) : ''); conversa.ultimoAutorId = msg.autorId;
    conversa.ultimoAutorNome = msg.autorNome; conversa.ultimaMensagemEm = msg.criadoEm;
    const aberta = atual?.id === msg.conversaId;
    if (aberta) {
      if (!mensagens.some(m => m.id === msg.id)) {
        // igual ao WhatsApp: se esta no fim (ou a mensagem e sua) a conversa sobe sozinha;
        // se esta lendo mais acima, nao pula e mostra o botao de novas mensagens
        const seguir = msg.autorId === eu.id || pertoDoFim();
        mensagens.push(msg);
        desenharMensagens(false);
        if (seguir) rolarParaFim(true); else { novasAbaixo++; atualizarBotaoNovas(); }
      }
      if (msg.autorId !== eu.id) { $('chat-digitando').hidden = true; marcarLida(); }
    } else if (msg.autorId !== eu.id) {
      conversa.naoLidas++;
      if ((msg.mencionados || []).includes(eu.id)) conversa.mencoesNaoLidas = (conversa.mencoesNaoLidas || 0) + 1;
    }
    const mencionado = (msg.mencionados || []).includes(eu.id);
    if (msg.autorId !== eu.id && (document.hidden || !aberta || mencionado)) {
      notificar(conversa, msg, mencionado);
      // mencao toca mesmo com a conversa silenciada
      if (!conversa.silenciado || mencionado) window.fmChatSom?.tocar('msg-' + msg.id);
    }
    conversas.sort((a, b) => (b.ultimaMensagemEm || '').localeCompare(a.ultimaMensagemEm || ''));
    desenharLista();
  }

  function notificar(conversa, msg, mencionado) {
    try {
      if ('Notification' in window && Notification.permission === 'granted') {
        const corpo = (grupo(conversa) ? primeiroNome(msg.autorNome) + ': ' : '') + (msg.texto || (msg.anexos?.[0]?.tipo.startsWith('audio/') ? '🎤 enviou uma mensagem de voz' : '📎 enviou um arquivo'));
        const n = new Notification(mencionado ? `@ ${msg.autorNome} mencionou você` + (grupo(conversa) ? ` em ${conversa.nome}` : '') : conversa.nome, {body: corpo.slice(0, 140), tag: 'chat-' + conversa.id});
        n.onclick = () => { window.focus(); abrir(conversa.id); n.close(); };
      }
    } catch (_) {}
  }

  function ajustarAltura() {
    const t = $('chat-texto'); t.style.height = 'auto'; t.style.height = Math.min(t.scrollHeight, 160) + 'px';
  }

  function avisarDigitando() {
    if (!atual || Date.now() - ultimoDigitando < 3000) return;
    ultimoDigitando = Date.now();
    api(`/api/chat/conversas/${atual.id}/digitando`, {method: 'POST'}).catch(() => {});
  }

  function conectar() {
    if (fonte) fonte.close();
    fonte = new EventSource('/api/chat/eventos');
    fonte.addEventListener('pronto', () => aviso(''));
    fonte.addEventListener('mensagem', e => receberMensagem(JSON.parse(e.data)));
    fonte.addEventListener('chamada', e => tocar(JSON.parse(e.data)));
    fonte.addEventListener('conversa', () => { cacheParticipantes = {}; carregarConversas().catch(() => {}); });
    fonte.addEventListener('mensagem-alterada', e => {
      const msg = JSON.parse(e.data);
      trocarMensagem(msg);
      const c = conversas.find(x => x.id === msg.conversaId);
      if (c && c.ultimoAutorId === msg.autorId) carregarConversas().catch(() => {});
    });
    fonte.addEventListener('status', e => {
      const {usuarioId, status, recado} = JSON.parse(e.data);
      conversas.forEach(c => { if (c.contato?.id === usuarioId) Object.assign(c.contato, {status, recado}); });
      if (atual?.contato?.id === usuarioId) desenharCabecalho();
      desenharLista();
    });
    fonte.addEventListener('presenca', e => {
      const {usuarioId, online} = JSON.parse(e.data);
      conversas.forEach(c => { if (c.contato?.id === usuarioId) c.contato.online = online; });
      if (atual?.contato?.id === usuarioId) desenharCabecalho();
      desenharLista();
    });
    fonte.addEventListener('leitura', e => {
      const {conversaId, usuarioId} = JSON.parse(e.data);
      if (usuarioId === eu.id || atual?.id !== conversaId) return;
      // no grupo o "visto por todos" depende do participante mais atrasado: recarrega o marcador
      api(`/api/chat/conversas/${conversaId}/mensagens`).then(p => {
        if (atual?.id === conversaId) { leituraContato = p.leituraContato; desenharMensagens(false); }
      }).catch(() => {});
    });
    fonte.addEventListener('digitando', e => {
      const {conversaId, nome} = JSON.parse(e.data);
      if (atual?.id !== conversaId) return;
      const p = $('chat-digitando'); p.textContent = `${grupo(atual) ? primeiroNome(nome) : atual.nome} está digitando…`; p.hidden = false;
      clearTimeout(digitandoTimer); digitandoTimer = setTimeout(() => { p.hidden = true; }, 5000);
    });
    fonte.onerror = () => {
      if (fonte.readyState === EventSource.CLOSED) { aviso('Conexão do chat perdida. Tentando reconectar…'); setTimeout(conectar, 5000); }
    };
  }

  async function carregarConversas() {
    conversas = await api('/api/chat/conversas');
    if (atual) {
      const ainda = conversas.find(c => c.id === atual.id);
      if (!ainda) { fecharConversa(); return; }
      atual = ainda; desenharCabecalho();
    }
    desenharLista();
  }

  // ---- videochamada (Jitsi proprio, embutido na pagina)
  let video = {ativo: false, dominio: ''}, jitsi = null, toque = null, toqueTimer = null;

  function carregarApiJitsi(dominio) {
    if (window.JitsiMeetExternalAPI) return Promise.resolve();
    return new Promise((ok, falha) => {
      const s = document.createElement('script');
      s.src = `https://${dominio}/external_api.js`;
      s.onload = ok; s.onerror = () => falha(new Error('Não foi possível conectar ao servidor de vídeo.'));
      document.head.append(s);
    });
  }

  async function entrarNaSala(chamada) {
    esconderToque();
    if (jitsi) sairDaSala();
    try {
      await carregarApiJitsi(chamada.dominio);
      $('chat-sala-titulo').textContent = `Videochamada · ${chamada.conversaNome || ''}`;
      $('chat-sala').hidden = false;
      jitsi = new window.JitsiMeetExternalAPI(chamada.dominio, {
        roomName: chamada.sala,
        parentNode: $('chat-sala-video'),
        width: '100%', height: '100%', lang: 'ptBR',
        userInfo: {displayName: eu.nome, email: ''},
        configOverwrite: {prejoinConfig: {enabled: false}, disableDeepLinking: true, startWithVideoMuted: false},
        interfaceConfigOverwrite: {SHOW_JITSI_WATERMARK: false, MOBILE_APP_PROMO: false}
      });
      jitsi.addListener('readyToClose', sairDaSala);
    } catch (e) { $('chat-sala').hidden = true; aviso(e.message); }
  }

  function sairDaSala() {
    try { jitsi?.dispose(); } catch (_) {}
    jitsi = null;
    $('chat-sala-video').replaceChildren();
    $('chat-sala').hidden = true;
  }

  async function iniciarChamada() {
    if (!atual || !video.ativo) return;
    try { entrarNaSala(await api(`/api/chat/conversas/${atual.id}/chamada`, {method: 'POST'})); }
    catch (e) { aviso(e.message); }
  }

  function tocar(chamada) {
    if (jitsi) return; // ja esta em outra chamada
    toque = chamada;
    $('chat-toque-texto').textContent = chamada.deNome === chamada.conversaNome || !chamada.conversaNome
      ? `${chamada.deNome} está chamando você para uma videochamada`
      : `${chamada.deNome} iniciou uma videochamada em “${chamada.conversaNome}”`;
    $('chat-toque').hidden = false;
    clearTimeout(toqueTimer); toqueTimer = setTimeout(esconderToque, 60000);
    try {
      if ('Notification' in window && Notification.permission === 'granted' && document.hidden) {
        const n = new Notification('Videochamada', {body: $('chat-toque-texto').textContent, tag: 'chamada-' + chamada.sala});
        n.onclick = () => { window.focus(); n.close(); };
      }
    } catch (_) {}
  }

  function esconderToque() { toque = null; clearTimeout(toqueTimer); $('chat-toque').hidden = true; }

  // ---- silenciar e janela separada
  async function alternarSilencio() {
    if (!atual) return;
    const novo = !atual.silenciado;
    try {
      await api(`/api/chat/conversas/${atual.id}/silencio`, json({silenciado: novo}, 'PUT'));
      atual.silenciado = novo;
      desenharCabecalho(); desenharLista();
      if (window.fmAvisar) window.fmAvisar(novo ? 'Som desta conversa silenciado' : 'Som desta conversa reativado', 'ok');
    } catch (e) { aviso(e.message); }
  }

  function abrirJanela(c) {
    const largura = 440, altura = 680;
    const esquerda = Math.max(0, (window.screenX || 0) + window.outerWidth - largura - 40);
    const topo = Math.max(0, (window.screenY || 0) + 80);
    const janela = window.open(`chat.html?janela=1#c${c.id}`, `fm-chat-${c.id}`,
      `popup=yes,width=${largura},height=${altura},left=${esquerda},top=${topo}`);
    if (!janela) aviso('O navegador bloqueou a janela. Permita pop-ups para este site e tente de novo.');
    else janela.focus();
  }

  // ---- emojis
  const EMOJIS = {
    'Frequentes': '😀 😂 🙂 😉 😊 😍 🤔 😅 😎 🙏 👍 👎 👏 🙌 💪 🤝 ✅ ❌ ⚠️ 🔥',
    'Rostos': '😁 😃 😄 😆 🤣 😇 🥰 😘 😋 😜 🤗 🤭 🤫 😐 😑 😶 🙄 😏 😬 😌 😔 😴 😷 🤒 🥳 🥺 😢 😭 😤 😡 😱 😳 🤯 😮',
    'Gestos': '👋 🤙 👌 ✌️ 🤞 👉 👈 👆 👇 ☝️ ✋ 🤚 🖐️ 🫡 🙋 🤷 🙅 🙆 💁 🤦',
    'Trabalho': '📌 📎 📝 📄 📁 📊 📈 📉 🗂️ 📅 ⏰ ⏳ 💰 💵 🧾 🏥 💊 🩺 📞 💻 📧 🔍 🔒 ✔️',
    'Símbolos': '❤️ 💙 💚 💛 🧡 💜 ⭐ ✨ 🎉 🎂 ☕ 🍕 🚀 💡 ❓ ❗ 💯 🆗 🔴 🟢 🟡'
  };

  function montarEmojis() {
    const painel = $('chat-emoji-painel');
    for (const [titulo, lista] of Object.entries(EMOJIS)) {
      painel.append(el('p', 'chat-emoji-titulo', titulo));
      const grade = el('div', 'chat-emoji-grade');
      for (const emoji of lista.split(' ')) {
        const b = el('button', 'chat-emoji-item', emoji); b.type = 'button'; b.title = emoji;
        b.addEventListener('click', () => inserirEmoji(emoji));
        grade.append(b);
      }
      painel.append(grade);
    }
  }

  function alternarEmojis(abrir) {
    const painel = $('chat-emoji-painel');
    const aberto = abrir ?? painel.hidden;
    painel.hidden = !aberto;
    $('chat-emoji-abrir').setAttribute('aria-expanded', String(aberto));
  }

  function inserirEmoji(emoji) {
    const t = $('chat-texto');
    const ini = t.selectionStart ?? t.value.length, fim = t.selectionEnd ?? t.value.length;
    t.value = t.value.slice(0, ini) + emoji + t.value.slice(fim);
    t.selectionStart = t.selectionEnd = ini + emoji.length;
    ajustarAltura(); t.focus();
  }

  // ---- busca nas mensagens (a partir de 2 letras, junto com o filtro de nomes)
  let timerBusca = null;
  function buscarMensagens() {
    clearTimeout(timerBusca);
    const termo = $('chat-busca').value.trim();
    const caixa = $('chat-busca-resultados');
    if (termo.length < 2) { caixa.hidden = true; return; }
    timerBusca = setTimeout(async () => {
      try {
        const lista = await api(`/api/chat/busca?q=${encodeURIComponent(termo)}`);
        if ($('chat-busca').value.trim() !== termo) return;
        const ul = $('chat-busca-lista'); ul.replaceChildren();
        caixa.hidden = false;
        if (!lista.length) { ul.append(el('li', 'chat-vazio', 'Nenhuma mensagem com esse texto.')); return; }
        for (const r of lista) {
          const b = el('button', 'chat-busca-item'); b.type = 'button';
          const topo = el('span', 'chat-item-linha');
          topo.append(el('strong', null, r.conversaNome), el('time', null, hora(r.criadoEm)));
          const texto = el('small');
          texto.append(`${primeiroNome(r.autorNome)}: `, destacar(r.texto, termo));
          b.append(topo, texto);
          b.addEventListener('click', () => irParaMensagem(r.mensagemId, r.conversaId));
          const li = el('li'); li.append(b); ul.append(li);
        }
      } catch (_) {}
    }, 300);
  }
  function destacar(texto, termo) {
    const frag = document.createDocumentFragment();
    const i = texto.toLowerCase().indexOf(termo.toLowerCase());
    if (i < 0) { frag.append(texto); return frag; }
    frag.append(texto.slice(0, i), el('mark', null, texto.slice(i, i + termo.length)), texto.slice(i + termo.length));
    return frag;
  }

  // ---- meu status
  let timerRecado = null;
  async function salvarStatus() {
    try { await api('/api/chat/status', json({status: $('chat-meu-status-sel').value, recado: $('chat-recado').value}, 'PUT')); }
    catch (e) { aviso(e.message); }
  }

  // ---- notificacoes na area de trabalho e prazo de guarda (configuracoes)
  function atualizarBotaoNotificar() {
    const b = $('chat-config-notificar');
    if (!('Notification' in window)) { b.disabled = true; b.textContent = 'Este navegador não mostra avisos na área de trabalho'; return; }
    const p = Notification.permission;
    b.disabled = p !== 'default';
    b.textContent = p === 'granted' ? '✅ Avisos na área de trabalho ativados' : p === 'denied' ? '🚫 Avisos bloqueados no navegador (libere no cadeado da barra de endereço)' : '🔔 Ativar avisos na área de trabalho';
  }
  async function carregarRetencao() {
    const caixa = $('chat-config-retencao-caixa');
    caixa.hidden = eu?.perfil !== 'ADMIN';
    if (caixa.hidden) return;
    try { const c = await api('/api/chat/configuracao'); $('chat-config-retencao').value = c.retencaoMeses ? String(c.retencaoMeses) : ''; } catch (_) {}
  }

  // ---- nova conversa / novo grupo
  let modoGrupo = false, contatos = [];
  const escolhidos = new Set();

  function trocarModo(grupoAtivo) {
    modoGrupo = grupoAtivo;
    $('chat-modo-direta').classList.toggle('ativo', !grupoAtivo); $('chat-modo-direta').setAttribute('aria-selected', String(!grupoAtivo));
    $('chat-modo-grupo').classList.toggle('ativo', grupoAtivo); $('chat-modo-grupo').setAttribute('aria-selected', String(grupoAtivo));
    $('chat-grupo-nome-rotulo').hidden = !grupoAtivo; $('chat-grupo-acoes').hidden = !grupoAtivo;
    $('chat-grupo-oficial-rotulo').hidden = !(grupoAtivo && eu?.perfil === 'ADMIN'); if (!grupoAtivo) $('chat-grupo-oficial').checked = false;
    desenharContatos();
  }

  function desenharContatos() {
    const termo = $('chat-contato-busca').value.trim().toLowerCase();
    const ul = $('chat-contatos'); ul.replaceChildren();
    contatos.filter(c => !termo || c.nome.toLowerCase().includes(termo)).forEach(c => {
      const b = el('button', 'chat-item' + (modoGrupo && escolhidos.has(c.id) ? ' ativo' : '')); b.type = 'button';
      const corpo = el('span', 'chat-item-corpo');
      corpo.append(el('strong', null, c.nome), el('small', null, (c.online ? '● online · ' : '') + c.perfil));
      b.append(avatar(c.nome, c.online, false, c.foto, c.id), corpo);
      if (modoGrupo) {
        b.setAttribute('aria-pressed', String(escolhidos.has(c.id)));
        b.append(el('span', 'chat-marca', escolhidos.has(c.id) ? '✓' : ''));
      }
      b.addEventListener('click', () => modoGrupo ? alternar(c.id) : conversarCom(c.id));
      const li = el('li'); li.append(b); ul.append(li);
    });
    if (!ul.children.length) ul.append(el('li', 'chat-vazio', 'Ninguém encontrado.'));
    $('chat-grupo-selecionados').textContent = `${escolhidos.size} pessoa(s) escolhida(s)`;
  }

  function alternar(id) { escolhidos.has(id) ? escolhidos.delete(id) : escolhidos.add(id); desenharContatos(); }

  async function conversarCom(usuarioId) {
    try {
      const conversa = await api('/api/chat/conversas', json({usuarioId}));
      $('chat-dialogo').close();
      await carregarConversas();
      abrir(conversa.id);
    } catch (e) { aviso(e.message); }
  }

  async function criarGrupo() {
    try {
      const conversa = await api('/api/chat/grupos', json({nome: $('chat-grupo-nome').value, usuarioIds: [...escolhidos], oficial: $('chat-grupo-oficial').checked}));
      $('chat-dialogo').close();
      await carregarConversas();
      abrir(conversa.id);
    } catch (e) { alert(e.message); }
  }

  async function abrirDialogo() {
    $('chat-contato-busca').value = ''; $('chat-grupo-nome').value = ''; escolhidos.clear();
    try {
      contatos = await api('/api/chat/contatos');
      trocarModo(false);
      $('chat-dialogo').showModal();
      $('chat-contato-busca').focus();
    } catch (e) { aviso(e.message); }
  }

  // ---- participantes do grupo
  async function abrirParticipantes() {
    if (!grupo(atual)) return;
    const conversaId = atual.id;
    try {
      const [lista, todos] = await Promise.all([api(`/api/chat/conversas/${conversaId}/participantes`), api('/api/chat/contatos')]);
      const ul = $('chat-participantes-lista'); ul.replaceChildren();
      for (const p of lista) {
        const linha = el('div', 'chat-item');
        const corpo = el('span', 'chat-item-corpo');
        corpo.append(el('strong', null, p.id === eu.id ? `${p.nome} (você)` : p.nome),
          el('small', null, (p.online ? '● online · ' : '') + p.perfil + (p.criador ? ' · criou o grupo' : '')));
        linha.append(avatar(p.nome, p.online, false, p.foto, p.id), corpo);
        const podeRemover = atual.oficial ? eu.perfil === 'ADMIN' : atual.souCriador;
        if (podeRemover && p.id !== eu.id) {
          const rm = el('button', 'text-button', 'Remover'); rm.type = 'button';
          rm.addEventListener('click', () => removerParticipante(conversaId, p.id, `Remover ${p.nome} do grupo?`));
          linha.append(rm);
        }
        const li = el('li'); li.append(linha); ul.append(li);
      }
      const dentro = new Set(lista.map(p => p.id));
      const sel = $('chat-participante-novo'); sel.replaceChildren(el('option', null, 'Escolha alguém para adicionar'));
      sel.firstChild.value = '';
      todos.filter(c => !dentro.has(c.id)).forEach(c => { const o = el('option', null, c.nome); o.value = c.id; sel.append(o); });
      $('chat-participantes-titulo').textContent = `Participantes · ${atual.nome}`;
      // grupo oficial: so o ADMIN adiciona, remove ou sai
      const gestor = !atual.oficial || eu.perfil === 'ADMIN';
      sel.closest('.chat-grupo-acoes').hidden = !gestor;
      $('chat-sair-grupo').hidden = !gestor;
      const d = $('chat-participantes'); if (!d.open) d.showModal();
    } catch (e) { aviso(e.message); }
  }

  async function adicionarParticipante() {
    const usuarioId = Number($('chat-participante-novo').value);
    if (!usuarioId || !atual) return;
    try {
      await api(`/api/chat/conversas/${atual.id}/participantes`, json({usuarioId}));
      await carregarConversas();
      abrirParticipantes();
    } catch (e) { alert(e.message); }
  }

  async function removerParticipante(conversaId, usuarioId, pergunta) {
    if (!confirm(pergunta)) return;
    try {
      await api(`/api/chat/conversas/${conversaId}/participantes/${usuarioId}`, {method: 'DELETE'});
      if (usuarioId === eu.id) { $('chat-participantes').close(); await carregarConversas(); fecharConversa(); }
      else { await carregarConversas(); abrirParticipantes(); }
    } catch (e) { alert(e.message); }
  }

  document.addEventListener('DOMContentLoaded', async () => {
    $('chat-busca').addEventListener('input', () => { desenharLista(); buscarMensagens(); });
    $('chat-meu-status-sel').addEventListener('change', salvarStatus);
    $('chat-recado').addEventListener('input', () => { clearTimeout(timerRecado); timerRecado = setTimeout(salvarStatus, 800); });
    $('chat-respondendo-cancelar').addEventListener('click', cancelarResposta);
    document.querySelector('.chat-fixada-ir').addEventListener('click', () => atual?.fixada && irParaMensagem(atual.fixada.id));
    document.querySelector('.chat-fixada-soltar').addEventListener('click', () => fixar(null));
    $('chat-config-notificar').addEventListener('click', async () => { try { await Notification.requestPermission(); } catch (_) {} atualizarBotaoNotificar(); });
    $('chat-config-retencao').addEventListener('change', async e => {
      try {
        await api('/api/chat/configuracao', json({retencaoMeses: e.target.value ? Number(e.target.value) : null}, 'PUT'));
        window.fmAvisar?.(e.target.value ? 'Prazo de guarda atualizado' : 'Mensagens guardadas sem prazo', 'ok');
      } catch (err) { aviso(err.message); }
    });
    $('chat-texto').addEventListener('keydown', e => { if (e.key === 'Escape' && respondendo) cancelarResposta(); });
    $('chat-nova').addEventListener('click', abrirDialogo);
    $('chat-nova-vazio').addEventListener('click', abrirDialogo);
    montarEmojis();
    $('chat-emoji-abrir').addEventListener('click', e => { e.stopPropagation(); alternarEmojis(); });
    document.addEventListener('click', e => { if (!e.target.closest('.chat-emoji')) alternarEmojis(false); });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') alternarEmojis(false); });
    $('chat-dialogo-fechar').addEventListener('click', () => $('chat-dialogo').close());
    $('chat-modo-direta').addEventListener('click', () => trocarModo(false));
    $('chat-modo-grupo').addEventListener('click', () => trocarModo(true));
    $('chat-contato-busca').addEventListener('input', desenharContatos);
    $('chat-grupo-criar').addEventListener('click', criarGrupo);
    $('chat-participantes-abrir').addEventListener('click', abrirParticipantes);
    $('chat-participantes-fechar').addEventListener('click', () => $('chat-participantes').close());
    $('chat-participante-adicionar').addEventListener('click', adicionarParticipante);
    $('chat-sair-grupo').addEventListener('click', () => atual && removerParticipante(atual.id, eu.id, `Sair do grupo “${atual.nome}”?`));
    $('chat-video').addEventListener('click', iniciarChamada);
    $('chat-silenciar').addEventListener('click', alternarSilencio);
    $('chat-config').addEventListener('click', abrirConfigAudio);
    $('chat-config-fechar').addEventListener('click', () => $('chat-config-audio').close());
    $('chat-config-audio').addEventListener('close', pararMedidor);
    $('chat-config-permitir').addEventListener('click', async () => { if (await medirMicrofone()) listarAparelhos(); });
    $('chat-config-mic').addEventListener('change', e => { salvarPrefsAudio({...prefsAudio(), mic: e.target.value}); medirMicrofone(); });
    $('chat-config-saida').addEventListener('change', e => {
      salvarPrefsAudio({...prefsAudio(), saida: e.target.value});
      window.fmChatSom?.definirSaida?.(e.target.value);
      if (audioTocando) aplicarSaida(audioTocando);
    });
    $('chat-config-testar').addEventListener('click', () => window.fmChatSom?.tocar());
    $('chat-config-gravar-teste').addEventListener('click', gravarTeste);
    [['chat-config-ruido', 'reduzirRuido'], ['chat-config-volume', 'volumeAuto'], ['chat-config-voz', 'tratarVoz']].forEach(([id, chave]) =>
      $(id).addEventListener('change', e => salvarPrefsAudio({...prefsAudio(), [chave]: e.target.checked})));
    $('chat-gravar').addEventListener('click', iniciarGravacao);
    $('chat-gravacao-cancelar').addEventListener('click', () => pararGravacao(true));
    $('chat-gravacao-enviar').addEventListener('click', () => pararGravacao(false));
    $('chat-texto').addEventListener('input', atualizarBotaoEnvio);
    atualizarBotaoEnvio();
    $('chat-anexar').addEventListener('click', () => $('chat-arquivo').click());
    $('chat-arquivo').addEventListener('change', e => { adicionarArquivos([...e.target.files]); e.target.value = ''; });
    $('chat-texto').addEventListener('paste', e => {
      const arquivos = [...(e.clipboardData?.files || [])];
      if (arquivos.length) { e.preventDefault(); adicionarArquivos(arquivos); }
    });
    const area = document.querySelector('.chat-conversa');
    area.addEventListener('dragover', e => { if (atual && e.dataTransfer?.types.includes('Files')) { e.preventDefault(); area.classList.add('soltando'); } });
    area.addEventListener('dragleave', e => { if (!area.contains(e.relatedTarget)) area.classList.remove('soltando'); });
    area.addEventListener('drop', e => {
      area.classList.remove('soltando');
      if (!atual || !e.dataTransfer?.files.length) return;
      e.preventDefault(); adicionarArquivos([...e.dataTransfer.files]);
    });
    $('chat-sala-sair').addEventListener('click', sairDaSala);
    $('chat-toque-entrar').addEventListener('click', () => {
      const c = toque; if (!c) return;
      if (conversas.some(x => x.id === c.conversaId)) abrir(c.conversaId);
      entrarNaSala(c);
    });
    $('chat-toque-recusar').addEventListener('click', esconderToque);
    $('chat-form').addEventListener('submit', enviar);
    $('chat-anteriores').addEventListener('click', anteriores);
    $('chat-novas').addEventListener('click', () => rolarParaFim(true));
    $('chat-mensagens').addEventListener('scroll', () => { if (novasAbaixo && pertoDoFim()) { novasAbaixo = 0; atualizarBotaoNovas(); } }, {passive: true});
    $('chat-voltar').addEventListener('click', () => painel.classList.remove('com-conversa'));
    const texto = $('chat-texto');
    texto.addEventListener('input', () => { ajustarAltura(); avisarDigitando(); sugerirMencao(); });
    texto.addEventListener('keydown', e => { if (teclaMencao(e)) return; if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); $('chat-form').requestSubmit(); } });
    texto.addEventListener('blur', () => setTimeout(() => { $('chat-mencoes').hidden = true; }, 150));
    document.addEventListener('visibilitychange', marcarLida);
    try {
      eu = await api('/api/auth/me');
      api('/api/chat/status').then(s => { $('chat-meu-status-sel').value = s.status; $('chat-recado').value = s.recado || ''; }).catch(() => {});
      video = await api('/api/chat/video').catch(() => video);
      await carregarConversas();
      conectar();
      const alvo = Number((location.hash.match(/^#c(\d+)$/) || [])[1]);
      if (alvo) abrir(alvo);
      if ('Notification' in window && Notification.permission === 'default') {
        texto.addEventListener('focus', () => Notification.requestPermission().catch(() => {}), {once: true});
      }
    } catch (e) { aviso(e.message); }
  });
})();
