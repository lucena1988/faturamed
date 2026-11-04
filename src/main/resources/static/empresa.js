(function () {
  const $ = id => document.getElementById(id);
  const money = value => value == null ? '-' : new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value);
  const dateBr = value => value ? new Date(value+'T12:00:00').toLocaleDateString('pt-BR') : 'Sem data';
  const monthBr = value => value ? new Date(value+'-01T12:00:00').toLocaleDateString('pt-BR',{month:'long',year:'numeric'}) : 'Sem data';
  const statusLabel = {PAGA:'Faturada',PENDENTE:'Aguardando faturamento',DIVERGENTE:'Em conferência'};
  const normalize = v => (v||'').normalize('NFD').replace(/[̀-ͯ]/g,'').toLowerCase();
  let painel = null, generation = 0, first = true;

  function cell(row, value, label) { const td = document.createElement('td'); td.textContent = value; if (label) td.dataset.label = label; row.append(td); return td; }
  function empty(body, cols, text) { const tr = document.createElement('tr'); const td = cell(tr, text); td.colSpan = cols; body.append(tr); }
  function groups(body, list, label, onPick) {
    body.replaceChildren();
    const max = Math.max(0, ...list.map(g => Number(g.totais.faturado) + Number(g.totais.aguardando) + Number(g.totais.emConferencia)));
    for (const g of list) {
      const t = g.totais, tr = document.createElement('tr');
      const name = cell(tr, '');
      if (onPick) { const b = document.createElement('button'); b.type = 'button'; b.className = 'text-button'; b.textContent = label(g); b.addEventListener('click', () => onPick(g)); name.append(b); }
      else name.textContent = label(g);
      // Barra proporcional ao volume (faturado + aguardando + conferencia) do maior grupo.
      const total = Number(t.faturado) + Number(t.aguardando) + Number(t.emConferencia);
      if (max > 0) { const bar = document.createElement('span'); bar.className = 'company-bar'; bar.style.setProperty('--share', `${Math.round(total / max * 100)}%`); bar.style.setProperty('--paid', total ? `${Math.round(Number(t.faturado) / total * 100)}%` : '0%'); bar.title = `${money(total)} no total`; name.append(bar); }
      cell(tr, `${t.visitas} (${t.pagas} faturadas)`); cell(tr, money(t.faturado)); cell(tr, money(t.recebido)); cell(tr, money(t.saldo)); cell(tr, money(t.aguardando)); cell(tr, money(t.emConferencia));
      body.append(tr);
    }
    if (!list.length) empty(body, 7, 'Nenhuma visita publicada para os filtros selecionados.');
  }
  function filteredVisits() {
    if (!painel) return [];
    const status = $('company-status').value, search = normalize($('company-search').value.trim());
    return painel.visitas.filter(v => (!status || v.status === status) && (!search || normalize(`${v.atendimento} ${v.procedimento||''}`).includes(search)));
  }
  function renderVisits() {
    const body = $('company-visits-rows'), list = filteredVisits(); body.replaceChildren();
    const heads = [...document.querySelectorAll('.company-visits th')].map(th => th.textContent);
    for (const v of list.slice(0, 500)) {
      const tr = document.createElement('tr');
      [dateBr(v.data), v.medico, v.hospital, v.atendimento, v.procedimento || 'Não informado', money(v.repasse), v.inconsistente ? 'Em conferência' : money(v.recebido), statusLabel[v.status] || v.status, v.motivo || ''].forEach((value, i) => cell(tr, value, heads[i]));
      body.append(tr);
    }
    if (!list.length) empty(body, 9, 'Nenhuma visita encontrada.');
    $('company-count').textContent = list.length > 500 ? `Mostrando 500 de ${list.length} visitas. Use os filtros ou exporte o CSV.` : `${list.length} visitas`;
    $('company-export').disabled = !list.length;
  }
  function render() {
    const r = painel.resumo;
    $('company-name').textContent = painel.empresa;
    $('company-paid').textContent = money(r.faturado); $('company-paid-detail').textContent = `${r.pagas} visitas faturadas`;
    $('company-received').textContent = money(r.recebido); $('company-balance').textContent = money(r.saldo);
    $('company-pending').textContent = money(r.aguardando); $('company-pending-detail').textContent = `${r.pendentes} visitas`;
    $('company-blocked').textContent = money(r.emConferencia); $('company-blocked-detail').textContent = `${r.divergentes} visitas`;
    $('company-visits').textContent = r.visitas; $('company-doctors-detail').textContent = `${painel.porMedico.length} de ${painel.medicos.length} médicos com visitas`;
    groups($('company-by-doctor'), painel.porMedico, g => g.rotulo, g => { $('company-doctor').value = g.chave; load(); });
    groups($('company-by-hospital'), painel.porHospital, g => g.rotulo, g => { $('company-hospital').value = g.chave; load(); });
    groups($('company-by-month'), painel.porMes, g => monthBr(g.chave));
    renderVisits();
  }
  function fillFilters() {
    const doctor = $('company-doctor'), hospital = $('company-hospital');
    doctor.replaceChildren(new Option('Todos os médicos', ''), ...painel.medicos.map(m => new Option(`${m.nome} · CRM ${m.crm||"a preencher"}/${m.uf||"--"}`, m.id)));
    hospital.replaceChildren(new Option('Todos os hospitais', ''), ...painel.hospitais.map(h => new Option(h, h)));
    doctor.disabled = !painel.medicos.length; hospital.disabled = !painel.hospitais.length;
  }
  async function load() {
    const token = ++generation, params = new URLSearchParams();
    for (const [key, id] of [['medicoId','company-doctor'],['hospital','company-hospital'],['de','company-from'],['ate','company-to']]) if ($(id).value) params.set(key, $(id).value);
    $('company-message').textContent = 'Carregando...';
    try {
      const data = await FaturamedAuth.request(`/api/empresa/painel?${params}`);
      if (token !== generation) return;
      painel = data;
      if (first) { fillFilters(); first = false; }
      render();
      const doctor = $('company-doctor').selectedOptions[0], hospital = $('company-hospital').value;
      $('company-scope').textContent = [doctor?.value ? doctor.textContent : 'Todos os médicos', hospital || 'todos os hospitais'].join(' · ');
      $('company-message').textContent = painel.medicos.length ? (painel.visitas.length ? '' : 'Nenhuma visita publicada para os filtros selecionados.') : 'Nenhum médico vinculado a esta empresa. Fale com a FaturaMed.';
    } catch (error) { if (token === generation) $('company-message').textContent = error.message; }
  }
  function csv() {
    const quote = v => `"${String(v ?? '').replaceAll('"', '""')}"`;
    const lines = [['Data','Médico','Hospital','Atendimento','Procedimento','Repasse','Recebido','A receber','Situação','Motivo'].map(quote).join(';')];
    for (const v of filteredVisits()) lines.push([v.data || '', v.medico, v.hospital, v.atendimento, v.procedimento || '', v.repasse ?? '', v.recebido ?? '', v.saldo ?? '', statusLabel[v.status] || v.status, v.motivo || ''].map(quote).join(';'));
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob(['﻿' + lines.join('\r\n')], {type: 'text/csv;charset=utf-8'}));
    link.download = `visitas-${normalize(painel.empresa).replace(/[^a-z0-9]+/g, '-')}.csv`;
    link.click(); setTimeout(() => URL.revokeObjectURL(link.href), 1000);
  }
  for (const id of ['company-doctor','company-hospital','company-from','company-to']) $(id).addEventListener('change', load);
  $('company-filters').addEventListener('reset', () => setTimeout(load));
  $('company-filters').addEventListener('submit', event => event.preventDefault());
  $('company-status').addEventListener('change', renderVisits);
  $('company-search').addEventListener('input', renderVisits);
  $('company-export').addEventListener('click', csv);
  load();
})();
