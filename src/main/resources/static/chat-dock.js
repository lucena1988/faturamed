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
      <a class="fmc-cheio" href="chat.html" title="Abrir o FaturaChat completo">Abrir completo ↗</a>
      <button type="button" class="fmc-fechar" aria-label="Fechar (Esc)">×</button></header>
    <div class="fmc-lista-vista"><input type="search" class="fmc-busca" placeholder="Buscar conversa" aria-label="Buscar conversa"><ul class="fmc-lista"></ul></div>
    <div class="fmc-conversa-vista" hidden><ol class="fmc-msgs" aria-live="polite"></ol>
      <div class="fmc-anexos" hidden></div>
      <div class="fmc-emojis" hidden role="dialog" aria-label="Emojis"></div>
      <div class="fmc-mencoes" hidden role="listbox" aria-label="Mencionar"></div>
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
  fab.innerHTML = ICONE + '<span class="fmc-badge" hidden></span>';
  fab.addEventListener('click', () => alternarTrilho());
  document.body.append(gaveta, trilho, fab);
  const $ = s => gaveta.querySelector(s);

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
    avisos.prepend(el);
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
        ${avatar(c)}${c.naoLidas ? `<b class="fmc-ponto">${c.naoLidas > 9 ? '9+' : c.naoLidas}</b>` : ''}
        <span class="fmc-dispensar" role="button" tabindex="0" title="Tirar do balão (volta quando chegar mensagem nova)" aria-label="Tirar ${esc(c.nome)} do balão">×</span>
        <span class="fmc-previa" role="tooltip"><strong>${esc(c.nome)}</strong><small>${esc((c.tipo === 'GRUPO' && c.ultimoAutorNome ? c.ultimoAutorNome.split(/\s+/)[0] + ': ' : '') + c.ultimaMensagem)}</small><em>${c.ultimaMensagemEm ? hora(c.ultimaMensagemEm) : ''}</em></span></div>`).join('')
      + `<button type="button" class="fmc-trilho-mais" style="--i:${recentes.length}" aria-label="Todas as conversas" title="Todas as conversas">⋯</button>`;
  }
  function alternarTrilho(forcar) {
    trilhoAberto = typeof forcar === 'boolean' ? forcar : !trilhoAberto;
    renderTrilho(); posicionar();
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
    const fundo = cab ? Math.max(12, Math.min(140, cab.getBoundingClientRect().bottom + 10)) : 16;
    gaveta.style.setProperty('--fmc-topo', fundo + 'px');
    gaveta.style.setProperty('--fmc-dir', (trilhoAberto ? 84 : 16) + 'px');
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
    $('.fmc-titulo').textContent = 'FaturaChat'; $('.fmc-cheio').href = 'chat.html';
    renderLista();
  }
  function renderLista() {
    const termo = $('.fmc-busca').value.trim().toLowerCase();
    const lista = conversas.filter(c => !termo || String(c.nome).toLowerCase().includes(termo));
    $('.fmc-lista').innerHTML = lista.length ? lista.map(c => `<li><button type="button" class="fmc-item${c.naoLidas ? ' nao-lida' : ''}" data-id="${c.id}">
        ${avatar(c)}<span class="fmc-item-corpo"><strong>${esc(c.nome)}</strong><small>${esc(c.ultimaMensagem || 'Sem mensagens ainda')}</small></span>
        <span class="fmc-item-meta">${c.ultimaMensagemEm ? `<em>${hora(c.ultimaMensagemEm)}</em>` : ''}${c.naoLidas ? `<b>${c.naoLidas > 99 ? '99+' : c.naoLidas}</b>` : ''}</span></button></li>`).join('')
      : `<li class="fmc-vazio">${termo ? 'Nenhuma conversa com esse nome.' : 'Você ainda não tem conversas. Abra o FaturaChat completo para começar uma.'}</li>`;
  }
  $('.fmc-busca').addEventListener('input', renderLista);
  $('.fmc-lista').addEventListener('click', e => { const b = e.target.closest('.fmc-item'); if (b) abrirConversa(Number(b.dataset.id)); });
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
    $('.fmc-cheio').href = `chat.html#c${id}`;
    $('.fmc-msgs').innerHTML = '<li class="fmc-vazio">Carregando…</li>';
    await carregarMensagens(true);
    clearInterval(timerMsgs); timerMsgs = setInterval(() => carregarMensagens(false), 5000);
    $('.fmc-envio textarea').focus();
  }
  let ultimaId = 0;
  async function carregarMensagens(rolar) {
    const id = aberta; if (!id) return;
    try {
      if (!eu) eu = await fetch('/api/auth/me').then(r => r.json()).catch(() => ({}));
      const r = await fetch(`/api/chat/conversas/${id}/mensagens`, {headers: {Accept: 'application/json'}});
      if (!r.ok || aberta !== id) return;
      const pagina = await r.json(), msgs = pagina.mensagens || [];
      const nova = msgs.length ? msgs[msgs.length - 1].id : 0;
      if (!rolar && nova === ultimaId) return;
      const lista = $('.fmc-msgs'), noFim = lista.scrollHeight - lista.scrollTop - lista.clientHeight < 60;
      ultimaId = nova;
      lista.innerHTML = msgs.length ? msgs.map(m => {
        if (m.sistema) return `<li class="fmc-sis">${esc(m.texto)}</li>`;
        const minha = eu && m.autorId === eu.id;
        const anexos = (m.anexos || []).map(a => String(a.tipo || '').startsWith('image/') ? `<a class="fmc-img" href="/api/chat/anexos/${a.id}" target="_blank" rel="noopener" title="${esc(a.nome)}"><img alt="${esc(a.nome)}" loading="lazy" src="/api/chat/anexos/${a.id}"></a>` : `<a href="/api/chat/anexos/${a.id}" target="_blank" rel="noopener">📎 ${esc(a.nome)}</a>`).join('');
        return `<li class="fmc-msg${minha ? ' minha' : ''}">${minha ? '' : `<span class="fmc-autor">${esc(m.autorNome)}</span>`}
          <p>${m.apagada ? '<em>Mensagem apagada</em>' : esc(m.texto || '').replace(/(https?:\/\/[^\s<]+)/g, '<a href="$1" target="_blank" rel="noopener">$1</a>').replace(/(^|\s)(@[\p{L}.\-]+)/gu, '$1<span class="fmc-marca">$2</span>')}</p>${anexos}<time>${hora(m.criadoEm)}${m.editada ? ' · editada' : ''}</time></li>`;
      }).join('') : '<li class="fmc-vazio">Nenhuma mensagem ainda. Diga oi!</li>';
      if (rolar || noFim) lista.scrollTop = lista.scrollHeight;
      if (nova) fetch(`/api/chat/conversas/${id}/leitura`, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ateId: nova})})
        .then(() => { const c = conversas.find(x => x.id === id); if (c && c.naoLidas) { mostrarTotal(Math.max(0, total - c.naoLidas)); c.naoLidas = 0; renderTrilho(); } }).catch(() => {});
    } catch (_) {}
  }
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
    if (c?.tipo === 'GRUPO' && 'todos'.startsWith(termo)) candidatos.push({id: 0, nome: 'todos', todos: true});
    if (!candidatos.length) { caixa.hidden = true; return; }
    ativo = 0;
    caixa.innerHTML = candidatos.map((p, i) => `<button type="button" role="option" class="fmc-mencao${i === 0 ? ' ativo' : ''}" data-i="${i}">
      <span class="fmc-av mini ${p.todos ? 'grupo' : ''}">${p.todos ? '@' : esc(iniciais(p.nome))}${!p.todos && p.foto ? `<img alt="" src="/api/usuarios/${p.id}/foto?v=${p.foto}" onerror="this.remove()">` : ''}</span>
      <span>${p.todos ? '@todos — avisar todo o grupo' : esc(p.nome)}</span></button>`).join('');
    caixa.hidden = false;
  }
  function escolherMencao(i) {
    const p = candidatos[i], alvo = termoMencao(); if (!p || !alvo) return;
    const inserir = '@' + (p.todos ? 'todos' : nomeMencao(p.nome)) + ' ';
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
  campo.addEventListener('keydown', e => { if (teclaMencao(e)) return; if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); $('.fmc-envio').requestSubmit(); } });
  campo.addEventListener('input', () => { campo.style.height = 'auto'; campo.style.height = Math.min(120, campo.scrollHeight) + 'px'; sugerirMencao(); });
  campo.addEventListener('click', sugerirMencao);
  campo.addEventListener('blur', () => setTimeout(() => { $('.fmc-mencoes').hidden = true; }, 150));
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
    caixa.innerHTML = pendentes.map((f, i) => `<span class="fmc-anexo">${f.type.startsWith('image/') ? `<img alt="" src="${URL.createObjectURL(f)}">` : '<i>📄</i>'}
      <small title="${esc(f.name)}">${esc(f.name)}</small><button type="button" data-i="${i}" aria-label="Remover ${esc(f.name)}">×</button></span>`).join('');
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

  window.fmChatDock = {total: mostrarTotal, atualizar: carregarConversas, abrir};
  carregarConversas();
  setInterval(carregarConversas, 15000);
})();
