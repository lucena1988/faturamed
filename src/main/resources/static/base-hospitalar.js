(() => {
  const find = id => document.getElementById(id);
  const labels = {AGUARDANDO_PRODUCAO:'Aguardando produção',CONCILIADA:'Conciliada',DIVERGENTE:'Divergente',DUPLICADO_PAGO_OUTRO_MES:'Duplicado pago em outro mês'};
  const request = window.FaturamedAuth.request;
  let visits = [], generation = 0, selectedVisit, busy = false, flow = null;
  const stages = {AGUARDANDO_PRODUCAO:'Aguardando produção',AGUARDANDO_FATURAMENTO:'Aguardando faturamento',FATURADO_SEM_RECEBIMENTO:'Faturado sem recebimento',RECEBIDO_PARCIALMENTE:'Recebido parcialmente',SEM_SALDO:'Sem saldo a receber',EM_CONFERENCIA:'Em conferência'};
  const money = value => value == null ? 'Não confirmado' : new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value);
  function element(tag, text) { const item = document.createElement(tag); if (text != null) item.textContent = text; return item; }
  function link(text, href) { const item = element('a', text); item.href = href; return item; }
  function button(text, action) { const item = element('button', text); item.type = 'button'; item.addEventListener('click', () => action().catch(error => { find('message').textContent = error.message; })); return item; }
  function renderVisits() {
    const filtered = visits.filter(visit => (!find('status').value || visit.situacao === find('status').value) && visit.atendimento.includes(find('search').value.trim()));
    find('visits').replaceChildren();
    for (const visit of filtered) {
      const row = element('tr');
      for (const value of [visit.id, visit.competencia, visit.atendimento, visit.data_visita, visit.medico_nome || 'Não identificado', visit.procedimento || 'Não informado', labels[visit.situacao], visit.anterior_id || '—']) row.append(element('td', value));
      const actions = element('td');
      if (visit.decisao === 'EM_ANALISE') actions.append(button('Analisar repetição', async () => openDecision(visit)));
      actions.append(button('Histórico', async () => {
        const history = await request(`/api/base-hospitalar/visitas/${visit.id}/historico`);
        find('decision-history').replaceChildren(...history.map(item => element('li', `${item.decisao} · ${item.responsavel} · ${item.criado_em} · ID original ${item.anterior_id || 'não se aplica'} · ${item.justificativa} · ${item.evidencia}`)));
        if (!history.length) find('decision-history').append(element('li', 'Nenhuma decisão manual registrada.'));
        find('history-dialog').showModal();
      }));
      if (visit.relatorio_id) actions.append(link('Ver produção vinculada', `/conciliacao.html?relatorio=${visit.relatorio_id}&linha=${visit.linha_producao}`));
      actions.append(link('Arquivo original', `/api/base-hospitalar/arquivos/${visit.arquivo_id}/original`));
      if (visit.arquivo_atual_id && visit.arquivo_atual_id !== visit.arquivo_id) actions.append(link('Última atualização', `/api/base-hospitalar/arquivos/${visit.arquivo_atual_id}/original`));
      row.append(actions); find('visits').append(row);
    }
    find('count').textContent = `${filtered.length} ocorrências · IDs das visitas distintas são preservados`;
  }
  async function load() {
    const token = ++generation, hospital = find('hospital').value;
    if (!hospital) { visits = []; flow = null; renderFlow(); renderVisits(); find('files').replaceChildren(); return; }
    const [data, files, tracking] = await Promise.all([request(`/api/base-hospitalar/visitas?hospitalId=${hospital}`), request(`/api/base-hospitalar/arquivos?hospitalId=${hospital}`), request(`/api/base-hospitalar/acompanhamento?hospitalId=${hospital}`)]);
    if (token !== generation) return;
    visits = data; flow = tracking; renderFlow(); renderVisits(); find('files').replaceChildren();
    for (const file of files) {
      const row = element('tr');
      for (const value of [file.nome, file.tipo, file.competencia, file.medico || (file.tipo === 'PRODUCAO' && file.modo_producao === 'VARIOS' ? 'Vários médicos' : '—')]) row.append(element('td', value));
      const actions = element('td'); actions.append(link('Baixar original', `/api/base-hospitalar/arquivos/${file.id}/original`));
      if (file.relatorio_id) {
        actions.append(link('Abrir conciliação', `/conciliacao.html?relatorio=${file.relatorio_id}`));
        actions.append(button('Atualizar conciliação', async () => {
          if (busy || !confirm('Criar uma nova versão com as visitas hospitalares disponíveis? Revisões e recebimentos serão preservados.')) return;
          await perform(async () => {
            const current = await request(`/api/conciliacoes/visitas/${file.relatorio_id}/acompanhamento`);
            const result = await request(`/api/base-hospitalar/relatorios/${file.relatorio_id}/atualizar?versao=${current.versao}`, {method:'POST'});
            find('message').replaceChildren(element('span', 'Nova versão criada. Confira e libere novamente para o médico. '), link('Abrir relatório', `/conciliacao.html?relatorio=${result.id}`));
            await load();
          });
        }));
      }
      row.append(actions); find('files').append(row);
    }
  }
  async function perform(action) {
    if (busy) return;
    busy = true;
    document.querySelectorAll('button, input, select, textarea').forEach(control => { control.disabled = true; });
    try { await action(); }
    finally { busy = false; document.querySelectorAll('button, input, select, textarea').forEach(control => { control.disabled = false; }); updateDecision(); updateProduction(); }
  }
  for (const [formId, endpoint] of [['hospital-form','hospital'], ['production-form','producao']]) find(formId).addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget, data = new FormData(form);
    if (!find('hospital').value) { find('message').textContent = 'Cadastre e selecione um hospital ativo.'; return; }
    data.set('hospitalId', find('hospital').value);
    try {
      await perform(async () => {
        find('message').textContent = 'Processando arquivo. Aguarde a confirmação.';
        const result = await request(`/api/base-hospitalar/${endpoint}`, {method:'POST', body:data});
        const text = result.repetido ? 'Arquivo já importado nesta competência. A importação anterior foi mantida.' : `${result.visitas} registros processados; ${result.atualizadas || 0} visitas atualizadas mantendo o ID; ${result.divergencias} divergências para análise.`;
        find('message').replaceChildren(element('span', `${text} `));
        if (result.relatorioId) find('message').append(link('Abrir conciliação e revisar', `/conciliacao.html?relatorio=${result.relatorioId}`));
        form.querySelector('[type=file]').value = '';
        await load();
      });
    } catch (error) { find('message').textContent = error.message; }
  });
  function updateProduction() {
    const individual = find('production-mode').value === 'UNICO';
    find('production-doctor-label').hidden = !individual;
    find('medico').required = individual;
    find('medico').disabled = !individual;
    find('production-help').textContent = individual
      ? 'Na produção individual, selecione o médico e envie Atendimento e Dt.'
      : 'Na produção coletiva, envie Atendimento e Dt. Medico é opcional: quando vazio, o sistema identifica pelo hospital somente se houver uma correspondência única e um cadastro médico ativo identificado com segurança. Vários registros ou nomes desconhecidos exigem revisão.';
  }
  function renderFlow() {
    find('flow-stats').replaceChildren(); find('flow-rows').replaceChildren(); find('flow-money').textContent = '';
    if (!flow) return;
    for (const [key,label] of Object.entries(stages)) {
      const item = element('div',label); item.append(element('strong',flow.etapas[key] || 0)); find('flow-stats').append(item);
    }
    for (const [label,value] of [['Produção sem base hospitalar',flow.producaoSemBase],['Ocorrências repetidas em análise',flow.ocorrenciasEmAnalise],['Duplicadas confirmadas excluídas dos totais',flow.duplicadas]]) {
      const item = element('div',label); item.append(element('strong',value)); find('flow-stats').append(item);
    }
    find('flow-money').textContent = `Subtotais confirmados · Faturado: ${money(flow.faturado)} · Recebido: ${money(flow.recebido)} · Saldo: ${money(flow.saldo)}`;
    for (const visit of flow.visitas.filter(item => !find('flow-filter').value || item.etapa === find('flow-filter').value)) {
      const row = element('tr');
      for (const value of [visit.visitaId,visit.atendimento,visit.data,visit.medico || 'Não identificado',stages[visit.etapa],money(visit.faturado),money(visit.recebido),money(visit.saldo)]) row.append(element('td',value));
      const actions = element('td');
      if (visit.relatorio) actions.append(link('Revisar',`/conciliacao.html?relatorio=${visit.relatorio}&linha=${visit.linha}`));
      if (visit.saldo > 0) actions.append(link('Recebimentos',`/recebimentos.html?relatorio=${visit.relatorio}`));
      row.append(actions); find('flow-rows').append(row);
    }
  }
  function updateDecision() {
    const duplicate = find('decision-action').value === 'DUPLICADO_PAGO_OUTRO_MES';
    for (const name of ['anteriorId','evidencia']) {
      const field = find('decision-form').elements.namedItem(name); field.required = duplicate; field.disabled = !duplicate;
    }
    find('original-label').hidden = !duplicate; find('evidence-label').hidden = !duplicate;
  }
  function openDecision(visit) {
    selectedVisit = visit; find('decision-form').reset();
    find('decision-summary').textContent = `Ocorrência ID ${visit.id} · atendimento ${visit.atendimento} · ${visit.data_visita} · ${visit.medico_nome} · competência ${visit.competencia}. Possível original ID ${visit.anterior_id}.`;
    find('decision-form').elements.namedItem('anteriorId').value = visit.anterior_id;
    find('decision-message').textContent = ''; updateDecision(); find('decision-dialog').showModal();
  }
  find('decision-form').addEventListener('submit', async event => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(event.currentTarget)); data.anteriorId = data.anteriorId ? Number(data.anteriorId) : null;
    try { await perform(async () => {
      await request(`/api/base-hospitalar/visitas/${selectedVisit.id}/decisao`, {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});
      find('decision-dialog').close(); find('message').textContent = 'Decisão registrada. Atualize a conciliação das produções afetadas, se necessário.'; await load();
    }); } catch (error) { find('decision-message').textContent = error.message; }
  });
  find('decision-action').addEventListener('change', updateDecision);
  find('production-mode').addEventListener('change', updateProduction);
  find('flow-filter').replaceChildren(new Option('Todas as etapas',''),...Object.entries(stages).map(([value,label]) => new Option(label,value)));
  find('flow-filter').addEventListener('change', renderFlow);
  find('decision-close').addEventListener('click', () => find('decision-dialog').close());
  find('history-close').addEventListener('click', () => find('history-dialog').close());
  for (const id of ['status','search']) find(id).addEventListener('input', renderVisits);
  find('hospital').addEventListener('change', () => load().catch(error => { find('message').textContent = error.message; }));
  find('reload').addEventListener('click', () => load().catch(error => { find('message').textContent = error.message; }));
  async function initialize() {
    const [hospitals, doctors] = await Promise.all([request('/api/hospitais'), request('/api/medicos')]);
    find('hospital').replaceChildren(new Option('Selecione um hospital', ''), ...hospitals.filter(item => item.ativo).map(item => new Option(item.nome, item.id)));
    find('medico').replaceChildren(new Option('Selecione o médico', ''), ...doctors.filter(item => item.ativo).map(item => new Option(`${item.nome} · CRM ${item.crm}/${item.uf}`, item.id)));
    const params = new URLSearchParams(location.search);
    if (hospitals.some(item => item.ativo && String(item.id) === params.get('hospitalId'))) find('hospital').value = params.get('hospitalId');
    await load(); updateDecision(); updateProduction();
  }
  initialize().catch(error => { find('message').textContent = error.message; });
})();
