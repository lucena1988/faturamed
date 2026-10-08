const $ = selector => document.querySelector(selector);
const labels = { PAGA: 'Paga', PENDENTE: 'Pendente', DIVERGENTE: 'Divergente' };
const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const integer = new Intl.NumberFormat('pt-BR');
const percentage = new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 1 });
const viewTitles = { dashboard: 'Dashboard', importacoes: 'Importacoes', divergencias: 'Divergencias', layouts: 'Layouts' };
let reports = [];
let report = null;
let reportId = null;
let chartMode = 'status';
let generation = 0;
let controller;

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text != null) node.textContent = text;
  if (className) node.className = className;
  return node;
}
function options(select, entries, selected) {
  select.replaceChildren(...entries.map(([value, label]) => new Option(label, value)));
  if (entries.some(([value]) => String(value) === String(selected))) select.value = selected;
  select.disabled = entries.length === 0;
}
async function request(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.erro || `Falha ao carregar os dados (${response.status})`);
  }
  return response.json();
}
function currentVisits() {
  const period = $('#period-filter').value;
  return (report?.visitas || []).filter(visit => !period || (period === 'SEM_DATA' ? !visit.data : visit.data?.startsWith(period)));
}
function attentionVisits() {
  return currentVisits().filter(visit => visit.status !== 'PAGA').sort((a, b) =>
    (a.status === 'DIVERGENTE' ? 0 : 1) - (b.status === 'DIVERGENTE' ? 0 : 1)
    || (effectiveRepasse(b) ?? -1) - (effectiveRepasse(a) ?? -1) || a.linhaMedico - b.linhaMedico);
}
function effectiveRepasse(visit) {
  return visit.camposRevisados ? visit.repasse : visit.repasse ?? visit.hospital?.repasse;
}
function uniqueHospitalRows(rows) {
  return [...new Map(rows.map(row => [row.linha, row])).values()];
}
function knownRepasse(rows) {
  return uniqueHospitalRows(rows).filter(row => row.repasse != null && Number.isFinite(Number(row.repasse)));
}
function renderValues(visits) {
  const allHospital = uniqueHospitalRows([...report.hospitalSemProducao,
    ...report.visitas.flatMap(visit => visit.candidatos)]);
  const knownTotal = knownRepasse(allHospital);
  const allocated = new Map(report.visitas.filter(visit => visit.hospital)
    .map(visit => [visit.hospital.linha, visit.linhaMedico]));
  $('#kpi-repasse').textContent = knownTotal.length
    ? currency.format(knownTotal.reduce((sum, row) => sum + Number(row.repasse), 0)) : 'Nao informado';
  $('#kpi-repasse-detail').textContent = knownTotal.length < allHospital.length
    ? `Total informado; ${allHospital.length - knownTotal.length} registros sem repasse`
    : 'Total da planilha do hospital';
  for (const [status, suffix] of [['PAGA', 'pagas'], ['PENDENTE', 'pendentes'], ['DIVERGENTE', 'divergentes']]) {
    const selected = visits.filter(visit => visit.status === status);
    const candidatesForValue = visit => (visit.hospital ? [visit.hospital] : visit.candidatos)
      .filter(row => !allocated.has(row.linha) || allocated.get(row.linha) === visit.linhaMedico);
    const rows = knownRepasse(selected.filter(visit => !visit.camposRevisados).flatMap(candidatesForValue));
    const corrected = selected.filter(visit => visit.camposRevisados && effectiveRepasse(visit) != null);
    const missing = selected.filter(visit => {
      if (visit.camposRevisados) return effectiveRepasse(visit) == null;
      const candidates = candidatesForValue(visit);
      return !candidates.length || candidates.some(row => row.repasse == null || !Number.isFinite(Number(row.repasse)));
    }).length;
    $(`#kpi-valor-${suffix}`).textContent = rows.length || corrected.length || !selected.length
      ? currency.format(rows.reduce((sum, row) => sum + Number(row.repasse), 0)
        + corrected.reduce((sum, visit) => sum + Number(effectiveRepasse(visit)), 0)) : 'Nao informado';
    $(`#kpi-valor-${suffix}-detail`).textContent = missing
      ? `${missing} visitas sem valor informado`
      : selected.some(visit => visit.camposRevisados) ? 'Repasse com ajustes da revisao'
      : status === 'DIVERGENTE' ? 'Repasse dos registros em conferencia' : 'Repasse informado pelo hospital';
  }
}
function clearData(message) {
  report = null;
  for (const id of ['kpi-repasse', 'kpi-total', 'kpi-pagas', 'kpi-pendentes', 'kpi-divergentes',
    'kpi-valor-pagas', 'kpi-valor-pendentes', 'kpi-valor-divergentes',
    'legend-pagas', 'legend-pendentes', 'legend-divergentes', 'paid-percent', 'imports-count']) $(`#${id}`).textContent = '...';
  for (const suffix of ['pagas', 'pendentes', 'divergentes']) $(`#kpi-valor-${suffix}-detail`).textContent = '';
  $('#kpi-total-detail').textContent = '';
  $('#kpi-repasse-detail').textContent = 'Total da planilha do hospital';
  $('#kpi-pagas-detail').textContent = '';
  $('#dashboard-message').textContent = message;
  for (const id of ['priority-table', 'divergence-table', 'imports-table', 'audit-chart']) $(`#${id}`).replaceChildren();
  $('#status-donut').style.background = 'var(--line)';
  $('#period-filter').disabled = true;
  $('#queue-count').textContent = '';
  $('#imports-context').textContent = '';
  $('#chart-context').textContent = '';
}
async function loadReports(preferredId) {
  controller?.abort(); controller = new AbortController();
  const signal = controller.signal;
  const token = ++generation;
  clearData('Carregando conciliacoes...');
  $('#unit-filter').disabled = true; $('#report-filter').disabled = true;
  try {
    const items = await request('/api/conciliacoes/visitas', { signal });
    if (token !== generation) return;
    reports = items;
    const selected = reports.find(item => String(item.id) === String(preferredId ?? reportId))
      || (reportId === '' && preferredId == null ? null : reports[0]);
    const hospitals = [...new Set(reports.map(item => item.hospital))];
    options($('#unit-filter'), [['', 'Selecione um hospital'], ...hospitals.map(name => [name, name])], selected?.hospital ?? '');
    populateReports(selected?.id);
    if (selected) await loadReport(selected.id);
    else {
      clearSelection(reports.length ? 'Selecione hospital e relatorio.' : 'Nenhuma conciliacao salva.');
      for (const id of ['kpi-total', 'kpi-pagas', 'kpi-pendentes', 'kpi-divergentes', 'legend-pagas', 'legend-pendentes', 'legend-divergentes']) $(`#${id}`).textContent = '0';
      $('#kpi-repasse').textContent = currency.format(0);
      for (const suffix of ['pagas', 'pendentes', 'divergentes']) $(`#kpi-valor-${suffix}`).textContent = currency.format(0);
      $('#paid-percent').textContent = '0%';
      $('#imports-count').textContent = '0 arquivos';
    }
  } catch (error) {
    if (error.name === 'AbortError' || token !== generation) return;
    clearData(error.message); $('#report-context').textContent = 'Dados indisponiveis';
  }
}
function populateReports(selected) {
  const items = reports.filter(item => item.hospital === $('#unit-filter').value);
  options($('#report-filter'), [['', 'Selecione um relatorio'], ...items.map(item => [item.id, `#${item.id} - ${item.arquivo_medico}`])], selected ?? '');
}
function clearSelection(message = 'Selecione hospital e relatorio.') {
  controller?.abort(); ++generation; reportId = '';
  clearData(message); options($('#period-filter'), [['', 'Todos os meses']], '');
  $('#period-filter').disabled = true;
  $('#report-context').textContent = 'Nenhum relatorio selecionado';
  for (const id of ['kpi-repasse', 'kpi-total', 'kpi-pagas', 'kpi-pendentes', 'kpi-divergentes',
    'kpi-valor-pagas', 'kpi-valor-pendentes', 'kpi-valor-divergentes', 'legend-pagas', 'legend-pendentes',
    'legend-divergentes', 'paid-percent', 'imports-count']) $(`#${id}`).textContent = '-';
}
async function loadReport(id) {
  if (!id) { clearSelection(); return; }
  controller?.abort(); controller = new AbortController();
  const token = ++generation;
  clearData('Carregando resultados...');
  try {
    const data = await request(`/api/conciliacoes/visitas/${id}`, { signal: controller.signal });
    if (token !== generation) return;
    report = data; reportId = id;
    const previousPeriod = $('#period-filter').value;
    const periods = [...new Set(data.visitas.filter(visit => visit.data).map(visit => visit.data.slice(0, 7)))].sort().reverse();
    const undated = data.visitas.some(visit => !visit.data) ? [['SEM_DATA', 'Sem data informada']] : [];
    options($('#period-filter'), [['', 'Todos os meses'], ...undated, ...periods.map(period => [period,
      new Date(`${period}-01T12:00:00`).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' })])],
      periods.includes(previousPeriod) || (undated.length && previousPeriod === 'SEM_DATA') ? previousPeriod : '');
    $('#dashboard-message').textContent = '';
    renderDashboard();
  } catch (error) {
    if (error.name === 'AbortError' || token !== generation) return;
    clearData(error.message); $('#report-context').textContent = 'Dados indisponiveis';
  }
}
function renderDashboard() {
  if (!report) return;
  const visits = currentVisits();
  const counts = Object.fromEntries(Object.keys(labels).map(status => [status, visits.filter(visit => visit.status === status).length]));
  const paidRate = visits.length ? counts.PAGA / visits.length : 0;
  $('#report-context').textContent = `${report.hospital} - Conciliacao #${reportId}`;
  renderValues(visits);
  $('#kpi-total').textContent = integer.format(visits.length);
  $('#kpi-total-detail').textContent = `${new Set(visits.map(v => v.atendimento)).size} atendimentos`;
  $('#kpi-pagas-detail').textContent = `${percentage.format(paidRate)} das visitas`;
  for (const [status, suffix] of [['PAGA', 'pagas'], ['PENDENTE', 'pendentes'], ['DIVERGENTE', 'divergentes']]) {
    $(`#kpi-${suffix}`).textContent = integer.format(counts[status]);
    $(`#legend-${suffix}`).textContent = integer.format(counts[status]);
  }
  $('#paid-percent').textContent = percentage.format(paidRate);
  const green = paidRate * 100;
  const amber = green + (visits.length ? counts.PENDENTE / visits.length * 100 : 0);
  $('#status-donut').style.background = visits.length
    ? `conic-gradient(var(--green) 0 ${green}%, var(--amber) ${green}% ${amber}%, var(--rose) ${amber}% 100%)` : 'var(--line)';
  $('#queue-count').textContent = `${counts.DIVERGENTE + counts.PENDENTE} visitas para conferencia`;
  fillAttentionTable($('#priority-table'), attentionVisits().slice(0, 10), false);
  const reasons = [...new Set(attentionVisits().map(visit => visit.motivo))];
  options($('#type-filter'), [['TODOS', 'Todos os motivos'], ...reasons.map(reason => [reason, reason])], $('#type-filter').value);
  renderDivergenceTable();
  renderChart();
  renderImports();
}
function renderChart() {
  if (!report) return;
  const groups = new Map();
  for (const visit of currentVisits()) {
    const label = chartMode === 'status' ? labels[visit.status]
      : chartMode === 'medico' ? (visit.medico ?? visit.hospital?.medico ?? visit.original.medico) || 'Nao identificado'
      : visit.motivo;
    groups.set(label, (groups.get(label) || 0) + 1);
  }
  const chart = $('#audit-chart'); chart.replaceChildren();
  const maximum = Math.max(1, ...groups.values());
  for (const [label, count] of [...groups].sort((a, b) => b[1] - a[1])) {
    const row = element('div', null, 'chart-row');
    const bar = element('div', null, 'bar');
    const fill = element('i'); fill.style.width = `${count / maximum * 100}%`; bar.append(fill);
    row.append(element('span', label), bar, element('strong', integer.format(count))); chart.append(row);
  }
  $('#chart-context').textContent = `${integer.format(currentVisits().length)} visitas`;
  if (!groups.size) chart.append(element('p', 'Sem visitas neste periodo.'));
}
function fillAttentionTable(table, visits, detailed) {
  table.replaceChildren();
  for (const visit of visits) {
    const row = element('tr');
    const value = effectiveRepasse(visit);
    const cells = detailed ? [visit.atendimento, visit.data ? visit.data.split('-').reverse().join('/') : 'Nao informada',
      (visit.procedimento ?? visit.hospital?.procedimento ?? visit.original['procedimento/mat-med']) || 'Nao identificado', visit.motivo,
      value == null ? 'Nao informado' : currency.format(value)]
      : [visit.atendimento, (visit.procedimento ?? visit.hospital?.procedimento ?? visit.original['procedimento/mat-med']) || 'Nao identificado',
        visit.motivo, (visit.medico ?? visit.hospital?.medico ?? visit.original.medico) || 'Nao identificado',
        value == null ? 'Nao informado' : currency.format(value)];
    for (const value of cells) row.append(element('td', value));
    const status = element('td');
    status.append(element('span', labels[visit.status], `pill ${visit.status === 'DIVERGENTE' ? 'danger' : 'warning'}`));
    row.append(status);
    const url = `conciliacao.html?relatorio=${encodeURIComponent(reportId)}&atendimento=${encodeURIComponent(visit.atendimento)}&status=${visit.status}`;
    const link = element('a', detailed ? 'Abrir' : visit.atendimento);
    link.href = url;
    if (detailed) { const action = element('td'); action.append(link); row.append(action); }
    else row.firstChild.replaceChildren(link);
    table.append(row);
  }
  if (!visits.length) {
    const row = element('tr'); const cell = element('td', 'Nenhum item para conferencia.');
    cell.colSpan = detailed ? 7 : 6; row.append(cell); table.append(row);
  }
}
function renderDivergenceTable() {
  if (!report) return;
  const type = $('#type-filter').value;
  const status = $('#status-filter').value;
  fillAttentionTable($('#divergence-table'), attentionVisits().filter(visit =>
    (type === 'TODOS' || visit.motivo === type) && (status === 'TODOS' || visit.status === status)), true);
}
function renderImports() {
  const metadata = reports.find(item => String(item.id) === String(reportId));
  const hospitalRows = new Set(report.hospitalSemProducao.map(row => row.linha));
  for (const visit of report.visitas) for (const candidate of visit.candidatos) hospitalRows.add(candidate.linha);
  const received = metadata ? new Date(metadata.criado_em).toLocaleString('pt-BR') : '';
  const rows = [[report.arquivoMedico, 'PRODUCAO', report.visitas.length],
    [report.arquivoHospital, 'FATURAMENTO', hospitalRows.size]];
  const table = $('#imports-table'); table.replaceChildren();
  for (const values of rows) {
    const row = element('tr');
    for (const value of [...values, 'Processada', received]) row.append(element('td', value));
    table.append(row);
  }
  $('#imports-count').textContent = '2 arquivos';
  $('#imports-context').textContent = `Conciliacao #${reportId} - ${report.hospital}`;
}
function showView(view) {
  if (!viewTitles[view]) view = 'dashboard';
  document.querySelectorAll('.view').forEach(section => section.classList.toggle('active', section.dataset.view === view));
  document.querySelectorAll('[data-view-link]').forEach(button => button.classList.toggle('active', button.dataset.viewLink === view));
  $('#view-title').textContent = viewTitles[view];
}
function showToast(message) {
  $('#toast').textContent = message; $('#toast').classList.add('visible');
  setTimeout(() => $('#toast').classList.remove('visible'), 2500);
}
document.querySelectorAll('[data-view-link]').forEach(button => button.addEventListener('click', () => showView(button.dataset.viewLink)));
$('#refresh-button').addEventListener('click', () => loadReports());
$('#unit-filter').addEventListener('change', () => { populateReports(); loadReport($('#report-filter').value); });
$('#report-filter').addEventListener('change', () => loadReport($('#report-filter').value));
$('#period-filter').addEventListener('change', renderDashboard);
$('#type-filter').addEventListener('change', renderDivergenceTable);
$('#status-filter').addEventListener('change', renderDivergenceTable);
document.querySelectorAll('[data-chart]').forEach(button => button.addEventListener('click', () => {
  chartMode = button.dataset.chart;
  document.querySelectorAll('[data-chart]').forEach(tab => {
    tab.classList.toggle('active', tab === button); tab.setAttribute('aria-selected', String(tab === button));
  });
  renderChart();
}));
$('#import-form').addEventListener('submit', async event => {
  event.preventDefault(); const form = event.currentTarget;
  const submit = form.querySelector('[type=submit]'); submit.disabled = true;
  $('#import-message').textContent = 'Processando planilhas...';
  try {
    const result = await request('/api/conciliacoes/visitas', { method: 'POST', body: new FormData(form) });
    await loadReports(result.id);
    $('#import-message').textContent = `Conciliacao #${result.id} salva.`;
    showView('dashboard'); showToast('Conciliacao concluida');
    form.reset();
  } catch (error) { $('#import-message').textContent = error.message; }
  finally { submit.disabled = false; }
});

const layoutFields = [['A', 'data_atendimento', 'dd/MM/yyyy'], ['B', 'paciente_nome', 'texto'],
  ['C', 'paciente_documento', 'CPF/carteirinha'], ['D', 'procedimento_codigo', 'codigo TUSS'],
  ['E', 'procedimento_nome', 'texto'], ['F', 'medico_nome', 'texto'], ['G', 'quantidade', 'numero'], ['H', 'valor_total', 'moeda']];
function renderLayoutBoard() {
  const board = $('#layout-board'); board.replaceChildren();
  for (const [column, field, type] of layoutFields) {
    const tile = element('article', null, 'field-tile'); const header = element('header');
    header.append(element('span', column, 'column-letter'), element('span', 'obrigatorio', 'badge'));
    tile.append(header, element('strong', field), element('small', type)); board.append(tile);
  }
}
$('#add-column').addEventListener('click', () => {
  layoutFields.push([String.fromCharCode(65 + layoutFields.length), 'novo_campo', 'texto']);
  renderLayoutBoard(); showToast('Campo adicionado ao layout');
});
renderLayoutBoard();
showView(location.hash.slice(1));
loadReports();
