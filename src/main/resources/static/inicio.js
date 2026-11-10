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
  function contar(raiz = document) {
    raiz.querySelectorAll('[data-contar]').forEach(el => {
      const alvo = Number(el.dataset.contar) || 0;
      if (semAnimacao || !alvo) { el.textContent = num(alvo); return; }
      const inicio = performance.now();
      const passo = t => {
        const p = Math.min(1, (t - inicio) / 900), suave = 1 - Math.pow(1 - p, 3);
        el.textContent = num(Math.round(alvo * suave));
        if (p < 1) requestAnimationFrame(passo);
      };
      requestAnimationFrame(passo);
      // garante o valor final mesmo se o navegador pausar a animacao (aba em segundo plano)
      setTimeout(() => { el.textContent = num(alvo); }, 1000);
    });
  }


  // topo: chips do que fazer primeiro, do mais urgente (vermelho) ao que esta em dia (verde)
  function chips(r) {
    const plural = (n, um, varios) => `${num(n)} ${n === 1 ? um : varios}`;
    const lista = [];
    if (r.visitasDivergentes) lista.push(['critico', plural(r.visitasDivergentes, 'divergência', 'divergências'), 'index.html#dashboard', ICONES.visitas]);
    if (r.chamadosBloqueados) lista.push(['critico', plural(r.chamadosBloqueados, 'chamado bloqueado', 'chamados bloqueados'), 'chamados.html', ICONES.chamados]);
    const pendVisitas = (r.visitasAtencao || 0) - (r.visitasDivergentes || 0);
    if (pendVisitas > 0) lista.push(['atencao', plural(pendVisitas, 'visita pendente', 'visitas pendentes'), 'index.html#dashboard', ICONES.visitas]);
    const abertos = (r.chamadosAbertos || 0) - (r.chamadosBloqueados || 0);
    if (abertos > 0) lista.push(['atencao', plural(abertos, 'chamado aberto', 'chamados abertos'), 'chamados.html', ICONES.chamados]);
    if (r.lotesPendentes) lista.push(['atencao', plural(r.lotesPendentes, 'lote a enviar', 'lotes a enviar'), 'producao.html', ICONES.lotes]);
    if (r.solicitacoesPendentes) lista.push(['atencao', plural(r.solicitacoesPendentes, 'solicitação de médico', 'solicitações de médicos'), 'solicitacoes-medicos.html', ICONES.solicitacoes]);
    const emDia = [[!r.visitasAtencao, 'Visitas em dia', ICONES.visitas], [!r.chamadosAbertos, 'Service Desk em dia', ICONES.chamados], [!r.lotesPendentes, 'Lotes em dia', ICONES.lotes], [!r.solicitacoesPendentes, 'Solicitações em dia', ICONES.solicitacoes]];
    emDia.forEach(([ok, texto, icone]) => { if (ok) lista.push(['ok', texto, null, icone]); });
    const urgentes = lista.filter(c => c[0] !== 'ok').length;
    $('#inicio-frase').textContent = urgentes ? 'O que precisa de você primeiro:' : 'Tudo em dia por aqui.';
    $('#inicio-chips').innerHTML = lista.map(([nivel, texto, href, icone], i) => {
      const corpo = `<svg viewBox="0 0 24 24" aria-hidden="true">${icone}</svg>${esc(texto)}${href ? '<span aria-hidden="true">→</span>' : ''}`;
      return href ? `<a class="ini-chip ${nivel}" href="${href}" style="--i:${i}">${corpo}</a>` : `<span class="ini-chip ${nivel}" style="--i:${i}">${corpo}</span>`;
    }).join('');
  }



  // ciclo das visitas: medidor semicircular (pagas, pendentes e divergentes em faixas) com ponteiro no % pago
  function donut(r) {
    const partes = [['Pagas', r.visitasPagas || 0, 'var(--green)'], ['Pendentes', r.visitasPendentes || 0, 'var(--amber)'], ['Divergentes', r.visitasDivergentes || 0, 'var(--rose)']];
    const total = partes.reduce((a, p) => a + p[1], 0);
    if (r.relatorioHospital) $('#inicio-ciclo-sub').textContent = `Último relatório · ${r.relatorioHospital}`;
    if (!total) { $('#inicio-donut').innerHTML = '<p class="ini-vazio">Nenhuma visita conciliada ainda.</p>'; return; }
    const ARCO = 'M20 100 A80 80 0 0 1 180 100';
    let inicio = 0;
    const faixas = partes.map(([, v, cor], i) => {
      const tam = v / total * 100, f = v ? `<path d="${ARCO}" pathLength="100" style="stroke:${cor};--i:${i}" stroke-dasharray="${Math.max(0, tam - .6).toFixed(2)} 100" stroke-dashoffset="${(-inicio).toFixed(2)}"/>` : '';
      inicio += tam; return f;
    }).join('');
    const pct = Math.round((r.visitasPagas || 0) / total * 100), div = r.visitasDivergentes || 0;
    $('#inicio-donut').innerHTML = `<svg class="ini-medidor" viewBox="0 0 200 128" role="img" aria-label="${pct}% das visitas pagas">
        <path class="trilho" d="${ARCO}"/>${faixas}
        <g class="ponteiro" style="--giro:${(pct * 1.8).toFixed(1)}deg"><line x1="100" y1="100" x2="34" y2="100"/><circle cx="100" cy="100" r="7"/></g>
        <text x="20" y="122" class="escala">0%</text><text x="180" y="122" class="escala">100%</text>
        <text x="100" y="80" class="centro${div && !pct ? ' alerta' : ''}">${pct}%</text></svg>
      <p class="ini-medidor-rotulo">das visitas pagas</p>
      <ul>${partes.map(([nome, v, cor]) => `<li><i style="background:${cor}"></i>${nome}<b data-contar="${Number(v) || 0}">0</b></li>`).join('')}</ul>
      <p class="ini-donut-rodape">${num(total)} visitas${div ? ` · ${num(div)} divergente(s) · <a href="index.html#dashboard">Revisar divergências →</a>` : ''}</p>`;
  }

  // producao: barras empilhadas por situacao do lote (rascunho, conferido, enviado) em cada dia
  const SITUACOES = [['enviado', 'Enviado', 'var(--green)'], ['conferido', 'Conferido', 'var(--blue)'], ['rascunho', 'Rascunho', 'var(--amber)']];
  function area(r) {
    const dias = r.lotesPorDia || [];
    if (!dias.length) { $('#inicio-area').innerHTML = '<p class="ini-vazio">Sem dados de produção.</p>'; return; }
    const v = dias.map(d => Number(d.total) || 0), max = Math.max(1, ...v), total = v.reduce((a, b) => a + b, 0), media = total / v.length;
    const passo = Math.ceil(dias.length / 16), unidade = unidadeProducao === 'd' ? 'dia' : unidadeProducao === 'h' ? 'hora' : 'minuto';
    const emUm = unidadeProducao === 'h' ? 'em uma hora' : `em um ${unidade}`, pico = Math.max(0, ...v);
    const somas = Object.fromEntries(SITUACOES.map(([k]) => [k, dias.reduce((a, d) => a + (Number(d[k]) || 0), 0)]));
    const dica = (d, i) => `${d.dia}: ${num(v[i])} lote(s)` + SITUACOES.map(([k, nome]) => Number(d[k]) ? ` · ${nome} ${num(d[k])}` : '').join('');
    $('#inicio-area').innerHTML = `<div class="ini-barras ini-empilhadas" role="img" aria-label="${total} lotes em ${v.length} períodos, por situação" style="--media:${(media / max).toFixed(3)}">
      <span class="ini-media" title="Média do período"><em>média ${media.toLocaleString('pt-BR', {maximumFractionDigits: 1})}</em></span>
      ${dias.map((d, i) => `<div class="ini-barra${v[i] ? '' : ' zero'}" style="--h:${(v[i] / max * 100).toFixed(1)}%;--i:${i}" title="${esc(dica(d, i))}">
        <b>${v[i] ? num(v[i]) : ''}</b><span>${SITUACOES.map(([k, , cor]) => Number(d[k]) ? `<i style="flex:${Number(d[k])};background:${cor}"></i>` : '').join('')}</span><small>${i % passo ? '' : esc(d.dia)}</small></div>`).join('')}
    </div><p class="ini-area-total">${SITUACOES.slice().reverse().map(([k, nome, cor]) => `<span class="ini-leg" style="background:${cor}"></span>${nome} <b>${num(somas[k])}</b>`).join(' · ')} · <b>${num(total)}</b> lotes no período${pico ? ` · pico de <b>${num(pico)}</b> ${emUm}` : ''} · média de <b>${media.toLocaleString('pt-BR', {maximumFractionDigits: 1})}</b> por ${unidade}</p>`;
  }

  // --- periodo editavel: numero digitado + unidade (minutos, horas, dias) ---
  const UNIDADES = [['m', 'minutos', 'minuto', 120], ['h', 'horas', 'hora', 72], ['d', 'dias', 'dia', 90]];
  let unidadeProducao = 'd';
  function rotuloPeriodo(valor) {
    const m = /^(\d+)([mhd])$/.exec(valor || ''); if (!m) return '';
    const n = Number(m[1]), [, plural, singular] = UNIDADES.find(u => u[0] === m[2]), fem = m[2] === 'h';
    return n === 1 ? `${fem ? 'última' : 'último'} ${singular}` : `${fem ? 'últimas' : 'últimos'} ${n} ${plural}`;
  }
  // "no último minuto", "na última hora", "nos últimos 15 dias", "nas últimas 3 horas"
  function noPeriodo(valor) {
    const r = rotuloPeriodo(valor);
    return `${r.startsWith('últimas') ? 'nas' : r.startsWith('últimos') ? 'nos' : r.startsWith('última') ? 'na' : 'no'} ${r}`;
  }
  function montarPeriodos() {
    document.querySelectorAll('.ini-periodo-ctl').forEach(ctl => {
      const m = /^(\d+)([mhd])$/.exec(ctl.dataset.padrao || ''), nome = esc(ctl.dataset.nome);
      ctl.innerHTML = `<input type="number" min="1" max="${m ? UNIDADES.find(u => u[0] === m[2])[3] : 90}" step="1" inputmode="numeric" value="${m ? m[1] : 15}" aria-label="Quantidade · ${nome}" title="Digite a quantidade"${m ? '' : ' hidden'}>
        <select aria-label="Unidade · ${nome}">${ctl.dataset.extra ? `<option value="">${esc(ctl.dataset.extra)}</option>` : ''}${UNIDADES.map(([u, plural]) => `<option value="${u}"${m && m[2] === u ? ' selected' : ''}>${plural}</option>`).join('')}</select>`;
      const input = ctl.querySelector('input'), sel = ctl.querySelector('select');
      let espera;
      const avisar = () => { clearTimeout(espera); espera = setTimeout(() => ctl.dispatchEvent(new CustomEvent('periodo', {bubbles: true})), 400); };
      const limitar = () => {
        const u = UNIDADES.find(x => x[0] === sel.value); if (!u) return;
        input.max = u[3];
        const n = parseInt(input.value, 10);
        if (n > u[3]) input.value = u[3];
      };
      sel.addEventListener('change', () => {
        input.hidden = !sel.value;
        if (sel.value && !parseInt(input.value, 10)) input.value = sel.value === 'd' ? 15 : sel.value === 'h' ? 1 : 30;
        limitar(); avisar();
        if (sel.value) input.focus();
      });
      input.addEventListener('input', () => { if (parseInt(input.value, 10) >= 1) { limitar(); avisar(); } });
      input.addEventListener('blur', () => { if (!(parseInt(input.value, 10) >= 1)) { input.value = 1; avisar(); } });
    });
  }
  function valorPeriodo(nome) {
    const ctl = $(`.ini-periodo-ctl[data-periodo="${nome}"]`), sel = ctl.querySelector('select');
    if (!sel.value) return '';
    return `${Math.max(1, parseInt(ctl.querySelector('input').value, 10) || 1)}${sel.value}`;
  }

  // esteira de lotes: trilha de etapas (rascunho -> conferido -> enviado) com a quantidade e o % de cada etapa
  function esteira(r, periodo) {
    const dados = r.lotesPorStatus || {};
    const etapas = [['RASCUNHO', 'Rascunho', 'var(--amber)'], ['CONFERIDO', 'Conferido', 'var(--blue)'], ['ENVIADO', 'Enviado', 'var(--green)']];
    const total = etapas.reduce((a, [k]) => a + (dados[k] || 0), 0);
    $('#inicio-esteira-sub').textContent = `Do rascunho ao envio · ${periodo ? `criados ${noPeriodo(periodo)}` : 'todo o período'}`;
    if (!total) { $('#inicio-esteira').innerHTML = '<p class="ini-vazio">Nenhum lote no período.</p>'; return; }
    $('#inicio-esteira').innerHTML = `<ol class="ini-trilha">${etapas.map(([k, nome, cor], i) => `
      <li style="--cor:${cor};--i:${i}"><span class="ini-trilha-no"><b data-contar="${Number(dados[k]) || 0}">0</b></span>
        <strong>${nome}</strong><small>${Math.round((dados[k] || 0) / total * 100)}% do total</small></li>`).join('')}</ol>
      <p class="ini-donut-rodape"><b>${num(total)}</b> lotes · <a href="producao.html">Abrir lotes →</a></p>`;
  }
  // service desk: cartoes por situacao e uma barra com o % de chamados dentro do SLA
  function servicedesk(r, periodo) {
    const dados = r.chamadosPorStatus || {}, sla = r.chamadosSla || {total: 0, noPrazo: 0};
    const situacoes = [['ABERTO', 'Abertos', 'var(--rose)'], ['EM_ATENDIMENTO', 'Em atendimento', 'var(--amber)'], ['AGUARDANDO_USUARIO', 'Aguardando', 'var(--blue)'], ['RESOLVIDO', 'Resolvidos', 'var(--green)'], ['FECHADO', 'Fechados', 'var(--muted)']];
    $('#inicio-chamados-sub').textContent = `Chamados por situação · ${periodo ? `abertos ${noPeriodo(periodo)}` : 'todo o período'}`;
    const pctSla = sla.total ? Math.round(sla.noPrazo / sla.total * 100) : null;
    const corSla = pctSla == null ? 'var(--muted)' : pctSla >= 90 ? 'var(--green)' : pctSla >= 70 ? 'var(--amber)' : 'var(--rose)';
    $('#inicio-chamados').innerHTML = `<div class="ini-status">${situacoes.map(([k, nome, cor], i) => `
      <a href="chamados.html" class="${dados[k] ? '' : 'zero'}" style="--cor:${cor};--i:${i}"><b data-contar="${Number(dados[k]) || 0}">0</b><span>${nome}</span></a>`).join('')}</div>
      <div class="ini-sla" style="--cor:${corSla}">
        <div class="ini-sla-cab"><span>Dentro do SLA</span><b>${pctSla == null ? '—' : `${pctSla}%`}</b></div>
        <div class="ini-sla-barra"><span style="width:${pctSla || 0}%"></span></div>
        <small>${sla.total ? `${num(sla.noPrazo)} de ${num(sla.total)} chamados no prazo` : 'Nenhum chamado com prazo de SLA no período'}</small>
      </div>`;
  }

  // atualiza (e anima) somente o card cujo periodo foi alterado; os outros ficam como estao
  const CARD_DO_PERIODO = {producao: 'producao', visitas: 'ciclo', lotes: 'esteira', chamados: 'servicedesk'};
  async function aplicarPeriodo(base, alvo) {
    const p = {producao: valorPeriodo('producao') || '15d', visitas: valorPeriodo('visitas'), lotes: valorPeriodo('lotes'), chamados: valorPeriodo('chamados')};
    const qs = Object.entries(p).filter(([, v]) => v).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&');
    const r = await FaturamedAuth.request(`/api/inicio/resumo?${qs}`);
    const card = $(`[data-card="${CARD_DO_PERIODO[alvo]}"]`);
    if (alvo === 'lotes') { esteira(r, p.lotes); contar(card); return; }
    if (alvo === 'chamados') { servicedesk(r, p.chamados); contar(card); return; }
    if (alvo === 'visitas') { cicloPeriodo(r, p.visitas, base); contar(card); return; }
    unidadeProducao = p.producao.slice(-1);
    const rot = rotuloPeriodo(p.producao);
    $('#inicio-area-titulo').textContent = `Produção · ${rot.charAt(0).toUpperCase() + rot.slice(1)}`;
    $('#inicio-area-titulo').nextElementSibling.textContent = unidadeProducao === 'd' ? 'Lotes por data de produção' : 'Lotes pelo momento do registro';
    area(r);
  }
  function cicloPeriodo(r, periodo, base) {
    const p = {visitas: periodo};
    if (p.visitas) {
      donut({visitasPagas: r.visitasPagasPeriodo, visitasPendentes: r.visitasPendentesPeriodo, visitasDivergentes: r.visitasDivergentesPeriodo,
        visitasAtencao: r.visitasPendentesPeriodo + r.visitasDivergentesPeriodo});
      const rv = rotuloPeriodo(p.visitas);
      $('#inicio-ciclo-sub').textContent = `${rv.charAt(0).toUpperCase() + rv.slice(1)} · ${num(r.relatoriosPeriodo)} relatório(s)`;
      if (!(r.visitasPagasPeriodo + r.visitasPendentesPeriodo + r.visitasDivergentesPeriodo))
        $('#inicio-donut').innerHTML = `<p class="ini-vazio">Nenhum relatório de conciliação ${noPeriodo(p.visitas)}.</p>`;
    } else donut(base);
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
  const GRUPOS = [['.ini-graficos', 'data-card'], ['#inicio-modulos', 'data-modulo']];
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
    modulos(perfil); montarPeriodos(); aplicarOrdens();
    try {
      const r = await FaturamedAuth.request('/api/inicio/resumo');
      donut(r); area(r); aplicarOrdens();
      $('.ini-graficos').addEventListener('periodo', e => aplicarPeriodo(r, e.target.dataset.periodo).catch(() => { $('#inicio-msg').textContent = 'Não foi possível aplicar o período agora.'; }));
      esteira(r, ''); servicedesk(r, '');
      chips(r);
      contar();
    } catch (_) { $('#inicio-msg').textContent = 'Não foi possível carregar os indicadores agora.'; }
  }
  carregar();
})();
