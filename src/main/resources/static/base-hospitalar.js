const TIPOS_PROCEDIMENTO = { VISITA: 'Visita', EXAME: 'Exame', CONSULTA: 'Consulta', OUTRO: 'Outro' };
(() => {
  const find = id => document.getElementById(id);
  const labels = {AGUARDANDO_PRODUCAO:'Aguardando produção',CONCILIADA:'Conciliada',DIVERGENTE:'Divergente',DUPLICADO_PAGO_OUTRO_MES:'Duplicado pago em outro mês',DUPLICADO_MESMA_COMPETENCIA:'Duplicidade confirmada na mesma competência'};
  const request = window.FaturamedAuth.request;
  let periodo = null, arquivosCarregados = [], visits = [], generation = 0, selectedVisit, busy = false, flow = null;
  const stages = {AGUARDANDO_PRODUCAO:'Aguardando produção',AGUARDANDO_FATURAMENTO:'Aguardando faturamento',PAGO:'Pago',EM_CONFERENCIA:'Em conferência'};
  const money = value => value == null ? 'Não confirmado' : new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value);
  function element(tag, text) { const item = document.createElement(tag); if (text != null) item.textContent = text; return item; }
  function link(text, href) { const item = element('a', text); item.href = href; return item; }
  function showMessage(type, ...nodes) {
    const box = find('message');
    box.dataset.type = type;
    box.replaceChildren(...nodes.map(node => typeof node === 'string' ? element('span', node) : node));
    box.scrollIntoView({behavior: 'smooth', block: 'nearest'});
  }
  function button(text, action) { const item = element('button', text); item.type = 'button'; item.addEventListener('click', () => action().catch(error => showMessage('error', error.message))); return item; }
  function renderVisits() {
    const comp = find('competencia-filter').value;
    const filtered = visits.filter(visit => (!comp || (visit.competencia_exame || visit.competencia) === comp) && (!find('status').value || visit.situacao === find('status').value) && (!find('tipo-filter').value || (visit.tipo_procedimento || 'VISITA') === find('tipo-filter').value) && visit.atendimento.includes(find('search').value.trim()));
    find('visits').replaceChildren();
    for (const visit of filtered) {
      const row = element('tr');
      row.append(element('td', visit.id));
      const compCell = element('td'); compCell.append(element('div', mesAno(visit.competencia_exame || visit.competencia)));
      const fatSub = element('div', `Fat. ${mesAno(visit.competencia)}`); fatSub.className = 'muted'; compCell.append(fatSub); row.append(compCell);
      for (const value of [visit.atendimento, visit.data_visita, TIPOS_PROCEDIMENTO[visit.tipo_procedimento] || 'Visita', visit.medico_nome || 'Não identificado', visit.procedimento || 'Não informado']) row.append(element('td', value));
      const etapa = flow?.visitas.find(item => item.visitaId === visit.id)?.etapa;
      row.append(visit.situacao === 'CONCILIADA' ? statusCell(etapa, visit.competencia, labels[visit.situacao]) : element('td', labels[visit.situacao]));
      row.append(element('td', visit.anterior_id || '—'));
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
    find('count').textContent = `${filtered.length} ocorrências · IDs dos procedimentos distintos são preservados`;
  }
  function atualizarConciliacao(file) {
    return button('Atualizar conciliação', async () => {
      if (busy || !await fmConfirmar('Criar uma nova versão com os procedimentos hospitalares disponíveis? Revisões e recebimentos serão preservados.')) return;
      await perform(async () => {
        const current = await request(`/api/conciliacoes/visitas/${file.relatorio_id}/acompanhamento`);
        const result = await request(`/api/base-hospitalar/relatorios/${file.relatorio_id}/atualizar?versao=${current.versao}`, {method:'POST'});
        await load();
        const atualizado = arquivosCarregados.find(item => item.id === file.id) || file;
        const resumo = parseFaturadas(atualizado).map(({comp, total}) => `${mesAno(comp)}: ${total}`).join(' · ');
        showMessage('success', `Nova versão criada. Procedimentos conciliados por competência faturada pelo hospital — ${resumo || 'nenhum'}. Confira e libere novamente para o médico. `, link('Abrir relatório', `/conciliacao.html?relatorio=${result.id}`));
      });
    });
  }
  const mesAno = comp => comp ? comp.split('-').reverse().join('/') : '—';
  function parseFaturadas(file) { return String(file.competencias_faturadas || '').split(',').filter(Boolean).map(item => { const [comp, total] = item.split(':'); return {comp, total: Number(total)}; }); }
  function chipsCompetencias(titulo, texto, competenciaArquivo) {
    const box = element('div'); box.className = 'faturadas'; box.append(element('span', `${titulo} `));
    for (const item of String(texto).split(',').filter(Boolean)) {
      const [comp, total] = item.split(':'); const chip = element('span', `${mesAno(comp)} · ${total}`);
      chip.className = 'chip' + (comp !== competenciaArquivo ? ' chip-outra' : ''); box.append(chip);
    }
    return box;
  }
  function competenciasFaturadas(file) {
    const box = element('div'); box.className = 'faturadas';
    const lista = parseFaturadas(file);
    if (!lista.length) { box.append(element('span', 'Nenhum procedimento conciliado ainda')); return box; }
    box.append(element('span', 'Faturado pelo hospital em: '));
    for (const {comp, total} of lista) { const chip = element('span', `${mesAno(comp)} · ${total}`); chip.className = 'chip' + (comp !== file.competencia ? ' chip-outra' : ''); chip.title = comp !== file.competencia ? 'Competência diferente da produção' : 'Mesma competência da produção'; box.append(chip); }
    return box;
  }
  // Etapas a partir de faturado indicam conciliacao com status PAGA pelo hospital.
  const ETAPAS_PAGAS = ['PAGO'];
  function statusCell(etapa, competencia, texto) {
    const td = element('td');
    if (ETAPAS_PAGAS.includes(etapa)) { const tag = element('span', `PAGO · ${mesAno(competencia)}`); tag.className = 'chip chip-pago'; td.append(tag); }
    else td.textContent = texto;
    return td;
  }
  function fileRow(file) {
    const row = element('tr');
    const origem = file.tipo === 'HOSPITAL' ? (file.modo_hospital === 'ATUALIZAR' ? 'Hospital · atualização' : 'Hospital') : 'Produção';
    const tipo = TIPOS_PROCEDIMENTO[file.tipo_procedimento] || (file.tipo === 'HOSPITAL' ? 'Automático' : '—');
    const medico = file.tipo === 'PRODUCAO' ? (file.medico || (file.modo_producao === 'VARIOS' ? 'Vários médicos' : '—')) : '—';
    const nome = element('td'); nome.className = 'file-name'; nome.append(element('div', file.nome));
    if (file.tipo === 'PRODUCAO') nome.append(competenciasFaturadas(file));
    if (file.tipo === 'HOSPITAL' && file.competencias_exames) nome.append(chipsCompetencias('Exames de:', file.competencias_exames, file.competencia));
    row.append(nome);
    for (const value of [origem, tipo, medico, file.quantidade_registros ?? '—', String(file.criado_em || '').slice(0, 10).split('-').reverse().join('/')]) row.append(element('td', value));
    const actions = element('td'); actions.append(link('Baixar', `/api/base-hospitalar/arquivos/${file.id}/original`));
    if (file.relatorio_id) { actions.append(link('Abrir conciliação', `/conciliacao.html?relatorio=${file.relatorio_id}`)); actions.append(atualizarConciliacao(file)); }
    if (file.tipo === 'HOSPITAL' || file.relatorio_id) actions.append(excluirArquivo(file));
    row.append(actions); return row;
  }
  function excluirArquivo(file) {
    const item = button('Excluir', async () => {
      if (busy) return;
      const hospital = file.tipo === 'HOSPITAL';
      const texto = hospital
        ? `A planilha "${file.nome}" (${file.competencia.split('-').reverse().join('/')}) e os ${file.quantidade_registros ?? ''} procedimentos que ela criou serão excluídos definitivamente. Só é permitido se nenhum procedimento já estiver conciliado com produção. Depois você pode importá-la de novo na competência certa.`
        : `A produção "${file.nome}" e o relatório de conciliação dela (com todas as versões, revisões, publicações e recebimentos) serão excluídos definitivamente. Os procedimentos do hospital voltam a aguardar produção.`;
      const resposta = await window.fmUi.perguntar({titulo: 'Excluir arquivo importado', texto, campos: [{nome: 'senha', rotulo: 'Confirme com sua senha', tipo: 'password', obrigatorio: true}], confirmar: 'Excluir definitivamente', perigo: true});
      if (!resposta?.senha) return;
      await perform(async () => {
        const url = hospital ? `/api/base-hospitalar/arquivos/${file.id}/exclusao` : `/api/conciliacoes/visitas/${file.relatorio_id}/exclusao`;
        await request(url, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({senha: resposta.senha})});
        showMessage('success', hospital ? `Planilha excluída. Importe-a novamente na competência correta.` : 'Produção e relatório de conciliação excluídos.');
        await load();
      });
    });
    item.className = 'danger-link'; return item;
  }
  function renderCompetencias(files) {
    const box = find('competencias'), groups = new Map();
    for (const file of files) { if (!groups.has(file.competencia)) groups.set(file.competencia, []); groups.get(file.competencia).push(file); }
    if (!groups.size) { box.replaceChildren(element('p', 'Nenhuma importação.')); return; }
    const abertas = new Set([...box.querySelectorAll('details[open]')].map(d => d.dataset.comp));
    const keys = [...groups.keys()].sort().reverse();
    box.replaceChildren(...keys.map((comp, index) => {
      const list = groups.get(comp), hosp = list.filter(f => f.tipo === 'HOSPITAL'), prod = list.filter(f => f.tipo === 'PRODUCAO');
      const total = hosp.reduce((s, f) => s + (f.quantidade_registros || 0), 0);
      const det = element('details'); det.className = 'competencia-card'; det.dataset.comp = comp;
      det.open = abertas.size ? abertas.has(comp) : (index === 0 || keys.length === 1 || find('competencia-filter').value === comp);
      const sum = element('summary'); sum.append(element('strong', comp.split('-').reverse().join('/')));
      for (const txt of [`${hosp.length} arquivo(s) do hospital · ${total} registros`, `${prod.length} produção(ões)`]) { const chip = element('span', txt); chip.className = 'chip'; sum.append(chip); }
      det.append(sum);
      const wrap = element('div'); wrap.className = 'conciliation-table';
      const table = element('table'), head = element('tr');
      for (const h of ['Arquivo', 'Origem', 'Tipo', 'Médico', 'Registros', 'Importado em', 'Ações']) head.append(element('th', h));
      const thead = element('thead'); thead.append(head);
      const tbody = element('tbody'); [...hosp, ...prod].forEach(f => tbody.append(fileRow(f)));
      table.append(thead, tbody); wrap.append(table); det.append(wrap); return det;
    }));
  }
  async function load() {
    const token = ++generation, hospital = find('hospital').value;
    if (!hospital) { visits = []; flow = null; renderFlow(); renderVisits(); renderCompetencias([]); return; }
    let [data, files, tracking] = await Promise.all([request(`/api/base-hospitalar/visitas?hospitalId=${hospital}`), request(`/api/base-hospitalar/arquivos?hospitalId=${hospital}`), request(`/api/base-hospitalar/acompanhamento?hospitalId=${hospital}${find('competencia-filter').value ? `&competencia=${find('competencia-filter').value}` : ''}`)]);
    if (token !== generation) return;
    const filter = find('competencia-filter'), atual = filter.value;
    // Competencias das planilhas e tambem dos exames que elas trazem (uma NF de 09 pode ter exames de 07 e 08).
    const comps = [...new Set([...files.map(f => f.competencia), ...files.flatMap(f => String(f.competencias_exames || '').split(',').filter(Boolean).map(item => item.split(':')[0]))])].sort().reverse();
    filter.replaceChildren(new Option('Todas as competências', ''), ...comps.map(c => new Option(c.replace('-', '/'), c)));
    filter.value = comps.includes(atual) ? atual : '';
    arquivosCarregados = files;
    files = files.filter(f => !filter.value || f.competencia === filter.value || String(f.competencias_exames || '').split(',').some(item => item.startsWith(filter.value + ':')));
    visits = data; flow = tracking; renderFlow(); renderVisits(); renderCompetencias(files);
  }
  async function perform(action) {
    if (busy) return;
    busy = true;
    document.querySelectorAll('button, input, select, textarea').forEach(control => { control.disabled = true; });
    try { await action(); }
    finally { busy = false; document.querySelectorAll('button, input, select, textarea').forEach(control => { control.disabled = false; }); updateDecision(); }
  }
  // Opcoes de medico e empresa vem das proprias linhas do acompanhamento, preservando a selecao.
  function fillFlowOptions() {
    const medicos = [...new Set(flow.visitas.map(v => v.medico).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'pt-BR'));
    const empresas = new Map(); let semEmpresa = false;
    for (const v of flow.visitas) { if (v.empresaId != null) empresas.set(String(v.empresaId), v.empresa); else semEmpresa = true; }
    const selMedico = find('flow-medico'), selEmpresa = find('flow-empresa'), medicoAtual = selMedico.value, empresaAtual = selEmpresa.value;
    selMedico.replaceChildren(new Option('Todos os médicos', ''), ...medicos.map(m => new Option(m, m)));
    selEmpresa.replaceChildren(new Option('Todas as empresas', ''), ...[...empresas].sort((a, b) => a[1].localeCompare(b[1], 'pt-BR')).map(([id, nome]) => new Option(nome, id)), ...(semEmpresa ? [new Option('Sem empresa (sem produção vinculada)', 'SEM')] : []));
    selMedico.value = medicos.includes(medicoAtual) ? medicoAtual : '';
    selEmpresa.value = [...selEmpresa.options].some(o => o.value === empresaAtual) ? empresaAtual : '';
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
    find('flow-money').textContent = `Subtotais confirmados · Faturado: ${money(flow.faturado)} · Recebido: ${money(flow.recebido)}`;
    fillFlowOptions();
    const etapa = find('flow-filter').value, medico = find('flow-medico').value, empresa = find('flow-empresa').value;
    const {inicio, fim} = periodo ? periodo.valor() : {inicio: null, fim: null};
    const filtradas = flow.visitas.filter(item => (!etapa || item.etapa === etapa) && (!medico || item.medico === medico)
      && (!empresa || (empresa === 'SEM' ? item.empresaId == null : String(item.empresaId) === empresa))
      && (!inicio || (String(item.data).slice(0, 10) >= inicio && String(item.data).slice(0, 10) <= fim)));
    const soma = campo => filtradas.reduce((s, v) => s + (v[campo] || 0), 0);
    find('flow-filtered').textContent = (etapa || medico || empresa || inicio)
      ? `${filtradas.length} procedimentos nos filtros · Faturado: ${money(soma('faturado'))} · Recebido: ${money(soma('recebido'))}`
      : `${filtradas.length} procedimentos`;
    const params = new URLSearchParams({hospitalId: find('hospital').value});
    for (const [chave, valor] of [['competencia', find('competencia-filter').value], ['etapa', etapa], ['medico', medico], ['empresa', empresa], ['dataInicio', inicio], ['dataFim', fim]]) if (valor) params.set(chave, valor);
    find('flow-export').href = `/api/base-hospitalar/acompanhamento/exportar?${params}`;
    // Colunas agrupadas para caber na tela; o Excel mantem um campo por coluna.
    const celula = (principal, secundario, classe) => {
      const td = element('td'); td.append(element('div', principal));
      if (secundario) { const sub = element('div', secundario); sub.className = 'sub'; td.append(sub); }
      if (classe) td.className = classe; return td;
    };
    const dataBr = data => data ? String(data).slice(0, 10).split('-').reverse().join('/') : '—';
    for (const visit of filtradas) {
      const row = element('tr');
      row.append(element('td', visit.visitaId));
      row.append(celula(visit.atendimento, dataBr(visit.data)));
      row.append(celula(visit.paciente || '—', visit.convenio || null, 'col-paciente'));
      const proc = celula(visit.procedimento || 'Não informado', null, 'col-proc');
      if (visit.codigoProcedimento) { const cod = element('div', visit.codigoProcedimento); cod.className = 'sub'; proc.prepend(cod); }
      row.append(proc);
      row.append(celula(visit.medico || 'Não identificado', visit.empresa || null, 'col-medico'));
      const comp = element('td'); comp.className = 'col-comp';
      const referencia = visit.competenciaProducao || visit.competenciaExame;
      const outra = referencia && visit.competencia !== referencia;
      for (const [rotulo, valor, destaque] of [['Exame', mesAno(visit.competenciaExame), false], ['Prod.', mesAno(visit.competenciaProducao), false], ['Fat.', mesAno(visit.competencia), outra]]) {
        const linha = element('div'); linha.append(element('span', `${rotulo} `)); linha.firstChild.className = 'sub';
        const v = element('strong', valor); if (destaque) { v.className = 'comp-outra'; v.title = 'Faturado pelo hospital em competência diferente da produção'; }
        linha.append(v); comp.append(linha);
      }
      row.append(comp);
      const etapaCell = statusCell(visit.etapa, visit.competencia, stages[visit.etapa]); etapaCell.classList.add('col-etapa'); row.append(etapaCell);
      const valores = element('td'); valores.className = 'col-valores';
      if (visit.faturado == null) valores.append(element('div', 'Não confirmado'));
      else for (const [rotulo, valor] of [['Faturado', visit.faturado], ['Recebido', visit.recebido]]) {
        const linha = element('div'); const r = element('span', rotulo); r.className = 'sub'; linha.append(r, element('span', money(valor))); valores.append(linha);
      }
      row.append(valores);
      const actions = element('td'); actions.className = 'col-acoes';
      if (visit.relatorio) actions.append(link('Revisar',`/conciliacao.html?relatorio=${visit.relatorio}&linha=${visit.linha}`));
      row.append(actions); find('flow-rows').append(row);
    }
  }
  function updateDecision() {
    const duplicate = find('decision-action').value === 'DUPLICADO_PAGO_OUTRO_MES';
    const original = duplicate || find('decision-action').value === 'DUPLICADO_MESMA_COMPETENCIA';
    const originalField = find('decision-form').elements.namedItem('anteriorId');
    originalField.required = original; originalField.disabled = !original;
    const evidenceField = find('decision-form').elements.namedItem('evidencia');
    evidenceField.required = duplicate; evidenceField.disabled = !duplicate;
    find('original-label').hidden = !original; find('evidence-label').hidden = !duplicate;
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
      find('decision-dialog').close(); showMessage('success', 'Decisão registrada. Atualize a conciliação das produções afetadas, se necessário.'); await load();
    }); } catch (error) { find('decision-message').textContent = error.message; }
  });
  find('decision-action').addEventListener('change', updateDecision);
  document.querySelectorAll('.base-tabs [data-tab]').forEach(tab => tab.addEventListener('click', () => {
    document.querySelectorAll('.base-tabs [data-tab]').forEach(item => { item.setAttribute('aria-selected', String(item === tab)); find(item.dataset.tab).hidden = item !== tab; });
  }));
  find('flow-filter').replaceChildren(new Option('Todas as etapas',''),...Object.entries(stages).map(([value,label]) => new Option(label,value)));
  for (const id of ['flow-filter','flow-medico','flow-empresa']) find(id).addEventListener('change', renderFlow);
  periodo = window.FmPeriodo.criar(find('flow-periodo'), {aoMudar: renderFlow, rotulo: 'Período do procedimento'});
  find('decision-close').addEventListener('click', () => find('decision-dialog').close());
  find('history-close').addEventListener('click', () => find('history-dialog').close());
  for (const id of ['status','tipo-filter','search']) find(id).addEventListener('input', renderVisits);
  find('competencia-filter').addEventListener('change', () => load().catch(error => showMessage('error', error.message)));
  find('hospital').addEventListener('change', () => { find('competencia-filter').value = ''; load().catch(error => showMessage('error', error.message)); });
  find('reload').addEventListener('click', () => load().catch(error => showMessage('error', error.message)));
  async function initialize() {
    const hospitals = await request("/api/hospitais");
    find('hospital').replaceChildren(new Option('Selecione um hospital', ''), ...hospitals.filter(item => item.ativo).map(item => new Option(item.nome, item.id)));
    const params = new URLSearchParams(location.search);
    if (hospitals.some(item => item.ativo && String(item.id) === params.get('hospitalId'))) find('hospital').value = params.get('hospitalId');
    await load(); updateDecision();
  }
  initialize().catch(error => showMessage('error', error.message));
})();
