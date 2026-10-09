const form = document.querySelector('#conciliation-form');
const message = document.querySelector('#message');
const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 5 });
let report;
let reportId;
let reportsHistory=[];
let progress = new Map();
let progressGeneration = 0;
let medicosCadastro = [];
const normalizeDoctor = value => (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().replace(/\s+/g, ' ').toLowerCase();
function cadastroCandidates(name) {
  const key=normalizeDoctor(name);
  return key ? medicosCadastro.filter(m=>m.ativo && [m.nome,...m.aliases].some(n=>normalizeDoctor(n)===key)) : [];
}
function doctorId(visit) {
  if(visit.medicoCadastroId!=null) return visit.medicoCadastroId;
  const found=cadastroCandidates(visit.medico ?? visit.hospital?.medico ?? visit.original.medico);
  return found.length===1?found[0].id:null;
}
function doctorKey(visit) { const id=doctorId(visit);return id!=null?`id:${id}`:normalizeDoctor(visit.medico ?? visit.hospital?.medico ?? visit.original.medico); }
const categoryLabels = { SEM_PENDENCIA: 'Sem pendencia', AMBIGUIDADE: 'Ambiguidade', DADOS_FALTANTES: 'Dados faltantes',
  VALOR_DIVERGENTE: 'Valor divergente', SEM_CORRESPONDENCIA: 'Sem correspondencia', AGUARDANDO_FATURAMENTO: 'Aguardando faturamento',
  DADOS_DIVERGENTES: 'Dados divergentes', CONFERENCIA_MANUAL: 'Conferencia manual' };
const originLabels = { NAO_REVISADA: 'Nao revisada', MANUAL: 'Revisao manual', AUTOMATICA_IMPORTACAO: 'Automatica na importacao',
  AUTOMATICA_REVISAO: 'Automatica apos revisao' };
let reviewDetail;
let reviewLine;
let reviewSaving = false;
let reviewGeneration = 0;
const reviewDialog = document.querySelector('#review-dialog');
const actionLabels = { CONFIRMAR_CORRESPONDENCIA: 'Correspondencia confirmada', MANTER_PENDENTE: 'Mantida pendente',
  MANTER_DIVERGENTE: 'Mantida divergente', RESTAURAR_AUTOMATICO: 'Resultado automatico restaurado', AJUSTAR_DADOS: 'Dados da visita ajustados',
  CORRESPONDENCIA_AUTOMATICA: 'Correspondencia automatica apos revisao' };
async function request(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const error = new Error(body.erro || `Falha ao processar (${response.status})`);
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}
function showReport(id, data, tracking) {
  reportId = id;
  report = data;
  const token = ++progressGeneration;
  progress = new Map((tracking?.visitas || []).map(item => [item.linhaMedico, item]));
  document.querySelector('#origin-filter').disabled = !tracking;
  document.querySelector('#category-filter').disabled = !tracking;
  if (!tracking) {
    document.querySelector('#progress-counts').replaceChildren();
    document.querySelector('#delivery-status').textContent = 'Carregando acompanhamento...';
    document.querySelector('#delivery-status').dataset.state = '';
    request(`/api/conciliacoes/visitas/${id}/acompanhamento`).then(result => {
      if (token === progressGeneration && String(id) === String(reportId)) showReport(id, result.relatorio, result);
    }).catch(error => {
      if (token === progressGeneration) document.querySelector('#delivery-status').textContent = `Acompanhamento indisponivel: ${error.message}`;
    });
  }
  document.querySelector('#results').hidden = false;
  const download = document.querySelector('#download');
  download.href = `/api/conciliacoes/visitas/${id}/relatorio.xlsx`;
  download.hidden = false;
  const doctors = new Map();
  const normalize = value => (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().replace(/\s+/g, ' ').toLowerCase();
  for (const visit of data.visitas) {
    const name = visit.medico ?? visit.hospital?.medico ?? visit.original.medico ?? '';
    const cadastro=medicosCadastro.find(m=>m.id===doctorId(visit));
    if (name.trim()) doctors.set(doctorKey(visit), cadastro?`${cadastro.nome} - CRM ${cadastro.crm}/${cadastro.uf}${cadastro.ativo?'':' (inativo)'}`:name.trim());
  }
  const select = document.querySelector('#doctor-filter'); const previous = select.value;
  select.replaceChildren(new Option('Todos os medicos', ''), new Option('Medico nao identificado', '__SEM_MEDICO__'),
    ...[...doctors].sort((a, b) => a[1].localeCompare(b[1], 'pt-BR')).map(([key, name]) => new Option(name, key)));
  if ([...select.options].some(option => option.value === previous)) select.value = previous;
  const stats = document.querySelector('#stats');
  stats.replaceChildren();
  for (const [label, value] of [...Object.entries(data.resumo), ['Repasse correspondente', money.format(data.repasseCorrespondente)]]) {
    const item = document.createElement('div');
    item.textContent = label;
    const number = document.createElement('strong');
    number.textContent = value;
    item.append(number); stats.append(item);
  }
  renderRows();
}
function renderRows() {
  if (!report) return;
  const status = document.querySelector('#status').value;
  const search = document.querySelector('#search').value.trim();
  const doctor = document.querySelector('#doctor-filter');
  const normalize = value => (value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().replace(/\s+/g, ' ').toLowerCase();
  const selectedDoctor = report.visitas.filter(v => {
    const key = doctorKey(v);
    return !doctor.value || (doctor.value === '__SEM_MEDICO__' ? !key : key === doctor.value);
  });
  const origin = document.querySelector('#origin-filter').disabled ? '' : document.querySelector('#origin-filter').value;
  const category = document.querySelector('#category-filter').disabled ? '' : document.querySelector('#category-filter').value;
  const filtered = selectedDoctor.filter(v => {
    const item = progress.get(v.linhaMedico);
    return (!status || v.status === status) && v.atendimento.includes(search)
      && (!origin || (origin === 'AUTOMATICA' ? item?.origem.startsWith('AUTOMATICA_') : item?.origem === origin))
      && (!category || item?.categoria === category);
  });
  if (progress.size) {
    const items = selectedDoctor.map(v => progress.get(v.linhaMedico)).filter(Boolean);
    const pending = items.filter(item => item.precisaConferencia).length;
    const unassigned = report.visitas.filter(v => !progress.get(v.linhaMedico)?.medicoIdentificado).length;
    const counters = [['Nao revisadas', items.filter(i => i.origem === 'NAO_REVISADA').length],
      ['Revisadas manualmente', items.filter(i => i.origem === 'MANUAL').length],
      ['Resolvidas automaticamente', items.filter(i => i.origem.startsWith('AUTOMATICA_')).length],
      ['A conferir', pending], ['Sem medico no relatorio geral', unassigned]];
    const counts = document.querySelector('#progress-counts'); counts.replaceChildren();
    for (const [label, count] of counters) { const item = node('div', label); item.append(node('strong', count)); counts.append(item); }
    const delivery = document.querySelector('#delivery-status');
    delivery.dataset.state = pending ? 'attention' : 'ready';
    delivery.textContent = !selectedDoctor.length ? 'Nenhuma visita neste filtro de medico.'
      : pending ? `${pending} visitas precisam de conferencia antes do envio${doctor.value && doctor.value !== '__SEM_MEDICO__' ? ' ao medico selecionado' : ''}.`
      : 'Sem pendencias de conferencia neste filtro de medico.';
    if (doctor.value && doctor.value !== '__SEM_MEDICO__' && unassigned) delivery.textContent += ` ${unassigned} visitas sem medico permanecem na conferencia interna.`;
  }
  const doctorDownload = document.querySelector('#download-doctor');
  const ambiguous=cadastroCandidates(doctor.value).length>1;
  doctorDownload.hidden = !doctor.value || doctor.value === '__SEM_MEDICO__' || ambiguous;
  doctorDownload.href = `/api/conciliacoes/visitas/${reportId}/relatorio.xlsx?${new URLSearchParams(doctor.value.startsWith('id:')?{medicoId:doctor.value.slice(3)}:{medico:doctor.value})}`;
  if(ambiguous) document.querySelector('#delivery-status').textContent='Nome ambiguo no cadastro. Vincule as visitas ao medico correto na revisao antes de exportar.';
  const stats = document.querySelector('#stats'); stats.replaceChildren();
  for (const [label, value] of ['PAGA', 'PENDENTE', 'DIVERGENTE'].map(status => [status, selectedDoctor.filter(v => v.status === status).length])
    .concat([['Repasse correspondente', money.format(selectedDoctor.filter(v => v.status === 'PAGA').reduce((sum, v) => sum + Number(v.repasse ?? v.hospital?.repasse ?? 0), 0))]])) {
    const item = node('div', label); item.append(node('strong', value)); stats.append(item);
  }
  const rows = document.querySelector('#rows'); rows.replaceChildren();
  for (const visit of filtered) {
    const h = visit.hospital;
    const total = visit.camposRevisados ? visit.valorHospital : visit.valorHospital ?? h?.valorTotal;
    const repasse = visit.camposRevisados ? visit.repasse : visit.repasse ?? h?.repasse;
    const values = [visit.linhaMedico, visit.data ? visit.data.split('-').reverse().join('/') : 'Nao informada', visit.atendimento,
      visit.medico ?? h?.medico ?? visit.original.medico ?? '', visit.procedimento ?? h?.procedimento ?? visit.original['procedimento/mat-med'] ?? '',
      (visit.camposRevisados ? visit.codigoProcedimento : visit.codigoProcedimento ?? h?.codigo) || 'Nao informado',
      total == null ? '' : money.format(total), repasse == null ? '' : money.format(repasse), visit.status, visit.motivo,
      categoryLabels[progress.get(visit.linhaMedico)?.categoria] || 'Carregando...', originLabels[progress.get(visit.linhaMedico)?.origem] || 'Carregando...'];
    const row = document.createElement('tr');
    const headers = document.querySelectorAll('#results thead th');
    values.forEach((value, index) => { const cell = document.createElement('td'); cell.textContent = value;
      cell.dataset.label = headers[index].textContent; row.append(cell); });
    const action = document.createElement('td'); const button = document.createElement('button');
    action.dataset.label = 'Revisao';
    button.type = 'button'; button.className = 'review-row'; button.textContent = 'Revisar';
    button.setAttribute('aria-label', `Revisar linha ${visit.linhaMedico}`);
    button.disabled=!!reportsHistory.find(r=>String(r.id)===String(reportId))?.proxima_id;
    if(button.disabled)button.title='Relatorio historico: abra a versao mais recente para revisar';
    button.addEventListener('click', () => openReview(visit.linhaMedico));
    action.append(button); row.append(action);
    rows.append(row);
  }
  document.querySelector('#count').textContent = `${filtered.length} de ${selectedDoctor.length} visitas${doctor.value ? ' no filtro de medico' : ''}`;
}
async function loadHistory() {
  const items = await request('/api/conciliacoes/visitas');
  reportsHistory=items;
  const list = document.querySelector('#history'); list.replaceChildren();
  for (const item of items) {
    const li = document.createElement('li'); const button = document.createElement('button');
    button.type = 'button'; button.textContent = `#${item.id} - v${item.numero_versao||1} - ${item.hospital} - ${item.arquivo_medico}${item.proxima_id?' (historico; nova versao #'+item.proxima_id+')':''}`;
    button.addEventListener('click', async () => {
      try { showReport(item.id, await request(`/api/conciliacoes/visitas/${item.id}`)); message.textContent = ''; }
      catch (error) { message.textContent = error.message; }
    });
    const remove = document.createElement('button');
    remove.type = 'button'; remove.className = 'delete-report';
    remove.textContent = 'Excluir'; remove.title = `Excluir relatorio #${item.id}`;
    remove.setAttribute('aria-label', `Excluir relatorio ${item.id}`);
    remove.addEventListener('click', async () => {
      if (!confirm(`Excluir definitivamente o relatorio #${item.id} de ${item.hospital}?\nAs visitas conciliadas e todo o historico de revisoes deste relatorio serao apagados. Esta acao nao pode ser desfeita.`)) return;
      remove.disabled = true; button.disabled = true;
      try {
        await request(`/api/conciliacoes/visitas/${item.id}`, { method: 'DELETE' });
        if (String(reportId) === String(item.id)) {
          ++reviewGeneration; reviewDetail = null; reviewDialog.close(); report = null; reportId = null;
          ++progressGeneration; progress.clear();
          document.querySelector('#results').hidden = true; document.querySelector('#download').hidden = true;
          document.querySelector('#download-doctor').hidden = true;
          document.querySelector('#rows').replaceChildren(); document.querySelector('#stats').replaceChildren();
          const url = new URL(location.href); url.searchParams.delete('relatorio');
          window.history.replaceState(null, '', url);
        }
        message.textContent = `Relatorio #${item.id} excluido.`;
        await loadHistory();
      } catch (error) { message.textContent = error.message; remove.disabled = false; button.disabled = false; }
    });
    li.append(button, remove); list.append(li);
  }
  if (!items.length) list.append(node('li', 'Nenhum relatorio salvo.'));
}
form.addEventListener('submit', async event => {
  event.preventDefault(); const submit = document.querySelector('#submit'); submit.disabled = true;
  message.textContent = 'Processando planilhas...';
  try {
    const data=await window.FaturamedVersoes.prepare(form);
    if(!data){message.textContent='Importacao cancelada.';return;}
    const result = await request('/api/conciliacoes/visitas', { method: 'POST', body: data });
    showReport(result.id, result.relatorio);
    message.textContent = window.FaturamedVersoes.message(result);
    await loadHistory();
    await window.FaturamedVersoes.load();
  } catch (error) { message.textContent = error.message; }
  finally { submit.disabled = false; }
});
document.querySelector('#status').addEventListener('change', renderRows);
document.querySelector('#doctor-filter').addEventListener('change', renderRows);
document.querySelector('#origin-filter').addEventListener('change', renderRows);
document.querySelector('#category-filter').addEventListener('change', renderRows);
document.querySelector('#search').addEventListener('input', renderRows);
async function initialize() {
  try { medicosCadastro = await request('/api/medicos'); }
  catch (error) { message.textContent = `Cadastro de medicos indisponivel: ${error.message}`; }
  await loadHistory();
  const params = new URLSearchParams(location.search);
  const id = params.get('relatorio');
  if (id && /^[0-9]+$/.test(id)) {
    document.querySelector('#search').value = params.get('atendimento') || '';
    const status = params.get('status');
    if (['PAGA', 'PENDENTE', 'DIVERGENTE'].includes(status)) document.querySelector('#status').value = status;
    showReport(id, await request(`/api/conciliacoes/visitas/${id}`));
  }
}
initialize().catch(error => { message.textContent = error.message; });

function node(tag, text) {
  const result = document.createElement(tag); if (text != null) result.textContent = text; return result;
}
async function openReview(line) {
  const token = ++reviewGeneration;
  reviewLine = line; reviewDetail = null;
  const id = reportId;
  const savedOwner = document.querySelector('#review-owner').value;
  document.querySelector('#review-form').reset();
  document.querySelector('#review-owner').value = savedOwner;
  document.querySelector('#review-save').disabled = true;
  document.querySelector('#review-reload').hidden = true;
  document.querySelector('#review-message').textContent = 'Carregando detalhes...';
  for (const selector of ['#review-summary', '#review-candidates', '#review-history', '#review-group']) document.querySelector(selector).replaceChildren();
  document.querySelector('#review-candidate-message').textContent = '';
  document.querySelector('#review-title').textContent = `Revisar linha ${line}`;
  if (!reviewDialog.open) reviewDialog.showModal();
  try {
    const detail = await request(`/api/conciliacoes/visitas/${id}/visitas/${line}/revisao`);
    if (token !== reviewGeneration || !reviewDialog.open || id !== reportId) return;
    reviewDetail = detail;
    const current = detail.atual;
    const doctorSelect=document.querySelector('#edit-doctor-id');
    doctorSelect.replaceChildren(new Option('Sem vinculo ao cadastro',''),...medicosCadastro.filter(m=>m.ativo||m.id===current.medicoCadastroId).map(m=>{
      const option=new Option(`${m.nome} - CRM ${m.crm}/${m.uf}${m.ativo?'':' (inativo)'}`,m.id);option.disabled=!m.ativo;return option;
    }));
    doctorSelect.value=doctorId(current)??'';
    const group = report.visitas.filter(v => v.atendimento === current.atendimento);
    document.querySelector('#review-group-title').textContent = `Atendimento ${current.atendimento} - ${group.length} visitas`;
    for (const visit of group) {
      const row = node('tr');
      if (visit.linhaMedico === line) row.className = 'review-current';
      for (const value of [visit.linhaMedico, visit.data ? visit.data.split('-').reverse().join('/') : 'Nao informada',
        visit.medico || 'Nao identificado', visit.status, visit.hospital ? `Linha ${visit.hospital.linha}` : 'Sem vinculo']) row.append(node('td', value));
      const cell = node('td'); const button = node('button', visit.linhaMedico === line ? 'Em revisao' : 'Revisar');
      button.type = 'button'; button.disabled = visit.linhaMedico === line;
      button.setAttribute('aria-label', `Revisar visita do grupo linha ${visit.linhaMedico}`);
      button.addEventListener('click', () => { if (!reviewSaving && confirm('Trocar de visita? Alteracoes nao salvas serao descartadas.')) openReview(visit.linhaMedico); });
      cell.append(button); row.append(cell); document.querySelector('#review-group').append(row);
    }
    for (const [id, value] of [['edit-date', current.data], ['edit-attendance', current.atendimento],
      ['edit-doctor', current.medico], ['edit-procedure', current.procedimento], ['edit-code', current.codigoProcedimento],
      ['edit-value', current.valorHospital], ['edit-repasse', current.repasse], ['edit-status', current.status], ['edit-reason', current.motivo]]) {
      document.querySelector(`#${id}`).value = value ?? '';
    }
    document.querySelector('#review-title').textContent = `Atendimento ${detail.atual.atendimento} - linha ${line}`;
    const original = detail.original;
    const summary = document.querySelector('#review-summary');
    for (const [label, value] of [['Data da visita', original.data ? original.data.split('-').reverse().join('/') : 'Nao informada'],
      ['Status automatico', original.status], ['Status atual', detail.atual.status],
      ['Medico na producao', original.original.medico || 'Nao informado'],
      ['Procedimento na producao', original.original['procedimento/mat-med'] || 'Nao informado'],
      ['Valor na producao', original.original['valor orig'] || 'Nao informado']]) {
      const item = node('div'); item.append(node('dt', label), node('dd', value)); summary.append(item);
    }
    const candidates = document.querySelector('#review-candidates');
    for (const candidate of detail.candidatos) {
      const h = candidate.registro; const row = node('tr'); const choice = node('td');
      const radio = node('input'); radio.type = 'radio'; radio.name = 'linhaHospital'; radio.value = h.linha;
      radio.dataset.reserved = candidate.associadoALinha != null ? 'true' : 'false';
      radio.title = candidate.associadoALinha != null ? `Associado a linha ${candidate.associadoALinha}` : `Selecionar linha ${h.linha}`;
      radio.checked = detail.atual.hospital?.linha === h.linha;
      radio.setAttribute('aria-label', `Selecionar linha hospital ${h.linha}`); choice.append(radio); row.append(choice);
      for (const value of [h.linha, h.data.split('-').reverse().join('/'), h.medico, h.paciente, `${h.codigo} - ${h.procedimento}`, h.conta,
        h.valorTotal == null ? 'Nao informado' : money.format(h.valorTotal), h.repasse == null ? 'Nao informado' : money.format(h.repasse),
        h.setor, candidate.associadoALinha != null ? `Associado a linha ${candidate.associadoALinha}` : 'Disponivel']) row.append(node('td', value));
      candidates.append(row);
    }
    document.querySelector('#review-candidate-message').textContent = detail.candidatos.length
      ? original.motivo : 'Nenhum registro hospitalar para este atendimento e data.';
    const confirmOption = document.querySelector('#review-action [value=CONFIRMAR_CORRESPONDENCIA]');
    confirmOption.disabled = !detail.candidatos.some(candidate => candidate.associadoALinha == null);
    document.querySelector('#review-action').value = 'AJUSTAR_DADOS';
    const historyList = document.querySelector('#review-history');
    for (const review of detail.historico.slice().reverse()) {
      const item = node('li');
      item.append(node('strong', actionLabels[review.acao]), node('p', `${review.antes.status} -> ${review.depois.status}`),
        node('p', `${review.responsavel} - ${new Date(review.criadoEm).toLocaleString('pt-BR')}`), node('p', review.justificativa));
      item.append(node('p', review.depois.hospital
        ? `Registro hospitalar: linha ${review.depois.hospital.linha} - ${review.depois.hospital.medico}`
        : 'Sem registro hospitalar associado'));
      const changes = [];
      for (const [key, label] of [['data', 'Data'], ['atendimento', 'Atendimento'], ['medico', 'Medico'],
        ['procedimento', 'Procedimento'], ['codigoProcedimento', 'Codigo'], ['valorHospital', 'Valor hospital'], ['repasse', 'Repasse'], ['medicoCadastroId','Cadastro medico']]) {
        if (review.antes[key] !== review.depois[key]) changes.push(`${label}: ${review.antes[key] ?? 'Nao informado'} -> ${review.depois[key] ?? 'Nao informado'}`);
      }
      for (const change of changes) item.append(node('p', change));
      historyList.append(item);
    }
    if (!detail.historico.length) historyList.append(node('li', 'Nenhuma revisao registrada.'));
    document.querySelector('#review-message').textContent = '';
    document.querySelector('#review-save').disabled = false;
    updateReviewAction();
  } catch (error) {
    if (token !== reviewGeneration) return;
    document.querySelector('#review-message').textContent = error.message;
    document.querySelector('#review-reload').hidden = false;
  }
}
function updateReviewAction() {
  const action = document.querySelector('#review-action').value;
  const editing = action === 'AJUSTAR_DADOS';
  const confirm = action === 'CONFIRMAR_CORRESPONDENCIA' || editing;
  document.querySelector('#review-edit-fields').hidden = !editing;
  document.querySelector('#review-edit-fields').disabled = !editing;
  document.querySelectorAll('[name=linhaHospital]').forEach(radio => {
    radio.disabled = !confirm || radio.dataset.reserved === 'true';
  });
}
document.querySelector('#edit-search-candidates').addEventListener('click', async () => {
  if (!reviewDetail || reviewSaving) return;
  const atendimento = document.querySelector('#edit-attendance').value.trim();
  const data = document.querySelector('#edit-date').value;
  if (!atendimento) { document.querySelector('#review-message').textContent = 'Informe atendimento.'; return; }
  const token = ++reviewGeneration;
  const activeDetail = reviewDetail;
  document.querySelector('#review-save').disabled = true;
  document.querySelector('#review-message').textContent = 'Buscando correspondencias...';
  try {
    const detail = await request(`/api/conciliacoes/visitas/${reportId}/visitas/${reviewLine}/revisao?${new URLSearchParams(data ? { atendimento, data } : { atendimento })}`);
    if (token !== reviewGeneration || !reviewDialog.open || reviewDetail !== activeDetail) return;
    // Uma busca nao atualiza a versao da decisao que o operador abriu.
    if (detail.versao !== reviewDetail.versao) throw Object.assign(new Error('A conciliacao foi revisada. Atualize os detalhes.'), { status: 409 });
    reviewDetail.candidatos = detail.candidatos;
    const table = document.querySelector('#review-candidates'); table.replaceChildren();
    for (const candidate of detail.candidatos) {
      const h = candidate.registro; const row = node('tr'); const cell = node('td'); const radio = node('input');
      radio.type = 'radio'; radio.name = 'linhaHospital'; radio.value = h.linha;
      radio.dataset.reserved = candidate.associadoALinha != null ? 'true' : 'false';
      radio.setAttribute('aria-label', `Selecionar linha hospital ${h.linha}`); cell.append(radio); row.append(cell);
      for (const value of [h.linha, h.data.split('-').reverse().join('/'), h.medico, h.paciente, `${h.codigo} - ${h.procedimento}`, h.conta,
        h.valorTotal == null ? 'Nao informado' : money.format(h.valorTotal), h.repasse == null ? 'Nao informado' : money.format(h.repasse),
        h.setor, candidate.associadoALinha != null ? `Associado a linha ${candidate.associadoALinha}` : 'Disponivel']) row.append(node('td', value));
      table.append(row);
    }
    document.querySelector('#review-candidate-message').textContent = `${detail.candidatos.length} registros para atendimento e data informados.`;
    document.querySelector('#review-message').textContent = '';
    updateReviewAction();
  } catch (error) {
    if (token !== reviewGeneration || !reviewDialog.open) return;
    document.querySelector('#review-message').textContent = error.message;
    if (error.status === 409) { reviewDetail = null; document.querySelector('#review-reload').hidden = false; }
  } finally {
    if (token === reviewGeneration) document.querySelector('#review-save').disabled = !reviewDetail;
  }
});
document.querySelector('#review-action').addEventListener('change', updateReviewAction);
document.querySelector('#edit-doctor-id').addEventListener('change',event=>{
  const medico=medicosCadastro.find(m=>String(m.id)===event.target.value);
  if(medico) document.querySelector('#edit-doctor').value=medico.nome;
});
document.querySelector('#edit-doctor').addEventListener('input',()=>document.querySelector('#edit-doctor-id').value='');
document.querySelector('#edit-fill-candidate').addEventListener('click', () => {
  const selected = document.querySelector('[name=linhaHospital]:checked:not(:disabled)');
  const hospital = reviewDetail?.candidatos.find(c => String(c.registro.linha) === selected?.value)?.registro;
  if (!hospital) { document.querySelector('#review-message').textContent = 'Selecione um registro do hospital.'; return; }
  for (const [id, value] of [['edit-date', hospital.data], ['edit-attendance', hospital.atendimento],
    ['edit-doctor', hospital.medico], ['edit-procedure', hospital.procedimento], ['edit-code', hospital.codigo],
    ['edit-value', hospital.valorTotal], ['edit-repasse', hospital.repasse]]) document.querySelector(`#${id}`).value = value ?? '';
  document.querySelector('#edit-doctor-id').value=doctorId({medico:hospital.medico,original:{}})??'';
  document.querySelector('#edit-status').value = hospital.setor.trim().toLowerCase() === 'faturado' ? 'PAGA' : 'PENDENTE';
  document.querySelector('#edit-reason').value = `Dados conferidos com registro hospitalar da linha ${hospital.linha}`;
  document.querySelector('#review-message').textContent = '';
});
document.querySelector('#review-close').addEventListener('click', () => { reviewGeneration++; reviewDialog.close(); });
reviewDialog.addEventListener('cancel', event => { if (reviewSaving) event.preventDefault(); else reviewGeneration++; });
document.querySelector('#review-reload').addEventListener('click', () => openReview(reviewLine));
document.querySelector('#review-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (!reviewDetail || reviewSaving) return;
  const acao = document.querySelector('#review-action').value;
  const selected = document.querySelector('[name=linhaHospital]:checked:not(:disabled)');
  if (acao === 'CONFIRMAR_CORRESPONDENCIA' && !selected) {
    document.querySelector('#review-message').textContent = 'Selecione um registro do hospital.'; return;
  }
  const id = reportId; const line = reviewLine;
  reviewSaving = true;
  for (const selector of ['#review-save', '#review-close', '#review-reload']) document.querySelector(selector).disabled = true;
  document.querySelector('#review-message').textContent = 'Salvando revisao...';
  try {
    const adjusted = acao === 'AJUSTAR_DADOS';
    const amount = id => document.querySelector(id).value === '' ? null : Number(document.querySelector(id).value);
    const result = await request(`/api/conciliacoes/visitas/${id}/visitas/${line}/revisao`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ acao,
        linhaHospital: selected && (acao === 'CONFIRMAR_CORRESPONDENCIA' || adjusted) ? Number(selected.value) : null,
        responsavel: document.querySelector('#review-owner').value,
        justificativa: document.querySelector('#review-justification').value, versao: reviewDetail.versao,
        ajuste: adjusted ? { data: document.querySelector('#edit-date').value || null, atendimento: document.querySelector('#edit-attendance').value,
          medico: document.querySelector('#edit-doctor').value, procedimento: document.querySelector('#edit-procedure').value,
          codigoProcedimento: document.querySelector('#edit-code').value, valorHospital: amount('#edit-value'), repasse: amount('#edit-repasse'),
          status: document.querySelector('#edit-status').value, motivo: document.querySelector('#edit-reason').value,
          medicoCadastroId: document.querySelector('#edit-doctor-id').value?Number(document.querySelector('#edit-doctor-id').value):null } : null }) });
    showReport(id, result.relatorio); reviewDialog.close();
    message.textContent = `Revisao da linha ${line} salva. ${result.automaticas || 0} visitas preenchidas automaticamente. Indicadores e relatorio atualizados.`;
  } catch (error) {
    document.querySelector('#review-message').textContent = error.message;
    if (error.status === 409) {
      reviewDetail = null; document.querySelector('#review-reload').hidden = false;
    }
  } finally {
    reviewSaving = false;
    document.querySelector('#review-save').disabled = !reviewDetail;
    document.querySelector('#review-close').disabled = false;
    document.querySelector('#review-reload').disabled = false;
  }
});
