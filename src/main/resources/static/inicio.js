// Tela de inicio da equipe interna: central de comando com indicadores, graficos (SVG proprio) e atalhos dos modulos.
(function () {
  const PERFIS = {ADMIN: 'Administrador', DEV: 'Desenvolvedor', OPERADOR: 'Operador'};
  const MODULOS = [
    ['producao', 'Fechamento por Produção', 'Lotes, conferência e envio da produção.'],
    ['visitas', 'Visitas hospitalares', 'Base hospitalar, importações e conciliação.'],
    ['financeiro', 'Financeiro', 'Recebimentos e fila de cobrança.'],
    ['cadastros', 'Cadastros', 'Médicos, hospitais e empresas.'],
    ['gestao', 'Gestão', 'Central de chamados e acessos.']
  ];
  // modulos visiveis por perfil (null = todos; hoje a equipe interna enxerga todos)
  const PERMITIDOS = {ADMIN: null, DEV: null, OPERADOR: null};
  const ICONES = {
    visitas: '<path d="M12 3 2.8 20h18.4L12 3Z"/><path d="M12 9v5m0 3h.01"/>',
    chamados: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    lotes: '<path d="M4 4h16v16H4z"/><path d="M8 8v8M11 8v8M14 8v8M17 8v8"/>',
    solicitacoes: '<path d="M4 4h16v12H8l-4 4V4Z"/><path d="M8 9h8M8 12h5"/>'
  };
  const ESC = {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'};
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ESC[c]);
  const $ = s => document.querySelector(s);
  const num = n => Number(n || 0).toLocaleString('pt-BR');
  const semAnimacao = matchMedia('(prefers-reduced-motion: reduce)').matches;

  const agora = new Date();
  const h = agora.getHours();
  const periodo = h < 12 ? 'Bom dia' : h < 18 ? 'Boa tarde' : 'Boa noite';
  const dataTxt = agora.toLocaleDateString('pt-BR', {weekday: 'long', day: 'numeric', month: 'long'});
  $('#inicio-data').textContent = dataTxt.charAt(0).toUpperCase() + dataTxt.slice(1);
  const relogio = () => { $('#inicio-relogio').textContent = new Date().toLocaleTimeString('pt-BR', {hour: '2-digit', minute: '2-digit'}); };
  relogio(); setInterval(relogio, 15000);

  // numeros sobem de 0 ate o valor
  function contar() {
    document.querySelectorAll('[data-contar]').forEach(el => {
      const alvo = Number(el.dataset.contar) || 0;
      if (semAnimacao || !alvo) { el.textContent = num(alvo); return; }
      const inicio = performance.now();
      const passo = t => {
        const p = Math.min(1, (t - inicio) / 900), suave = 1 - Math.pow(1 - p, 3);
        el.textContent = num(Math.round(alvo * suave));
        if (p < 1) requestAnimationFrame(passo);
      };
      requestAnimationFrame(passo);
    });
  }

  function kpis(r) {
    const total = (r.visitasAtencao || 0) + (r.visitasPagas || 0);
    const lotes = Object.values(r.lotesPorStatus || {}).reduce((a, b) => a + b, 0);
    const cards = [
      ['visitas', 'Visitas que pedem atenção', r.visitasAtencao, 'index.html#dashboard', r.visitasDivergentes ? `${num(r.visitasDivergentes)} divergente(s)` : 'no último relatório', total ? r.visitasAtencao / total : 0, false],
      ['chamados', 'Chamados abertos', r.chamadosAbertos, 'chamados.html', r.chamadosBloqueados ? `${num(r.chamadosBloqueados)} bloqueado(s)` : 'no Service Desk', r.chamadosAbertos ? (r.chamadosBloqueados || 0) / r.chamadosAbertos : 0, r.chamadosBloqueados > 0],
      ['lotes', 'Lotes a enviar', r.lotesPendentes, 'producao.html', `de ${num(lotes)} lotes`, lotes ? r.lotesPendentes / lotes : 0, false],
      ['solicitacoes', 'Solicitações dos médicos', r.solicitacoesPendentes, 'solicitacoes-medicos.html', 'aguardando resposta', r.solicitacoesPendentes ? 1 : 0, false]
    ];
    $('#inicio-kpis').innerHTML = cards.map(([tipo, titulo, n, href, detalhe, fracao, alerta], i) => {

      return `<a class="ini-kpi${alerta ? ' alerta' : ''}" data-tipo="${tipo}" href="${href}" style="--i:${i}">
        <span class="ini-kpi-icone" aria-hidden="true"><svg viewBox="0 0 24 24">${ICONES[tipo]}</svg></span>
        <span class="ini-kpi-corpo"><span class="ini-kpi-titulo">${esc(titulo)}</span><strong data-contar="${Number(n || 0)}">0</strong><small>${esc(detalhe)}</small></span>
        ${tipo === 'lotes' ? sparkline(r.lotesPorDia || []) : selo(tipo, n, alerta || (tipo === 'visitas' && r.visitasDivergentes > 0))}
      </a>`;
    }).join('');
  }

  function selo(tipo, n, critico) {
    if (!Number(n)) return '<span class="ini-selo ok" title="Nada pendente">Em dia</span>';
    return `<span class="ini-selo ${critico ? 'critico' : 'pendente'}">${critico ? 'Ação necessária' : 'Pendente'}</span>`;
  }

  function sparkline(dias) {
    const v = dias.slice(-7).map(d => Number(d.total) || 0);
    if (v.length < 2) return '';
    const max = Math.max(1, ...v), w = 64, h = 28, x = i => (i / (v.length - 1) * w).toFixed(1), y = n => (h - 3 - n / max * (h - 6)).toFixed(1);
    const d = v.map((n, i) => `${i ? 'L' : 'M'}${x(i)},${y(n)}`).join('');
    return `<span class="ini-spark" title="Lotes por dia nos últimos 7 dias"><svg viewBox="0 0 ${w} ${h}" aria-hidden="true"><path d="${d}"/><circle cx="${x(v.length - 1)}" cy="${y(v[v.length - 1])}" r="2.6"/></svg><small>7 dias</small></span>`;
  }

  function donut(r) {
    const partes = [['Pagas', r.visitasPagas || 0, 'var(--green)'], ['Pendentes', r.visitasPendentes || 0, 'var(--amber)'], ['Divergentes', r.visitasDivergentes || 0, 'var(--rose)']];
    const total = partes.reduce((a, p) => a + p[1], 0);
    if (r.relatorioHospital) $('#inicio-ciclo-sub').textContent = `Último relatório · ${r.relatorioHospital}`;
    if (!total) { $('#inicio-donut').innerHTML = '<p class="ini-vazio">Nenhuma visita conciliada ainda.</p>'; return; }
    const c = 2 * Math.PI * 54; let acumulado = 0;
    const arcos = partes.map(([, v, cor]) => {
      const tam = v / total * c, arco = `<circle cx="70" cy="70" r="54" style="stroke:${cor}" stroke-dasharray="${tam.toFixed(2)} ${(c - tam).toFixed(2)}" stroke-dashoffset="${(-acumulado).toFixed(2)}"/>`;
      acumulado += tam; return v ? arco : '';
    }).join('');
    const pct = Math.round((r.visitasPagas || 0) / total * 100), div = r.visitasDivergentes || 0;
    const [valor, rotulo] = div ? [num(div), div === 1 ? 'divergente' : 'divergentes'] : [`${pct}%`, 'pagas'];
    $('#inicio-donut').innerHTML = `<svg viewBox="0 0 140 140" role="img" aria-label="${valor} ${rotulo}"><circle class="trilho" cx="70" cy="70" r="54"/><g transform="rotate(-90 70 70)">${arcos}</g>
      <text x="70" y="70" class="centro${div ? ' alerta' : ''}">${valor}</text><text x="70" y="88" class="legenda">${rotulo}</text></svg>
      <ul>${partes.map(([nome, v, cor]) => `<li><i style="background:${cor}"></i>${nome}<b>${num(v)}</b></li>`).join('')}</ul>
      <p class="ini-donut-rodape">${pct}% pagas de ${num(total)} visitas${div ? ' · <a href="index.html#dashboard">Revisar divergências →</a>' : ''}</p>`;
  }

  function area(r) {
    const dias = r.lotesPorDia || [];
    if (!dias.length) { $('#inicio-area').innerHTML = '<p class="ini-vazio">Sem dados de produção.</p>'; return; }
    const v = dias.map(d => Number(d.total) || 0), max = Math.max(1, ...v), total = v.reduce((a, b) => a + b, 0), media = total / v.length;
    $('#inicio-area').innerHTML = `<div class="ini-barras" role="img" aria-label="${total} lotes em ${v.length} dias" style="--media:${(media / max).toFixed(3)}">
      <span class="ini-media" title="Média do período"><em>média ${media.toLocaleString('pt-BR', {maximumFractionDigits: 1})}</em></span>
      ${dias.map((d, i) => `<div class="ini-barra${v[i] === max && v[i] ? ' pico' : ''}${v[i] ? '' : ' zero'}" style="--h:${(v[i] / max * 100).toFixed(1)}%;--i:${i}" title="${esc(d.dia)}: ${num(v[i])} lote(s)">
        <b>${v[i] ? num(v[i]) : ''}</b><span></span><small>${esc(d.dia)}</small></div>`).join('')}
    </div><p class="ini-area-total"><b>${num(total)}</b> lotes no período · pico de <b>${num(max)}</b> em um dia · média de <b>${media.toLocaleString('pt-BR', {maximumFractionDigits: 1})}</b> por dia</p>`;
  }

  async function aplicarPeriodo(base) {
    const dp = $('#inicio-dias-producao').value, dv = $('#inicio-dias-visitas').value;
    const r = await FaturamedAuth.request(`/api/inicio/resumo?diasProducao=${dp}${dv ? `&diasVisitas=${dv}` : ''}`);
    $('#inicio-area-titulo').textContent = `Produção nos últimos ${dp} dias`;
    area(r);
    if (dv) {
      donut({visitasPagas: r.visitasPagasPeriodo, visitasPendentes: r.visitasPendentesPeriodo, visitasDivergentes: r.visitasDivergentesPeriodo,
        visitasAtencao: r.visitasPendentesPeriodo + r.visitasDivergentesPeriodo});
      $('#inicio-ciclo-sub').textContent = `Últimos ${dv} dias · ${num(r.relatoriosPeriodo)} relatório(s)`;
    } else donut(base);
    if (dv && !(r.visitasPagasPeriodo + r.visitasPendentesPeriodo + r.visitasDivergentesPeriodo))
      $('#inicio-donut').innerHTML = `<p class="ini-vazio">Nenhum relatório de conciliação nos últimos ${dv} dias.</p>`;
  }

  function barras(alvo, dados, etapas) {
    const total = etapas.reduce((a, [k]) => a + (dados[k] || 0), 0);
    if (!total) { $(alvo).innerHTML = '<p class="ini-vazio">Nada por aqui.</p>'; return; }
    $(alvo).innerHTML = `<div class="ini-esteira">${etapas.map(([k, , cor]) => dados[k] ? `<span style="flex:${dados[k]};background:${cor}"></span>` : '').join('')}</div>
      <ul class="ini-esteira-legenda">${etapas.map(([k, nome, cor]) => `<li><i style="background:${cor}"></i>${nome}<b>${num(dados[k])}</b></li>`).join('')}</ul>`;
  }

  function modulos(perfil) {
    const itens = window.fmNavItens || [];
    const permitidos = PERMITIDOS[perfil];
    $('#inicio-modulos').innerHTML = MODULOS.filter(([m]) => !permitidos || permitidos.includes(m)).map(([m, titulo, desc], i) => {
      const doGrupo = itens.filter(it => it.grupo === titulo);
      const links = doGrupo.map(it => `<li><a href="${esc(it.url)}">${esc(it.label)}</a></li>`).join('');
      return `<article class="ini-modulo" data-modulo="${m}" style="--i:${i}">
        <span class="ini-modulo-icone" aria-hidden="true"><svg viewBox="0 0 24 24">${doGrupo[0]?.icone || ''}</svg></span>
        <h3><a href="${esc(doGrupo[0] ? doGrupo[0].url : '#')}">${esc(titulo)}</a></h3><p>${esc(desc)}</p><ul>${links}</ul></article>`;
    }).join('');
  }

  // ordem dos cards escolhida pelo usuário (arrastar e soltar), guardada neste navegador
  const GRUPOS = [['#inicio-kpis', 'data-tipo'], ['.ini-graficos', 'data-card'], ['#inicio-modulos', 'data-modulo']];
  let usuarioOrdem = 'anonimo';
  const chaveOrdem = sel => `fm-inicio-ordem:${usuarioOrdem}:${sel}`;
  function lerOrdem(sel) { try { return JSON.parse(localStorage.getItem(chaveOrdem(sel)) || 'null'); } catch (_) { return null; } }
  function gravarOrdem(sel, ordem) { try { ordem ? localStorage.setItem(chaveOrdem(sel), JSON.stringify(ordem)) : localStorage.removeItem(chaveOrdem(sel)); } catch (_) {} }
  function atualizarRestaurar() { $('#inicio-restaurar').hidden = !GRUPOS.some(([sel]) => lerOrdem(sel)); }

  function ordenavel(sel, attr) {
    const box = $(sel); if (!box) return;
    const itens = () => [...box.children].filter(el => el.hasAttribute(attr));
    if (!itens().length) return;
    if (!box.dataset.padrao) box.dataset.padrao = JSON.stringify(itens().map(el => el.getAttribute(attr)));
    const salva = lerOrdem(sel);
    if (salva) salva.forEach(id => { const el = itens().find(e => e.getAttribute(attr) === id); if (el) box.append(el); });
    itens().forEach(el => { el.draggable = true; el.classList.add('ini-arrastavel'); });
    if (box.dataset.ordenavel) return;
    box.dataset.ordenavel = '1';
    let arrastando = null, moveu = false;
    box.addEventListener('dragstart', e => {
      arrastando = e.target.closest(`[${attr}]`); if (!arrastando || arrastando.parentElement !== box) return;
      if (e.target.closest('select, input, button')) { e.preventDefault(); return; }
      moveu = false; e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', arrastando.getAttribute(attr));
      requestAnimationFrame(() => arrastando && arrastando.classList.add('arrastando')); box.classList.add('ordenando');
    });
    box.addEventListener('dragover', e => {
      if (!arrastando) return; e.preventDefault();
      const alvo = e.target.closest(`[${attr}]`);
      if (!alvo || alvo === arrastando || alvo.parentElement !== box) return;
      const r = alvo.getBoundingClientRect(), depois = (e.clientY - r.top) / r.height > .5 || (e.clientX - r.left) / r.width > .5;
      box.insertBefore(arrastando, depois ? alvo.nextSibling : alvo); moveu = true;
    });
    box.addEventListener('drop', e => e.preventDefault());
    box.addEventListener('dragend', () => {
      if (!arrastando) return;
      arrastando.classList.remove('arrastando'); box.classList.remove('ordenando'); arrastando = null;
      const ordem = itens().map(el => el.getAttribute(attr));
      gravarOrdem(sel, JSON.stringify(ordem) === box.dataset.padrao ? null : ordem); atualizarRestaurar();
    });
    box.addEventListener('click', e => { if (moveu) { e.preventDefault(); moveu = false; } }, true);
  }
  function aplicarOrdens() { GRUPOS.forEach(([sel, attr]) => ordenavel(sel, attr)); atualizarRestaurar(); }
  $('#inicio-restaurar').addEventListener('click', () => {
    GRUPOS.forEach(([sel, attr]) => {
      gravarOrdem(sel, null); const box = $(sel); if (!box || !box.dataset.padrao) return;
      JSON.parse(box.dataset.padrao).forEach(id => { const el = box.querySelector(`:scope > [${attr}="${id}"]`); if (el) box.append(el); });
    });
    atualizarRestaurar();
  });

  async function carregar() {
    let perfil = null;
    try {
      const eu = await FaturamedAuth.request('/api/auth/me');
      perfil = eu.perfil; usuarioOrdem = String(eu.id || eu.email || eu.login || eu.nome || 'anonimo');
      const nome = (eu.nome || '').trim().split(/\s+/)[0];
      $('#inicio-saudacao').textContent = nome ? `${periodo}, ${nome}` : periodo;
      $('#inicio-perfil').textContent = PERFIS[perfil] || perfil || '';
    } catch (_) { $('#inicio-saudacao').textContent = periodo; }
    modulos(perfil); aplicarOrdens();
    try {
      const r = await FaturamedAuth.request('/api/inicio/resumo');
      kpis(r); donut(r); area(r); aplicarOrdens();
      ['#inicio-dias-producao', '#inicio-dias-visitas'].forEach(s => $(s).addEventListener('change', () => aplicarPeriodo(r).catch(() => {})));
      barras('#inicio-esteira', r.lotesPorStatus || {}, [['RASCUNHO', 'Rascunho', 'var(--amber)'], ['CONFERIDO', 'Conferido', 'var(--blue)'], ['ENVIADO', 'Enviado', 'var(--green)']]);
      barras('#inicio-chamados', r.chamadosPorStatus || {}, [['ABERTO', 'Abertos', 'var(--rose)'], ['EM_ATENDIMENTO', 'Em atendimento', 'var(--amber)'], ['AGUARDANDO_USUARIO', 'Aguardando', 'var(--blue)'], ['RESOLVIDO', 'Resolvidos', 'var(--green)'], ['FECHADO', 'Fechados', 'var(--muted)']]);
      const pend = (r.visitasAtencao || 0) + (r.chamadosAbertos || 0) + (r.lotesPendentes || 0) + (r.solicitacoesPendentes || 0);
      $('#inicio-frase').textContent = pend ? `${num(pend)} itens aguardam sua atenção hoje.` : 'Tudo em dia por aqui.';
      contar();
    } catch (_) { $('#inicio-msg').textContent = 'Não foi possível carregar os indicadores agora.'; }
  }
  carregar();
})();
