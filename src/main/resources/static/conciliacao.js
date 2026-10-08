const form = document.querySelector('#conciliation-form');
const message = document.querySelector('#message');
const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 5 });
let report;
async function request(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.erro || `Falha ao processar (${response.status})`);
  }
  return response.json();
}
function showReport(id, data) {
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
    const values = [visit.linhaMedico, visit.data.split('-').reverse().join('/'), visit.atendimento,
      h?.medico || visit.original.medico || '', h?.procedimento || visit.original['procedimento/mat-med'] || '',
      h?.valorTotal == null ? '' : money.format(h.valorTotal), h?.repasse == null ? '' : money.format(h.repasse), visit.status, visit.motivo];
    const row = document.createElement('tr');
    for (const value of values) { const cell = document.createElement('td'); cell.textContent = value; row.append(cell); }
    rows.append(row);
  }
  document.querySelector('#count').textContent = `${filtered.length} de ${report.visitas.length} visitas`;
}
async function history() {
  const items = await request('/api/conciliacoes/visitas');
  const list = document.querySelector('#history'); list.replaceChildren();
  for (const item of items) {
    const li = document.createElement('li'); const button = document.createElement('button');
    button.type = 'button'; button.textContent = `#${item.id} - ${item.hospital} - ${item.arquivo_medico}`;
    button.addEventListener('click', async () => {
      try { showReport(item.id, await request(`/api/conciliacoes/visitas/${item.id}`)); message.textContent = ''; }
      catch (error) { message.textContent = error.message; }
    });
    li.append(button); list.append(li);
  }
}
form.addEventListener('submit', async event => {
  event.preventDefault(); const submit = document.querySelector('#submit'); submit.disabled = true;
  message.textContent = 'Processando planilhas...';
  try {
    const result = await request('/api/conciliacoes/visitas', { method: 'POST', body: new FormData(form) });
    showReport(result.id, result.relatorio);
    message.textContent = `Relatorio #${result.id} salvo. ${result.relatorio.visitas.length} visitas processadas.`;
    await history();
  } catch (error) { message.textContent = error.message; }
  finally { submit.disabled = false; }
});
document.querySelector('#status').addEventListener('change', renderRows);
document.querySelector('#search').addEventListener('input', renderRows);
history().catch(error => { message.textContent = error.message; });
