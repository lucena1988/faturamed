const form = document.querySelector('#conciliation-form');
const message = document.querySelector('#message');
const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 5 });
let report;
let reportId;
let reviewDetail;
let reviewLine;
let reviewSaving = false;
let reviewGeneration = 0;
const reviewDialog = document.querySelector('#review-dialog');
const actionLabels = { CONFIRMAR_CORRESPONDENCIA: 'Correspondencia confirmada', MANTER_PENDENTE: 'Mantida pendente',
  MANTER_DIVERGENTE: 'Mantida divergente', RESTAURAR_AUTOMATICO: 'Resultado automatico restaurado', AJUSTAR_DADOS: 'Dados da visita ajustados' };
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
function showReport(id, data) {
  reportId = id;
  report = data;
  document.querySelector('#results').hidden = false;
  const download = document.querySelector('#download');
  download.href = `/api/conciliacoes/visitas/${id}/relatorio.xlsx`;
  download.hidden = false;
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
  const filtered = report.visitas.filter(v => (!status || v.status === status) && v.atendimento.includes(search));
  const rows = document.querySelector('#rows'); rows.replaceChildren();
  for (const visit of filtered) {
    const h = visit.hospital;
    const total = visit.camposRevisados ? visit.valorHospital : visit.valorHospital ?? h?.valorTotal;
    const repasse = visit.camposRevisados ? visit.repasse : visit.repasse ?? h?.repasse;
    const values = [visit.linhaMedico, visit.data ? visit.data.split('-').reverse().join('/') : 'Nao informada', visit.atendimento,
      visit.medico ?? h?.medico ?? visit.original.medico ?? '', visit.procedimento ?? h?.procedimento ?? visit.original['procedimento/mat-med'] ?? '',
      total == null ? '' : money.format(total), repasse == null ? '' : money.format(repasse), visit.status, visit.motivo];
    const row = document.createElement('tr');
    for (const value of values) { const cell = document.createElement('td'); cell.textContent = value; row.append(cell); }
    const action = document.createElement('td'); const button = document.createElement('button');
    button.type = 'button'; button.className = 'review-row'; button.textContent = 'Revisar';
    button.setAttribute('aria-label', `Revisar linha ${visit.linhaMedico}`);
    button.addEventListener('click', () => openReview(visit.linhaMedico));
    action.append(button); row.append(action);
    rows.append(row);
  }
  document.querySelector('#count').textContent = `${filtered.length} de ${report.visitas.length} visitas`;
}
async function loadHistory() {
  const items = await request('/api/conciliacoes/visitas');
  const list = document.querySelector('#history'); list.replaceChildren();
  for (const item of items) {
    const li = document.createElement('li'); const button = document.createElement('button');
    button.type = 'button'; button.textContent = `#${item.id} - ${item.hospital} - ${item.arquivo_medico}`;
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
          document.querySelector('#results').hidden = true; document.querySelector('#download').hidden = true;
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
    const result = await request('/api/conciliacoes/visitas', { method: 'POST', body: new FormData(form) });
    showReport(result.id, result.relatorio);
    message.textContent = `Relatorio #${result.id} salvo. ${result.relatorio.visitas.length} visitas processadas.`;
    await loadHistory();
  } catch (error) { message.textContent = error.message; }
  finally { submit.disabled = false; }
});
document.querySelector('#status').addEventListener('change', renderRows);
document.querySelector('#search').addEventListener('input', renderRows);
async function initialize() {
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
  for (const selector of ['#review-summary', '#review-candidates', '#review-history']) document.querySelector(selector).replaceChildren();
  document.querySelector('#review-candidate-message').textContent = '';
  document.querySelector('#review-title').textContent = `Revisar linha ${line}`;
  if (!reviewDialog.open) reviewDialog.showModal();
  try {
    const detail = await request(`/api/conciliacoes/visitas/${id}/visitas/${line}/revisao`);
    if (token !== reviewGeneration || !reviewDialog.open || id !== reportId) return;
    reviewDetail = detail;
    const current = detail.atual;
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
      for (const value of [h.linha, h.medico, h.paciente, `${h.codigo} - ${h.procedimento}`, h.conta,
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
        ['procedimento', 'Procedimento'], ['codigoProcedimento', 'Codigo'], ['valorHospital', 'Valor hospital'], ['repasse', 'Repasse']]) {
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
      for (const value of [h.linha, h.medico, h.paciente, `${h.codigo} - ${h.procedimento}`, h.conta,
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
document.querySelector('#edit-fill-candidate').addEventListener('click', () => {
  const selected = document.querySelector('[name=linhaHospital]:checked:not(:disabled)');
  const hospital = reviewDetail?.candidatos.find(c => String(c.registro.linha) === selected?.value)?.registro;
  if (!hospital) { document.querySelector('#review-message').textContent = 'Selecione um registro do hospital.'; return; }
  for (const [id, value] of [['edit-date', hospital.data], ['edit-attendance', hospital.atendimento],
    ['edit-doctor', hospital.medico], ['edit-procedure', hospital.procedimento], ['edit-code', hospital.codigo],
    ['edit-value', hospital.valorTotal], ['edit-repasse', hospital.repasse]]) document.querySelector(`#${id}`).value = value ?? '';
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
          status: document.querySelector('#edit-status').value, motivo: document.querySelector('#edit-reason').value } : null }) });
    showReport(id, result.relatorio); reviewDialog.close();
    message.textContent = `Revisao da linha ${line} salva. Indicadores e relatorio atualizados.`;
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
