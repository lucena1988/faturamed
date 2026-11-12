// Tela de inicio da equipe interna: central de comando com indicadores, graficos (SVG proprio) e atalhos dos modulos.
(function () {
  const PERFIS = {ADMIN: 'Administrador', DEV: 'Desenvolvedor', OPERADOR: 'Operador'};
  const MODULOS = [
    ['producao', 'Fechamento por Produção', 'Lotes, conferência e envio da produção.'],
    ['visitas', 'Fechamento Faturado', 'Base hospitalar, importações e conciliação de procedimentos.'],
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
    if (pendVisitas > 0) lista.push(['atencao', plural(pendVisitas, 'procedimento pendente', 'procedimentos pendentes'), 'index.html#dashboard', ICONES.visitas]);
    const abertos = (r.chamadosAbertos || 0) - (r.chamadosBloqueados || 0);
    if (abertos > 0) lista.push(['atencao', plural(abertos, 'chamado aberto', 'chamados abertos'), 'chamados.html', ICONES.chamados]);
    if (r.lotesPendentes) lista.push(['atencao', plural(r.lotesPendentes, 'lote a enviar', 'lotes a enviar'), 'producao.html', ICONES.lotes]);
    if (r.solicitacoesPendentes) lista.push(['atencao', plural(r.solicitacoesPendentes, 'solicitação de médico', 'solicitações de médicos'), 'solicitacoes-medicos.html', ICONES.solicitacoes]);
    const emDia = [[!r.visitasAtencao, 'Faturado em dia', ICONES.visitas], [!r.chamadosAbertos, 'Service Desk em dia', ICONES.chamados], [!r.lotesPendentes, 'Lotes em dia', ICONES.lotes], [!r.solicitacoesPendentes, 'Solicitações em dia', ICONES.solicitacoes]];
    emDia.forEach(([ok, texto, icone]) => { if (ok) lista.push(['ok', texto, null, icone]); });
    const urgentes = lista.filter(c => c[0] !== 'ok').length;
    $('#inicio-frase').textContent = urgentes ? 'O que precisa de você primeiro:' : 'Tudo em dia por aqui.';
    $('#inicio-chips').innerHTML = lista.map(([nivel, texto, href, icone], i) => {
      const corpo = `<svg viewBox="0 0 24 24" aria-hidden="true">${icone}</svg>${esc(texto)}${href ? '<span aria-hidden="true">→</span>' : ''}`;
      return href ? `<a class="ini-chip ${nivel}" href="${href}" style="--i:${i}">${corpo}</a>` : `<span class="ini-chip ${nivel}" style="--i:${i}">${corpo}</span>`;
    }).join('');
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
  const CARD_DO_PERIODO = {producao: 'producao', faturado: 'faturado', lotes: 'esteira', chamados: 'servicedesk'};
  async function aplicarPeriodo(base, alvo) {
    if (alvo === 'faturado') { await faturado(); return; }
    const p = {producao: valorPeriodo('producao') || '15d', lotes: valorPeriodo('lotes'), chamados: valorPeriodo('chamados')};
    const qs = Object.entries(p).filter(([, v]) => v).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&');
    const r = await FaturamedAuth.request(`/api/inicio/resumo?${qs}`);
    const card = $(`[data-card="${CARD_DO_PERIODO[alvo]}"]`);
    if (alvo === 'lotes') { esteira(r, p.lotes); contar(card); return; }
    if (alvo === 'chamados') { servicedesk(r, p.chamados); contar(card); return; }
    unidadeProducao = p.producao.slice(-1);
    const rot = rotuloPeriodo(p.producao);
    $('#inicio-area-titulo').textContent = `Produção · ${rot.charAt(0).toUpperCase() + rot.slice(1)}`;
    $('#inicio-area-titulo').nextElementSibling.textContent = unidadeProducao === 'd' ? 'Lotes por data de produção' : 'Lotes pelo momento do registro';
    area(r);
  }
  // --- Fechamento Faturado: procedimentos do hospital por periodo, com filtro de hospital e exame ---
  const SIT_FF = [['conciliado', 'Conciliado', 'var(--green)'], ['aguardando', 'Aguardando produção', 'var(--amber)'], ['divergente', 'Divergente', 'var(--rose)']];
  const TIPOS_FF = {VISITA: 'Visitas', EXAME: 'Exames', CONSULTA: 'Consultas', OUTRO: 'Outros'};
  let geracaoFF = 0;
  async function opcoesExame() {
    const sel = $('#inicio-ff-exame'), atual = sel.value, hospital = $('#inicio-ff-hospital').value;
    const procs = await FaturamedAuth.request(`/api/fechamento-faturado/repasses/procedimentos${hospital ? `?hospitalId=${hospital}` : ''}`).catch(() => []);
    const porTipo = {};
    procs.forEach(p => (porTipo[p.tipo || 'OUTRO'] ||= []).push(p));
    sel.innerHTML = '<option value="">Todos</option>'
      + `<optgroup label="Por tipo">${Object.entries(TIPOS_FF).map(([k, n]) => `<option value="tipo:${k}">Todos: ${n.toLowerCase()}</option>`).join('')}</optgroup>`
      + Object.entries(TIPOS_FF).filter(([k]) => porTipo[k]).map(([k, n]) => `<optgroup label="${n}">${porTipo[k].map(p =>
        `<option value="cod:${esc(p.codigo)}">${esc(p.codigo)} · ${esc(p.descricao || '')}</option>`).join('')}</optgroup>`).join('');
    sel.value = [...sel.options].some(o => o.value === atual) ? atual : '';
  }
  async function montarFiltrosFaturado() {
    const hosp = await FaturamedAuth.request('/api/hospitais').catch(() => []);
    $('#inicio-ff-hospital').insertAdjacentHTML('beforeend', hosp.map(h => `<option value="${h.id}">${esc(h.nome)}</option>`).join(''));
    await opcoesExame();
    $('#inicio-ff-hospital').addEventListener('change', async () => { await opcoesExame(); faturado().catch(() => {}); });
    $('#inicio-ff-exame').addEventListener('change', () => faturado().catch(() => {}));
  }
  // Composicao por tipo: blocos proporcionais, valores e lista com % conciliado (mesmos filtros do card grande)
  const CORES_TIPO = {EXAME: 'var(--blue)', VISITA: 'var(--green)', CONSULTA: 'var(--amber)', OUTRO: 'var(--muted)'};
  const brl = n => Number(n || 0).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL', maximumFractionDigits: Number(n) >= 1000 ? 0 : 2});
  // composicao segue os filtros do Fechamento Faturado (hospital, exame e periodo)
  let geracaoComp = 0;
  async function carregarComposicao() {
    const g = ++geracaoComp, periodo = valorPeriodo('faturado') || 'tudo';
    const hospital = $('#inicio-ff-hospital').value, exame = $('#inicio-ff-exame').value;
    const qs = new URLSearchParams({periodo}); if (hospital) qs.set('hospitalId', hospital); if (exame) qs.set('exame', exame);
    const r = await FaturamedAuth.request(`/api/inicio/fechamento-faturado?${qs}`);
    if (g !== geracaoComp) return;
    const filtros = [hospital && $('#inicio-ff-hospital').selectedOptions[0].textContent, exame && $('#inicio-ff-exame').selectedOptions[0].textContent].filter(Boolean);
    composicao(r, filtros, periodo === 'tudo' ? 'todo o período' : rotuloPeriodo(periodo));
  }
  function composicao(r, filtros, rot) {
    const lista = r.composicao || [], alvo = $('#inicio-comp');
    $('#inicio-comp-sub').textContent = `${rot.charAt(0).toUpperCase() + rot.slice(1)}${filtros.length ? ` · ${filtros.join(' · ')}` : ''}`;
    const total = lista.reduce((a, t) => a + Number(t.total), 0);
    if (!total) { alvo.innerHTML = '<p class="ini-vazio">Sem procedimentos com estes filtros.</p>'; return; }
    const soma = k => lista.reduce((a, t) => a + Number(t[k] || 0), 0);
    const semRepasse = soma('sem_repasse_planilha');
    alvo.innerHTML = `<div class="ini-comp-blocos" role="img" aria-label="Procedimentos por tipo">${lista.map((t, i) => {
        const pct = Math.round(t.total / total * 100);
        return `<div style="flex:${Number(t.total)};--cor:${CORES_TIPO[t.tipo] || 'var(--muted)'};animation-delay:${i * 80}ms" title="${esc(TIPOS_FF[t.tipo] || t.tipo)}: ${num(t.total)} (${pct}%)">${pct >= 12 ? `<b>${esc(TIPOS_FF[t.tipo] || t.tipo)}</b><small>${num(t.total)} · ${pct}%</small>` : ''}</div>`;
      }).join('')}</div>
      <div class="ini-comp-kpis">
        <div><span>Valor hospital</span><strong>${brl(soma('valor'))}</strong></div>
        <div><span>Repasse na planilha</span><strong>${brl(soma('repasse_planilha'))}</strong></div>
        <div title="Linhas sem Vl. a Repassar nem Regra: usam o repasse cadastrado"><span>Usam repasse cadastrado</span><strong>${Math.round(semRepasse / total * 100)}%</strong></div>
      </div>
      <ul class="ini-comp-lista">${lista.map(t => `<li style="--cor:${CORES_TIPO[t.tipo] || 'var(--muted)'}"><i></i><span>${esc(TIPOS_FF[t.tipo] || t.tipo)} <em>${num(t.total)}</em></span><em>${Math.round(t.conciliado / t.total * 100)}% conciliado</em><b>${brl(t.valor)}</b></li>`).join('')}</ul>`;
  }
  async function faturado() {
    const g = ++geracaoFF, periodo = valorPeriodo('faturado') || 'tudo', tudo = periodo === 'tudo';
    const hospital = $('#inicio-ff-hospital').value, exame = $('#inicio-ff-exame').value;
    const qs = new URLSearchParams({periodo}); if (hospital) qs.set('hospitalId', hospital); if (exame) qs.set('exame', exame);
    const r = await FaturamedAuth.request(`/api/inicio/fechamento-faturado?${qs}`);
    if (g !== geracaoFF) return;
    const un = tudo ? 'M' : periodo.slice(-1), rot = tudo ? 'todo o período' : rotuloPeriodo(periodo), unidade = tudo ? 'mês' : un === 'd' ? 'dia' : un === 'h' ? 'hora' : 'minuto';
    $('#inicio-ff-titulo').textContent = `Fechamento Faturado · ${rot.charAt(0).toUpperCase() + rot.slice(1)}`;
    const filtros = [hospital && $('#inicio-ff-hospital').selectedOptions[0].textContent, exame && $('#inicio-ff-exame').selectedOptions[0].textContent].filter(Boolean);
    $('#inicio-ff-sub').textContent = `${un === 'd' ? 'Procedimentos do hospital por data' : un === 'M' ? 'Procedimentos do hospital por mês' : 'Procedimentos pelo momento da importação'}${filtros.length ? ` · ${filtros.join(' · ')}` : ''}`;
    const dias = r.serie || [], alvo = $('#inicio-ff');
    const v = dias.map(d => Number(d.total) || 0), total = v.reduce((a, b) => a + b, 0);
    const dataBr = d => d ? String(d).slice(0, 10).split('-').reverse().join('/') : '';
    carregarComposicao().catch(() => {});
    if (!total) { alvo.innerHTML = `<p class="ini-vazio">Nenhum procedimento ${tudo ? 'importado' : noPeriodo(periodo)}${filtros.length ? ' com estes filtros' : ''}.${r.ultimaData && !tudo ? ` O último é de <b>${dataBr(r.ultimaData)}</b>; aumente o período ou escolha <b>Todo o período</b>.` : ''}</p>`; return; }
    const max = Math.max(1, ...v), media = total / v.length, passo = Math.max(1, Math.ceil(dias.length / 7)), pico = Math.max(...v);
    const somas = Object.fromEntries(SIT_FF.map(([k]) => [k, dias.reduce((a, d) => a + (Number(d[k]) || 0), 0)]));
    const pct = Math.round(somas.conciliado / total * 100);
    const dica = (d, i) => `${d.dia}: ${num(v[i])} procedimento(s)` + SIT_FF.map(([k, nome]) => Number(d[k]) ? ` · ${nome} ${num(d[k])}` : '').join('');
    const tipos = Object.entries(r.porTipo || {}).map(([k, n]) => `${TIPOS_FF[k] || k} <b>${num(n)}</b>`).join(' · ');
    // area empilhada suave: divergente embaixo, aguardando no meio, conciliado em cima
    const W = 1000, H = 200, topo = max * 1.12, n = dias.length;
    const xs = dias.map((_, i) => n === 1 ? W / 2 : i * W / (n - 1));
    const y = val => H - val / topo * H;
    const curva = pts => pts.map(([x, yy], i) => {
      if (!i) return `M${x},${yy}`;
      const [x0, y0] = pts[i - 1], dx = (x - x0) / 2;
      return `C${x0 + dx},${y0} ${x - dx},${yy} ${x},${yy}`;
    }).join(' ');
    const ordem = SIT_FF.slice().reverse(); // divergente, aguardando, conciliado
    let base = dias.map(() => 0);
    const camadas = ordem.map(([k, nome, cor]) => {
      const cima = dias.map((d, i) => base[i] + (Number(d[k]) || 0));
      const sup = xs.map((x, i) => [x, y(cima[i])]), inf = xs.map((x, i) => [x, y(base[i])]).reverse();
      const d = `${curva(sup)} L${inf[0][0]},${inf[0][1]} ${curva(inf).replace(/^M[^C]*/, '')} Z`;
      base = cima;
      return `<path d="${d}" fill="${cor}" class="ini-ff-camada"><title>${nome}</title></path><path d="${curva(sup)}" fill="none" stroke="${cor}" stroke-width="2" vector-effect="non-scaling-stroke" class="ini-ff-borda"/>`;
    }).join('');
    const yMedia = y(media).toFixed(1);
    alvo.innerHTML = `<div class="ini-ff-grafico" role="img" aria-label="${total} procedimentos em ${n} períodos, por situação">
      <svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" class="ini-ff-svg">
        <g class="ini-ff-grade">${[0.25, 0.5, 0.75].map(f => `<line x1="0" x2="${W}" y1="${H * f}" y2="${H * f}" vector-effect="non-scaling-stroke"/>`).join('')}</g>
        ${camadas}
        <line x1="0" x2="${W}" y1="${yMedia}" y2="${yMedia}" class="ini-ff-media" vector-effect="non-scaling-stroke"/>
      </svg>
      <span class="ini-ff-media-rot" style="top:${(yMedia / H * 100).toFixed(1)}%">média ${media.toLocaleString('pt-BR', {maximumFractionDigits: 1})}</span>
      <div class="ini-ff-alvos" style="--larg:${(100 / n).toFixed(2)}%">${dias.map((d, i) => `<span title="${esc(dica(d, i))}" style="--x:${n === 1 ? 50 : (i / (n - 1) * 100).toFixed(2)}%"><b style="bottom:${(v[i] / topo * 100).toFixed(1)}%">${v[i] ? num(v[i]) : ''}</b></span>`).join('')}</div>
      <div class="ini-ff-eixo">${dias.map((d, i) => i % passo ? '' : `<small style="left:${n === 1 ? 50 : (i / (n - 1) * 100).toFixed(2)}%">${esc(d.dia)}</small>`).join('')}</div>
    </div><div class="ini-ff-rodape">
      <div class="ini-ff-legenda">${SIT_FF.map(([k, nome, cor]) => `<span class="ini-ff-chip" style="--cor:${cor}"><i></i>${nome}<b>${num(somas[k])}</b></span>`).join('')}</div>
      <div class="ini-ff-resumo"><span><b>${num(total)}</b> procedimentos</span><span><b>${pct}%</b> conciliados</span><span>pico <b>${num(pico)}</b>/${unidade}</span>${somas.divergente ? `<a href="index.html#divergencias">Revisar divergências →</a>` : ''}</div>
    </div>`;
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
      area(r); aplicarOrdens();
      montarFiltrosFaturado(); faturado().catch(() => { $('#inicio-ff').innerHTML = '<p class="ini-vazio">Não foi possível carregar o Fechamento Faturado.</p>'; });
      $('.ini-graficos').addEventListener('periodo', e => aplicarPeriodo(r, e.target.dataset.periodo).catch(() => { $('#inicio-msg').textContent = 'Não foi possível aplicar o período agora.'; }));
      esteira(r, ''); servicedesk(r, '');
      chips(r);
      contar();
    } catch (_) { $('#inicio-msg').textContent = 'Não foi possível carregar os indicadores agora.'; }
  }
  carregar();
})();
