// Barra lateral unica do FaturaMed. Monta o menu em todas as paginas administrativas
// e permite recolher a barra para mostrar so os icones (lembrado entre paginas).
(function () {
  const key = 'faturamed-barra-recolhida';
  const root = document.documentElement;
  try { root.classList.toggle('sidebar-recolhida', localStorage.getItem(key) === '1'); } catch (_) {}
  // aparencia escolhida no menu do usuario (Classica ou Vidro), aplicada antes de desenhar a pagina
  try { const a = localStorage.getItem('faturamed-aparencia'); if (['vidro', 'aurora', 'papel', 'contraste'].includes(a)) root.dataset.aparencia = a; } catch (_) {}

  const page = location.pathname.split('/').pop() || 'index.html';
  const onIndex = page === 'index.html';
  const host = location.hostname;
  const ambiente = host === 'localhost' || host === '127.0.0.1' ? 'local' : host.includes('homologacao') ? 'de homologação' : 'de produção';
  const groups = [
    ['nav-fechamento-producao', 'Fechamento por Produção', [
      ['Lotes de produção', 'producao.html', null, '<path d="M4 4h16v16H4z"/><path d="M8 8v8M11 8v8M14 8v8M17 8v8"/>'],
      ['Fechamento e envio', 'fechamento-producao.html', null, '<path d="M4 4h16v16H4z"/><path d="M8 9h8M8 13h8M8 17h5"/>']], 'producao'],
    ['nav-producao', 'Visitas hospitalares', [
      ['Base hospitalar', 'base-hospitalar.html', null, '<path d="M4 21V5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v16"/><path d="M2 21h20M10 21v-4h4v4M12 7v4M10 9h4"/>'],
      ['Importações', null, 'importacoes', '<path d="M12 3v11m0 0 4-4m-4 4-4-4"/><path d="M5 15v4h14v-4"/>'],
      ['Conciliação de visitas', 'conciliacao.html', null, '<path d="M4 5h16M4 12h7M4 19h7M14 16l2 2 5-6"/>'],
      ['Layouts', null, 'layouts', '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 9v12"/>']], 'visitas'],
    ['nav-faturamento', 'Faturamento', [
      ['Dashboard', null, 'dashboard', '<path d="M4 13h6V4H4v9Zm10 7h6V4h-6v16ZM4 20h6v-4H4v4Z"/>'],
      ['Divergências', null, 'divergencias', '<path d="M12 3 2.8 20h18.4L12 3Z"/><path d="M12 9v5m0 3h.01"/>'],
      ['Solicitações dos médicos', 'solicitacoes-medicos.html', null, '<path d="M4 4h16v12H8l-4 4V4Z"/><path d="M8 9h8M8 12h5"/>']], 'faturamento'],
    ['nav-financeiro', 'Financeiro', [
      ['Recebimentos', 'recebimentos.html', null, '<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="12" cy="12" r="3"/><path d="M6 12h.01M18 12h.01"/>'],
      ['Fila de cobrança', 'cobrancas.html', null, '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z"/><path d="M9 8h6M9 12h6M9 16h3"/>']], 'financeiro'],
    ['nav-cadastros', 'Cadastros', [
      ['Médicos', null, 'medicos', '<path d="M6 3v5a4 4 0 0 0 8 0V3"/><path d="M10 12v3a5 5 0 0 0 10 0v-2"/><circle cx="20" cy="11" r="2"/>'],
      ['Hospitais', null, 'hospitais', '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M12 7v6M9 10h6M9 21v-4h6v4"/>'],
      ['Empresas', null, 'empresas', '<path d="M3 21h18M5 21V7l7-4 7 4v14"/><path d="M9 10h.01M15 10h.01M9 14h.01M15 14h.01M10 21v-3h4v3"/>'],
      ['Painel do médico', null, 'painel-medico', '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>']], 'cadastros'],
    ['nav-gestao', 'Gestão', [
      ['Central de chamados', 'chamados.html', null, '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>'],
      ['Acessos', 'usuarios.html', null, '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>']], 'gestao']
  ];

  function item([label, href, view, icon]) {
    const icone = `<span class="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24">${icon}</svg></span>`;
    const contador = view === 'divergencias' ? '<span class="nav-count" id="nav-divergencias-count" hidden></span>' : '';
    // No index as visoes trocam por hash (app.js); nas outras paginas o link leva ao index.
    const destino = href || (onIndex ? `#${view}` : `index.html#${view}`);
    const ativo = href ? href === page : onIndex && view === 'dashboard';
    const dataView = view && onIndex ? ` data-view-link="${view}"` : '';
    return `<a class="nav-item${ativo ? ' active' : ''}" href="${destino}"${dataView} title="${label}"${ativo ? ' aria-current="page"' : ''}>${icone}${label}${contador}</a>`;
  }

  const html = `
    <div class="sidebar-top">
      <a class="brand" href="${onIndex ? '#dashboard' : 'index.html#dashboard'}"${onIndex ? ' data-view-link="dashboard"' : ''} aria-label="FaturaMed dashboard">
        <span class="brand-mark" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M5 4.75A2.75 2.75 0 0 1 7.75 2h8.5A2.75 2.75 0 0 1 19 4.75v14.5A2.75 2.75 0 0 1 16.25 22h-8.5A2.75 2.75 0 0 1 5 19.25V4.75Z"/><path d="M9 7h6M9 11h6M9 15h2.5"/><path d="M14.25 14.25v4.5M12 16.5h4.5"/></svg></span>
        <span><strong>FaturaMed</strong><small>Faturamento e consultoria</small></span>
      </a>
      <button class="sidebar-toggle" type="button" aria-controls="sidebar-nav"><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16M15 10l-2 2 2 2"/></svg></button>
    </div>
    <nav class="nav-list" id="sidebar-nav" aria-label="Módulos">
      ${groups.map(([id, title, items, modulo]) => `
      <div class="nav-group"${modulo ? ` data-modulo="${modulo}"` : ''} role="group" aria-labelledby="${id}">
        <button class="nav-group-title" id="${id}" type="button" aria-expanded="true" aria-controls="${id}-itens">${title}<svg class="nav-chevron" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg></button>
        <div class="nav-group-items" id="${id}-itens">${items.map(item).join('')}</div>
      </div>`).join('')}
    </nav>
    <a class="company-identity" href="./assets/faturamed-identidade.jpeg" target="_blank" rel="noopener" aria-label="Identidade da FaturaMed">
      <img src="./assets/faturamed-identidade.jpeg" alt="FaturaMed: faturamento de contas medicas e consultoria medica">
    </a>
    <div class="sidebar-foot"><span class="status-dot"></span><span>Ambiente ${ambiente}</span></div>`;

  // Janelas e avisos no visual do sistema; usam o ui.js quando ja carregou, senao caem nas caixas do navegador.
  window.fmConfirmar = (texto, opcoes = {}) => window.fmUi
    ? window.fmUi.confirmar({titulo: opcoes.titulo || 'Confirmar ação', texto, confirmar: opcoes.confirmar || 'Confirmar', perigo: !!opcoes.perigo})
    : Promise.resolve(window.confirm(texto));
  window.fmAvisar = (texto, tipo = 'erro') => window.fmUi ? window.fmUi.toast(texto, tipo) : window.alert(texto);
  window.fmPerguntar = (texto, rotulo = 'Resposta') => window.fmUi
    ? window.fmUi.perguntar({titulo: texto, campos: [{nome: 'valor', rotulo, obrigatorio: true}], confirmar: 'Continuar'}).then(r => r ? r.valor : null)
    : Promise.resolve(window.prompt(texto));

  // Estilos globais (componentes e cores por modulo) e grupo da pagina atual (usado na trilha do cabecalho)
  for (const href of ['modulos.css?v=20261107-3', 'ui.css?v=20261108-3', 'vidro.css?v=20261107-2', 'temas.css?v=20261107-1', 'efeitos.css?v=20261107-1']) {
    if (document.querySelector(`link[href^="${href.split('?')[0]}"]`)) continue;
    const css = document.createElement('link');
    css.rel = 'stylesheet'; css.href = href;
    document.head.append(css);
  }
  if (!document.querySelector('script[src^="efeitos.js"]')) {
    const ef = document.createElement('script');
    ef.src = 'efeitos.js?v=20261107-1';
    document.head.append(ef);
  }
  if (!document.querySelector('script[src^="ui.js"]')) {
    const js = document.createElement('script');
    js.src = 'ui.js?v=20261108-1';
    document.head.append(js);
  }
  window.fmNavItens = groups.flatMap(([, titulo, itens]) => itens.map(([label, href, view]) => ({label, grupo: titulo, url: href || `index.html#${view}`})));
  // no index as telas trocam pelo #hash: aplica a cor/grupo do modulo da tela visivel
  if (onIndex) {
    const porHash = () => {
      const view = (location.hash || '#dashboard').slice(1);
      const g = groups.find(x => x[2].some(([, href, v]) => !href && v === view));
      window.fmNavAtual = g ? {titulo: g[1], modulo: g[3] || null} : null;
      if (g?.[3]) document.body.dataset.modulo = g[3]; else delete document.body.dataset.modulo;
    };
    window.addEventListener('hashchange', porHash);
    if (document.body) porHash(); else document.addEventListener('DOMContentLoaded', porHash);
  }
  const grupoAtual = groups.find(g => g[2].some(([, href]) => href && href === page));
  if (grupoAtual) {
    window.fmNavAtual = {titulo: grupoAtual[1], modulo: grupoAtual[3] || null};
    if (grupoAtual[3]) document.body.dataset.modulo = grupoAtual[3];
  }

  let aside = document.querySelector('aside.sidebar');
  if (!aside) {
    // Paginas sem barra: envolve o <main> existente no mesmo layout do index.
    const main = document.querySelector('body > main');
    if (!main) return;
    const shell = document.createElement('div');
    shell.className = 'app-shell';
    aside = document.createElement('aside');
    aside.className = 'sidebar';
    aside.setAttribute('aria-label', 'Navegação principal');
    main.before(shell);
    shell.append(aside, main);
    main.classList.add('page-content');
  }
  aside.innerHTML = html;

  const toggle = aside.querySelector('.sidebar-toggle');
  function label() {
    const recolhida = root.classList.contains('sidebar-recolhida');
    toggle.title = recolhida ? 'Mostrar menu' : 'Ocultar menu';
    toggle.setAttribute('aria-label', toggle.title);
    toggle.setAttribute('aria-expanded', String(!recolhida));
  }
  label();
  toggle.addEventListener('click', () => {
    const recolhida = root.classList.toggle('sidebar-recolhida');
    try { localStorage.setItem(key, recolhida ? '1' : '0'); } catch (_) {}
    label();
  });
})();
