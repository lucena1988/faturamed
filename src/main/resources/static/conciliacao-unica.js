(() => {
  const find = id => document.getElementById(id);
  const request = window.FaturamedAuth.request;
  const SITUACOES = {PAGA: 'Paga', PENDENTE: 'Aguardando hospital', DIVERGENTE: 'Divergente', EM_ANALISE: 'Em análise'};
  const CLASSES = {PAGA: 'chip-pago', PENDENTE: 'chip-pendente', DIVERGENTE: 'chip-divergente', EM_ANALISE: 'chip-analise'};
  let visitas = [], semProducao = [], medicos = [], busy = false, geracao = 0;

  const element = (tag, text) => { const item = document.createElement(tag); if (text != null) item.textContent = text; return item; };
  const mesAno = comp => comp ? comp.split('-').reverse().join('/') : '—';
  const dataBr = data => data ? String(data).slice(0, 10).split('-').reverse().join('/') : '—';
  function showMessage(type, ...nodes) {
    const box = find('message');
    box.dataset.type = type;
    box.replaceChildren(...nodes.map(node => typeof node === 'string' ? element('span', node) : node));
    box.scrollIntoView({behavior: 'smooth', block: 'nearest'});
  }
  async function perform(action) {
    if (busy) return;
    busy = true;
    document.querySelectorAll('button, input, select').forEach(control => { control.disabled = true; });
    try { await action(); }
    finally { busy = false; document.querySelectorAll('button, input, select').forEach(control => { control.disabled = false; }); }
  }
  const nomeMedico = visita => medicos.find(m => m.id === visita.medico_id)?.nome || visita.medico_nome || '';

  function renderCards() {
    const porComp = new Map();
    for (const v of visitas) {
      const item = porComp.get(v.competencia) || {total: 0, PAGA: 0, PENDENTE: 0, DIVERGENTE: 0, EM_ANALISE: 0};
      item.total++; item[v.situacao]++; porComp.set(v.competencia, item);
    }
    const atual = find('competencia-filter').value;
    find('comp-cards').replaceChildren();
    if (!porComp.size) { find('comp-cards').append(element('p', find('hospital').value ? 'Nenhuma produção importada para este hospital.' : 'Selecione um hospital.')).className = 'muted'; return; }
    for (const comp of [...porComp.keys()].sort().reverse()) {
      const dados = porComp.get(comp);
      // div com role=button: o estilo global de button sobrescreve cores de cartoes.
      const card = element('div'); card.className = 'comp-card'; card.setAttribute('role', 'button'); card.tabIndex = 0; card.setAttribute('aria-pressed', String(atual === comp));
      card.append(element('h3', mesAno(comp)), element('span', `${dados.total} ${dados.total === 1 ? 'visita' : 'visitas'}`));
      card.lastChild.className = 'total';
      for (const chave of Object.keys(SITUACOES)) {
        const linha = element('div'); linha.className = 'linha'; linha.append(element('span', SITUACOES[chave]), element('strong', dados[chave])); card.append(linha);
      }
      const alternar = () => { find('competencia-filter').value = atual === comp ? '' : comp; renderCards(); renderVisits(); };
      card.addEventListener('click', alternar);
      card.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); alternar(); } });
      find('comp-cards').append(card);
    }
  }
  function fillFilters() {
    const comps = [...new Set(visitas.map(v => v.competencia))].sort().reverse();
    const selComp = find('competencia-filter'), compAtual = selComp.value;
    selComp.replaceChildren(new Option('Todas', ''), ...comps.map(c => new Option(mesAno(c), c)));
    selComp.value = comps.includes(compAtual) ? compAtual : '';
    const nomes = [...new Set(visitas.map(nomeMedico).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'pt-BR'));
    const selMed = find('medico-filter'), medAtual = selMed.value;
    selMed.replaceChildren(new Option('Todos', ''), ...nomes.map(n => new Option(n, n)), new Option('Não identificado', '__SEM__'));
    selMed.value = [...selMed.options].some(o => o.value === medAtual) ? medAtual : '';
  }
  function renderVisits() {
    const comp = find('competencia-filter').value, status = find('status').value, med = find('medico-filter').value, busca = find('search').value.trim();
    const filtradas = visitas.filter(v => (!comp || v.competencia === comp) && (!status || v.situacao === status)
      && (!med || (med === '__SEM__' ? !nomeMedico(v) : nomeMedico(v) === med)) && v.atendimento.includes(busca));
    find('visits').replaceChildren();
    for (const v of filtradas) {
      const row = element('tr');
      row.append(element('td', v.id));
      const atend = element('td'); atend.append(element('div', v.atendimento || 'Sem atendimento')); const sub = element('div', dataBr(v.data_visita)); sub.className = 'muted'; atend.append(sub); row.append(atend);
      const medico = element('td', nomeMedico(v) || 'Não identificado');
      if (v.medico_inferido) { const tag = element('span', 'inferido'); tag.className = 'chip chip-inferido'; tag.title = 'Médico identificado pelo registro único do hospital'; medico.append(tag); }
      row.append(medico);
      const cell = element('td', mesAno(v.competencia));
      if (v.competencia !== v.competencia_derivada) { const tag = element('div', `Original ${mesAno(v.competencia_derivada)}`); tag.className = 'muted'; cell.append(tag); }
      row.append(cell);
      row.append(element('td', v.procedimento || 'Não informado'));
      const situacao = element('td'); const chip = element('span', SITUACOES[v.situacao] || v.situacao); chip.className = `chip ${CLASSES[v.situacao] || ''}`; situacao.append(chip); row.append(situacao);
      const motivo = element('td', v.motivo); motivo.className = 'motivo'; row.append(motivo);
      find('visits').append(row);
    }
    find('count').textContent = `${filtradas.length} ${filtradas.length === 1 ? 'visita' : 'visitas'}`;
  }
  function renderOrphans() {
    find('orphans').replaceChildren();
    for (const h of semProducao) {
      const row = element('tr');
      for (const value of [h.id, h.atendimento, dataBr(h.data_visita), mesAno(h.competencia_exame), h.medico_nome || 'Não identificado', h.procedimento || 'Não informado']) row.append(element('td', value));
      find('orphans').append(row);
    }
    const aba = document.querySelector('[data-tab="tab-sem-producao"]');
    aba.textContent = `Hospital sem produção${semProducao.length ? ` (${semProducao.length})` : ''}`;
  }
  async function load() {
    const id = ++geracao, hospitalId = find('hospital').value;
    if (!hospitalId) { visitas = []; semProducao = []; } else {
      const [lista, orfaos] = await Promise.all([request(`/api/conciliacao-unica/visitas?hospitalId=${hospitalId}`), request(`/api/conciliacao-unica/hospital-sem-producao?hospitalId=${hospitalId}`)]);
      if (id !== geracao) return;
      visitas = lista; semProducao = orfaos;
    }
    fillFilters(); renderCards(); renderVisits(); renderOrphans();
  }
  function resumoConciliacao(c) {
    if (!c) return '';
    return ` Conciliação: ${c.vinculadas} vinculadas, ${c.divergentes} divergentes, ${c.pendentes} aguardando${c.medicosInferidos ? `, ${c.medicosInferidos} médicos identificados pelo hospital` : ''}.`;
  }

  find('production-form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget, data = new FormData(form);
    if (!find('hospital').value) { showMessage('error', 'Selecione um hospital ativo.'); return; }
    if (!data.get('medicoId')) data.delete('medicoId');
    data.set('hospitalId', find('hospital').value);
    try {
      await perform(async () => {
        showMessage('info', 'Processando a produção. Aguarde a confirmação.');
        const r = await request('/api/conciliacao-unica/producao', {method: 'POST', body: data});
        if (r.repetido) showMessage('warning', 'Arquivo já importado para este hospital e médico. A importação anterior foi mantida.');
        else {
          const meses = Object.entries(r.competencias || {}).map(([comp, total]) => `${mesAno(comp)}: ${total}`).join(' · ');
          const aviso = [r.emAnalise ? `${r.emAnalise} repetidas em análise.` : '', r.semMedico ? `${r.semMedico} sem médico identificado.` : ''].filter(Boolean).join(' ');
          showMessage(r.emAnalise || r.semMedico ? 'warning' : 'success', `${r.visitas} visitas importadas — competências: ${meses}. ${aviso}${resumoConciliacao(r.conciliacao)}`);
        }
        form.querySelector('[type=file]').value = '';
        await load();
      });
    } catch (error) { showMessage('error', error.message); }
  });
  find('hospital-form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget, data = new FormData(form);
    if (!find('hospital').value) { showMessage('error', 'Selecione um hospital ativo.'); return; }
    data.set('hospitalId', find('hospital').value);
    if (!data.get('tipoProcedimento')) data.delete('tipoProcedimento');
    try {
      await perform(async () => {
        showMessage('info', 'Processando o arquivo do hospital. Aguarde a confirmação.');
        const r = await request('/api/conciliacao-unica/hospital', {method: 'POST', body: data});
        if (r.repetido) showMessage('warning', 'Arquivo já importado nesta competência. A importação anterior foi mantida.');
        else showMessage(r.divergencias > 0 ? 'warning' : 'success', `${r.visitas} registros do hospital processados; ${r.atualizadas || 0} atualizados mantendo o ID; ${r.divergencias} repetições para análise.${resumoConciliacao(r.conciliacao)}`);
        form.querySelector('[type=file]').value = '';
        await load();
      });
    } catch (error) { showMessage('error', error.message); }
  });
  document.querySelectorAll('.base-tabs [data-tab]').forEach(tab => tab.addEventListener('click', () => {
    document.querySelectorAll('.base-tabs [data-tab]').forEach(item => { item.setAttribute('aria-selected', String(item === tab)); find(item.dataset.tab).hidden = item !== tab; });
  }));
  for (const id of ['competencia-filter', 'status', 'medico-filter', 'search']) find(id).addEventListener('input', () => { renderCards(); renderVisits(); });
  find('hospital').addEventListener('change', () => load().catch(error => showMessage('error', error.message)));
  find('reload').addEventListener('click', () => load().catch(error => showMessage('error', error.message)));

  async function initialize() {
    let hospitais;
    [hospitais, medicos] = await Promise.all([request('/api/hospitais'), request('/api/medicos')]);
    find('hospital').replaceChildren(new Option('Selecione um hospital', ''), ...hospitais.filter(item => item.ativo).map(item => new Option(item.nome, item.id)));
    find('medico').replaceChildren(new Option('Vários médicos (identificar por linha)', ''), ...medicos.filter(item => item.ativo).map(item => new Option(`${item.nome} · CRM ${item.crm || 'a preencher'}/${item.uf || '--'}`, item.id)));
    const params = new URLSearchParams(location.search);
    if (hospitais.some(item => item.ativo && String(item.id) === params.get('hospitalId'))) find('hospital').value = params.get('hospitalId');
    await load();
  }
  initialize().catch(error => showMessage('error', error.message));
})();
