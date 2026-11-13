// FaturaChat "de bolso": icone no topo com gaveta lateral (conversa rapida sem sair da tela)
// e trilho de avatares a direita com quem falou por ultimo. A tela cheia continua em chat.html.
(function () {
  if (window.fmChatDock || (location.pathname.split('/').pop() || '') === 'chat.html') return;
  const ESC = {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'};
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ESC[c]);
  const iniciais = nome => String(nome || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join('');
  const hora = iso => { const d = new Date(iso); const hoje = new Date().toDateString() === d.toDateString();
    return hoje ? d.toLocaleTimeString('pt-BR', {hour: '2-digit', minute: '2-digit'}) : d.toLocaleDateString('pt-BR', {day: '2-digit', month: '2-digit'}); };

  let conversas = [], aberta = null, eu = null, total = 0, timerMsgs = null;

  // ---------- estrutura ----------
  const gaveta = document.createElement('aside');
  gaveta.className = 'fmc-gaveta'; gaveta.hidden = true; gaveta.setAttribute('aria-label', 'FaturaChat');
  gaveta.innerHTML = `<header class="fmc-cab"><button type="button" class="fmc-voltar" hidden aria-label="Voltar para as conversas">‹</button>
      <strong class="fmc-titulo">FaturaChat</strong>
      <button type="button" class="fmc-mudo-conversa" hidden aria-pressed="false"></button>
      <button type="button" class="fmc-nao-perturbe" aria-pressed="false" title="Não perturbe por horário" aria-label="Não perturbe por horário">🌙</button>
      <button type="button" class="fmc-mudo-geral" aria-pressed="false"></button>
      <a class="fmc-cheio" href="chat.html" title="Abrir o FaturaChat completo" aria-label="Abrir o FaturaChat completo"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 17 17 7"/><path d="M8 7h9v9"/></svg></a>
      <button type="button" class="fmc-fechar" aria-label="Fechar (Esc)">×</button></header>
    <div class="fmc-lista-vista"><div class="fmc-abas" role="tablist"><button type="button" role="tab" class="fmc-aba-conversas ativo" aria-selected="true">Conversas</button><button type="button" role="tab" class="fmc-aba-comigo" aria-selected="false" title="Pendências abertas com você">Comigo <b></b></button><button type="button" role="tab" class="fmc-aba-salvas" aria-selected="false" title="Mensagens salvas">⭐ Salvas</button></div><input type="search" class="fmc-busca" placeholder="Buscar conversa" aria-label="Buscar conversa"><ul class="fmc-lista"></ul></div>
    <div class="fmc-conversa-vista" hidden><ol class="fmc-msgs" aria-live="polite"></ol>
      <div class="fmc-anexos" hidden></div>
      <div class="fmc-emojis" hidden role="dialog" aria-label="Emojis"></div>
      <div class="fmc-mencoes" hidden role="listbox" aria-label="Mencionar"></div>
      <div class="fmc-mencoes fmc-respostas" hidden role="listbox" aria-label="Respostas rápidas"></div>
      <form class="fmc-envio">
        <button type="button" class="fmc-acao fmc-emoji-botao" title="Emojis" aria-label="Emojis">🙂</button>
        <button type="button" class="fmc-acao fmc-clipe" title="Anexar arquivo (ou cole uma imagem com Ctrl+V)" aria-label="Anexar arquivo"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m21 12-8.5 8.5a5 5 0 0 1-7-7L14 5a3.5 3.5 0 0 1 5 5l-8.5 8.5a2 2 0 0 1-3-3L15 8"/></svg></button>
        <input type="file" class="fmc-arquivo" multiple hidden>
        <textarea rows="1" maxlength="4000" placeholder="Mensagem" title="Enter envia · Shift+Enter quebra linha · Ctrl+V cola imagem" aria-label="Mensagem"></textarea><button type="submit" class="fmc-enviar" aria-label="Enviar">➤</button></form></div>`;
  const trilho = document.createElement('nav');
  trilho.className = 'fmc-trilho'; trilho.setAttribute('aria-label', 'Conversas recentes do FaturaChat');
  // icone do FaturaChat: balao de conversa com a linha de batimento (tema saude) e um ponto de status
  const ICONE = '<svg viewBox="0 0 32 32" aria-hidden="true" class="fmc-icone"><path class="fmc-icone-balao" d="M6 6.5h20a3 3 0 0 1 3 3V20a3 3 0 0 1-3 3H14l-6 5v-5H6a3 3 0 0 1-3-3V9.5a3 3 0 0 1 3-3Z"/><path class="fmc-icone-pulso" d="M7.5 15h4l2-4 3 8 2-4h6"/></svg>';
  const fab = document.createElement('button');
  fab.type = 'button'; fab.className = 'fmc-fab'; fab.title = 'FaturaChat'; fab.setAttribute('aria-label', 'FaturaChat: conversas recentes'); fab.setAttribute('aria-expanded', 'false');
  // esfera 3D: anel tecnologico girando, nucleo com brilho, icone e ondas (notificacao e clique)
  fab.innerHTML = '<span class="fmc-fab-anel" aria-hidden="true"></span><span class="fmc-fab-nucleo" aria-hidden="true"></span><span class="fmc-fab-brilho" aria-hidden="true"></span>' + ICONE + '<span class="fmc-fab-onda" aria-hidden="true"></span><span class="fmc-fab-onda o2" aria-hidden="true"></span><span class="fmc-badge" hidden></span>';
  // ---------- silenciar: geral (todas as conversas, guardado no navegador) e por conversa (no servidor) ----------
  const CHAVE_MUDO = 'fm-chat-mudo';
  const mudoGeral = () => { try { return localStorage.getItem(CHAVE_MUDO) === '1'; } catch (_) { return false; } };
  const SINO = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>';
  const SINO_CORTADO = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8.7 3A6 6 0 0 1 18 8c0 2.7.5 4.6 1.1 6"/><path d="M17 17H3s3-2 3-9c0-.8.1-1.5.4-2.2"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/><path d="m2 2 20 20"/></svg>';
  const animar = classe => { fab.classList.remove(classe); void fab.offsetWidth; fab.classList.add(classe); };
  fab.addEventListener('animationend', e => { if (e.target === fab) fab.classList.remove('chegou', 'clicou'); });
  fab.addEventListener('pointerdown', () => animar('clicou'));
  fab.addEventListener('click', () => alternarTrilho());
  document.body.append(gaveta, trilho, fab);
  const $ = s => gaveta.querySelector(s);

  // ---------- encaixe do balao no cabecalho de cada tela ----------
  // Em vez de ficar fixo no canto (e cobrir o menu da conta em telas largas), o balao entra como
  // ultimo item do cabecalho da pagina; a coluna de avatares e os avisos abrem logo abaixo dele.
  function cabecalho() { return document.querySelector('main :is(.account-heading, .conciliation-heading, .topbar)'); }
  // ---------- arrastar o balao para qualquer lugar (posicao lembrada; duplo clique volta ao topo) ----------
  const CHAVE_POS = 'fm-chat-balao-pos';
  let posLivre = (() => { try { return JSON.parse(localStorage.getItem(CHAVE_POS) || 'null'); } catch (_) { return null; } })();
  function aplicarLivre() {
    const tam = 52;
    const x = Math.min(window.innerWidth - tam - 8, Math.max(8, posLivre.x * window.innerWidth));
    const y = Math.min(window.innerHeight - tam - 8, Math.max(8, posLivre.y * window.innerHeight));
    if (fab.parentElement !== document.body) document.body.append(fab);
    fab.classList.remove('no-topo'); fab.classList.add('livre');
    fab.style.left = x + 'px'; fab.style.top = y + 'px';
    ancorar();
  }
  function voltarAoTopo() {
    posLivre = null; try { localStorage.removeItem(CHAVE_POS); } catch (_) {}
    fab.classList.remove('livre'); fab.style.left = fab.style.top = '';
    if (!encaixar()) ancorar();
  }
  let arraste = null, acabouDeArrastar = false;
  fab.addEventListener('pointerdown', e => {
    if (e.button !== 0) return;
    const r = fab.getBoundingClientRect();
    arraste = {id: e.pointerId, dx: e.clientX - r.left, dy: e.clientY - r.top, x0: e.clientX, y0: e.clientY, movendo: false};
  });
  window.addEventListener('pointermove', e => {
    if (!arraste || e.pointerId !== arraste.id) return;
    if (!arraste.movendo) {
      if (Math.hypot(e.clientX - arraste.x0, e.clientY - arraste.y0) < 6) return;
      arraste.movendo = true; fab.setPointerCapture?.(e.pointerId);
      // a coluna e o painel acompanham o balao durante o arraste
      document.body.append(fab); fab.classList.remove('no-topo'); fab.classList.add('livre', 'arrastando');
    }
    const tam = fab.offsetWidth || 52;
    const x = Math.min(window.innerWidth - tam - 4, Math.max(4, e.clientX - arraste.dx));
    const y = Math.min(window.innerHeight - tam - 4, Math.max(4, e.clientY - arraste.dy));
    fab.style.left = x + 'px'; fab.style.top = y + 'px';
    ancorar(); if (!gaveta.hidden) posicionar();
  });
  window.addEventListener('pointerup', e => {
    if (!arraste || e.pointerId !== arraste.id) return;
    const moveu = arraste.movendo; arraste = null;
    if (!moveu) return;
    fab.classList.remove('arrastando'); acabouDeArrastar = true; setTimeout(() => { acabouDeArrastar = false; }, 50);
    const r = fab.getBoundingClientRect();
    posLivre = {x: r.left / window.innerWidth, y: r.top / window.innerHeight};
    try { localStorage.setItem(CHAVE_POS, JSON.stringify(posLivre)); } catch (_) {}
    ancorar();
  });
  // o clique que encerra um arraste nao abre a coluna
  fab.addEventListener('click', e => { if (acabouDeArrastar) { e.stopImmediatePropagation(); e.preventDefault(); } }, true);
  fab.addEventListener('dblclick', e => { e.preventDefault(); if (posLivre) voltarAoTopo(); });
  window.addEventListener('resize', () => { if (posLivre) aplicarLivre(); });

  function encaixar() {
    if (posLivre) { aplicarLivre(); return true; }
    const cab = cabecalho(); if (!cab) return false;
    const acoes = cab.querySelector('.session-actions');
    const alvo = acoes || cab;
    if (fab.parentElement !== alvo || alvo.lastElementChild !== fab) alvo.append(fab);
    fab.classList.add('no-topo');
    ancorar();
    return true;
  }
  function ancorar() {
    const r = fab.getBoundingClientRect();
    const topo = Math.max(8, r.bottom + 10), direita = Math.max(8, window.innerWidth - r.right);
    for (const el of [trilho, avisos]) { el.style.setProperty('--fmc-ancora-topo', topo + 'px'); el.style.setProperty('--fmc-ancora-dir', direita + 'px'); }
    trilho.style.setProperty('--fmc-ancora-centro', (window.innerWidth - (r.left + r.width / 2)) + 'px');
    // balao na metade de baixo da tela: a coluna e os avisos abrem para cima
    const embaixo = r.top + r.height / 2 > window.innerHeight / 2;
    trilho.classList.toggle('para-cima', embaixo); avisos.classList.toggle('para-cima', embaixo);
    for (const el of [trilho, avisos]) el.style.setProperty('--fmc-ancora-base', Math.max(8, window.innerHeight - r.top + 10) + 'px');
    // balao na metade esquerda: os avisos aparecem a direita dele
    const esquerda = fab.classList.contains('livre') && r.left + r.width / 2 < window.innerWidth / 2;
    avisos.classList.toggle('a-direita', esquerda);
    avisos.style.setProperty('--fmc-ancora-esq', (r.right + 12) + 'px');
    if (fab.classList.contains('livre')) avisos.style.setProperty('--fmc-ancora-dir', (window.innerWidth - r.left + 12) + 'px');
    if (fab.classList.contains('livre')) for (const el of [trilho, avisos]) if (!embaixo) el.style.setProperty('--fmc-ancora-topo', (r.bottom + 10) + 'px');
  }
  // o menu da conta (ui.js) e montado depois: observa o cabecalho ate o balao ficar por ultimo
  setTimeout(() => { if (!encaixar()) {
    const obs = new MutationObserver(() => { if (encaixar()) obs.disconnect(); });
    obs.observe(document.body, {childList: true, subtree: true}); setTimeout(() => obs.disconnect(), 10000);
  } }, 0);
  const cab0 = cabecalho();
  if (cab0) new MutationObserver(() => { const a = cab0.querySelector('.session-actions') || cab0; if (a.lastElementChild !== fab) encaixar(); }).observe(cab0, {childList: true, subtree: true});
  window.addEventListener('resize', ancorar);
  window.addEventListener('scroll', () => { if (trilhoAberto || avisos.children.length) ancorar(); }, {passive: true});

  // ---------- icone no topo ----------
  function botaoTopo() {
    const acoes = document.querySelector('main :is(.account-heading, .conciliation-heading, .topbar) .session-actions') || document.querySelector('.session-actions');
    if (!acoes || acoes.querySelector('.fmc-botao')) return !!acoes;
    const b = document.createElement('button');
    b.type = 'button'; b.className = 'fmc-botao'; b.title = 'FaturaChat'; b.setAttribute('aria-label', 'Abrir FaturaChat');
    b.innerHTML = ICONE + '<span class="fmc-badge" hidden></span>';
    b.addEventListener('click', () => alternarTrilho());
    acoes.prepend(b);
    return true;
  }
  // o icone do topo foi retirado: o acesso ao chat fica so no balao flutuante (canto superior direito)

  function mostrarTotal(n) {
    if (n > total && !mudoGeral() && !window.fmChatSom?.emNaoPerturbe?.()) animar('chegou'); // chegou mensagem nova: o balao pula, o icone balanca e solta ondas
    total = n;
    document.querySelectorAll('.fmc-badge').forEach(el => { el.hidden = !n; el.textContent = n > 99 ? '99+' : String(n); });
  }

  // ---------- dados ----------
  async function carregarConversas() {
    try {
      const r = await fetch('/api/chat/conversas', {headers: {Accept: 'application/json'}});
      if (!r.ok) return;
      const novas = (await r.json()).filter(c => !c.sistema);
      avisarNovas(novas);
      conversas = novas;
      renderTrilho(); if (!gaveta.hidden && !aberta) renderLista();
    } catch (_) {}
  }

  // ---------- bolinhas dispensadas (x): somem do trilho ate chegar mensagem nova na conversa ----------
  const CHAVE_OCULTAS = 'fm-chat-ocultas';
  const ocultas = (() => { try { return JSON.parse(localStorage.getItem(CHAVE_OCULTAS) || '{}'); } catch (_) { return {}; } })();
  const salvarOcultas = () => { try { localStorage.setItem(CHAVE_OCULTAS, JSON.stringify(ocultas)); } catch (_) {} };
  const instante = c => c.ultimaMensagemEm ? new Date(c.ultimaMensagemEm).getTime() : 0;
  function oculta(c) {
    const quando = ocultas[c.id];
    if (quando == null) return false;
    if (instante(c) > quando) { delete ocultas[c.id]; salvarOcultas(); return false; } // chegou mensagem nova: volta
    return true;
  }
  function dispensar(id) {
    const c = conversas.find(x => x.id === id); if (!c) return;
    ocultas[id] = instante(c); salvarOcultas(); renderTrilho();
  }

  // ---------- balao de previa quando chega mensagem nova ----------
  let ultimaVista = null; // id da conversa -> instante da ultima mensagem ja conhecida
  const avisos = document.createElement('div');
  avisos.className = 'fmc-avisos'; avisos.setAttribute('aria-live', 'polite');
  document.body.append(avisos);
  function avisarNovas(novas) {
    const antes = ultimaVista;
    ultimaVista = new Map(novas.map(c => [c.id, instante(c)]));
    if (!antes) return; // primeira carga: nao avisa o historico
    novas.filter(c => c.naoLidas && !c.silenciado && instante(c) > (antes.get(c.id) || 0) && c.id !== aberta)
      .slice(0, 3).forEach(mostrarAviso);
  }
  function mostrarAviso(c) {
    if (mudoGeral()) return;
    if (window.fmChatSom?.emNaoPerturbe?.() && !c.mencoesNaoLidas) return; // nao perturbe: so mencoes geram previa
    avisos.querySelector(`[data-id="${c.id}"]`)?.remove();
    const el = document.createElement('div');
    el.className = 'fmc-aviso'; el.dataset.id = c.id; el.setAttribute('role', 'button'); el.tabIndex = 0;
    const autor = c.tipo === 'GRUPO' && c.ultimoAutorNome ? `<em>${esc(c.ultimoAutorNome.split(/\s+/)[0])}:</em> ` : '';
    el.innerHTML = `${avatar(c)}<span class="fmc-aviso-corpo"><strong>${esc(c.nome)}</strong><small>${autor}${esc(c.ultimaMensagem || '')}</small></span>
      <button type="button" class="fmc-aviso-fechar" aria-label="Fechar aviso">×</button><i class="fmc-aviso-tempo"></i>`;
    const sair = () => { el.classList.add('saindo'); setTimeout(() => el.remove(), 220); };
    el.addEventListener('click', e => { if (e.target.closest('.fmc-aviso-fechar')) { sair(); return; } sair(); abrir(c.id); });
    el.addEventListener('keydown', e => { if (e.key === 'Enter') { sair(); abrir(c.id); } });
    let timer = setTimeout(sair, 7000);
    el.addEventListener('mouseenter', () => { clearTimeout(timer); el.classList.add('pausado'); });
    el.addEventListener('mouseleave', () => { el.classList.remove('pausado'); timer = setTimeout(sair, 3000); });
    ancorar(); avisos.prepend(el);
    while (avisos.children.length > 3) avisos.lastElementChild.remove();
  }
  function avatar(c, classe = '') {
    const grupo = c.tipo === 'GRUPO';
    const foto = !grupo && c.contato?.foto ? `<img alt="" loading="lazy" src="/api/usuarios/${c.contato.id}/foto?v=${c.contato.foto}" onerror="this.remove()">` : '';
    const online = !grupo && c.contato?.online ? '<i class="fmc-online"></i>' : '';
    return `<span class="fmc-av ${grupo ? 'grupo' : ''} ${classe}">${grupo ? '#' : esc(iniciais(c.nome))}${foto}${online}</span>`;
  }

  // ---------- trilho (D) ----------
  // trilho escondido por padrao: aparece ao clicar no icone do topo ou no balao flutuante
  let trilhoAberto = false;
  function renderTrilho() {
    trilho.classList.toggle('aberto', trilhoAberto);
    fab.classList.toggle('ativo', trilhoAberto);
    fab.setAttribute('aria-expanded', String(trilhoAberto));
    document.querySelectorAll('.fmc-botao').forEach(b => b.setAttribute('aria-expanded', String(trilhoAberto)));
    const recentes = conversas.filter(c => c.ultimaMensagem && !oculta(c)).slice(0, 7);
    trilho.innerHTML = recentes.map((c, i) => `<div class="fmc-trilho-item${c.naoLidas ? ' nao-lida' : ''}" role="button" tabindex="0" data-id="${c.id}" style="--i:${i}" aria-label="${esc(c.nome)}${c.naoLidas ? `, ${c.naoLidas} não lida(s)` : ''}">
        ${avatar(c)}${c.silenciado ? '<i class="fmc-silenciada" title="Silenciada">🔕</i>' : ''}${c.naoLidas ? `<b class="fmc-ponto">${c.naoLidas > 9 ? '9+' : c.naoLidas}</b>` : ''}
        <span class="fmc-dispensar" role="button" tabindex="0" title="Tirar do balão (volta quando chegar mensagem nova)" aria-label="Tirar ${esc(c.nome)} do balão">×</span>
        <span class="fmc-previa" role="tooltip"><strong>${esc(c.nome)}</strong><small>${esc((c.tipo === 'GRUPO' && c.ultimoAutorNome ? c.ultimoAutorNome.split(/\s+/)[0] + ': ' : '') + c.ultimaMensagem)}</small><em>${c.ultimaMensagemEm ? hora(c.ultimaMensagemEm) : ''}</em></span></div>`).join('')
      + `<button type="button" class="fmc-trilho-mais" style="--i:${recentes.length}" aria-label="Todas as conversas" title="Todas as conversas">⋯</button>`;
  }
  function alternarTrilho(forcar) {
    trilhoAberto = typeof forcar === 'boolean' ? forcar : !trilhoAberto;
    renderTrilho(); posicionar(); ancorar();
  }
  trilho.addEventListener('keydown', e => {    if (e.key !== 'Enter' && e.key !== ' ') return;    const x = e.target.closest('.fmc-dispensar'); if (x) { e.preventDefault(); dispensar(Number(x.closest('.fmc-trilho-item').dataset.id)); return; }    const item = e.target.closest('.fmc-trilho-item'); if (item) { e.preventDefault(); abrir(Number(item.dataset.id)); }  });
  trilho.addEventListener('click', e => {
    const x = e.target.closest('.fmc-dispensar');
    if (x) { e.stopPropagation(); dispensar(Number(x.closest('.fmc-trilho-item').dataset.id)); return; }
    const item = e.target.closest('.fmc-trilho-item');
    if (item) { abrir(Number(item.dataset.id)); return; }
    if (e.target.closest('.fmc-trilho-mais')) abrir();
  });
  // clicar fora (em qualquer ponto do site) fecha o painel da conversa e o trilho
  document.addEventListener('click', e => {
    const caminho = e.composedPath ? e.composedPath() : [e.target];
    if (caminho.some(el => el === gaveta || el === trilho || el === fab || el === avisos)) return;
    if (document.querySelector('dialog[open]')) return;
    if (!gaveta.hidden) fechar();
    if (trilhoAberto) alternarTrilho(false);
  });

  // ---------- gaveta (A) ----------
  // o painel abre abaixo do cabecalho da pagina (nao cobre o menu da conta) e ao lado do trilho
  function posicionar() {
    const cab = document.querySelector('main :is(.account-heading, .conciliation-heading, .topbar)');
    const fab0 = fab.classList.contains('no-topo') ? fab.getBoundingClientRect().bottom + 10 : 0;
    const fundo = Math.max(12, fab0 || (cab ? Math.min(140, cab.getBoundingClientRect().bottom + 10) : 16));
    gaveta.style.setProperty('--fmc-topo', fundo + 'px'); gaveta.style.setProperty('--fmc-base', '16px');
    if (fab.classList.contains('livre')) {
      // balao solto: o painel acompanha a altura do balao (abaixo dele, ou acima se estiver na metade de baixo)
      const rf = fab.getBoundingClientRect();
      if (rf.top + rf.height / 2 > window.innerHeight / 2) { gaveta.style.setProperty('--fmc-topo', '16px'); gaveta.style.setProperty('--fmc-base', Math.max(16, window.innerHeight - rf.top + 10) + 'px'); }
      else gaveta.style.setProperty('--fmc-topo', Math.min(window.innerHeight - 320, rf.bottom + 10) + 'px');
    }
    // painel ao lado da coluna de avatares (sem cobrir), do lado em que houver espaco; sem coluna, junto do balao
    const ref = (trilhoAberto ? trilho : fab).getBoundingClientRect();
    const doLadoEsquerdo = ref.left + ref.width / 2 < window.innerWidth / 2;
    gaveta.classList.toggle('a-direita', doLadoEsquerdo);
    gaveta.style.setProperty('--fmc-dir', Math.max(12, window.innerWidth - ref.left + 12) + 'px');
    gaveta.style.setProperty('--fmc-esq', Math.max(12, ref.right + 12) + 'px');
    if (!trilhoAberto && !fab.classList.contains('livre')) gaveta.style.setProperty('--fmc-dir', Math.max(12, window.innerWidth - ref.right) + 'px');
  }
  window.addEventListener('resize', () => { if (!gaveta.hidden) posicionar(); });
  window.addEventListener('scroll', () => { if (!gaveta.hidden) posicionar(); }, {passive: true});
  function abrir(id) {
    posicionar(); gaveta.hidden = false; document.body.classList.add('fmc-aberta');
    requestAnimationFrame(() => gaveta.classList.add('visivel'));
    if (id) abrirConversa(id); else { voltar(); $('.fmc-busca').focus(); }
  }
  function fechar() {
    gaveta.classList.remove('visivel'); document.body.classList.remove('fmc-aberta');
    guardarRascunho(); clearInterval(timerMsgs); aberta = null;
    setTimeout(() => { if (!gaveta.classList.contains('visivel')) gaveta.hidden = true; }, 200);
  }
  function voltar() {
    guardarRascunho(); aberta = null; clearInterval(timerMsgs);
    $('.fmc-lista-vista').hidden = false; $('.fmc-conversa-vista').hidden = true; $('.fmc-voltar').hidden = true;
    $('.fmc-titulo').textContent = 'FaturaChat'; $('.fmc-cheio').href = 'chat.html'; $('.fmc-mudo-conversa').hidden = true; $('.fmc-mudo-geral').hidden = false; $('.fmc-nao-perturbe').hidden = false;
    renderLista();
  }
  function renderLista() {
    if (vendoPendencias) { renderPendencias(); return; }
    if (vendoSalvas) { renderSalvas(); return; }
    const termo = $('.fmc-busca').value.trim().toLowerCase();
    const lista = conversas.filter(c => !termo || String(c.nome).toLowerCase().includes(termo));
    $('.fmc-lista').innerHTML = lista.length ? lista.map(c => `<li><button type="button" class="fmc-item${c.naoLidas ? ' nao-lida' : ''}" data-id="${c.id}">
        ${avatar(c)}<span class="fmc-item-corpo"><strong>${esc(c.nome)}</strong><small>${esc(c.ultimaMensagem || 'Sem mensagens ainda')}</small></span>
        <span class="fmc-item-meta">${c.ultimaMensagemEm ? `<em>${hora(c.ultimaMensagemEm)}</em>` : ''}${c.naoLidas ? `<b>${c.naoLidas > 99 ? '99+' : c.naoLidas}</b>` : ''}</span></button><button type="button" class="fmc-item-mudo${c.silenciado ? ' ativo' : ''}" data-id="${c.id}" aria-pressed="${!!c.silenciado}" title="${c.silenciado ? 'Conversa silenciada: clique para reativar' : 'Silenciar esta conversa'}" aria-label="${c.silenciado ? 'Reativar' : 'Silenciar'} ${esc(c.nome)}">${c.silenciado ? SINO_CORTADO : SINO}</button></li>`).join('')
      : `<li class="fmc-vazio">${termo ? 'Nenhuma conversa com esse nome.' : 'Você ainda não tem conversas. Abra o FaturaChat completo para começar uma.'}</li>`;
  }
  $('.fmc-busca').addEventListener('input', renderLista);
  function renderPendencias() {
    $('.fmc-lista').innerHTML = pendencias.length ? pendencias.map(p => {
      const [ic, nome] = ROTULO_STATUS[p.status] || ['', p.status];
      return `<li><button type="button" class="fmc-item fmc-pendencia" data-id="${p.conversaId}">
        <span class="fmc-av mini">${ic}</span><span class="fmc-item-corpo"><strong>${esc(p.conversa || 'Conversa')}${p.mencionado ? ' · @você' : ''}</strong>
        <small>${esc(p.autorNome)}: ${esc(p.texto || '')}</small><small>${nome}${p.responsavelNome ? ' · com ' + esc(p.responsavelNome.split(/\s+/)[0]) : ''}</small></span></button></li>`;
    }).join('') : '<li class="fmc-vazio">Nada pendente com você. 🎉</li>';
  }
  // aba "Salvas": mensagens que a pessoa guardou
  let vendoSalvas = false, salvas = [], idsSalvos = new Set();
  async function carregarSalvas() {
    salvas = await fetch('/api/chat/salvas', {headers: {Accept: 'application/json'}}).then(r => r.ok ? r.json() : []).catch(() => []);
    idsSalvos = new Set(salvas.map(s => s.mensagemId));
    if (vendoSalvas && !aberta) renderLista();
  }
  function renderSalvas() {
    $('.fmc-lista').innerHTML = salvas.length ? salvas.map(s => `<li><button type="button" class="fmc-item fmc-pendencia" data-id="${s.conversaId}">
        <span class="fmc-av mini">⭐</span><span class="fmc-item-corpo"><strong>${esc(s.conversa || 'Conversa')}</strong>
        <small>${esc(s.autorNome)}: ${esc(s.texto || '')}</small><small>${new Date(s.criadoEm).toLocaleDateString('pt-BR')}</small></span></button></li>`).join('')
      : '<li class="fmc-vazio">Nenhuma mensagem salva. Use ⋯ → ⭐ Salvar numa mensagem.</li>';
  }
  function trocarAba(aba) {
    vendoPendencias = aba === 'comigo'; vendoSalvas = aba === 'salvas';
    for (const [nome, el] of [['conversas', '.fmc-aba-conversas'], ['comigo', '.fmc-aba-comigo'], ['salvas', '.fmc-aba-salvas']]) {
      $(el).classList.toggle('ativo', aba === nome); $(el).setAttribute('aria-selected', String(aba === nome));
    }
    $('.fmc-busca').hidden = aba !== 'conversas';
    if (vendoPendencias) contarPendencias();
    if (vendoSalvas) carregarSalvas();
    renderLista();
  }
  $('.fmc-aba-conversas').addEventListener('click', () => trocarAba('conversas'));
  $('.fmc-aba-comigo').addEventListener('click', () => trocarAba('comigo'));
  $('.fmc-aba-salvas').addEventListener('click', () => trocarAba('salvas'));
  setTimeout(carregarSalvas, 0);
  setTimeout(() => { contarPendencias(); setInterval(contarPendencias, 60000); }, 0);
  $('.fmc-lista').addEventListener('click', e => {
    const m = e.target.closest('.fmc-item-mudo'); if (m) { e.stopPropagation(); alternarSilencio(Number(m.dataset.id)); return; }
    const b = e.target.closest('.fmc-item'); if (b) abrirConversa(Number(b.dataset.id));
  });
  $('.fmc-voltar').addEventListener('click', voltar);
  $('.fmc-fechar').addEventListener('click', fechar);
  $('.fmc-cheio').addEventListener('click', e => {
    if (e.ctrlKey || e.metaKey || e.shiftKey) return;
    const href = $('.fmc-cheio').getAttribute('href'), hash = href.split('#')[1];
    const largura = Math.min(1000, screen.availWidth - 40), altura = Math.min(720, screen.availHeight - 60);
    const j = window.open(`chat.html?janela=avulsa${hash ? '#' + hash : ''}`, 'fm-chat-equipe', `popup=yes,width=${largura},height=${altura},left=${Math.max(0, screen.availWidth - largura - 20)},top=40`);
    if (j) { e.preventDefault(); j.focus(); fechar(); }
  });
  document.addEventListener('keydown', e => { if (e.key !== 'Escape' || document.querySelector('dialog[open]')) return; if (!gaveta.hidden) fechar(); else if (trilhoAberto) alternarTrilho(false); });

  // rascunho por conversa: o que ficou digitado (e os anexos) nao vaza para outra conversa
  const rascunhos = new Map();
  function guardarRascunho() {
    if (!aberta) return;
    const texto = campo.value, arquivos = pendentes.slice();
    if (texto.trim() || arquivos.length) rascunhos.set(aberta, {texto, arquivos}); else rascunhos.delete(aberta);
  }
  function restaurarRascunho(id) {
    const r = rascunhos.get(id);
    campo.value = r ? r.texto : ''; pendentes = r ? r.arquivos.slice() : [];
    campo.style.height = 'auto'; if (campo.value) campo.style.height = Math.min(120, campo.scrollHeight) + 'px';
    renderPendentes(); painelEmoji.hidden = true;
  }
  async function abrirConversa(id) {
    const c = conversas.find(x => x.id === id) || {id, nome: 'Conversa'};
    if (aberta !== id) { guardarRascunho(); restaurarRascunho(id); }
    aberta = id;
    $('.fmc-lista-vista').hidden = true; $('.fmc-conversa-vista').hidden = false; $('.fmc-voltar').hidden = false;
    $('.fmc-titulo').innerHTML = `${avatar(c, 'mini')}<span>${esc(c.nome)}</span>`;
    atualizarMudoConversa(); $('.fmc-mudo-geral').hidden = true; $('.fmc-nao-perturbe').hidden = true; // dentro da conversa, so o sininho dela
    $('.fmc-cheio').href = `chat.html#c${id}`;
    $('.fmc-msgs').innerHTML = '<li class="fmc-vazio">Carregando…</li>';
    await carregarMensagens(true);
    clearInterval(timerMsgs); timerMsgs = setInterval(() => carregarMensagens(false), 5000);
    $('.fmc-envio textarea').focus();
  }
  let ultimaId = 0, ultimaAssinatura = '';
  const REACOES = ['👍', '✅', '👀', '❤️', '😂', '🙏', '🎉', '😮'];
  function htmlReacoes(m) {
    const lista = (m.reacoes || []).filter(r => r.usuarios?.length);
    if (!lista.length) return '';
    return `<div class="fmc-reacoes">${lista.map(r => {
      const minha = eu && r.usuarios.includes(eu.id);
      return `<button type="button" class="fmc-reacao${minha ? ' minha' : ''}" data-msg="${m.id}" data-emoji="${esc(r.emoji)}" title="${esc((r.nomes || []).join(', '))}">${esc(r.emoji)}<b>${r.usuarios.length}</b></button>`;
    }).join('')}</div>`;
  }
  async function carregarMensagens(rolar) {
    const id = aberta; if (!id) return;
    try {
      if (!eu) eu = await fetch('/api/auth/me').then(r => r.json()).catch(() => ({}));
      const r = await fetch(`/api/chat/conversas/${id}/mensagens`, {headers: {Accept: 'application/json'}});
      const statusLista = await fetch(`/api/chat/conversas/${id}/status`, {headers: {Accept: 'application/json'}}).then(x => x.ok ? x.json() : []).catch(() => []);
      statusAtuais = new Map(statusLista.map(s => [s.mensagemId, s]));
      if (!r.ok || aberta !== id) return;
      const pagina = await r.json(), msgs = pagina.mensagens || [];
      const nova = msgs.length ? msgs[msgs.length - 1].id : 0;
      // redesenha quando muda algo visivel (mensagem nova, edicao ou reacao)
      const assinatura = msgs.map(m => `${m.id}:${m.editada ? 1 : 0}${m.apagada ? 1 : 0}:${(m.reacoes || []).map(x => x.emoji + x.usuarios.length).join(',')}:${statusAtuais.get(m.id)?.status || ''}${statusAtuais.get(m.id)?.responsavelId || ''}`).join('|');
      if (!rolar && assinatura === ultimaAssinatura) return;
      const lista = $('.fmc-msgs'), noFim = lista.scrollHeight - lista.scrollTop - lista.clientHeight < 60, topo = lista.scrollTop;
      ultimaId = nova; ultimaAssinatura = assinatura; mensagensAtuais = msgs;
      lista.innerHTML = msgs.length ? msgs.map(m => {
        if (m.sistema) return `<li class="fmc-sis">${esc(m.texto)}</li>`;
        const minha = eu && m.autorId === eu.id;
        const anexos = (m.anexos || []).map(a => String(a.tipo || '').startsWith('image/') ? `<a class="fmc-img" href="/api/chat/anexos/${a.id}" target="_blank" rel="noopener" title="${esc(a.nome)}"><img alt="${esc(a.nome)}" loading="lazy" src="/api/chat/anexos/${a.id}"></a>` : `<a href="/api/chat/anexos/${a.id}" target="_blank" rel="noopener">📎 ${esc(a.nome)}</a>`).join('');
        return `<li class="fmc-msg${minha ? ' minha' : ''}" data-msg="${m.id}">${minha ? '' : `<span class="fmc-autor">${esc(m.autorNome)}</span>`}
          <p>${m.apagada ? '<em>Mensagem apagada</em>' : esc(m.texto || '').replace(/(https?:\/\/[^\s<]+)/g, '<a href="$1" target="_blank" rel="noopener">$1</a>').replace(/(^|\s)(@[\p{L}.\-]+)/gu, '$1<span class="fmc-marca">$2</span>').replace(/^[\s\S]*$/, t => window.fmChatRefs ? window.fmChatRefs.linkificar(t) : t)}</p>${anexos}<time>${idsSalvos.has(m.id) ? '⭐ ' : ''}${hora(m.criadoEm)}${m.editada ? ' · editada' : ''}</time>
          ${m.apagada ? '' : `<span class="fmc-msg-acoes"><button type="button" class="fmc-reagir" data-msg="${m.id}" title="Reagir" aria-label="Reagir à mensagem">☺<small>+</small></button><button type="button" class="fmc-mais" data-msg="${m.id}" title="Mais ações" aria-label="Mais ações da mensagem">⋯</button></span>`}
          ${htmlStatus(statusAtuais.get(m.id))}${htmlReacoes(m)}</li>`;
      }).join('') : '<li class="fmc-vazio">Nenhuma mensagem ainda. Diga oi!</li>';
      if (rolar || noFim) lista.scrollTop = lista.scrollHeight; else lista.scrollTop = topo;
      // cartoes das referencias citadas (#lote, #chamado, #relatorio, #div e links das telas)
      if (window.fmChatRefs) for (const m of msgs) {
        if (m.apagada || !m.texto || !window.fmChatRefs.extrair(m.texto).length) continue;
        const li = lista.querySelector(`.fmc-msg[data-msg="${m.id}"]`);
        const reacoes = li?.querySelector('.fmc-reacoes');
        const caixa = document.createElement('div'); li?.insertBefore(caixa, reacoes || null);
        window.fmChatRefs.cartoes(caixa, m.texto).then(() => { if (rolar || noFim) lista.scrollTop = lista.scrollHeight; });
      }
      if (nova) fetch(`/api/chat/conversas/${id}/leitura`, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ateId: nova})})
        .then(() => { const c = conversas.find(x => x.id === id); if (c && c.naoLidas) { mostrarTotal(Math.max(0, total - c.naoLidas)); c.naoLidas = 0; renderTrilho(); } }).catch(() => {});
    } catch (_) {}
  }
  // ---------- reacoes ----------
  const seletorReacao = document.createElement('div');
  seletorReacao.className = 'fmc-seletor-reacao'; seletorReacao.hidden = true; seletorReacao.setAttribute('role', 'menu');
  seletorReacao.innerHTML = REACOES.map(e => `<button type="button" role="menuitem" data-emoji="${e}">${e}</button>`).join('');
  gaveta.append(seletorReacao);
  async function reagir(msgId, emoji) {
    seletorReacao.hidden = true;
    const r = await fetch(`/api/chat/mensagens/${msgId}/reacoes`, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({emoji})}).catch(() => null);
    if (!r || !r.ok) { window.fmToast?.('Não foi possível reagir', 'erro'); return; }
    ultimaAssinatura = ''; carregarMensagens(false);
  }
  $('.fmc-msgs').addEventListener('click', e => {
    const chip = e.target.closest('.fmc-reacao');
    if (chip) { reagir(Number(chip.dataset.msg), chip.dataset.emoji); return; }
    const b = e.target.closest('.fmc-reagir'); if (!b) return;
    e.stopPropagation();
    const rb = b.getBoundingClientRect(), rg = gaveta.getBoundingClientRect();
    seletorReacao.dataset.msg = b.dataset.msg; seletorReacao.hidden = false;
    const larg = seletorReacao.offsetWidth;
    seletorReacao.style.left = Math.max(8, Math.min(rg.width - larg - 8, rb.left - rg.left + rb.width / 2 - larg / 2)) + 'px';
    seletorReacao.style.top = Math.max(56, rb.top - rg.top - 46) + 'px';
  });
  seletorReacao.addEventListener('click', e => { const b = e.target.closest('button'); if (b) reagir(Number(seletorReacao.dataset.msg), b.dataset.emoji); });
  // ---------- acoes na mensagem: criar chamado no Service Desk e copiar o texto ----------
  let mensagensAtuais = [];
  const menuAcoes = document.createElement('div');
  menuAcoes.className = 'fmc-menu-acoes'; menuAcoes.hidden = true; menuAcoes.setAttribute('role', 'menu');
  menuAcoes.innerHTML = `<button type="button" role="menuitem" data-acao="chamado">🛟 Criar chamado no Service Desk</button>
    <button type="button" role="menuitem" data-acao="copiar">📋 Copiar texto</button>
    <button type="button" role="menuitem" data-acao="salvar">⭐ Salvar mensagem</button>    <button type="button" role="menuitem" data-lembrar="60">⏰ Lembrar em 1 hora</button>    <button type="button" role="menuitem" data-lembrar="amanha">⏰ Lembrar amanhã às 9h</button>    <button type="button" role="menuitem" data-lembrar="escolher">⏰ Lembrar em… (escolher)</button>
    <hr><button type="button" role="menuitem" data-status="PENDENTE">🟡 Marcar como pendente</button>
    <button type="button" role="menuitem" data-status="EM_ANALISE" data-assumir="1">🔵 Em análise (assumir)</button>
    <button type="button" role="menuitem" data-status="RESOLVIDO">🟢 Resolvido</button>
    <button type="button" role="menuitem" data-status="" data-limpar="1">✖ Tirar status</button>`;
  gaveta.append(menuAcoes);
  $('.fmc-msgs').addEventListener('click', e => {
    const b = e.target.closest('.fmc-mais'); if (!b) return;
    e.stopPropagation(); seletorReacao.hidden = true;
    const rb = b.getBoundingClientRect(), rg = gaveta.getBoundingClientRect();
    menuAcoes.dataset.msg = b.dataset.msg; menuAcoes.hidden = false;
    menuAcoes.querySelector('[data-acao="salvar"]').textContent = idsSalvos.has(Number(b.dataset.msg)) ? '⭐ Remover dos salvos' : '⭐ Salvar mensagem';
    const larg = menuAcoes.offsetWidth, alt = menuAcoes.offsetHeight;
    menuAcoes.style.left = Math.max(8, Math.min(rg.width - larg - 8, rb.right - rg.left - larg)) + 'px';
    menuAcoes.style.top = Math.min(rg.height - alt - 8, Math.max(56, rb.bottom - rg.top + 4)) + 'px';
  });
  gaveta.addEventListener('click', e => { if (!menuAcoes.hidden && !menuAcoes.contains(e.target) && !e.target.closest('.fmc-mais')) menuAcoes.hidden = true; });
  $('.fmc-msgs').addEventListener('scroll', () => { menuAcoes.hidden = true; }, {passive: true});
  menuAcoes.addEventListener('click', async e => {
    const b = e.target.closest('button'); if (!b) return;
    menuAcoes.hidden = true;
    const m = mensagensAtuais.find(x => x.id === Number(menuAcoes.dataset.msg)); if (!m) return;
    if (b.dataset.status !== undefined) { definirStatus(m.id, b.dataset.status, !!b.dataset.assumir); return; }
    if (b.dataset.acao === 'salvar') { alternarSalva(m); return; }
    if (b.dataset.lembrar) { lembrar(m, b.dataset.lembrar); return; }
    if (b.dataset.acao === 'copiar') {
      try { await navigator.clipboard.writeText(m.texto || ''); window.fmToast?.('Texto copiado', 'ok'); } catch (_) { window.fmToast?.('Não foi possível copiar', 'erro'); }
      return;
    }
    criarChamado(m);
  });
  // ---------- status de acompanhamento ----------
  let statusAtuais = new Map();
  const ROTULO_STATUS = {PENDENTE: ['🟡', 'Pendente', 'ambar'], EM_ANALISE: ['🔵', 'Em análise', 'azul'], RESOLVIDO: ['🟢', 'Resolvido', 'verde']};
  function htmlStatus(s) {
    if (!s) return '';
    const [ic, nome, cor] = ROTULO_STATUS[s.status] || ['', s.status, 'cinza'];
    return `<span class="fmc-status" data-cor="${cor}" title="Alterado por ${esc(s.alteradoPorNome)} em ${new Date(s.alteradoEm).toLocaleString('pt-BR')}">${ic} ${nome}${s.responsavelNome ? ` · com ${esc(s.responsavelNome.split(/\s+/)[0])}` : ''}</span>`;
  }
  async function definirStatus(id, status, assumir) {
    const r = await fetch(`/api/chat/mensagens/${id}/status`, {method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({status, assumir})}).catch(() => null);
    if (!r || !r.ok) { window.fmToast?.('Não foi possível alterar o status', 'erro'); return; }
    ultimaAssinatura = ''; carregarMensagens(false); contarPendencias();
  }
  // aba "Comigo": pendencias abertas em que fui mencionado, sou autor ou responsavel
  let vendoPendencias = false, pendencias = [];
  async function contarPendencias() {
    pendencias = await fetch('/api/chat/pendencias', {headers: {Accept: 'application/json'}}).then(r => r.ok ? r.json() : []).catch(() => []);
    const b = $('.fmc-aba-comigo'); if (b) b.querySelector('b').textContent = pendencias.length || '';
    if (vendoPendencias && !aberta) renderLista();
  }
  async function alternarSalva(m) {
    const salvar = !idsSalvos.has(m.id);
    const r = await fetch(`/api/chat/mensagens/${m.id}/salva`, {method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({salva: salvar})}).catch(() => null);
    if (!r || !r.ok) { window.fmToast?.('Não foi possível salvar', 'erro'); return; }
    salvar ? idsSalvos.add(m.id) : idsSalvos.delete(m.id);
    window.fmToast?.(salvar ? 'Mensagem salva ⭐' : 'Removida dos salvos', 'ok');
    carregarSalvas(); ultimaAssinatura = ''; carregarMensagens(false);
  }
  async function lembrar(m, quando) {
    let data;
    if (quando === '60') data = new Date(Date.now() + 3600e3);
    else if (quando === 'amanha') { data = new Date(); data.setDate(data.getDate() + 1); data.setHours(9, 0, 0, 0); }
    else {
      const d = document.createElement('dialog'); d.className = 'fm-dialogo fmc-dialogo';
      const padrao = new Date(Date.now() + 3600e3 - new Date().getTimezoneOffset() * 60e3).toISOString().slice(0, 16);
      d.innerHTML = `<form method="dialog"><h2>Lembrar desta mensagem</h2>
        <label class="fm-campo">Quando<input type="datetime-local" name="quando" required value="${padrao}"></label>
        <div class="fm-dialogo-acoes"><button type="button" class="secondary-button" value="cancelar">Cancelar</button><button type="submit" class="primary-button" value="ok">Criar lembrete</button></div></form>`;
      document.body.append(d); d.querySelector('[value=cancelar]').addEventListener('click', () => d.close('cancelar')); d.showModal();
      const escolha = await new Promise(ok => d.addEventListener('close', () => ok(d.returnValue), {once: true}));
      const valor = d.querySelector('[name=quando]').value; d.remove();
      if (escolha !== 'ok' || !valor) return;
      data = new Date(valor);
    }
    const r = await fetch(`/api/chat/mensagens/${m.id}/lembrete`, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({quando: data.toISOString()})}).catch(() => null);
    if (!r || !r.ok) { const erro = r ? (await r.json().catch(() => ({}))).erro : ''; window.fmToast?.(erro || 'Não foi possível criar o lembrete', 'erro'); return; }
    window.fmToast?.(`Lembrete criado para ${data.toLocaleString('pt-BR', {weekday: 'short', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'})} ⏰`, 'ok');
  }
  async function criarChamado(m) {
    const tipos = await fetch('/api/chamados/tipos', {headers: {Accept: 'application/json'}}).then(r => r.ok ? r.json() : []).catch(() => []);
    const ativos = tipos.filter(t => t.ativo);
    if (!ativos.length) { window.fmToast?.('Não foi possível carregar os tipos de chamado', 'erro'); return; }
    const c = conversas.find(x => x.id === aberta);
    const titulo = (m.texto || 'Mensagem do FaturaChat').replace(/\s+/g, ' ').slice(0, 120);
    const descricao = `${m.texto || ''}\n\n— Mensagem de ${m.autorNome} no FaturaChat (${c ? c.nome : 'conversa'}), em ${new Date(m.criadoEm).toLocaleString('pt-BR')}.`;
    const d = document.createElement('dialog');
    d.className = 'fm-dialogo fmc-dialogo';
    d.innerHTML = `<form method="dialog"><h2>Criar chamado no Service Desk</h2>
      <label class="fm-campo">Tipo<select name="tipo" required>${ativos.map(t => `<option value="${esc(t.codigo)}">${esc(t.nome)}</option>`).join('')}</select></label>
      <label class="fm-campo">Título<input name="titulo" required maxlength="180" value="${esc(titulo)}"></label>
      <label class="fm-campo">Descrição<textarea name="descricao" rows="5" required maxlength="4000">${esc(descricao)}</textarea></label>
      <div class="fm-dialogo-acoes"><button type="button" class="secondary-button" value="cancelar">Cancelar</button><button type="submit" class="primary-button" value="ok">Criar chamado</button></div></form>`;
    document.body.append(d);
    d.querySelector('[value=cancelar]').addEventListener('click', () => d.close('cancelar'));
    d.showModal();
    const escolha = await new Promise(ok => d.addEventListener('close', () => ok(d.returnValue), {once: true}));
    const form = d.querySelector('form');
    const dados = new FormData();
    dados.append('tipoCodigo', form.tipo.value); dados.append('titulo', form.titulo.value.trim()); dados.append('descricao', form.descricao.value.trim());
    dados.append('paginaOrigem', 'FaturaChat');
    d.remove();
    if (escolha !== 'ok') return;
    const r = await fetch('/api/chamados', {method: 'POST', body: dados}).catch(() => null);
    if (!r || !r.ok) { const erro = r ? (await r.json().catch(() => ({}))).erro : ''; window.fmToast?.(erro || 'Não foi possível criar o chamado', 'erro'); return; }
    const criado = await r.json();
    const id = criado?.chamado?.id;
    window.fmToast?.(`Chamado ${criado?.chamado?.protocolo || ''} criado`, 'ok');
    // avisa na conversa, com o cartao do chamado
    if (id && aberta) await fetch(`/api/chat/conversas/${aberta}/mensagens`, {method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({texto: `🛟 Abri um chamado a partir desta conversa: #chamado ${id}`, respondeA: m.id})}).catch(() => null);
    ultimaAssinatura = ''; carregarMensagens(true);
  }
  gaveta.addEventListener('click', e => { if (!seletorReacao.hidden && !seletorReacao.contains(e.target) && !e.target.closest('.fmc-reagir')) seletorReacao.hidden = true; });
  $('.fmc-msgs').addEventListener('scroll', () => { seletorReacao.hidden = true; }, {passive: true});
  const campo = $('.fmc-envio textarea');
  // ---------- mencoes: ao digitar "@" sugere os participantes (e @todos em grupo) ----------
  const normalizar = s => (s || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim().replace(/\s+/g, ' ');
  const nomeMencao = nome => (nome || '').trim().split(/\s+/).join('.');
  const cacheParticipantes = {};
  let candidatos = [], ativo = 0;
  async function pessoasDaConversa() {
    const c = conversas.find(x => x.id === aberta); if (!c) return [];
    if (c.tipo !== 'GRUPO') return c.contato ? [{id: c.contato.id, nome: c.contato.nome}] : [];
    if (!cacheParticipantes[c.id]) {
      try {
        if (!eu) eu = await fetch('/api/auth/me').then(r => r.json()).catch(() => ({}));
        const r = await fetch(`/api/chat/conversas/${c.id}/participantes`, {headers: {Accept: 'application/json'}});
        cacheParticipantes[c.id] = r.ok ? (await r.json()).filter(p => p.id !== eu?.id) : [];
      } catch (_) { return []; }
    }
    return cacheParticipantes[c.id];
  }
  function termoMencao() {
    const antes = campo.value.slice(0, campo.selectionStart);
    const m = antes.match(/(^|\s)@([\p{L}.\-]{0,40})$/u);
    return m ? {termo: m[2], inicio: antes.length - m[2].length - 1} : null;
  }
  async function sugerirMencao() {
    const caixa = $('.fmc-mencoes'), alvo = termoMencao();
    if (!alvo) { caixa.hidden = true; return; }
    const termo = normalizar(alvo.termo).replace(/\./g, ' ');
    const pessoas = await pessoasDaConversa();
    candidatos = pessoas.filter(p => !termo || normalizar(p.nome).startsWith(termo) || normalizar(p.nome).split(' ').some(x => x.startsWith(termo))).slice(0, 6);
    const c = conversas.find(x => x.id === aberta);
    if (c?.tipo === 'GRUPO' && 'todos'.startsWith(termo)) candidatos.push({id: 0, nome: 'todos', todos: true}); if (c?.tipo === 'GRUPO') [['equipe', 'toda a equipe interna'], ['admin', 'administradores'], ['operador', 'operadores'], ['dev', 'desenvolvedores']].filter(([n]) => n.startsWith(termo)).forEach(([n, d]) => candidatos.push({id: 0, nome: n, todos: true, rotulo: '@' + n + ' — ' + d}));
    if (!candidatos.length) { caixa.hidden = true; return; }
    ativo = 0;
    caixa.innerHTML = candidatos.map((p, i) => `<button type="button" role="option" class="fmc-mencao${i === 0 ? ' ativo' : ''}" data-i="${i}">
      <span class="fmc-av mini ${p.todos ? 'grupo' : ''}">${p.todos ? '@' : esc(iniciais(p.nome))}${!p.todos && p.foto ? `<img alt="" src="/api/usuarios/${p.id}/foto?v=${p.foto}" onerror="this.remove()">` : ''}</span>
      <span>${p.todos ? esc(p.rotulo || '@todos — avisar todo o grupo') : esc(p.nome)}</span></button>`).join('');
    caixa.hidden = false;
  }
  function escolherMencao(i) {
    const p = candidatos[i], alvo = termoMencao(); if (!p || !alvo) return;
    const inserir = '@' + (p.todos ? p.nome : nomeMencao(p.nome)) + ' ';
    campo.value = campo.value.slice(0, alvo.inicio) + inserir + campo.value.slice(campo.selectionStart);
    campo.selectionStart = campo.selectionEnd = alvo.inicio + inserir.length;
    $('.fmc-mencoes').hidden = true; campo.focus();
  }
  function teclaMencao(e) {
    const caixa = $('.fmc-mencoes'); if (caixa.hidden) return false;
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault(); ativo = (ativo + (e.key === 'ArrowDown' ? 1 : -1) + candidatos.length) % candidatos.length;
      [...caixa.children].forEach((b, i) => b.classList.toggle('ativo', i === ativo)); return true;
    }
    if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); escolherMencao(ativo); return true; }
    if (e.key === 'Escape') { caixa.hidden = true; e.stopPropagation(); return true; }
    return false;
  }
  $('.fmc-mencoes').addEventListener('mousedown', e => { const b = e.target.closest('.fmc-mencao'); if (b) { e.preventDefault(); escolherMencao(Number(b.dataset.i)); } });
  // ---------- respostas rapidas: "/" no comeco da mensagem lista os modelos da pessoa ----------
  let respostas = null, opcoesResposta = [], ativaResposta = 0;
  async function carregarRespostas(forcar) {
    if (respostas && !forcar) return respostas;
    respostas = await fetch('/api/chat/respostas-rapidas', {headers: {Accept: 'application/json'}}).then(r => r.ok ? r.json() : []).catch(() => []);
    return respostas;
  }
  async function sugerirResposta() {
    const caixa = $('.fmc-respostas'), m = campo.value.match(/^\/([\w\-]{0,30})$/);
    if (!m) { caixa.hidden = true; return; }
    const termo = m[1].toLowerCase();
    const lista = (await carregarRespostas()).filter(r => r.atalho.startsWith(termo) || r.texto.toLowerCase().includes(termo));
    opcoesResposta = [...lista.slice(0, 8), {novo: true}];
    ativaResposta = 0;
    caixa.innerHTML = opcoesResposta.map((r, i) => r.novo
      ? `<button type="button" class="fmc-mencao fmc-resposta-nova${i === 0 ? ' ativo' : ''}" data-i="${i}"><span class="fmc-av mini">＋</span><span>Nova resposta rápida${termo ? ` “/${esc(termo)}”` : ''}</span></button>`
      : `<button type="button" class="fmc-mencao${i === 0 ? ' ativo' : ''}" data-i="${i}"><span class="fmc-atalho">/${esc(r.atalho)}</span><span class="fmc-resposta-texto">${esc(r.texto)}</span><span class="fmc-resposta-apagar" data-apagar="${r.id}" title="Apagar esta resposta" role="button">×</span></button>`).join('');
    caixa.hidden = false;
  }
  async function escolherResposta(i) {
    const r = opcoesResposta[i]; if (!r) return;
    $('.fmc-respostas').hidden = true;
    if (r.novo) { await novaResposta(campo.value.replace(/^\//, '')); return; }
    campo.value = r.texto; campo.focus();
    campo.style.height = 'auto'; campo.style.height = Math.min(120, campo.scrollHeight) + 'px';
  }
  async function novaResposta(atalhoInicial) {
    const d = document.createElement('dialog'); d.className = 'fm-dialogo fmc-dialogo';
    d.innerHTML = `<form method="dialog"><h2>Nova resposta rápida</h2>
      <label class="fm-campo">Atalho (digite / e ele no chat)<input name="atalho" required maxlength="30" pattern="[A-Za-z0-9_\-]+" value="${esc(atalhoInicial || '')}" placeholder="recebido"></label>
      <label class="fm-campo">Texto<textarea name="texto" rows="3" required maxlength="1000" placeholder="Planilha recebida, vou conferir até hoje às 17h ✅"></textarea></label>
      <div class="fm-dialogo-acoes"><button type="button" class="secondary-button" value="cancelar">Cancelar</button><button type="submit" class="primary-button" value="ok">Salvar</button></div></form>`;
    document.body.append(d); d.querySelector('[value=cancelar]').addEventListener('click', () => d.close('cancelar')); d.showModal();
    const escolha = await new Promise(ok => d.addEventListener('close', () => ok(d.returnValue), {once: true}));
    const atalho = d.querySelector('[name=atalho]').value, texto = d.querySelector('[name=texto]').value; d.remove();
    if (escolha !== 'ok') return;
    const r = await fetch('/api/chat/respostas-rapidas', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({atalho, texto})}).catch(() => null);
    if (!r || !r.ok) { const erro = r ? (await r.json().catch(() => ({}))).erro : ''; window.fmToast?.(erro || 'Não foi possível salvar', 'erro'); return; }
    await carregarRespostas(true);
    campo.value = texto; campo.focus();
    window.fmToast?.('Resposta rápida salva', 'ok');
  }
  function teclaResposta(e) {
    const caixa = $('.fmc-respostas'); if (caixa.hidden) return false;
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault(); ativaResposta = (ativaResposta + (e.key === 'ArrowDown' ? 1 : -1) + opcoesResposta.length) % opcoesResposta.length;
      [...caixa.children].forEach((b, i) => b.classList.toggle('ativo', i === ativaResposta)); return true;
    }
    if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); escolherResposta(ativaResposta); return true; }
    if (e.key === 'Escape') { caixa.hidden = true; e.stopPropagation(); return true; }
    return false;
  }
  $('.fmc-respostas').addEventListener('mousedown', async e => {
    const apagar = e.target.closest('[data-apagar]');
    if (apagar) {
      e.preventDefault();
      await fetch(`/api/chat/respostas-rapidas/${apagar.dataset.apagar}`, {method: 'DELETE'}).catch(() => null);
      await carregarRespostas(true); sugerirResposta(); return;
    }
    const b = e.target.closest('.fmc-mencao'); if (b) { e.preventDefault(); escolherResposta(Number(b.dataset.i)); }
  });
  campo.addEventListener('keydown', e => { if (teclaResposta(e)) return; if (teclaMencao(e)) return; if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); $('.fmc-envio').requestSubmit(); } });
  campo.addEventListener('input', () => { campo.style.height = 'auto'; campo.style.height = Math.min(120, campo.scrollHeight) + 'px'; sugerirMencao(); sugerirResposta(); });
  campo.addEventListener('click', sugerirMencao);
  campo.addEventListener('blur', () => setTimeout(() => { gaveta.querySelectorAll('.fmc-mencoes').forEach(x => { x.hidden = true; }); }, 150));
  // ---------- anexos (clipe e Ctrl+V de imagem) ----------
  let pendentes = [];
  const LIMITE = 10 * 1024 * 1024;
  function adicionarArquivos(lista) {
    for (const f of lista) {
      if (f.size > LIMITE) { window.fmToast?.(`${f.name} passa de 10 MB`, 'erro'); continue; }
      if (pendentes.length >= 5) { window.fmToast?.('Até 5 anexos por mensagem', 'erro'); break; }
      pendentes.push(f);
    }
    renderPendentes();
  }
  function renderPendentes() {
    const caixa = $('.fmc-anexos');
    caixa.hidden = !pendentes.length;
    caixa.querySelectorAll('img').forEach(i => URL.revokeObjectURL(i.src));
    caixa.replaceChildren();
    pendentes.forEach((f, i) => {
      const item = document.createElement('span');
      item.className = 'fmc-anexo';

      if (f.type.startsWith('image/')) {
        const img = document.createElement('img');
        img.alt = '';
        img.src = URL.createObjectURL(f);
        item.appendChild(img);
      } else {
        const icon = document.createElement('i');
        icon.textContent = '📄';
        item.appendChild(icon);
      }

      const nome = String(f.name ?? '');
      const small = document.createElement('small');
      small.title = nome;
      small.textContent = nome;
      item.appendChild(small);

      const btn = document.createElement('button');
      btn.type = 'button';
      btn.dataset.i = String(i);
      btn.setAttribute('aria-label', `Remover ${nome}`);
      btn.textContent = '×';
      item.appendChild(btn);

      caixa.appendChild(item);
    });
  }
  $('.fmc-anexos').addEventListener('click', e => { const b = e.target.closest('button[data-i]'); if (b) { pendentes.splice(Number(b.dataset.i), 1); renderPendentes(); } });
  $('.fmc-clipe').addEventListener('click', () => $('.fmc-arquivo').click());
  $('.fmc-arquivo').addEventListener('change', e => { adicionarArquivos([...e.target.files]); e.target.value = ''; campo.focus(); });
  campo.addEventListener('paste', e => {
    const arquivos = [...(e.clipboardData?.items || [])].filter(it => it.kind === 'file').map(it => it.getAsFile()).filter(Boolean)
      .map((f, i) => f.name && f.name !== 'image.png' ? f : new File([f], `imagem-colada-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}${i ? '-' + i : ''}.${(f.type.split('/')[1] || 'png').replace('jpeg', 'jpg')}`, {type: f.type}));
    if (arquivos.length) { e.preventDefault(); adicionarArquivos(arquivos); }
  });
  // arrastar e soltar arquivo na conversa
  $('.fmc-conversa-vista').addEventListener('dragover', e => { if (e.dataTransfer?.types?.includes('Files')) { e.preventDefault(); gaveta.classList.add('soltando'); } });
  $('.fmc-conversa-vista').addEventListener('dragleave', e => { if (!gaveta.contains(e.relatedTarget)) gaveta.classList.remove('soltando'); });
  $('.fmc-conversa-vista').addEventListener('drop', e => { e.preventDefault(); gaveta.classList.remove('soltando'); adicionarArquivos([...(e.dataTransfer?.files || [])]); });

  // ---------- emojis ----------
  const EMOJIS = {
    'Frequentes': '😀 😂 🙂 😉 😊 😍 🤔 😅 😎 🙏 👍 👎 👏 🙌 💪 🤝 ✅ ❌ ⚠️ 🔥',
    'Rostos': '😁 😃 😄 😆 🤣 😇 🥰 😘 😋 😜 🤗 🤭 🤫 😐 😑 😶 🙄 😏 😬 😌 😔 😴 😷 🤒 🥳 🥺 😢 😭 😤 😡 😱 😳 🤯 😮',
    'Gestos': '👋 🤙 👌 ✌️ 🤞 👉 👈 👆 👇 ☝️ ✋ 🤚 🖐️ 🫡 🙋 🤷 🙅 🙆 💁 🤦',
    'Trabalho': '📌 📎 📝 📄 📁 📊 📈 📉 🗂️ 📅 ⏰ ⏳ 💰 💵 🧾 🏥 💊 🩺 📞 💻 📧 🔍 🔒 ✔️',
    'Símbolos': '❤️ 💙 💚 💛 🧡 💜 ⭐ ✨ 🎉 🎂 ☕ 🍕 🚀 💡 ❓ ❗ 💯 🆗 🔴 🟢 🟡'
  };
  const painelEmoji = $('.fmc-emojis');
  painelEmoji.innerHTML = Object.entries(EMOJIS).map(([t, l]) => `<p>${t}</p><div>${l.split(' ').map(e => `<button type="button">${e}</button>`).join('')}</div>`).join('');
  $('.fmc-emoji-botao').addEventListener('click', e => { e.stopPropagation(); painelEmoji.hidden = !painelEmoji.hidden; });
  painelEmoji.addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    const ini = campo.selectionStart ?? campo.value.length, fim = campo.selectionEnd ?? ini;
    campo.value = campo.value.slice(0, ini) + b.textContent + campo.value.slice(fim);
    campo.selectionStart = campo.selectionEnd = ini + b.textContent.length;
    campo.focus();
  });
  document.addEventListener('click', e => { if (!painelEmoji.hidden && !painelEmoji.contains(e.target)) painelEmoji.hidden = true; });

  $('.fmc-envio').addEventListener('submit', async e => {
    e.preventDefault();
    const texto = campo.value.trim(); if ((!texto && !pendentes.length) || !aberta) return;
    const arquivos = pendentes;
    rascunhos.delete(aberta); campo.value = ''; campo.style.height = 'auto'; pendentes = []; renderPendentes(); painelEmoji.hidden = true;
    let corpo;
    if (arquivos.length) {
      corpo = new FormData(); if (texto) corpo.append('texto', texto); arquivos.forEach(f => corpo.append('arquivos', f, f.name));
    }
    const r = await fetch(`/api/chat/conversas/${aberta}/mensagens`, arquivos.length ? {method: 'POST', body: corpo}
      : {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({texto})}).catch(() => null);
    if (!r || !r.ok) {
      campo.value = texto; pendentes = arquivos; renderPendentes();
      const msg = r ? (await r.json().catch(() => ({}))).erro : '';
      window.fmToast?.(msg || 'Não foi possível enviar', 'erro'); return;
    }
    carregarMensagens(true); carregarConversas();
  });

  function atualizarMudoGeral() {
    const b = $('.fmc-mudo-geral'), mudo = mudoGeral();
    b.innerHTML = mudo ? SINO_CORTADO : SINO; b.setAttribute('aria-pressed', String(mudo));
    b.title = mudo ? 'Notificações silenciadas (todas as conversas): clique para reativar' : 'Silenciar todas as notificações do chat';
    b.setAttribute('aria-label', b.title);
    fab.classList.toggle('mudo', mudo);
  }
  function atualizarMudoConversa() {
    const b = $('.fmc-mudo-conversa'), c = conversas.find(x => x.id === aberta);
    b.hidden = !c;
    if (!c) return;
    b.innerHTML = c.silenciado ? SINO_CORTADO : SINO; b.setAttribute('aria-pressed', String(!!c.silenciado));
    b.title = c.silenciado ? 'Esta conversa está silenciada: clique para reativar' : 'Silenciar só esta conversa';
    b.setAttribute('aria-label', b.title);
  }
  $('.fmc-mudo-geral').addEventListener('click', () => {
    try { mudoGeral() ? localStorage.removeItem(CHAVE_MUDO) : localStorage.setItem(CHAVE_MUDO, '1'); } catch (_) {}
    atualizarMudoGeral();
    window.fmToast?.(mudoGeral() ? 'Notificações do chat silenciadas' : 'Notificações do chat reativadas', 'info');
  });
  $('.fmc-mudo-conversa').addEventListener('click', () => alternarSilencio(aberta));
  async function alternarSilencio(id) {
    const c = conversas.find(x => x.id === id); if (!c) return;
    const novo = !c.silenciado;
    const r = await fetch(`/api/chat/conversas/${c.id}/silencio`, {method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({silenciado: novo})}).catch(() => null);
    if (!r || !r.ok) { window.fmToast?.('Não foi possível alterar o silêncio da conversa', 'erro'); return; }
    c.silenciado = novo; atualizarMudoConversa(); renderTrilho(); if (!aberta) renderLista();
    window.fmToast?.(novo ? `${c.nome}: silenciada` : `${c.nome}: notificações reativadas`, 'info');
  }
  window.addEventListener('storage', e => { if (e.key === CHAVE_MUDO) atualizarMudoGeral(); });
  atualizarMudoGeral();
  // ---------- nao perturbe por horario ----------
  function atualizarNaoPerturbe() {
    const c = window.fmChatSom?.naoPerturbe?.() || {}, b = $('.fmc-nao-perturbe');
    const agora = !!window.fmChatSom?.emNaoPerturbe?.();
    b.setAttribute('aria-pressed', String(!!c.ativo)); b.classList.toggle('agora', agora);
    b.title = c.ativo ? `Não perturbe: ${c.inicio || '18:00'} às ${c.fim || '08:00'}${c.fimDeSemana ? ' e fins de semana' : ''}${agora ? ' (ativo agora)' : ''}` : 'Não perturbe por horário';
    fab.classList.toggle('nao-perturbe', agora);
  }
  $('.fmc-nao-perturbe').addEventListener('click', async () => {
    const c = window.fmChatSom?.naoPerturbe?.() || {};
    const d = document.createElement('dialog'); d.className = 'fm-dialogo fmc-dialogo';
    d.innerHTML = `<form method="dialog"><h2>🌙 Não perturbe</h2>
      <p class="fmc-dica">Nesse horário o chat não toca nem mostra prévias. Menções a você continuam avisando.</p>
      <label class="fmc-check"><input type="checkbox" name="ativo" ${c.ativo ? 'checked' : ''}> Ativar não perturbe</label>
      <div class="fmc-linha"><label class="fm-campo">Das<input type="time" name="inicio" value="${c.inicio || '18:00'}"></label>
      <label class="fm-campo">Até<input type="time" name="fim" value="${c.fim || '08:00'}"></label></div>
      <label class="fmc-check"><input type="checkbox" name="fimDeSemana" ${c.fimDeSemana ? 'checked' : ''}> Também aos sábados e domingos</label>
      <div class="fm-dialogo-acoes"><button type="button" class="secondary-button" value="cancelar">Cancelar</button><button type="submit" class="primary-button" value="ok">Salvar</button></div></form>`;
    document.body.append(d); d.querySelector('[value=cancelar]').addEventListener('click', () => d.close('cancelar')); d.showModal();
    const escolha = await new Promise(ok => d.addEventListener('close', () => ok(d.returnValue), {once: true}));
    const f = d.querySelector('form');
    const novo = {ativo: f.ativo.checked, inicio: f.inicio.value || '18:00', fim: f.fim.value || '08:00', fimDeSemana: f.fimDeSemana.checked};
    d.remove();
    if (escolha !== 'ok') return;
    try { localStorage.setItem('fm-chat-nao-perturbe', JSON.stringify(novo)); } catch (_) {}
    atualizarNaoPerturbe();
    window.fmToast?.(novo.ativo ? `Não perturbe: ${novo.inicio} às ${novo.fim}` : 'Não perturbe desligado', 'info');
  });
  window.addEventListener('storage', e => { if (e.key === 'fm-chat-nao-perturbe') atualizarNaoPerturbe(); });
  setTimeout(atualizarNaoPerturbe, 500); setInterval(atualizarNaoPerturbe, 60000);
  window.fmChatDock = {total: mostrarTotal, atualizar: carregarConversas, abrir};
  carregarConversas();
  setInterval(carregarConversas, 15000);
})();
