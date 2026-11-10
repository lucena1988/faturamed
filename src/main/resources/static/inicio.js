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
      const c = 2 * Math.PI * 20, parte = Math.max(0, Math.min(1, fracao)) * c;
      return `<a class="ini-kpi${alerta ? ' alerta' : ''}" data-tipo="${tipo}" href="${href}" style="--i:${i}">
        <span class="ini-kpi-icone" aria-hidden="true"><svg viewBox="0 0 24 24">${ICONES[tipo]}</svg></span>
        <span class="ini-kpi-corpo"><span class="ini-kpi-titulo">${esc(titulo)}</span><strong data-contar="${Number(n || 0)}">0</strong><small>${esc(detalhe)}</small></span>
        <svg class="ini-kpi-anel" viewBox="0 0 48 48" aria-hidden="true"><circle cx="24" cy="24" r="20"/><circle class="v" cx="24" cy="24" r="20" stroke-dasharray="${parte.toFixed(1)} ${c.toFixed(1)}" transform="rotate(-90 24 24)"/></svg>
      </a>`;
    }).join('');
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
    const pct = Math.round((r.visitasPagas || 0) / total * 100);
    $('#inicio-donut').innerHTML = `<svg viewBox="0 0 140 140" role="img" aria-label="${pct}% das visitas pagas"><circle class="trilho" cx="70" cy="70" r="54"/><g transform="rotate(-90 70 70)">${arcos}</g>
      <text x="70" y="68" class="centro">${pct}%</text><text x="70" y="86" class="legenda">pagas</text></svg>
      <ul>${partes.map(([nome, v, cor]) => `<li><i style="background:${cor}"></i>${nome}<b>${num(v)}</b></li>`).join('')}</ul>`;
  }

  function area(r) {
    const dias = r.lotesPorDia || [];
    if (!dias.length) { $('#inicio-area').innerHTML = '<p class="ini-vazio">Sem dados de produção.</p>'; return; }
    const L = 560, A = 190, mx = 12, topo = 16, base = A - 26, max = Math.max(1, ...dias.map(d => Number(d.total)));
    const x = i => mx + i * (L - 2 * mx) / Math.max(1, dias.length - 1), y = v => base - v / max * (base - topo);
    const pts = dias.map((d, i) => [x(i), y(Number(d.total))]);
    // curva suave entre os pontos
    const linha = pts.map(([px, py], i) => {
      if (!i) return `M${px.toFixed(1)},${py.toFixed(1)}`;
      const [ax, ay] = pts[i - 1], m = (ax + px) / 2;
      return `C${m.toFixed(1)},${ay.toFixed(1)} ${m.toFixed(1)},${py.toFixed(1)} ${px.toFixed(1)},${py.toFixed(1)}`;
    }).join(' ');
    const total = dias.reduce((a, d) => a + Number(d.total), 0);
    $('#inicio-area').innerHTML = `<svg viewBox="0 0 ${L} ${A}" role="img" aria-label="${total} lotes em 14 dias">
      <defs><linearGradient id="ini-grad" x1="0" y1="0" x2="0" y2="1"><stop offset="0" class="g0"/><stop offset="1" class="g1"/></linearGradient></defs>
      ${[0, .5, 1].map(f => `<line class="grade" x1="${mx}" x2="${L - mx}" y1="${y(max * f)}" y2="${y(max * f)}"/>`).join('')}
      <path class="preenchimento" d="${linha} L${pts[pts.length - 1][0]},${base} L${pts[0][0]},${base} Z"/><path class="linha" d="${linha}"/>
      ${pts.map(([px, py], i) => `<circle class="ponto" cx="${px.toFixed(1)}" cy="${py.toFixed(1)}" r="3.5"><title>${esc(dias[i].dia)}: ${num(dias[i].total)} lote(s)</title></circle>`).join('')}
      ${dias.map((d, i) => i % 2 ? '' : `<text class="eixo" x="${x(i).toFixed(1)}" y="${A - 6}">${esc(d.dia)}</text>`).join('')}
    </svg><p class="ini-area-total"><b>${num(total)}</b> lotes no período · pico de <b>${num(max)}</b> em um dia</p>`;
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

  async function carregar() {
    let perfil = null;
    try {
      const eu = await FaturamedAuth.request('/api/auth/me');
      perfil = eu.perfil;
      const nome = (eu.nome || '').trim().split(/\s+/)[0];
      $('#inicio-saudacao').textContent = nome ? `${periodo}, ${nome}` : periodo;
      $('#inicio-perfil').textContent = PERFIS[perfil] || perfil || '';
    } catch (_) { $('#inicio-saudacao').textContent = periodo; }
    modulos(perfil);
    try {
      const r = await FaturamedAuth.request('/api/inicio/resumo');
      kpis(r); donut(r); area(r);
      barras('#inicio-esteira', r.lotesPorStatus || {}, [['RASCUNHO', 'Rascunho', 'var(--amber)'], ['CONFERIDO', 'Conferido', 'var(--blue)'], ['ENVIADO', 'Enviado', 'var(--green)']]);
      barras('#inicio-chamados', r.chamadosPorStatus || {}, [['ABERTO', 'Abertos', 'var(--rose)'], ['EM_ATENDIMENTO', 'Em atendimento', 'var(--amber)'], ['AGUARDANDO_USUARIO', 'Aguardando', 'var(--blue)'], ['RESOLVIDO', 'Resolvidos', 'var(--green)'], ['FECHADO', 'Fechados', 'var(--muted)']]);
      const pend = (r.visitasAtencao || 0) + (r.chamadosAbertos || 0) + (r.lotesPendentes || 0) + (r.solicitacoesPendentes || 0);
      $('#inicio-frase').textContent = pend ? `${num(pend)} itens aguardam sua atenção hoje.` : 'Tudo em dia por aqui.';
      contar();
    } catch (_) { $('#inicio-msg').textContent = 'Não foi possível carregar os indicadores agora.'; }
  }
  carregar();
})();
