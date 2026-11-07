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

  // ---------- card de resumo com icone ----------
  const ICONES_CARD = {
    relogio: '<circle cx="12" cy="12" r="8"/><path d="M12 8v4l3 2"/>',
    check: '<circle cx="12" cy="12" r="8"/><path d="m8.5 12 2.5 2.5 4.5-5"/>',
    nota: '<path d="M7 3h7l4 4v14H7z"/><path d="M14 3v4h4M10 12h5M10 16h5"/>',
    rascunho: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13 7 4 4"/>',
    enviado: '<path d="M3 11 21 3l-6 18-3-8z"/><path d="m12 13 9-10"/>',
    etiqueta: '<path d="M3 12V4h8l10 10-8 8z"/><circle cx="7.5" cy="7.5" r="1.5"/>'
  };
  const card = ({icone, rotulo, valor, sub = '', zerado = false, atributos = '', classe = '', extra = '', selo = ''}) =>
    `<div class="fm-card${zerado ? ' zerado' : ''}${classe ? ' ' + classe : ''}" ${atributos}><span class="fm-card-topo"><span>${esc(rotulo)}</span>${icone ? `<svg class="fm-card-icone" data-icone="${esc(icone)}" viewBox="0 0 24 24" aria-hidden="true">${ICONES_CARD[icone] || ''}</svg>` : ''}</span>
      <strong>${esc(valor)}</strong><small>${esc(sub)}${selo}</small>${extra}</div>`;

  // ---------- linha de etapas (ex.: Enviado -> Aprovado -> NF) ----------
  const etapas = (passos, atual) => {
    const idx = passos.findIndex(p => p.chave === atual);
    return `<ol class="fm-etapas" aria-label="Situação: ${esc(passos[idx]?.rotulo || atual)}">${passos.map((p, i) =>
      `<li class="${i < idx ? 'feito' : i === idx ? 'atual' : ''}" title="${esc(p.rotulo)}"><span></span><small>${esc(p.rotulo)}</small></li>`).join('')}</ol>`;
  };

  // ---------- menu de acoes "..." em linhas de tabela ----------
  const menuAcoes = itens => `<div class="fm-acoes-menu"><button type="button" class="fm-acoes-botao" aria-haspopup="menu" aria-expanded="false" aria-label="Mais ações">
      <svg class="fm-ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="5" cy="12" r="1.3"/><circle cx="12" cy="12" r="1.3"/><circle cx="19" cy="12" r="1.3"/></svg></button>
      <div class="fm-acoes-lista" role="menu" hidden>${itens.join('')}</div></div>`;
  document.addEventListener('click', e => {
    const botao = e.target.closest('.fm-acoes-botao');
    document.querySelectorAll('.fm-acoes-lista:not([hidden])').forEach(l => {
      if (!botao || l.previousElementSibling !== botao) { l.hidden = true; l.previousElementSibling.setAttribute('aria-expanded', 'false'); }
    });
    if (botao) { const l = botao.nextElementSibling; l.hidden = !l.hidden; botao.setAttribute('aria-expanded', String(!l.hidden)); }
    else if (e.target.closest('.fm-acoes-lista [role=menuitem]')) e.target.closest('.fm-acoes-lista').hidden = true;
  });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') document.querySelectorAll('.fm-acoes-lista').forEach(l => { l.hidden = true; }); });

  const prazo = dias => dias == null ? '' : dias < 0 ? status(`${-dias} dia(s) atrasado`, 'vermelho')
    : dias === 0 ? status('vence hoje', 'ambar') : dias === 1 ? status('vence amanhã', 'ambar') : status(`${dias} dias úteis p/ conferir`, 'cinza');

  window.fmUi = {prazo, card, etapas, menuAcoes, linhaDoTempo, mostrarHistorico, icone, toast, dialogo, confirmar, perguntar: dialogo, carregando, esqueleto, vazio, status, esc};

  // ---------- cabecalho: trilha + menu do usuario ----------
  function montarCabecalho() {
    const header = document.querySelector('main :is(.account-heading, .conciliation-heading, .topbar)');
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
    // escolha de aparencia (lembrada neste navegador)
    const TEMAS = [['classica', 'Clássica'], ['vidro', 'Vidro'], ['aurora', 'Aurora'], ['papel', 'Papel'], ['contraste', 'Alto contraste']];
    const aparencia = document.createElement('div');
    aparencia.className = 'fm-aparencia';
    aparencia.setAttribute('role', 'group');
    aparencia.setAttribute('aria-label', 'Aparência');
    const pintar = () => {
      const atual = document.documentElement.dataset.aparencia || 'classica';
      const escuro = document.documentElement.dataset.theme === 'dark';
      aparencia.innerHTML = `<span class="fm-aparencia-titulo">Modo</span>
        <div class="fm-modo" role="radiogroup" aria-label="Modo claro ou escuro">
          <button type="button" role="menuitemradio" aria-checked="${!escuro}" data-modo="light" class="${escuro ? '' : 'on'}"><svg class="fm-ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></svg>Claro</button>
          <button type="button" role="menuitemradio" aria-checked="${escuro}" data-modo="dark" class="${escuro ? 'on' : ''}"><svg class="fm-ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M20.9 13A9 9 0 0 1 11 3.1 9 9 0 1 0 20.9 13Z"/></svg>Escuro</button>
        </div>
        <span class="fm-aparencia-titulo">Aparência</span><div class="fm-aparencia-lista">${TEMAS.map(([v, nome]) =>
        `<button type="button" role="menuitemradio" aria-checked="${v === atual}" data-tema="${v}" class="${v === atual ? 'on' : ''}"><i class="fm-tema-amostra t-${v}"></i>${nome}</button>`).join('')}</div>`;
    };
    aparencia.addEventListener('click', e => {
      const modo = e.target.closest('[data-modo]');
      if (modo) {
        e.stopPropagation();
        // usa o botao do tema.js (agora escondido) para manter o modo salvo e sincronizado entre abas
        if ((document.documentElement.dataset.theme || 'light') !== modo.dataset.modo) document.querySelector('[data-theme-toggle]')?.click();
        if ((document.documentElement.dataset.theme || 'light') !== modo.dataset.modo) {
          document.documentElement.dataset.theme = modo.dataset.modo;
          try { localStorage.setItem('faturamed-tema', modo.dataset.modo); } catch (_) {}
        }
        pintar();
        return;
      }
      const b = e.target.closest('[data-tema]'); if (!b) return;
      e.stopPropagation();
      if (b.dataset.tema === 'classica') delete document.documentElement.dataset.aparencia;
      else document.documentElement.dataset.aparencia = b.dataset.tema;
      try { localStorage.setItem('faturamed-aparencia', b.dataset.tema); } catch (_) {}
      pintar();
    });
    pintar();
    menu.append(aparencia);
    if (sair) { sair.className = ''; sair.textContent = 'Sair'; sair.setAttribute('role', 'menuitem'); menu.append(sair); }
    acoes.append(caixa);
    header.classList.add('fm-com-menu-usuario');
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
      if (window.fmTatil) caixa.querySelector('.fm-avatar').style.setProperty('--av', window.fmTatil.cor(eu.nome || eu.email || ''));
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
      <input type="search" placeholder="Busque ou pergunte: “quanto o Morumbi faturou em outubro?”, “lotes pendentes”…" aria-label="Buscar" autocomplete="off">
      <kbd>Esc</kbd></div><ul class="fm-busca-lista" role="listbox"></ul>
      <p class="fm-busca-dica">↑ ↓ para navegar · Enter para abrir</p>`;
    document.body.append(paleta);
    const input = paleta.querySelector('input');
    const lista = paleta.querySelector('ul');
    let resultados = [], ativo = 0, geracao = 0, timer = null;
    const desenhar = () => {
      lista.innerHTML = resultados.length ? resultados.map((r, i) => r.resposta
        ? `<li role="option" aria-selected="${i === ativo}" data-i="${i}" class="fm-busca-resposta ${i === ativo ? 'ativo' : ''}"><span class="fm-busca-texto"><small>Resposta</small><strong>${esc(r.titulo)}</strong><p>${esc(r.detalhe || '')}</p></span></li>`
        : `<li role="option" aria-selected="${i === ativo}" data-i="${i}" class="${i === ativo ? 'ativo' : ''}">
          <span class="fm-busca-tipo">${esc(r.tipo)}</span><span class="fm-busca-texto"><strong>${esc(r.titulo)}</strong><small>${esc(r.detalhe || '')}</small></span></li>`).join('')
        : `<li class="fm-busca-vazio">${input.value.trim().length < 2 ? 'Digite ao menos 2 letras ou números.' : 'Nada encontrado.'}</li>`;
      lista.querySelector('.ativo')?.scrollIntoView({block: 'nearest'});
    };
    const paginas = termo => (window.fmNavItens || []).filter(p => sem(p.label + ' ' + p.grupo).includes(sem(termo)))
      .slice(0, 5).map(p => ({tipo: 'Página', titulo: p.label, detalhe: p.grupo, url: p.url}));
    let resposta = null;
    const ehPergunta = t => t.split(/\s+/).length >= 2 && /(\?$|^(quanto|quantos|quantas|quais|qual|lotes?|regras?|fechamentos?|total|valor)\b)/i.test(t);
    const buscar = () => {
      const termo = input.value.trim();
      const minha = ++geracao;
      resposta = null;
      if (ehPergunta(termo)) {
        fetch('/api/busca/pergunta?q=' + encodeURIComponent(termo), {cache: 'no-store'}).then(r => r.status === 200 ? r.json() : null)
          .then(dados => { if (minha !== geracao || !dados) return; resposta = dados; resultados = [{tipo: 'Resposta', titulo: dados.titulo, detalhe: dados.detalhe, url: dados.url, resposta: true}, ...resultados.filter(r => !r.resposta)]; ativo = 0; desenhar(); })
          .catch(() => {});
      }
      resultados = termo ? paginas(termo) : (window.fmNavItens || []).slice(0, 6).map(p => ({tipo: 'Página', titulo: p.label, detalhe: p.grupo, url: p.url}));
      ativo = 0; desenhar();
      if (termo.length < 2) return;
      clearTimeout(timer);
      timer = setTimeout(() => fetch('/api/busca?q=' + encodeURIComponent(termo), {cache: 'no-store'}).then(r => r.ok ? r.json() : [])
        .then(dados => { if (minha !== geracao) return; resultados = [...(resposta ? [{tipo: 'Resposta', titulo: resposta.titulo, detalhe: resposta.detalhe, url: resposta.url, resposta: true}] : []), ...dados, ...paginas(termo)]; ativo = 0; desenhar(); }).catch(() => {}), 200);
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
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k' && !document.body.hasAttribute('data-sem-busca')) { e.preventDefault(); abrirBusca(); }
  });
  window.fmUi.abrirBusca = abrirBusca;

  function botaoBusca() {
    if (document.body.hasAttribute('data-sem-busca')) return;
    const header = document.querySelector('main :is(.account-heading, .conciliation-heading, .topbar) .session-actions');
    if (!header || header.querySelector('.fm-busca-botao')) return;
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'fm-busca-botao';
    b.innerHTML = '<svg class="fm-ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg><span>Buscar</span><kbd>Ctrl K</kbd>';
    b.addEventListener('click', abrirBusca);
    header.prepend(b);
  }

  // ---------- padrao automatico nas telas antigas ----------
  // 1) mensagens de status da pagina (fora de janelas) viram avisos flutuantes
  // (mensagens de orientacao fixas, como "Selecione um medico.", ficam na tela)
  const MENSAGENS = ['message', 'dashboard-message', 'h-message', 'e-message', 'queue-message', 'account-message'];
  const progresso = t => /^(Carregando|Processando|Aguarde|Lendo|Importando|Enviando|Gerando|Salvando|Atualizando|Selecione)/i.test(t);
  const pareceErro = t => /(erro|falha|inv[aá]lid|n[aã]o foi poss|indispon|negad|obrigat[oó]ri|j[aá] cadastrad|n[aã]o encontrad|conflito|alterad[oa] por outro)/i.test(t);
  function avisosAutomaticos() {
    for (const id of MENSAGENS) {
      const el = document.getElementById(id);
      if (!el || el.closest('dialog') || el.dataset.fmToast) continue;
      el.dataset.fmToast = '1';
      el.classList.toggle('fm-msg-oculta', !progresso(el.textContent.trim()));
      let ultimo = '';
      new MutationObserver(() => {
        const t = el.textContent.trim();
        const andamento = progresso(t);
        el.classList.toggle('fm-msg-oculta', !andamento); // andamento ("Carregando...") continua visivel na pagina
        if (t && t !== ultimo && !andamento) toast(t, pareceErro(t) ? 'erro' : 'sucesso');
        ultimo = t;
      }).observe(el, {childList: true, characterData: true, subtree: true});
    }
  }
  // 2) linhas "Nenhum ... encontrado" viram tela vazia com icone
  function vaziosAutomaticos(raiz = document) {
    raiz.querySelectorAll('main tbody > tr:only-child > td:only-child').forEach(td => {
      const tr = td.parentElement;
      const texto = td.textContent.trim();
      if (tr.classList.contains('fm-vazio') || tr.classList.contains('fm-esqueleto') || !/^(Nenhum|Nenhuma|Sem |Nada )/i.test(texto) || td.children.length > 1) return;
      tr.classList.add('fm-vazio');
      td.innerHTML = `<span class="fm-vazio-icone"><svg viewBox="0 0 24 24">${/encontrad/i.test(texto) ? ICONES.busca : ICONES.folha}</svg></span><strong>${esc(texto)}</strong>`;
    });
  }
  function padraoAutomatico() {
    avisosAutomaticos();
    vaziosAutomaticos();
    let agendado = false;
    new MutationObserver(() => {
      if (agendado) return;
      agendado = true;
      requestAnimationFrame(() => { agendado = false; vaziosAutomaticos(); });
    }).observe(document.querySelector('main') || document.body, {childList: true, subtree: true});
  }

  function iniciar() { montarCabecalho(); botaoBusca(); padraoAutomatico(); }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
  else iniciar();
})();
