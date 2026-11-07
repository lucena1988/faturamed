(() => {
  const $ = s => document.querySelector(s);
  const TIPOS = {POR_EXAME: 'Por exame', POR_AGENDA: 'Fixo por agenda', TABELA_AGENDA: 'Tabela por quantidade', MENSAL_MEDICO: 'Fixo mensal'};
  const STATUS = {AGUARDANDO: 'Aguardando hospital', APROVADO: 'Aprovado', NF_EMITIDA: 'NF emitida'};
  const PERIODOS = {MANHA: 'Manhã', TARDE: 'Tarde', NOITE: 'Noite', INTEGRAL: 'Integral'};
  let exames = [];
  let regras = [];
  let regraEditando = null;
  let previaAtual = null;

  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c]);
  const brl = v => v == null ? '—' : Number(v).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'});
  const data = d => d ? new Date(d + 'T00:00').toLocaleDateString('pt-BR') : '—';
  const msg = (t, erro) => { const m = $('#mensagem'); m.textContent = t || ''; m.style.color = erro ? '#b02a37' : ''; };
  async function api(url, opcoes = {}) {
    const r = await fetch(url, opcoes);
    if (r.status === 204) return null;
    const corpo = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(corpo.message || corpo.erro || corpo.detail || `Erro ${r.status}`);
    return corpo;
  }
  const json = (method, body) => ({method, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
  const proteger = fn => async (...a) => { try { await fn(...a); } catch (e) { msg(e.message, true); } };

  // ---------- abas ----------
  document.querySelectorAll('.fp-aba').forEach(b => b.addEventListener('click', () => {
    document.querySelectorAll('.fp-aba').forEach(x => x.classList.toggle('ativa', x === b));
    $('#aba-fechamentos').hidden = b.dataset.aba !== 'fechamentos';
    $('#aba-regras').hidden = b.dataset.aba !== 'regras';
    msg('');
  }));

  async function carregarCadastros() {
    const [hospitais, medicos, listaExames] = await Promise.all([api('/api/hospitais'), api('/api/medicos'), api('/api/producao/exames')]);
    const opts = l => l.filter(x => x.ativo !== false).map(x => `<option value="${x.id}">${esc(x.nome)}</option>`).join('');
    document.querySelectorAll('[data-hospitais]').forEach(s => s.insertAdjacentHTML('beforeend', opts(hospitais)));
    document.querySelectorAll('[data-medicos]').forEach(s => s.insertAdjacentHTML('beforeend', opts(medicos)));
    exames = listaExames.filter(e => e.ativo !== false);
  }

  // ---------- regras ----------
  async function listarRegras() {
    const h = $('#regras-hospital').value;
    regras = await api('/api/producao/regras' + (h ? `?hospitalId=${h}` : ''));
    const nomeExame = id => exames.find(e => e.id === id)?.nome || `#${id}`;
    $('#regras').innerHTML = regras.map(r => {
      let valor = brl(r.valor);
      if (r.tipo === 'POR_EXAME') valor += ' / exame' + (r.minimoPorAgenda ? ` (mín. ${r.minimoPorAgenda} por agenda)` : '');
      if (r.tipo === 'POR_AGENDA') valor += ' / agenda';
      if (r.tipo === 'TABELA_AGENDA') valor = r.faixas.length ? `${r.faixas.length} faixas: ${brl(r.faixas[0].valor)} (até ${r.faixas[0].quantidade}) … ${brl(r.faixas.at(-1).valor)} (${r.faixas.at(-1).quantidade})` + (Number(r.incremento) ? ` + ${brl(r.incremento)}/ponto extra` : '') : '—';
      if (r.tipo === 'MENSAL_MEDICO') valor += ` — ${esc(r.medico)}`;
      return `<tr><td>${esc(r.hospital)}</td><td>${esc(r.nome)}</td><td>${TIPOS[r.tipo]}</td>
        <td>${r.exames.map(e => esc(nomeExame(e.exameId)) + (e.peso > 1 ? ` (${e.peso})` : '')).join(', ') || '—'}</td>
        <td>${valor}</td><td>${data(r.vigenciaInicio)} a ${r.vigenciaFim ? data(r.vigenciaFim) : 'atual'}</td>
        <td>${r.ativo ? 'Ativa' : 'Inativa'}</td>
        <td><button type="button" class="text-button" data-editar="${r.id}">Editar</button><button type="button" class="text-button" data-copiar="${r.id}" title="Copiar para outro hospital ou nova vigência">Copiar</button><button type="button" class="text-button prod-perigo" data-excluir="${r.id}">Excluir</button></td></tr>`;
    }).join('') || '<tr><td colspan="8" class="prod-vazio">Nenhuma regra cadastrada.</td></tr>';
  }

  function ajustarCamposTipo() {
    const tipo = $('#regra-tipo').value;
    document.querySelectorAll('#form-regra [data-campo-tipo]').forEach(el => { el.hidden = !el.dataset.campoTipo.split(' ').includes(tipo); });
  }

  function abrirRegra(regra, copia) {
    const f = $('#form-regra');
    f.reset();
    regraEditando = regra && !copia ? regra.id : null;
    $('#regra-titulo').textContent = regraEditando ? 'Editar regra' : copia ? 'Copiar regra' : 'Nova regra';
    $('#regra-msg').hidden = true;
    const pesos = new Map((regra?.exames || []).map(e => [e.exameId, e.peso]));
    $('#regra-exames').innerHTML = exames.map(e => `<label><input type="checkbox" value="${e.id}" ${pesos.has(e.id) ? 'checked' : ''}>${esc(e.nome)}
      <input type="number" min="1" value="${pesos.get(e.id) || 1}" title="Peso (pontos)" data-peso="${e.id}"></label>`).join('');
    if (regra) {
      for (const k of ['hospitalId', 'nome', 'tipo', 'valor', 'minimoPorAgenda', 'incremento', 'vigenciaInicio', 'vigenciaFim', 'observacao']) f.elements[k].value = regra[k] ?? '';
      f.elements.medicoId.value = regra.medicoId ?? '';
      f.elements.ativo.checked = regra.ativo;
      f.elements.faixas.value = regra.faixas.map(x => `${x.quantidade}\t${Number(x.valor).toLocaleString('pt-BR', {minimumFractionDigits: 2})}`).join('\n');
      if (copia) { f.elements.vigenciaInicio.value = ''; f.elements.vigenciaFim.value = ''; }
    } else {
      f.elements.hospitalId.value = $('#regras-hospital').value;
      f.elements.vigenciaInicio.value = new Date().toISOString().slice(0, 8) + '01';
    }
    ajustarCamposTipo();
    $('#dlg-regra').showModal();
  }

  // aceita linhas coladas da planilha: "11 EXAMES  R$ 1.170,21", "28 | 2872,61", "3;1200.00"
  function lerFaixas(texto) {
    const faixas = [];
    texto.split(/\r?\n/).map(l => l.trim()).filter(Boolean).forEach((l, n) => {
      const q = l.match(/^\D*?(\d+)/);
      const v = l.match(/(\d{1,3}(?:\.\d{3})*,\d{1,2}|\d+[.,]\d{1,2}|\d+)\s*$/);
      if (!q || !v || q.index + q[0].length > v.index) throw new Error(`Faixa da linha ${n + 1} não reconhecida: "${l}"`);
      let valor = v[1];
      valor = valor.includes(',') ? valor.replace(/\./g, '').replace(',', '.') : valor;
      faixas.push({quantidade: +q[1], valor: +valor});
    });
    return faixas;
  }

  async function salvarRegra() {
    const f = $('#form-regra');
    const aviso = $('#regra-msg');
    aviso.hidden = true;
    try {
      if (!f.reportValidity()) return;
      const tipo = f.elements.tipo.value;
      const corpo = {
        hospitalId: +f.elements.hospitalId.value, nome: f.elements.nome.value, tipo,
        valor: +f.elements.valor.value || 0, minimoPorAgenda: +f.elements.minimoPorAgenda.value || 0, incremento: +f.elements.incremento.value || 0,
        medicoId: tipo === 'MENSAL_MEDICO' && f.elements.medicoId.value ? +f.elements.medicoId.value : null,
        vigenciaInicio: f.elements.vigenciaInicio.value, vigenciaFim: f.elements.vigenciaFim.value || null,
        observacao: f.elements.observacao.value, ativo: f.elements.ativo.checked,
        exames: tipo === 'MENSAL_MEDICO' ? [] : [...document.querySelectorAll('#regra-exames input[type=checkbox]:checked')]
          .map(c => ({exameId: +c.value, peso: +document.querySelector(`[data-peso="${c.value}"]`).value || 1})),
        faixas: tipo === 'TABELA_AGENDA' ? lerFaixas(f.elements.faixas.value) : []
      };
      await api(regraEditando ? `/api/producao/regras/${regraEditando}` : '/api/producao/regras', json(regraEditando ? 'PUT' : 'POST', corpo));
      $('#dlg-regra').close();
      msg('Regra salva.');
      await listarRegras();
    } catch (e) {
      aviso.textContent = e.message; aviso.hidden = false;
    }
  }

  // ---------- fechamentos ----------
  async function calcularPrevia(e) {
    e?.preventDefault();
    const f = $('#form-previa');
    if (!f.reportValidity()) return;
    const p = new URLSearchParams(new FormData(f));
    previaAtual = await api('/api/producao/fechamentos/previa?' + p);
    const pv = previaAtual;
    $('#previa').hidden = false;
    $('#previa-total').textContent = `${pv.lotes} folha(s) · ${pv.etiquetas} etiqueta(s) · total ${brl(pv.total)}`;
    $('#previa-resumo').innerHTML = pv.resumo.map(r => `<tr><td>${esc(r.medico)}</td><td>${r.agendas}</td><td>${r.exames}</td><td class="fp-valor">${brl(r.total)}</td></tr>`).join('')
      + (pv.resumo.length ? `<tr><th>Total</th><th></th><th></th><th class="fp-valor">${brl(pv.total)}</th></tr>` : '<tr><td colspan="4" class="prod-vazio">Nenhuma folha conferida neste período.</td></tr>');
    $('#previa-calculo').innerHTML = pv.calculos.map(c => `<tr><td>${esc(c.medico)}</td><td>${data(c.data)}</td><td>${PERIODOS[c.periodo] || ''}</td>
      <td>${esc(c.descricao)}</td><td>${c.exames}</td><td>${c.pontos}</td><td class="fp-valor">${brl(c.valor)}</td></tr>`).join('');
    const alerta = $('#previa-alerta');
    alerta.hidden = !pv.semRegra.length;
    alerta.innerHTML = pv.semRegra.length ? `<strong>${pv.semRegra.length} etiqueta(s) sem regra de valor ou sem médico — o fechamento fica bloqueado:</strong>
      <ul>${pv.semRegra.map(s => `<li>${data(s.data)} · ${esc(s.medico || 'sem médico')} · ${esc(s.exame || 'sem exame')} · atend. ${esc(s.nrAtendimento)} ${esc(s.paciente)}</li>`).join('')}</ul>
      Cadastre a regra na aba "Regras de valor" ou corrija a folha.` : '';
    $('#btn-fechar').disabled = !pv.etiquetas || pv.semRegra.length > 0;
  }

  async function gerarFechamento() {
    const f = $('#form-previa');
    if (!confirm(`Gerar o fechamento de ${brl(previaAtual.total)}? As folhas incluídas ficam bloqueadas para edição.`)) return;
    const d = await api('/api/producao/fechamentos', json('POST', {hospitalId: +f.elements.hospitalId.value, inicio: f.elements.inicio.value,
      fim: f.elements.fim.value, observacao: $('#obs-fechamento').value}));
    $('#previa').hidden = true;
    msg(`Fechamento nº ${d.fechamento.id} gerado. Baixe a planilha e envie ao hospital.`);
    await listarFechamentos();
  }

  async function listarFechamentos() {
    const lista = await api('/api/producao/fechamentos');
    $('#fechamentos').innerHTML = lista.map(f => `<tr><td>${f.id}</td><td>${esc(f.hospital)}</td><td>${data(f.dataInicio)} a ${data(f.dataFim)}</td>
      <td>${f.etiquetas}</td><td class="fp-valor">${brl(f.total)}</td><td><span class="fp-status ${f.status}">${STATUS[f.status]}</span></td>
      <td>${f.aprovadoPor ? `${esc(f.aprovadoPor)} em ${data(f.aprovadoEm)}` : '—'}</td><td>${f.nfNumero ? `${esc(f.nfNumero)} (${data(f.nfData)})` : '—'}</td>
      <td><a class="text-button" href="/api/producao/fechamentos/${f.id}/planilha">Planilha</a>
        ${f.status === 'AGUARDANDO' ? `<button type="button" class="text-button" data-aprovar="${f.id}">Registrar ok do hospital</button><button type="button" class="text-button prod-perigo" data-cancelar="${f.id}">Cancelar</button>` : ''}
        ${f.status === 'APROVADO' ? `<button type="button" class="text-button" data-nf="${f.id}">Registrar NF</button>` : ''}</td></tr>`).join('')
      || '<tr><td colspan="9" class="prod-vazio">Nenhum fechamento ainda.</td></tr>';
  }

  $('#fechamentos').addEventListener('click', proteger(async e => {
    const b = e.target.closest('button'); if (!b) return;
    if (b.dataset.aprovar) {
      const quem = prompt('Quem aprovou no hospital (nome / e-mail)?'); if (!quem) return;
      await api(`/api/producao/fechamentos/${b.dataset.aprovar}/aprovar`, json('POST', {aprovadoPor: quem}));
      msg('Aprovação registrada. Agora é possível registrar a NF.');
    } else if (b.dataset.nf) {
      const numero = prompt('Número da NF emitida:'); if (!numero) return;
      await api(`/api/producao/fechamentos/${b.dataset.nf}/nf`, json('POST', {numero}));
      msg('NF registrada.');
    } else if (b.dataset.cancelar) {
      if (!confirm('Cancelar este fechamento? As folhas voltam a ficar conferidas e disponíveis para um novo fechamento.')) return;
      await api(`/api/producao/fechamentos/${b.dataset.cancelar}`, {method: 'DELETE'});
      msg('Fechamento cancelado.');
    }
    await listarFechamentos();
  }));

  $('#regras').addEventListener('click', proteger(async e => {
    const b = e.target.closest('button'); if (!b) return;
    const id = +(b.dataset.editar || b.dataset.copiar || b.dataset.excluir);
    const regra = regras.find(r => r.id === id);
    if (b.dataset.editar) abrirRegra(regra, false);
    else if (b.dataset.copiar) abrirRegra(regra, true);
    else if (b.dataset.excluir && confirm(`Excluir a regra "${regra.nome}"?`)) { await api(`/api/producao/regras/${id}`, {method: 'DELETE'}); await listarRegras(); }
  }));

  $('#regra-tipo').addEventListener('change', ajustarCamposTipo);
  $('#btn-nova-regra').addEventListener('click', () => abrirRegra(null, false));
  $('#btn-salvar-regra').addEventListener('click', salvarRegra);
  $('#btn-cancelar-regra').addEventListener('click', () => $('#dlg-regra').close());
  $('#regras-hospital').addEventListener('change', proteger(listarRegras));
  $('#form-previa').addEventListener('submit', proteger(calcularPrevia));
  $('#btn-fechar').addEventListener('click', proteger(gerarFechamento));

  proteger(async () => {
    await carregarCadastros();
    const hoje = new Date();
    const f = $('#form-previa').elements;
    f.inicio.value = new Date(hoje.getFullYear(), hoje.getMonth() - 1, 1).toISOString().slice(0, 10);
    f.fim.value = new Date(hoje.getFullYear(), hoje.getMonth(), 0).toISOString().slice(0, 10);
    await Promise.all([listarFechamentos(), listarRegras()]);
  })();
})();
