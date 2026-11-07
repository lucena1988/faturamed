(() => {
  const $ = s => document.querySelector(s);
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c]);
  const brl = v => Number(v || 0).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'});
  const data = d => d ? new Date(d + 'T00:00').toLocaleDateString('pt-BR') : '—';
  const variacao = (atual, antes) => {
    if (!Number(antes)) return '';
    const p = Math.round((Number(atual) - Number(antes)) / Number(antes) * 100);
    return p ? ` · ${p > 0 ? '↑' : '↓'} ${Math.abs(p)}% vs. mês anterior` : ' · igual ao mês anterior';
  };
  const ICONES = {
    rascunho: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13 7 4 4"/>',
    etiqueta: '<path d="M3 12V4h8l10 10-8 8z"/><circle cx="7.5" cy="7.5" r="1.5"/>',
    enviado: '<path d="M3 11 21 3l-6 18-3-8z"/><path d="m12 13 9-10"/>',
    relogio: '<circle cx="12" cy="12" r="8"/><path d="M12 8v4l3 2"/>',
    alerta: '<path d="M12 3 2.8 20h18.4L12 3Z"/><path d="M12 9v5m0 3h.01"/>',
    nota: '<path d="M7 3h7l4 4v14H7z"/><path d="M14 3v4h4M10 12h5M10 16h5"/>',
    check: '<circle cx="12" cy="12" r="8"/><path d="m8.5 12 2.5 2.5 4.5-5"/>'
  };
  const tarefa = (icone, valor, texto, url, urgente) =>
    `<a class="pp-tarefa${urgente && valor ? ' urgente' : ''}${valor ? '' : ' zero'}" href="${url}"><svg class="fm-card-icone" data-icone="${icone}" viewBox="0 0 24 24" aria-hidden="true">${ICONES[icone]}</svg>
      <div><strong>${valor}</strong><span>${esc(texto)}</span></div></a>`;

  function saudacao(nome) {
    const h = new Date().getHours();
    const quando = new Date().toLocaleDateString('pt-BR', {weekday: 'long', day: 'numeric', month: 'long'});
    $('#saudacao').textContent = `${h < 12 ? 'Bom dia' : h < 18 ? 'Boa tarde' : 'Boa noite'}${nome ? ', ' + nome.split(' ')[0] : ''} · ${quando}`;
  }

  function desenhar(p) {
    const t = p.tarefas;
    const itens = [
      ['alerta', t.atrasados, 'conferência(s) de hospital com prazo vencido', 'fechamento-producao.html', true],
      ['relogio', t.venceHoje, 'conferência(s) vencendo hoje', 'fechamento-producao.html', true],
      ['etiqueta', t.contestacoes, 'contestação(ões) de hospital para responder', 'fechamento-producao.html', true],
      ['rascunho', t.lotesRascunho, `lote(s) em rascunho · ${t.etiquetasPendentes} etiqueta(s) a revisar`, 'producao.html?status=RASCUNHO', false],
      ['enviado', t.etiquetasParaFechar, 'etiqueta(s) conferidas prontas para fechar', 'fechamento-producao.html', false],
      ['nota', t.aprovadosSemNf, 'fechamento(s) aprovados aguardando NF', 'fechamento-producao.html', false],
      ['check', t.aguardandoHospital, 'fechamento(s) aguardando o hospital', 'fechamento-producao.html', false]
    ];
    const comAlgo = itens.filter(i => i[1]);
    $('#tarefas').innerHTML = comAlgo.length
      ? itens.sort((a, b) => (b[1] ? 1 : 0) - (a[1] ? 1 : 0)).map(i => tarefa(...i)).join('')
      : '<p class="pp-tudo-certo">Tudo em dia por aqui. Nenhuma pendência na produção.</p>';

    const m = p.mes;
    $('#cards').innerHTML = [
      {icone: 'nota', rotulo: 'Fechado no mês', valor: brl(m.fechadoMes), sub: `mês anterior: ${brl(m.fechadoAnterior)}${variacao(m.fechadoMes, m.fechadoAnterior)}`, zerado: !Number(m.fechadoMes)},
      {icone: 'check', rotulo: 'NF emitida no mês', valor: brl(m.nfMes), sub: 'notas registradas neste mês', zerado: !Number(m.nfMes)},
      {icone: 'relogio', rotulo: 'A faturar (aguardando + aprovado)', valor: brl(m.aReceberHospital), sub: 'fechamentos ainda sem NF', zerado: !Number(m.aReceberHospital)},
      {icone: 'etiqueta', rotulo: 'Etiquetas no mês', valor: String(m.etiquetasMes), sub: `mês anterior: ${m.etiquetasAnterior}${variacao(m.etiquetasMes, m.etiquetasAnterior)}`, zerado: !m.etiquetasMes}
    ].map(fmUi.card).join('');

    $('#prazo-dias').textContent = `— ${p.prazoDias} dias úteis após o envio`;
    $('#prazos').innerHTML = p.prazos.length ? p.prazos.map(a => {
      const classe = a.diasUteis < 0 ? 'vencido' : a.diasUteis <= 1 ? 'perto' : 'ok';
      const rotulo = a.diasUteis < 0 ? `<b>${-a.diasUteis}</b>dia(s) atrasado` : a.diasUteis === 0 ? '<b>hoje</b>vence' : `<b>${a.diasUteis}</b>dia(s) útil(eis)`;
      return `<a class="pp-prazo" href="fechamento-producao.html"><span class="pp-contador ${classe}">${rotulo}</span>
        <div><strong>${esc(a.hospital)} · nº ${a.fechamentoId}</strong><small>${brl(a.total)} · prazo ${data(a.prazo)} · ${a.vistoPeloHospital ? 'já aberto pelo hospital' : 'ainda não aberto pelo hospital'}</small></div></a>`;
    }).join('') : fmUi.vazio(1, {titulo: 'Nenhum fechamento aguardando o hospital', icone: 'fechamento'}).replace(/<\/?t[rd][^>]*>/g, '');

    $('#hospitais').innerHTML = p.hospitais.map(h => `<tr><td>${esc(h.nome)}</td><td class="fm-num">${h.etiquetasParaFechar}</td>
      <td class="fp-valor">${brl(h.aguardando)}</td><td class="fp-valor">${brl(h.aprovado)}</td><td class="fp-valor">${brl(h.nfMes)}</td></tr>`).join('')
      || fmUi.vazio(5, {titulo: 'Nenhum hospital com produção ainda', icone: 'busca'});

    // mapa de calor: 26 semanas, segunda a domingo
    const porDia = new Map(p.mapa.map(d => [d.dia, d.etiquetas]));
    const max = Math.max(1, ...p.mapa.map(d => d.etiquetas));
    const hoje = new Date(); hoje.setHours(0, 0, 0, 0);
    const inicio = new Date(hoje); inicio.setDate(inicio.getDate() - 7 * 25 - ((hoje.getDay() + 6) % 7));
    const celulas = [];
    for (let d = new Date(inicio); celulas.length < 26 * 7; d.setDate(d.getDate() + 1)) {
      const chave = d.toISOString().slice(0, 10);
      const n = porDia.get(chave) || 0;
      const nivel = n === 0 ? 0 : Math.min(4, Math.ceil(n / max * 4));
      celulas.push(d > hoje ? '<i class="futuro"></i>' : `<i class="n${nivel}" title="${d.toLocaleDateString('pt-BR')}: ${n} etiqueta(s)"></i>`);
    }
    $('#mapa').innerHTML = celulas.join('');
  }

  (async () => {
    try {
      const [eu, painel] = await Promise.all([fetch('/api/auth/me').then(r => r.json()), fetch('/api/producao/painel').then(r => r.ok ? r.json() : Promise.reject(new Error('Não foi possível carregar o painel')))]);
      saudacao(eu.nome);
      // gestor (administrador) ve primeiro os numeros; funcionario ve primeiro as tarefas
      if (['ADMIN', 'DEV'].includes(eu.perfil)) $('#grade').classList.add('gestor');
      desenhar(painel);
    } catch (e) { fmUi.toast(e.message, 'erro'); }
  })();
})();
