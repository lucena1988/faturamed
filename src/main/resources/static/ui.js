// Componentes visuais globais do FaturaMed: trilha de navegacao, menu do usuario,
// avisos flutuantes (fmToast), janelas proprias (fmConfirmar / fmPerguntar), carregamento e tela vazia.
(function () {
  if (window.fmUi) return; // ja carregado nesta pagina
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c]);

  // ---------- avisos flutuantes ----------
  function toast(texto, tipo = 'info', ms = 4500) {
    if (!texto) return;
    let pilha = document.querySelector('.fm-toasts');
    if (!pilha) { pilha = document.createElement('div'); pilha.className = 'fm-toasts'; pilha.setAttribute('aria-live', 'polite'); document.body.append(pilha); }
    const el = document.createElement('div');
    el.className = `fm-toast ${tipo}`;
    el.setAttribute('role', tipo === 'erro' ? 'alert' : 'status');
    el.innerHTML = `<span>${esc(texto)}</span><button type="button" aria-label="Fechar aviso">×</button>`;
    const fechar = () => { el.classList.add('saindo'); setTimeout(() => el.remove(), 200); };
    el.querySelector('button').addEventListener('click', fechar);
    pilha.append(el);
    if (ms) setTimeout(fechar, tipo === 'erro' ? ms * 2 : ms);
  }

  // ---------- janelas proprias ----------
  function dialogo({titulo, texto = '', campos = [], confirmar = 'Confirmar', cancelar = 'Cancelar', perigo = false}) {
    return new Promise(resolve => {
      const d = document.createElement('dialog');
      d.className = 'fm-dialogo';
      d.innerHTML = `<form method="dialog"><h2>${esc(titulo)}</h2>${texto ? `<p>${esc(texto)}</p>` : ''}
        ${campos.map(c => `<label>${esc(c.rotulo)}<input name="${esc(c.nome)}" type="${c.tipo || 'text'}" value="${esc(c.valor ?? '')}" ${c.obrigatorio ? 'required' : ''} ${c.placeholder ? `placeholder="${esc(c.placeholder)}"` : ''}></label>`).join('')}
        <div class="fm-dialogo-acoes"><button type="button" class="secondary-button" value="cancelar">${esc(cancelar)}</button>
        <button type="submit" class="primary-button${perigo ? ' perigo' : ''}" value="ok">${esc(confirmar)}</button></div></form>`;
      document.body.append(d);
      const form = d.querySelector('form');
      let resultado = null;
      d.querySelector('[value=cancelar]').addEventListener('click', () => d.close());
      form.addEventListener('submit', e => {
        if (!form.reportValidity()) { e.preventDefault(); return; }
        resultado = campos.length ? Object.fromEntries(new FormData(form)) : true;
      });
      d.addEventListener('close', () => { d.remove(); resolve(resultado); });
      d.showModal();
      (d.querySelector('input') || d.querySelector('[type=submit]')).focus();
    });
  }
  const confirmar = opcoes => dialogo(opcoes).then(r => r === true);

  // ---------- botao em carregamento ----------
  function carregando(botao, texto = 'Aguarde…') {
    if (!botao) return () => {};
    const original = botao.innerHTML;
    botao.disabled = true;
    botao.innerHTML = `<span class="fm-spinner" aria-hidden="true"></span>${esc(texto)}`;
    return () => { botao.disabled = false; botao.innerHTML = original; };
  }

  // ---------- linhas de tabela: esqueleto e tela vazia ----------
  const esqueleto = (colunas, linhas = 4) => Array.from({length: linhas}, () =>
    `<tr class="fm-esqueleto">${Array.from({length: colunas}, () => '<td><span></span></td>').join('')}</tr>`).join('');
  const ICONES = {
    folha: '<path d="M6 3h9l4 4v14H6z"/><path d="M14 3v5h5M9 13h7M9 17h5"/>',
    regra: '<path d="M4 6h16M4 12h16M4 18h10"/>',
    fechamento: '<path d="M4 4h16v16H4z"/><path d="M8 9h8M8 13h8M8 17h5"/>',
    busca: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>'
  };
  const vazio = (colunas, {titulo, texto = '', icone = 'folha', acao = ''}) =>
    `<tr class="fm-vazio"><td colspan="${colunas}"><span class="fm-vazio-icone"><svg viewBox="0 0 24 24">${ICONES[icone] || ICONES.folha}</svg></span>
      <strong>${esc(titulo)}</strong>${texto ? `<p>${esc(texto)}</p>` : ''}${acao}</td></tr>`;

  // ---------- etiqueta de status ----------
  const ICONES_ACAO = {
    camera: '<svg class="fm-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 8h3l2-3h6l2 3h3v11H4z"/><circle cx="12" cy="13" r="3.5"/></svg>',
    ok: '<svg class="fm-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 5 5 9-10"/></svg>',
    fechar: '<svg class="fm-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg>'
  };
  const icone = nome => ICONES_ACAO[nome] || '';
  const status = (texto, cor) => `<span class="fm-status ${cor}">${esc(texto)}</span>`;

  // ---------- historico (linha do tempo) ----------
  const ACOES = {CRIADA: 'Criada', ALTERADA: 'Alterada', CONFERIDA: 'Conferida', REABERTA: 'Reaberta', FOTO: 'Foto', ENVIADA: 'Enviada',
    RETIRADA: 'Retirada', GERADO: 'Gerado', APROVADO: 'Aprovado', NF_EMITIDA: 'NF', CANCELADO: 'Cancelado'};
  const quando = q => new Date(q).toLocaleString('pt-BR', {day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'});
  const linhaDoTempo = eventos => eventos.length
    ? `<ol class="fm-timeline">${eventos.map(e => `<li class="acao-${esc(e.acao)}"><span class="fm-timeline-marca"></span>
        <div><strong>${esc(e.descricao || ACOES[e.acao] || e.acao)}</strong><small>${esc(e.usuario || 'sistema')} · ${quando(e.quando)}</small></div></li>`).join('')}</ol>`
    : '<p class="prod-dica">Nenhum registro ainda.</p>';
  async function mostrarHistorico(titulo, url) {
    const r = await fetch(url, {cache: 'no-store'});
    const eventos = r.ok ? await r.json() : [];
    const d = document.createElement('dialog');
    d.className = 'fm-dialogo fm-dialogo-largo';
    d.innerHTML = `<form method="dialog"><h2>${esc(titulo)}</h2>${linhaDoTempo(eventos)}<div class="fm-dialogo-acoes"><button class="secondary-button">Fechar</button></div></form>`;
    document.body.append(d);
    d.addEventListener('close', () => d.remove());
    d.showModal();
  }

  window.fmUi = {linhaDoTempo, mostrarHistorico, icone, toast, dialogo, confirmar, perguntar: dialogo, carregando, esqueleto, vazio, status, esc};

  // ---------- cabecalho: trilha + menu do usuario ----------
  function montarCabecalho() {
    const header = document.querySelector('main :is(.account-heading, .conciliation-heading)');
    if (!header) return;
    const marca = header.querySelector('.company-name');
    const grupo = window.fmNavAtual;
    if (marca) {
      const trilha = document.createElement('nav');
      trilha.className = 'fm-trilha';
      trilha.setAttribute('aria-label', 'Você está em');
      trilha.innerHTML = `<a href="/">Início</a>${grupo ? `<span class="fm-trilha-sep">›</span><span class="fm-trilha-grupo">${esc(grupo.titulo)}</span>` : ''}`;
      marca.replaceWith(trilha);
    }
    const acoes = header.querySelector('.session-actions');
    const conta = acoes?.querySelector('a[href="conta.html"]');
    const sair = acoes?.querySelector('[data-logout]');
    if (!acoes || (!conta && !sair)) return;
    acoes.querySelectorAll(':scope > a[href="/"]').forEach(a => a.remove()); // "Dashboard": a trilha e o menu ja levam ao inicio
    const caixa = document.createElement('div');
    caixa.className = 'fm-usuario';
    caixa.innerHTML = `<button type="button" class="fm-usuario-botao" aria-haspopup="menu" aria-expanded="false"><span class="fm-avatar">··</span><span class="fm-usuario-nome">Minha conta</span><span aria-hidden="true">▾</span></button>
      <div class="fm-usuario-menu" role="menu" hidden><div class="fm-usuario-info"><strong>…</strong><small></small></div></div>`;
    const menu = caixa.querySelector('.fm-usuario-menu');
    if (conta) { conta.textContent = 'Minha conta'; conta.setAttribute('role', 'menuitem'); menu.append(conta); }
    if (sair) { sair.className = ''; sair.textContent = 'Sair'; sair.setAttribute('role', 'menuitem'); menu.append(sair); }
    acoes.append(caixa);
    const botao = caixa.querySelector('.fm-usuario-botao');
    const abrir = aberto => { menu.hidden = !aberto; botao.setAttribute('aria-expanded', String(aberto)); };
    botao.addEventListener('click', e => { e.stopPropagation(); abrir(menu.hidden); });
    document.addEventListener('click', e => { if (!caixa.contains(e.target)) abrir(false); });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') abrir(false); });
    fetch('/api/auth/me', {cache: 'no-store'}).then(r => r.ok ? r.json() : null).then(eu => {
      if (!eu) return;
      const partes = String(eu.nome || eu.email || '?').trim().split(/\s+/);
      const iniciais = ((partes[0] || '')[0] || '') + (partes.length > 1 ? partes.at(-1)[0] : '');
      caixa.querySelector('.fm-avatar').textContent = iniciais.toUpperCase();
      caixa.querySelector('.fm-usuario-nome').textContent = partes[0];
      caixa.querySelector('.fm-usuario-info strong').textContent = eu.nome || '';
      caixa.querySelector('.fm-usuario-info small').textContent = eu.email || '';
    }).catch(() => {});
  }
  // ---------- busca global (Ctrl+K) ----------
  const sem = s => String(s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  let paleta = null;
  function abrirBusca() {
    if (paleta) { paleta.querySelector('input').focus(); return; }
    paleta = document.createElement('dialog');
    paleta.className = 'fm-busca';
    paleta.innerHTML = `<div class="fm-busca-campo"><svg class="fm-ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
      <input type="search" placeholder="Buscar atendimento, paciente, médico, hospital, fechamento ou página…" aria-label="Buscar" autocomplete="off">
      <kbd>Esc</kbd></div><ul class="fm-busca-lista" role="listbox"></ul>
      <p class="fm-busca-dica">↑ ↓ para navegar · Enter para abrir</p>`;
    document.body.append(paleta);
    const input = paleta.querySelector('input');
    const lista = paleta.querySelector('ul');
    let resultados = [], ativo = 0, geracao = 0, timer = null;
    const desenhar = () => {
      lista.innerHTML = resultados.length ? resultados.map((r, i) => `<li role="option" aria-selected="${i === ativo}" data-i="${i}" class="${i === ativo ? 'ativo' : ''}">
          <span class="fm-busca-tipo">${esc(r.tipo)}</span><span class="fm-busca-texto"><strong>${esc(r.titulo)}</strong><small>${esc(r.detalhe || '')}</small></span></li>`).join('')
        : `<li class="fm-busca-vazio">${input.value.trim().length < 2 ? 'Digite ao menos 2 letras ou números.' : 'Nada encontrado.'}</li>`;
      lista.querySelector('.ativo')?.scrollIntoView({block: 'nearest'});
    };
    const paginas = termo => (window.fmNavItens || []).filter(p => sem(p.label + ' ' + p.grupo).includes(sem(termo)))
      .slice(0, 5).map(p => ({tipo: 'Página', titulo: p.label, detalhe: p.grupo, url: p.url}));
    const buscar = () => {
      const termo = input.value.trim();
      const minha = ++geracao;
      resultados = termo ? paginas(termo) : (window.fmNavItens || []).slice(0, 6).map(p => ({tipo: 'Página', titulo: p.label, detalhe: p.grupo, url: p.url}));
      ativo = 0; desenhar();
      if (termo.length < 2) return;
      clearTimeout(timer);
      timer = setTimeout(() => fetch('/api/busca?q=' + encodeURIComponent(termo), {cache: 'no-store'}).then(r => r.ok ? r.json() : [])
        .then(dados => { if (minha !== geracao) return; resultados = [...dados, ...paginas(termo)]; ativo = 0; desenhar(); }).catch(() => {}), 200);
    };
    const ir = i => { const r = resultados[i]; if (r) { paleta.close(); location.href = r.url.startsWith('/') ? r.url : '/' + r.url; } };
    input.addEventListener('input', buscar);
    input.addEventListener('keydown', e => {
      if (e.key === 'ArrowDown') { e.preventDefault(); ativo = Math.min(ativo + 1, resultados.length - 1); desenhar(); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); ativo = Math.max(ativo - 1, 0); desenhar(); }
      else if (e.key === 'Enter') { e.preventDefault(); ir(ativo); }
    });
    lista.addEventListener('click', e => { const li = e.target.closest('[data-i]'); if (li) ir(+li.dataset.i); });
    paleta.addEventListener('click', e => { if (e.target === paleta) paleta.close(); });
    paleta.addEventListener('close', () => { paleta.remove(); paleta = null; });
    paleta.showModal();
    buscar();
    input.focus();
  }
  document.addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); abrirBusca(); }
  });
  window.fmUi.abrirBusca = abrirBusca;

  function botaoBusca() {
    const header = document.querySelector('main :is(.account-heading, .conciliation-heading) .session-actions, main .topbar');
    if (!header || header.querySelector('.fm-busca-botao')) return;
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'fm-busca-botao';
    b.innerHTML = '<svg class="fm-ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg><span>Buscar</span><kbd>Ctrl K</kbd>';
    b.addEventListener('click', abrirBusca);
    header.prepend(b);
  }

  function iniciar() { montarCabecalho(); botaoBusca(); }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
  else iniciar();
})();
