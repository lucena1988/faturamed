(() => {
  const $ = s => document.querySelector(s);
  const TIPOS = {POR_EXAME: 'Por exame', POR_AGENDA: 'Fixo por agenda', TABELA_AGENDA: 'Tabela por quantidade', MENSAL_MEDICO: 'Fixo mensal'};
  const PERIODOS = {MANHA: 'Manhã', TARDE: 'Tarde', NOITE: 'Noite', INTEGRAL: 'Integral'};
  let exames = [];
  let regras = [];
  let regraEditando = null;
  let previaAtual = null;

  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c]);
  const brl = v => v == null ? '—' : Number(v).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'});
  const data = d => d ? new Date(d + 'T00:00').toLocaleDateString('pt-BR') : '—';
  const msg = (t, erro) => { if (t) fmUi.toast(t, erro ? 'erro' : 'sucesso'); };
  const ETAPAS = [{chave: 'AGUARDANDO', rotulo: 'Enviado'}, {chave: 'APROVADO', rotulo: 'Aprovado'}, {chave: 'NF_EMITIDA', rotulo: 'NF emitida'}];
  const pad = n => String(n).padStart(2, '0');
  const hoje = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };
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
    document.querySelector('.fp-aba[data-aba=regras]').innerHTML = `Regras de valor<span class="fp-contador">${regras.length}</span>`;
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
    }).join('') || fmUi.vazio(8, {titulo: 'Nenhuma regra cadastrada', texto: 'Cadastre como cada hospital remunera os exames (por exame, por agenda ou por tabela).', icone: 'regra', acao: '<button type="button" class="primary-button" data-nova-regra>+ Nova regra</button>'});
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
    $('#previa-total').textContent = `${pv.lotes} lote(s) · ${pv.etiquetas} etiqueta(s) · total ${brl(pv.total)}`;
    $('#previa-resumo').innerHTML = pv.resumo.map(r => `<tr><td>${window.fmTatil ? fmTatil.pessoa(r.medico) : esc(r.medico)}</td><td>${r.agendas}</td><td>${r.exames}</td><td class="fp-valor">${brl(r.total)}</td></tr>`).join('')
      + (pv.resumo.length ? `<tr><th>Total</th><th></th><th></th><th class="fp-valor">${brl(pv.total)}</th></tr>` : fmUi.vazio(4, {titulo: 'Nenhum lote conferido neste período', texto: 'Confira os lotes do hospital em Lotes de produção e calcule de novo.', icone: 'busca'}));
    $('#previa-calculo').innerHTML = pv.calculos.map(c => `<tr><td>${esc(c.medico)}</td><td>${data(c.data)}</td><td>${PERIODOS[c.periodo] || ''}</td>
      <td>${esc(c.descricao)}</td><td>${c.exames}</td><td>${c.pontos}</td><td class="fp-valor">${brl(c.valor)}</td></tr>`).join('');
    const alerta = $('#previa-alerta');
    alerta.hidden = !pv.semRegra.length;
    alerta.innerHTML = pv.semRegra.length ? `<strong>${pv.semRegra.length} etiqueta(s) sem regra de valor ou sem médico — o fechamento fica bloqueado:</strong>
      <ul>${pv.semRegra.map(s => `<li>${data(s.data)} · ${esc(s.medico || 'sem médico')} · ${esc(s.exame || 'sem exame')} · atend. ${esc(s.nrAtendimento)} ${esc(s.paciente)}</li>`).join('')}</ul>
      Cadastre a regra na aba "Regras de valor" ou corrija o lote.` : '';
    $('#btn-fechar').disabled = !pv.etiquetas || pv.semRegra.length > 0;
  }

  async function gerarFechamento() {
    const f = $('#form-previa');
    if (!(await fmUi.confirmar({titulo: `Gerar fechamento de ${brl(previaAtual.total)}?`, texto: `${previaAtual.lotes} lote(s) e ${previaAtual.etiquetas} etiqueta(s) serão incluídas e ficarão bloqueadas para edição até o fechamento ser cancelado.`, confirmar: 'Gerar fechamento'}))) return;
    const pronto = fmUi.carregando($('#btn-fechar'), 'Gerando…');
    const fecharCubo = window.fmTatil?.carregando('Gerando o fechamento…');
    let d;
    try { d = await api('/api/producao/fechamentos', json('POST', {hospitalId: +f.elements.hospitalId.value, inicio: f.elements.inicio.value,
      fim: f.elements.fim.value, observacao: $('#obs-fechamento').value})); } finally { pronto(); fecharCubo?.(); }
    $('#previa').hidden = true;
    msg(`Fechamento nº ${d.fechamento.id} gerado. Baixe a planilha e envie ao hospital.`);
    await listarFechamentos();
  }

  async function listarFechamentos() {
    $('#fechamentos').innerHTML = fmUi.esqueleto(9, 2);
    const lista = await api('/api/producao/fechamentos');
    renderCards(lista);
    $('#fechamentos').innerHTML = lista.map(f => `<tr data-id="${f.id}"><td>${f.id}</td><td>${esc(f.hospital)}</td><td>${data(f.dataInicio)} a ${data(f.dataFim)}</td>
      <td class="fm-num">${f.etiquetas}</td><td class="fp-valor">${brl(f.total)}</td><td>${fmUi.etapas(ETAPAS, f.status)}</td>
      <td>${f.aprovadoPor ? `${esc(f.aprovadoPor)} em ${data(f.aprovadoEm)}` : f.vistoHospitalEm ? `<small>Aberto pelo hospital em ${new Date(f.vistoHospitalEm).toLocaleString('pt-BR', {day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'})}</small>` : '<small>Ainda não aberto pelo hospital</small>'}
        ${f.diasParaPrazo != null ? `<br>${fmUi.prazo(f.diasParaPrazo)}` : ''}
        ${f.contestadas ? `<br>${fmUi.status(`${f.contestadas} contestada(s)`, 'ambar')}` : ''}${f.valorEnviado != null && Number(f.valorEnviado) !== Number(f.total) ? `<br><small>enviado: ${brl(f.valorEnviado)}</small>` : ''}</td><td>${f.nfNumero ? `${esc(f.nfNumero)} (${data(f.nfData)})` : '—'}</td>
      <td><div class="fm-celula-acoes">
        ${f.status === 'AGUARDANDO' ? `<button type="button" class="secondary-button" data-aprovar="${f.id}">Registrar ok</button>` : ''}
        ${f.status === 'APROVADO' ? `<button type="button" class="secondary-button" data-nf="${f.id}">Registrar NF</button>` : ''}
        ${fmUi.menuAcoes([
          `<a role="menuitem" href="/api/producao/fechamentos/${f.id}/planilha">Baixar planilha</a>`,
          `<button type="button" role="menuitem" data-historico="${f.id}">Ver histórico</button>`,
          ...(f.nfArquivoNome ? [`<a role="menuitem" href="/api/producao/fechamentos/${f.id}/nf/arquivo">Baixar NF (${esc(f.nfArquivoNome)})</a>`] : []),
          ...(f.status === 'NF_EMITIDA' ? [`<button type="button" role="menuitem" data-anexar-nf="${f.id}">${f.nfArquivoNome ? 'Trocar arquivo da NF' : 'Anexar arquivo da NF'}</button>`] : []),
          ...(f.status === 'AGUARDANDO' ? ['<hr>', `<button type="button" role="menuitem" class="perigo" data-cancelar="${f.id}">Cancelar fechamento</button>`] : [])])}
      </div></td></tr>`).join('')
      || fmUi.vazio(9, {titulo: 'Nenhum fechamento ainda', texto: 'Escolha o hospital e o período acima e clique em "Calcular prévia".', icone: 'fechamento'});
  }

  async function enviarArquivoNf(id, arquivo) {
    const fd = new FormData();
    fd.append('arquivo', arquivo);
    await api(`/api/producao/fechamentos/${id}/nf/arquivo`, {method: 'POST', body: fd});
  }

  async function listarContestacoes() {
    const lista = await api('/api/producao/contestacoes');
    $('#contestacoes-box').hidden = !lista.length;
    $('#contestacoes-qtd').textContent = lista.length;
    $('#contestacoes').innerHTML = lista.map(c => `<tr><td>${esc(c.hospital)}</td><td>nº ${c.fechamentoId}</td><td>${data(c.data)}</td>
      <td>${esc(c.medico)}<br><small>${esc(c.exame)}</small></td><td>${esc(c.nrAtendimento)}<br><small>${esc(c.paciente)}</small></td>
      <td>${esc(c.motivo)}<br><small>${esc(c.por || '')}</small></td>
      <td><div class="fm-celula-acoes"><button type="button" class="secondary-button" data-reincluir="${c.itemId}">Reincluir</button>
        <button type="button" class="text-button prod-perigo" data-aceitar="${c.itemId}">Aceitar</button></div></td></tr>`).join('');
  }
  $('#contestacoes').addEventListener('click', proteger(async e => {
    const b = e.target.closest('[data-reincluir], [data-aceitar]'); if (!b) return;
    const reincluir = !!b.dataset.reincluir;
    const r = await fmUi.perguntar({titulo: reincluir ? 'Reincluir etiqueta' : 'Aceitar contestação',
      texto: reincluir ? 'A etiqueta volta a ficar disponível e entra no próximo fechamento deste hospital.' : 'A etiqueta não será faturada.',
      campos: [{nome: 'resposta', rotulo: 'Resposta ao hospital (opcional)'}], confirmar: reincluir ? 'Reincluir' : 'Aceitar contestação'});
    if (!r) return;
    await api(`/api/producao/contestacoes/${b.dataset.reincluir || b.dataset.aceitar}`, json('POST', {decisao: reincluir ? 'REINCLUIR' : 'ACEITA', resposta: r.resposta}));
    msg(reincluir ? 'Etiqueta reincluída para o próximo fechamento.' : 'Contestação aceita.');
    await listarContestacoes();
  }));

  function renderCards(lista) {
    const soma = st => lista.filter(f => f.status === st).reduce((t, f) => t + Number(f.total), 0);
    const qtd = st => lista.filter(f => f.status === st).length;
    const mes = hoje().slice(0, 7);
    const nfMes = lista.filter(f => f.status === 'NF_EMITIDA' && (f.nfData || '').startsWith(mes));
    // cards simples: sem bento, borda animada nem minigrafico (pedido do usuario)
    $('#cards').innerHTML = [
      {icone: 'relogio', rotulo: 'Aguardando ok do hospital', valor: brl(soma('AGUARDANDO')), sub: `${qtd('AGUARDANDO')} fechamento(s)`, zerado: !qtd('AGUARDANDO')},
      {icone: 'check', rotulo: 'Aprovado, aguardando NF', valor: brl(soma('APROVADO')), sub: `${qtd('APROVADO')} fechamento(s)`, zerado: !qtd('APROVADO')},
      {icone: 'nota', rotulo: 'NF emitida neste mês', valor: brl(nfMes.reduce((t, f) => t + Number(f.total), 0)), sub: `${nfMes.length} nota(s)`, zerado: !nfMes.length}
    ].map(fmUi.card).join('');
  }

  $('#fechamentos').addEventListener('click', proteger(async e => {
    const b = e.target.closest('button'); if (!b || !(b.dataset.historico || b.dataset.aprovar || b.dataset.nf || b.dataset.cancelar || b.dataset.anexarNf)) return;
    let pulsar = null;
    if (b.dataset.anexarNf) {
      const r = await fmUi.perguntar({titulo: 'Anexar arquivo da NF', texto: 'O hospital poderá baixar a nota pelo portal.', confirmar: 'Anexar',
        campos: [{nome: 'arquivo', rotulo: 'Arquivo da NF (PDF, XML ou imagem)', tipo: 'file', obrigatorio: true}]});
      if (!r || !(r.arquivo instanceof File) || !r.arquivo.size) return;
      await enviarArquivoNf(b.dataset.anexarNf, r.arquivo);
      msg('Arquivo da NF anexado.');
      await listarFechamentos();
      return;
    }
    if (b.dataset.historico) { await fmUi.mostrarHistorico(`Histórico do fechamento nº ${b.dataset.historico}`, `/api/producao/historico/fechamento/${b.dataset.historico}`); return; }
    if (b.dataset.aprovar) {
      const r = await fmUi.perguntar({titulo: 'Registrar ok do hospital', texto: 'Informe quem conferiu e aprovou a produção no hospital.', confirmar: 'Registrar aprovação',
        campos: [{nome: 'aprovadoPor', rotulo: 'Aprovado por (nome ou e-mail)', obrigatorio: true}, {nome: 'data', rotulo: 'Data da aprovação', tipo: 'date', valor: hoje(), obrigatorio: true}]});
      if (!r) return;
      await api(`/api/producao/fechamentos/${b.dataset.aprovar}/aprovar`, json('POST', r));
      msg('Aprovação registrada. Agora é possível registrar a NF.');
      pulsar = b.dataset.aprovar;
    } else if (b.dataset.nf) {
      const r = await fmUi.perguntar({titulo: 'Registrar nota fiscal', texto: 'Informe a NF emitida para este fechamento.', confirmar: 'Registrar NF',
        campos: [{nome: 'numero', rotulo: 'Número da NF', obrigatorio: true}, {nome: 'data', rotulo: 'Data de emissão', tipo: 'date', valor: hoje(), obrigatorio: true},
          {nome: 'arquivo', rotulo: 'Arquivo da NF (PDF, XML ou imagem) — opcional, o hospital baixa pelo portal', tipo: 'file'}]});
      if (!r) return;
      await api(`/api/producao/fechamentos/${b.dataset.nf}/nf`, json('POST', {numero: r.numero, data: r.data}));
      if (r.arquivo instanceof File && r.arquivo.size) await enviarArquivoNf(b.dataset.nf, r.arquivo);
      msg('NF registrada. O hospital foi avisado por e-mail.');
      pulsar = b.dataset.nf;
    } else if (b.dataset.cancelar) {
      if (!(await fmUi.confirmar({titulo: 'Cancelar este fechamento?', texto: 'Os lotes voltam a ficar conferidos e disponíveis para um novo fechamento.', confirmar: 'Cancelar fechamento', cancelar: 'Voltar', perigo: true}))) return;
      await api(`/api/producao/fechamentos/${b.dataset.cancelar}`, {method: 'DELETE'});
      msg('Fechamento cancelado.');
    }
    await listarFechamentos();
    if (pulsar) window.fmTatil?.confete();
    if (pulsar) window.fmEfeitos?.pulso(document.querySelector(`#fechamentos tr[data-id="${pulsar}"]`));
  }));

  $('#regras').addEventListener('click', proteger(async e => {
    const b = e.target.closest('button'); if (!b) return;
    const id = +(b.dataset.editar || b.dataset.copiar || b.dataset.excluir);
    const regra = regras.find(r => r.id === id);
    if (b.dataset.editar) abrirRegra(regra, false);
    else if (b.dataset.copiar) abrirRegra(regra, true);
    else if (b.dataset.excluir && await fmUi.confirmar({titulo: `Excluir a regra "${regra.nome}"?`, texto: 'Regras já usadas em fechamento não podem ser excluídas; nesse caso encerre a vigência.', confirmar: 'Excluir regra', perigo: true})) { await api(`/api/producao/regras/${id}`, {method: 'DELETE'}); msg('Regra excluída.'); await listarRegras(); }
    else if (b.dataset.novaRegra !== undefined) abrirRegra(null, false);
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
    const f = $('#form-previa').elements;
    const dataAtual = hoje();
    f.inicio.value = dataAtual;
    f.fim.value = dataAtual;
    const params = new URLSearchParams(location.search);
    if (params.get('hospitalId')) { $('#regras-hospital').value = params.get('hospitalId'); $('#form-previa').elements.hospitalId.value = params.get('hospitalId'); }
    if (params.get('inicio')) f.inicio.value = params.get('inicio');
    if (params.get('fim')) f.fim.value = params.get('fim');
    await Promise.all([listarFechamentos(), listarRegras(), listarContestacoes()]);
    if (params.get('aba') === 'regras') document.querySelector('.fp-aba[data-aba=regras]').click();
  })();
})();
