(function (root) {
  const statuses = {PAGA: 'Faturado pelo hospital', PENDENTE: 'Aguardando faturamento', DIVERGENTE: 'Em conferencia'};
  const normalize = name => (name || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().replace(/\s+/g, ' ').toLowerCase();
  const nameOf = visit => visit.medico ?? visit.hospital?.medico ?? visit.original?.medico ?? '';
  const keyOf = visit => visit.medicoCadastroId != null ? `id:${visit.medicoCadastroId}` : normalize(nameOf(visit));
  function valueOf(visit) {
    // Ambiguous hospital candidates are not confirmed amounts for this doctor.
    const value = visit.camposRevisados ? visit.repasse : visit.hospital ? visit.repasse ?? visit.hospital.repasse : null;
    return value != null && value !== '' && Number.isFinite(Number(value)) ? Number(value) : null;
  }
  function summarize(visits) {
    const result = {unknown: 0};
    for (const status of Object.keys(statuses)) {
      const selected = visits.filter(visit => visit.status === status);
      const values = selected.map(valueOf).filter(value => value != null);
      result[status] = {count: selected.length, missing: selected.length - values.length,
        value: values.length || !selected.length ? values.reduce((sum, value) => sum + value, 0) : null};
      result.unknown += result[status].missing;
    }
    return result;
  }
  const api = {keyOf, valueOf, summarize};
  if (typeof module !== 'undefined') module.exports = api;
  if (!root.document) return;
  const $ = id => document.getElementById(id);
  const money = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});
  const format = value => value == null ? 'Nao informado' : money.format(value);
  let currentReport = null, currentVisits = [];
  function row(table, values) {
    const tr = document.createElement('tr');
    values.forEach((value, index) => {
      const td = document.createElement('td'); td.textContent = value;
      td.dataset.label = table.closest('table').querySelectorAll('th')[index].textContent;
      tr.append(td);
    });
    table.append(tr);
  }
  function update() {
    const selected = $('panel-doctor').value;
    const visits = currentVisits.filter(visit => selected && keyOf(visit) === selected);
    const summary = summarize(visits);
    $('doctor-scope').textContent = currentReport ? `${currentReport.hospital} - Relatorio #${currentReport.id ?? root.document.querySelector('#report-filter').value}` : '';
    $('doctor-message').textContent = !currentReport ? 'Selecione hospital e relatorio.' : !selected ? 'Selecione um medico.' : !visits.length ? 'Sem visitas neste periodo.' : '';
    for (const [status, suffix] of [['PAGA', 'paid'], ['PENDENTE', 'pending'], ['DIVERGENTE', 'blocked']]) {
      $('doctor-' + suffix).textContent = selected ? format(summary[status].value) : '-';
      $('doctor-' + suffix + '-detail').textContent = selected ? `${summary[status].count} visitas${summary[status].missing ? `; ${summary[status].missing} sem valor definido` : ''}` : '';
    }
    $('doctor-unknown').textContent = selected ? summary.unknown : '-';
    $('doctor-hospitals').replaceChildren();
    if (selected && currentReport) row($('doctor-hospitals'), [currentReport.hospital, visits.length, format(summary.PAGA.value), format(summary.PENDENTE.value), format(summary.DIVERGENTE.value)]);
    const search = $('doctor-search').value.trim();
    const status = $('doctor-status').value;
    const pending = visits.filter(visit => visit.status !== 'PAGA' && (!status || status === visit.status) && String(visit.atendimento).includes(search));
    $('doctor-count').textContent = selected ? `${pending.length} visitas` : '';
    $('doctor-visits').replaceChildren();
    for (const visit of pending) row($('doctor-visits'), [visit.atendimento, visit.data ? visit.data.split('-').reverse().join('/') : 'Nao informada',
      (visit.procedimento ?? visit.hospital?.procedimento ?? visit.original?.['procedimento/mat-med']) || 'Nao identificado',
      format(valueOf(visit)), statuses[visit.status], visit.motivo]);
    if (!pending.length) row($('doctor-visits'), [selected ? 'Nenhuma pendencia neste filtro.' : 'Selecione um medico.', '', '', '', '', '']);
  }
  api.render = (report, visits) => {
    currentReport = report; currentVisits = visits;
    const select = $('panel-doctor'), previous = select.value;
    const doctors = new Map();
    for (const visit of report?.visitas || []) if (keyOf(visit) && nameOf(visit).trim()) doctors.set(keyOf(visit), `${nameOf(visit)}${visit.medicoCadastroId != null ? ` (cadastro #${visit.medicoCadastroId})` : ''}`);
    select.replaceChildren(new Option('Selecione um medico', ''), ...[...doctors].sort((a,b) => a[1].localeCompare(b[1], 'pt-BR')).map(([key, name]) => new Option(name, key)));
    if (doctors.has(previous)) select.value = previous;
    select.disabled = !report || !doctors.size;
    update();
  };
  $('panel-doctor').addEventListener('change', update);
  $('doctor-status').addEventListener('change', update);
  $('doctor-search').addEventListener('input', update);
  root.FaturamedPainel = api;
})(globalThis);
