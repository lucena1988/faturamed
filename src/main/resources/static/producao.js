(() => {
  const $ = s => document.querySelector(s);
  const CAMPOS = ['nrAtendimento', 'dataHoraAtendimento', 'paciente', 'dataNascimento', 'prontuario', 'convenio', 'plano', 'carteirinha', 'guia', 'senha', 'procedimento', 'medicoSolicitante'];
  const STATUS = {RASCUNHO: 'Rascunho', CONFERIDO: 'Conferido', ENVIADO: 'Enviado'};
  const PERIODOS = {MANHA: 'Manhã', TARDE: 'Tarde', NOITE: 'Noite', INTEGRAL: 'Integral'};
  let lote = null; // {id, versao, status}
  let itens = [];
  let duplicados = new Map();
  let fotoPendente = null;
  let exibirFaltas = false; // liga o destaque de problemas após a 1ª tentativa de conferir
  let medicos = [];
  let exames = [];
  const selMedicos = new Set();
  const selExames = new Set();

  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c]);
  const sem = s => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const msg = (t, erro) => { const m = $('#mensagem'); m.textContent = t || ''; m.style.color = erro ? '#b02a37' : ''; };
  async function api(url, opcoes = {}) {
    const r = await fetch(url, opcoes);
    if (r.status === 204) return null;
    const corpo = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(corpo.message || corpo.erro || corpo.detail || `Erro ${r.status}`);
    return corpo;
  }
  const json = (method, body) => ({method, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
  const nomeMedico = id => medicos.find(m => m.id === id)?.nome || '';
  const nomeExame = id => exames.find(e => e.id === id)?.nome || '';

  async function carregarCadastros() {
    const [hospitais, listaMedicos, listaExames] = await Promise.all([api('/api/hospitais'), api('/api/medicos'), api('/api/producao/exames')]);
    medicos = listaMedicos.filter(m => m.ativo !== false);
    exames = listaExames.filter(e => e.ativo !== false);
    const opts = lista => lista.filter(x => x.ativo !== false).map(x => `<option value="${x.id}">${esc(x.nome)}</option>`).join('');
    document.querySelectorAll('[data-hospitais]').forEach(s => s.insertAdjacentHTML('beforeend', opts(hospitais)));
    document.querySelectorAll('[data-medicos]').forEach(s => s.insertAdjacentHTML('beforeend', opts(medicos)));
  }

  // ---- seleção múltipla (médicos / exames) ----
  function renderMulti(det, lista, selecionados, rotuloVazio) {
    const busca = sem(det.querySelector('[data-busca]').value);
    det.querySelector('[data-opcoes]').innerHTML = lista.filter(x => !busca || sem(x.nome).includes(busca) || selecionados.has(x.id))
      .map(x => `<label><input type="checkbox" value="${x.id}" ${selecionados.has(x.id) ? 'checked' : ''}>${esc(x.nome)}</label>`).join('')
      || '<p class="prod-dica">Nada encontrado.</p>';
    const nomes = lista.filter(x => selecionados.has(x.id)).map(x => x.nome);
    det.querySelector('summary').textContent = nomes.length ? (nomes.length > 2 ? `${nomes.length} selecionados: ${nomes.join(', ')}` : nomes.join(', ')) : rotuloVazio;
    det.querySelector('summary').title = nomes.join(', ');
  }
  const renderMedicos = () => renderMulti($('#sel-medicos'), medicos, selMedicos, 'Selecione');
  const renderExames = () => renderMulti($('#sel-exames'), exames, selExames, 'Selecione');
  function ligarMulti(det, selecionados, render) {
    det.querySelector('[data-busca]').addEventListener('input', render);
    det.querySelector('[data-opcoes]').addEventListener('change', e => {
      const id = +e.target.value;
      e.target.checked ? selecionados.add(id) : selecionados.delete(id);
      render(); renderItens();
    });
  }
  document.addEventListener('click', e => document.querySelectorAll('.prod-multi details[open]').forEach(d => { if (!d.contains(e.target)) d.open = false; }));

  async function adicionarExame() {
    const campo = $('#novo-exame');
    if (!campo.value.trim()) return;
    const novo = await api('/api/producao/exames', json('POST', {nome: campo.value}));
    exames.push(novo); exames.sort((a, b) => a.nome.localeCompare(b.nome));
    selExames.add(novo.id); campo.value = '';
    renderExames(); renderItens();
  }

  async function listar() {
    const p = new URLSearchParams([...new FormData($('#filtros'))].filter(([, v]) => v));
    const lotes = await api('/api/producao/lotes?' + p);
    $('#lotes').innerHTML = lotes.length ? lotes.map(l => `<tr data-id="${l.id}">
      <td>${new Date(l.dataProducao + 'T00:00').toLocaleDateString('pt-BR')}</td><td>${PERIODOS[l.periodo] || l.periodo}</td>
      <td>${esc(l.hospital)}</td><td>${esc(l.medicos)}</td><td>${esc(l.exames)}</td><td>${l.itens}</td>
      <td>${l.pendentes ? `<strong>${l.pendentes}</strong>` : '0'}</td><td>${STATUS[l.status]}</td></tr>`).join('')
      : '<tr><td colspan="8" class="prod-vazio">Nenhum lote encontrado.</td></tr>';
  }

  function mostrar(view) { $('#view-lista').hidden = view !== 'lista'; $('#view-lote').hidden = view !== 'lote'; msg(''); }

  async function abrir(id) {
    const f = $('#cabecalho');
    f.reset(); fotoPendente = null; $('#foto').value = ''; $('#aviso-dup').hidden = true;
    exibirFaltas = false; mostrarProblemas([]);
    selMedicos.clear(); selExames.clear();
    const img = $('#foto-preview'); img.hidden = true; img.removeAttribute('src');
    if (id) {
      const l = await api('/api/producao/lotes/' + id);
      lote = {id: l.id, versao: l.versao, status: l.status};
      for (const k of ['hospitalId', 'dataProducao', 'periodo', 'auxiliar', 'observacao']) f.elements[k].value = l[k] ?? '';
      l.medicoIds.forEach(m => selMedicos.add(m));
      l.exameIds.forEach(e => selExames.add(e));
      itens = l.itens;
      if (l.temFoto) { img.src = `/api/producao/lotes/${l.id}/foto?t=${Date.now()}`; img.hidden = false; }
    } else {
      lote = null; itens = [];
      f.elements.dataProducao.value = new Date().toISOString().slice(0, 10);
    }
    renderMedicos(); renderExames();
    mostrar('lote');
    atualizarEstado();
    await verificarDuplicidades();
    $('#rapido input').focus();
  }

  function atualizarEstado() {
    const st = lote?.status || 'RASCUNHO';
    $('#lote-status').textContent = lote ? STATUS[st] : 'Novo (não salvo)';
    const bloqueado = st === 'ENVIADO';
    document.querySelectorAll('#cabecalho input, #cabecalho select, #cabecalho button, #rapido input, #rapido button, #foto, #btn-salvar').forEach(e => e.disabled = bloqueado);
    $('#btn-conferir').hidden = !lote || st !== 'RASCUNHO';
    $('#btn-enviar').hidden = !lote || st !== 'CONFERIDO';
    $('#btn-reabrir').hidden = !lote || st === 'RASCUNHO';
    $('#btn-excluir').hidden = !lote || bloqueado;
    renderItens();
  }

  function opcoesVinculo(ids, nome, atual) {
    const lista = [...ids];
    if (atual && !ids.has(atual)) lista.push(atual);
    return `<option value="">${lista.length ? '—' : 'Selecione no topo'}</option>` +
      lista.map(id => `<option value="${id}" ${id === atual ? 'selected' : ''}>${esc(nome(id))}</option>`).join('');
  }

  function renderItens() {
    const bloqueado = lote?.status === 'ENVIADO';
    // com um único médico/exame selecionado, as etiquetas assumem esse valor
    const medUnico = selMedicos.size === 1 ? [...selMedicos][0] : null;
    const exUnico = selExames.size === 1 ? [...selExames][0] : null;
    for (const it of itens) {
      if (medUnico && !it.medicoId) it.medicoId = medUnico;
      if (exUnico && !it.exameId) it.exameId = exUnico;
      if (it.medicoId && !selMedicos.has(it.medicoId)) it.medicoId = medUnico;
      if (it.exameId && !selExames.has(it.exameId)) it.exameId = exUnico;
    }
    const vistos = new Map();
    const dis = bloqueado ? 'disabled' : '';
    const faltas = new Map(exibirFaltas ? problemas().map(x => [x.i, new Set(x.p.map(p => p.campo))]) : []);
    const falta = (i, c) => faltas.get(i)?.has(c) ? ' class="prod-falta"' : '';
    $('#itens').innerHTML = itens.map((it, i) => {
      const n = (vistos.get(it.nrAtendimento) || 0) + 1; vistos.set(it.nrAtendimento, n);
      const semVinculo = !it.medicoId || (selExames.size && !it.exameId);
      const classes = [it.pendenteRevisao ? 'pendente' : '', duplicados.has(it.nrAtendimento) ? 'duplicado' : '', n > 1 ? 'repetido' : '', semVinculo ? 'sem-vinculo' : ''].join(' ');
      const dup = duplicados.get(it.nrAtendimento);
      return `<tr class="${classes}" data-i="${i}" title="${dup ? esc('Já lançado em: ' + dup.join('; ')) : ''}"><td>${i + 1}</td>
        <td${falta(i, 'medicoId')}><select data-vinculo="medicoId" ${dis}>${opcoesVinculo(selMedicos, nomeMedico, it.medicoId)}</select></td>
        <td${falta(i, 'exameId')}><select data-vinculo="exameId" ${dis}>${opcoesVinculo(selExames, nomeExame, it.exameId)}</select></td>
        ${CAMPOS.map(c => `<td${falta(i, c)}><input data-campo="${c}" value="${esc(it[c])}" ${dis}></td>`).join('')}
        <td>${bloqueado ? '' : `${it.pendenteRevisao ? '<button type="button" class="text-button" data-ok title="Marcar como revisado">✓</button>' : ''}<button type="button" class="text-button prod-perigo" data-remover title="Remover etiqueta">✕</button>`}</td></tr>`;
    }).join('') || '<tr><td colspan="16" class="prod-vazio">Nenhuma etiqueta. Leia a foto da folha ou digite o nº de atendimento.</td></tr>';
    const pend = itens.filter(i => i.pendenteRevisao).length;
    $('#contagem').textContent = `${itens.length} etiqueta(s) em ${vistos.size} atendimento(s)${pend ? ` · ${pend} pendente(s)` : ''}`;
    if (exibirFaltas) mostrarProblemas(problemas());
  }

  async function verificarDuplicidades() {
    duplicados = new Map();
    const atend = [...new Set(itens.map(i => i.nrAtendimento).filter(Boolean))];
    if (atend.length) {
      const lista = await api('/api/producao/duplicidades', json('POST', {atendimentos: atend, loteId: lote?.id}));
      for (const d of lista) {
        const txt = `lote #${d.loteId} de ${new Date(d.dataProducao + 'T00:00').toLocaleDateString('pt-BR')} (${d.hospital})`;
        duplicados.set(d.nrAtendimento, [...(duplicados.get(d.nrAtendimento) || []), txt]);
      }
    }
    const aviso = $('#aviso-dup');
    aviso.hidden = !duplicados.size;
    aviso.textContent = duplicados.size ? `⚠ Possível cobrança em dobro: ${duplicados.size} atendimento(s) já constam em outro lote (em vermelho; passe o mouse para ver).` : '';
    renderItens();
  }

  function novoItem(nr, origem) {
    return {id: null, nrAtendimento: nr, dataHoraAtendimento: '', paciente: '', prontuario: '', dataNascimento: '', filiacao: '', convenio: '', convenioCodigo: '', plano: '',
      carteirinha: '', guia: '', senha: '', nrPrescricao: '', procedimento: '', medicoSolicitante: '', origem, pendenteRevisao: true, medicoId: null, exameId: null};
  }

  async function adicionarRapido(e) {
    e.preventDefault();
    const campo = e.target.elements.nr;
    const nr = campo.value.replace(/\s/g, '');
    if (!nr) return;
    const existente = itens.find(i => i.nrAtendimento === nr);
    const item = novoItem(nr, 'MANUAL');
    if (existente) for (const k of Object.keys(item)) if (!['id', 'origem', 'pendenteRevisao', 'exameId'].includes(k)) item[k] = existente[k];
    itens.push(item);
    campo.value = '';
    const qtd = itens.filter(i => i.nrAtendimento === nr).length;
    msg(qtd > 1 ? `Atendimento ${nr}: ${qtd}ª etiqueta adicionada (dados copiados da anterior; escolha o exame).` : `Atendimento ${nr} adicionado. Complete os dados na tabela.`);
    await verificarDuplicidades();
    campo.focus();
  }

  // tenta casar o texto lido (procedimento / tipo de agenda) com um exame selecionado
  function sugerirExame(texto) {
    const t = sem(texto);
    if (!t) return null;
    const achado = exames.find(e => selExames.has(e.id) && (t.includes(sem(e.nome)) || sem(e.nome).includes(t)));
    return achado ? achado.id : null;
  }

  async function lerFoto(e) {
    const arquivo = e.target.files[0];
    if (!arquivo) return;
    fotoPendente = arquivo;
    const img = $('#foto-preview'); img.src = URL.createObjectURL(arquivo); img.hidden = false;
    const fd = new FormData(); fd.append('arquivo', arquivo);
    $('#ocr-aviso').textContent = 'Lendo etiquetas da foto… isso pode levar até um minuto.';
    try {
      const r = await api('/api/producao/ocr', {method: 'POST', body: fd});
      preencherCabecalho(r.cabecalho);
      for (const it of r.itens) it.exameId = sugerirExame(it.procedimento) || sugerirExame(r.cabecalho?.tipoAgenda);
      itens.push(...r.itens);
      const pend = r.itens.filter(i => i.pendenteRevisao).length;
      $('#ocr-aviso').textContent = `${r.itens.length} etiqueta(s) lida(s)${pend ? `, ${pend} para revisar (amarelo)` : ''}. Confira com a foto antes de salvar.`;
      await verificarDuplicidades();
    } catch (err) {
      $('#ocr-aviso').textContent = err.message + ' Você pode digitar as etiquetas ao lado.';
    }
  }

  function preencherCabecalho(c) {
    if (!c) return;
    const f = $('#cabecalho').elements;
    if (c.auxiliar && !f.auxiliar.value) f.auxiliar.value = c.auxiliar;
    const p = sem(c.periodo).toUpperCase();
    for (const k of Object.keys(PERIODOS)) if (p.startsWith(k.slice(0, 4))) f.periodo.value = k;
    if (c.medico) {
      const alvo = sem(c.medico);
      const m = medicos.find(x => sem(x.nome).includes(alvo));
      if (m) selMedicos.add(m.id);
    }
    if (c.tipoAgenda) {
      const alvo = sem(c.tipoAgenda);
      const ex = exames.find(x => alvo.includes(sem(x.nome)) || sem(x.nome).includes(alvo));
      if (ex) selExames.add(ex.id);
    }
    renderMedicos(); renderExames();
  }

  async function salvar() {
    const f = $('#cabecalho');
    if (!f.reportValidity()) return null;
    if (!selMedicos.size) { msg('Selecione ao menos um médico executante.', true); return null; }
    const semNr = itens.findIndex(i => !i.nrAtendimento);
    if (semNr >= 0) { msg(`Etiqueta ${semNr + 1} está sem nº de atendimento.`, true); return null; }
    const c = Object.fromEntries(new FormData(f));
    const corpo = {cabecalho: {...c, hospitalId: +c.hospitalId, medicoIds: [...selMedicos], exameIds: [...selExames], versao: lote?.versao ?? 0}, itens};
    const l = await api(lote ? `/api/producao/lotes/${lote.id}` : '/api/producao/lotes', json(lote ? 'PUT' : 'POST', corpo));
    lote = {id: l.id, versao: l.versao, status: l.status};
    if (fotoPendente) {
      const fd = new FormData(); fd.append('arquivo', fotoPendente);
      await api(`/api/producao/lotes/${l.id}/foto`, {method: 'POST', body: fd});
      fotoPendente = null;
    }
    itens = l.itens;
    atualizarEstado();
    msg('Lote salvo.');
    return l;
  }

  // lista, por etiqueta, o que impede a conferência do lote
  function problemas() {
    const lista = [];
    itens.forEach((it, i) => {
      const p = [];
      if (!/^\d{7,10}$/.test(it.nrAtendimento || '')) p.push({campo: 'nrAtendimento', txt: it.nrAtendimento ? `atendimento "${it.nrAtendimento}" inválido` : 'sem nº de atendimento'});
      if (!(it.paciente || '').trim()) p.push({campo: 'paciente', txt: 'sem paciente'});
      if (!it.medicoId) p.push({campo: 'medicoId', txt: 'sem médico'});
      if (selExames.size && !it.exameId) p.push({campo: 'exameId', txt: 'sem exame'});
      if (it.pendenteRevisao) p.push({campo: null, txt: 'não conferida com a foto (clique ✓ na linha)'});
      if (p.length) lista.push({i, p});
    });
    return lista;
  }

  function mostrarProblemas(lista) {
    const box = $('#problemas');
    if (!lista.length) { box.hidden = true; box.innerHTML = ''; return; }
    const soRevisao = lista.every(x => x.p.length === 1 && !x.p[0].campo);
    box.innerHTML = `<strong>Não é possível conferir: ${lista.length} etiqueta(s) com problema</strong>
      ${soRevisao ? '<button type="button" class="secondary-button" data-revisar-todas>✓ Já conferi todas com a foto</button>' : ''}
      <ul>${lista.map(x => `<li><a href="#" data-ir="${x.i}">Linha ${x.i + 1}${itens[x.i].paciente ? ` (${esc(itens[x.i].paciente)})` : ''}</a>: ${x.p.map(p => esc(p.txt)).join(', ')}</li>`).join('')}</ul>`;
    box.hidden = false;
  }

  async function mudarStatus(status) {
    if (status !== 'RASCUNHO') {
      const lista = problemas();
      exibirFaltas = true;
      renderItens();
      mostrarProblemas(lista);
      if (lista.length) { msg(''); $('#problemas').scrollIntoView({behavior: 'smooth', block: 'center'}); return; }
    }
    if (lote.status === 'RASCUNHO' && !(await salvar())) return;
    const l = await api(`/api/producao/lotes/${lote.id}/status`, json('POST', {status}));
    lote = {id: l.id, versao: l.versao, status: l.status};
    atualizarEstado();
    msg(`Lote ${STATUS[status].toLowerCase()}.`);
  }

  const proteger = fn => async (...a) => { try { await fn(...a); } catch (e) { msg(e.message, true); } };

  ligarMulti($('#sel-medicos'), selMedicos, renderMedicos);
  ligarMulti($('#sel-exames'), selExames, renderExames);
  $('#btn-novo-exame').addEventListener('click', proteger(adicionarExame));
  $('#novo-exame').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); proteger(adicionarExame)(); } });
  $('#filtros').addEventListener('submit', proteger(async e => { e.preventDefault(); await listar(); }));
  $('#lotes').addEventListener('click', proteger(async e => { const tr = e.target.closest('tr[data-id]'); if (tr) await abrir(+tr.dataset.id); }));
  $('#btn-novo').addEventListener('click', proteger(() => abrir(null)));
  $('#btn-voltar').addEventListener('click', proteger(async () => { mostrar('lista'); await listar(); }));
  $('#rapido').addEventListener('submit', proteger(adicionarRapido));
  $('#foto').addEventListener('change', proteger(lerFoto));
  $('#foto-preview').addEventListener('click', e => e.target.classList.toggle('ampliada'));
  $('#itens').addEventListener('input', e => {
    const tr = e.target.closest('tr[data-i]'); if (!tr || !e.target.dataset.campo) return;
    itens[+tr.dataset.i][e.target.dataset.campo] = e.target.value;
  });
  $('#itens').addEventListener('change', proteger(async e => {
    const tr = e.target.closest('tr[data-i]'); if (!tr) return;
    if (e.target.dataset.vinculo) { itens[+tr.dataset.i][e.target.dataset.vinculo] = e.target.value ? +e.target.value : null; renderItens(); }
    else if (e.target.dataset.campo === 'nrAtendimento') await verificarDuplicidades();
  }));
  $('#itens').addEventListener('click', proteger(async e => {
    const tr = e.target.closest('tr[data-i]'); if (!tr) return;
    const i = +tr.dataset.i;
    if (e.target.matches('[data-remover]')) { itens.splice(i, 1); await verificarDuplicidades(); }
    else if (e.target.matches('[data-ok]')) { itens[i].pendenteRevisao = false; renderItens(); }
  }));
  $('#problemas').addEventListener('click', e => {
    const ir = e.target.closest('[data-ir]');
    if (ir) {
      e.preventDefault();
      const tr = document.querySelector(`#itens tr[data-i="${ir.dataset.ir}"]`);
      tr?.scrollIntoView({behavior: 'smooth', block: 'center'});
      (tr?.querySelector('.prod-falta input, .prod-falta select') || tr?.querySelector('input'))?.focus();
    } else if (e.target.matches('[data-revisar-todas]')) {
      itens.forEach(it => { it.pendenteRevisao = false; });
      renderItens();
      msg('Todas as etiquetas foram marcadas como conferidas. Clique em "Marcar como conferido" novamente.');
    }
  });
  $('#btn-salvar').addEventListener('click', proteger(async () => { await salvar(); await verificarDuplicidades(); }));
  $('#btn-conferir').addEventListener('click', proteger(() => mudarStatus('CONFERIDO')));
  $('#btn-enviar').addEventListener('click', proteger(() => mudarStatus('ENVIADO')));
  $('#btn-reabrir').addEventListener('click', proteger(() => mudarStatus('RASCUNHO')));
  $('#btn-excluir').addEventListener('click', proteger(async () => {
    if (!confirm('Excluir este lote e todas as etiquetas?')) return;
    await api(`/api/producao/lotes/${lote.id}`, {method: 'DELETE'});
    mostrar('lista'); await listar();
  }));

  proteger(async () => {
    await carregarCadastros();
    const ocr = await api('/api/producao/ocr/status');
    if (!ocr.disponivel) $('#ocr-aviso').textContent = 'Leitura automática desligada: o Tesseract não está instalado no servidor. Lance as etiquetas manualmente.';
    await listar();
  })();
})();
