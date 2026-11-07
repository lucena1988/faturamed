(() => {
  const $ = s => document.querySelector(s);
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c]);
  const brl = v => v == null ? '—' : Number(v).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'});
  const data = d => d ? new Date(String(d).slice(0, 10) + 'T00:00').toLocaleDateString('pt-BR') : '—';
  const PERIODOS = {MANHA: 'Manhã', TARDE: 'Tarde', NOITE: 'Noite', INTEGRAL: 'Integral'};
  const ETAPAS = [{chave: 'AGUARDANDO', rotulo: 'Recebido'}, {chave: 'APROVADO', rotulo: 'Liberado p/ NF'}, {chave: 'NF_EMITIDA', rotulo: 'NF emitida'}];
  const msg = (t, erro) => { if (t) fmUi.toast(t, erro ? 'erro' : 'sucesso'); };
  const json = (method, body) => ({method, headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
  async function api(url, opcoes = {}) {
    const r = await fetch(url, opcoes);
    if (r.status === 204) return null;
    const corpo = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(corpo.message || corpo.erro || corpo.detail || `Erro ${r.status}`);
    return corpo;
  }
  const proteger = fn => async (...a) => { try { await fn(...a); } catch (e) { msg(e.message, true); } };
  let atual = null; // conferencia aberta
  let filtro = 'pendentes';
  let salvoEm = null;
  const marcarSalvo = () => { salvoEm = new Date(); pintarSalvo(); };
  const pintarSalvo = () => {
    const el = $('#conf-salvo'); if (!el) return;
    el.textContent = salvoEm ? `✓ Progresso salvo às ${salvoEm.toLocaleTimeString('pt-BR', {hour: '2-digit', minute: '2-digit'})}` : '✓ Tudo o que você marca é salvo na hora';
  };

  // ---------------- lista ----------------
  async function listar() {
    $('#fechamentos').innerHTML = fmUi.esqueleto(7, 3);
    const lista = await api('/api/hospital-portal/fechamentos');
    const por = st => lista.filter(f => f.status === st);
    const soma = l => l.reduce((t, f) => t + Number(f.total), 0);
    $('#cards').innerHTML = [
      {icone: 'relogio', rotulo: 'Para conferir', valor: String(por('AGUARDANDO').length), sub: brl(soma(por('AGUARDANDO'))), zerado: !por('AGUARDANDO').length, classe: por('AGUARDANDO').length ? 'atencao' : ''},
      {icone: 'check', rotulo: 'Liberados, aguardando NF', valor: String(por('APROVADO').length), sub: brl(soma(por('APROVADO'))), zerado: !por('APROVADO').length},
      {icone: 'nota', rotulo: 'NF emitidas', valor: String(por('NF_EMITIDA').length), sub: brl(soma(por('NF_EMITIDA'))), zerado: !por('NF_EMITIDA').length}
    ].map(fmUi.card).join('');
    $('#fechamentos').innerHTML = lista.map(f => `<tr data-id="${f.id}" tabindex="0">
        <td>${f.id}</td><td>${data(f.dataInicio)} a ${data(f.dataFim)}</td><td class="fm-num">${f.etiquetas}</td><td class="fp-valor">${brl(f.total)}</td>
        <td>${fmUi.etapas(ETAPAS, f.status)}</td><td>${f.nfNumero ? `${esc(f.nfNumero)} (${data(f.nfData)})${f.nfArquivoNome ? `<br><a class="text-button" href="/api/hospital-portal/fechamentos/${f.id}/nf">Baixar NF</a>` : ''}` : '—'}</td>
        <td><button type="button" class="${f.status === 'AGUARDANDO' ? 'primary-button' : 'secondary-button'}" data-abrir="${f.id}">${f.status === 'AGUARDANDO' ? 'Conferir' : 'Ver'}</button></td></tr>`).join('')
      || fmUi.vazio(7, {titulo: 'Nenhum fechamento recebido ainda', texto: 'Quando a FaturaMed enviar um fechamento de produção, ele aparece aqui para conferência.', icone: 'fechamento'});
  }

  // ---------------- conferencia ----------------
  function mostrar(view) { $('#view-lista').hidden = view !== 'lista'; $('#view-conferencia').hidden = view !== 'conf'; }

  async function abrir(id) {
    atual = await api(`/api/hospital-portal/fechamentos/${id}`);
    filtro = 'pendentes';
    salvoEm = null;
    mostrar('conf');
    desenhar();
    scrollTo({top: 0, behavior: 'smooth'});
  }

  function desenhar() {
    const {detalhe, lotes, valorEnviado} = atual;
    const f = detalhe.fechamento;
    const aberto = f.status === 'AGUARDANDO';
    $('#conf-titulo').textContent = `Fechamento nº ${f.id}`;
    $('#conf-sub').textContent = `${data(f.dataInicio)} a ${data(f.dataFim)}${f.observacao ? ' · ' + f.observacao : ''}`;
    $('#conf-etapas').innerHTML = fmUi.etapas(ETAPAS, f.status);
    const contestadas = detalhe.etiquetas.filter(e => e.contestacao === 'PENDENTE' || e.situacao === 'RETIRADA');
    $('#conf-cards').innerHTML = [
      {icone: 'nota', rotulo: aberto ? 'Valor enviado' : 'Valor aprovado', valor: brl(f.total), sub: !aberto && Number(valorEnviado) !== Number(f.total) ? `enviado: ${brl(valorEnviado)}` : `${f.etiquetas} etiqueta(s)`},
      {icone: 'check', rotulo: 'Lotes conferidos', valor: `${lotes.filter(l => l.conferidoEm).length} de ${lotes.length}`, sub: 'marque cada lote abaixo'},
      {icone: 'relogio', rotulo: 'Etiquetas contestadas', valor: String(contestadas.length), sub: aberto ? 'saem do valor ao aprovar' : 'em análise pela FaturaMed', zerado: !contestadas.length}
    ].map(fmUi.card).join('');
    $('#conf-resumo').innerHTML = detalhe.resumo.map(r => `<tr><td>${esc(r.medico)}</td><td class="fm-num">${r.agendas}</td><td class="fm-num">${r.exames}</td><td class="fp-valor">${brl(r.total)}</td></tr>`).join('')
      + `<tr><th>Total</th><th></th><th></th><th class="fp-valor">${brl(f.total)}</th></tr>`;
    const temContestacao = l => detalhe.etiquetas.some(e => e.loteId === l.loteId && (e.contestacao === 'PENDENTE' || e.situacao === 'RETIRADA'));
    const pendentes = lotes.filter(l => !l.conferidoEm);
    if (filtro === 'pendentes' && !pendentes.length) filtro = 'todos';
    const visiveis = filtro === 'pendentes' ? pendentes : filtro === 'contestados' ? lotes.filter(temContestacao) : lotes;
    $('#qtd-pendentes').textContent = pendentes.length;
    $('#qtd-todos').textContent = lotes.length;
    $('#qtd-contestados').textContent = lotes.filter(temContestacao).length;
    document.querySelectorAll('[data-filtro]').forEach(b => { b.classList.toggle('on', b.dataset.filtro === filtro); b.setAttribute('aria-checked', String(b.dataset.filtro === filtro)); });
    $('#conf-barra-progresso').style.width = `${lotes.length ? Math.round((lotes.length - pendentes.length) / lotes.length * 100) : 0}%`;
    $('#btn-conferir-todos').hidden = !aberto || !pendentes.length;
    $('#btn-continuar-depois').hidden = !aberto;
    pintarSalvo();
    $('#conf-lotes').innerHTML = (visiveis.length ? '' : `<p class="prod-dica hp-vazio">${filtro === 'contestados' ? 'Nenhum lote com contestação.' : 'Todos os lotes já foram conferidos.'}</p>`) + visiveis.map(l => {
      const etiquetas = detalhe.etiquetas.filter(e => e.loteId === l.loteId);
      return `<details class="hp-lote${l.conferidoEm ? ' conferido' : ''}" data-lote="${l.loteId}" ${aberto && !l.conferidoEm && visiveis.indexOf(l) < 3 ? 'open' : ''}>
        <summary><span><span class="hp-lote-titulo">${data(l.data)} · ${PERIODOS[l.periodo] || l.periodo}</span><br>
          <span class="hp-lote-sub">${esc(l.medicos)} · ${esc(l.exames)} · ${l.etiquetas} etiqueta(s)${l.contestadas ? ` · <b>${l.contestadas} contestada(s)</b>` : ''}</span></span>
          <span class="hp-lote-acoes"><label class="hp-check" title="${l.conferidoEm ? `Conferido por ${esc(l.conferidoPor)}` : ''}"><input type="checkbox" data-conferir="${l.loteId}" ${l.conferidoEm ? 'checked' : ''} ${aberto ? '' : 'disabled'}>${l.conferidoEm ? 'Lote conferido' : 'Marcar como conferido'}</label></span></summary>
        <div class="account-table"><table><thead><tr><th>Atendimento</th><th>Data/médico</th><th>Paciente</th><th>Exame</th><th>Convênio</th><th>Carteirinha</th><th></th></tr></thead><tbody>
        ${etiquetas.map(e => {
          const contestada = e.contestacao === 'PENDENTE' || e.contestacao === 'EM_ANALISE' || e.situacao === 'RETIRADA';
          const motivo = contestada && e.contestacaoMotivo ? `<span class="hp-motivo">Contestada: ${esc(e.contestacaoMotivo)}${e.contestacaoResposta ? ` · Resposta FaturaMed: ${esc(e.contestacaoResposta)}` : ''}</span>` : '';
          const acao = !aberto ? (e.situacao === 'RETIRADA' ? fmUi.status('Retirada do valor', 'cinza') : '')
            : e.contestacao === 'PENDENTE' ? `<button type="button" class="text-button" data-retirar="${e.itemId}">Retirar contestação</button>`
            : `<button type="button" class="text-button hp-contestar" data-contestar="${e.itemId}" data-nr="${esc(e.nrAtendimento)}">Contestar</button>`;
          return `<tr class="${contestada ? 'contestada' : ''} ${e.situacao === 'RETIRADA' ? 'retirada' : ''}"><td>${esc(e.nrAtendimento)}${motivo}</td>
            <td>${data(e.data)}<br><small>${esc(e.medico)}</small></td><td>${esc(e.paciente)}</td><td>${esc(e.exame)}</td>
            <td>${esc(e.convenio)}${e.plano ? `<br><small>${esc(e.plano)}</small>` : ''}</td><td>${esc(e.carteirinha)}</td><td>${acao}</td></tr>`;
        }).join('')}</tbody></table></div></details>`;
    }).join('');
    const conferidos = lotes.filter(l => l.conferidoEm).length;
    const pendContest = detalhe.etiquetas.filter(e => e.contestacao === 'PENDENTE').length;
    $('#conf-progresso').textContent = aberto
      ? `${conferidos} de ${lotes.length} lote(s) conferido(s)${pendContest ? ` · ${pendContest} etiqueta(s) contestada(s)` : ''}`
      : f.status === 'NF_EMITIDA' ? `NF ${f.nfNumero} emitida em ${data(f.nfData)}` : `Liberado para NF por ${f.aprovadoPor} em ${data(f.aprovadoEm)}`;
    $('#btn-nf').hidden = !f.nfArquivoNome;
    $('#btn-nf').href = `/api/hospital-portal/fechamentos/${f.id}/nf`;
    $('#btn-aprovar').hidden = !aberto;
    $('#btn-aprovar').disabled = conferidos < lotes.length;
    $('#btn-aprovar').title = conferidos < lotes.length ? 'Confira todos os lotes para liberar' : '';
    $('#btn-planilha').href = `/api/hospital-portal/fechamentos/${f.id}/planilha`;
  }

  async function recarregar() { atual = await api(`/api/hospital-portal/fechamentos/${atual.detalhe.fechamento.id}`); desenhar(); }

  $('#fechamentos').addEventListener('click', proteger(async e => {
    const b = e.target.closest('[data-abrir]') || e.target.closest('tr[data-id]');
    if (b) await abrir(+(b.dataset.abrir || b.dataset.id));
  }));
  $('#fechamentos').addEventListener('keydown', e => { if (e.key === 'Enter' && e.target.matches('tr[data-id]')) e.target.click(); });
  $('#btn-voltar').addEventListener('click', proteger(async () => { mostrar('lista'); await listar(); }));

  $('#conf-lotes').addEventListener('change', proteger(async e => {
    const c = e.target.closest('[data-conferir]'); if (!c) return;
    const id = atual.detalhe.fechamento.id;
    await api(`/api/hospital-portal/fechamentos/${id}/lotes/${c.dataset.conferir}`, json('POST', {conferido: c.checked}));
    marcarSalvo();
    // anima a saida do lote da lista "A conferir" antes de redesenhar
    const bloco = c.closest('.hp-lote');
    if (c.checked && filtro === 'pendentes' && bloco) { bloco.classList.add('saindo'); await new Promise(r => setTimeout(r, 260)); }
    await recarregar();
    window.fmEfeitos?.pulso(document.querySelector(`.hp-lote[data-lote="${c.dataset.conferir}"]`));
  }));
  $('#conf-lotes').addEventListener('click', proteger(async e => {
    const id = atual.detalhe.fechamento.id;
    const contestar = e.target.closest('[data-contestar]');
    const retirar = e.target.closest('[data-retirar]');
    if (contestar) {
      e.preventDefault();
      const r = await fmUi.perguntar({titulo: `Contestar atendimento ${contestar.dataset.nr}`,
        texto: 'Esta etiqueta sai do valor quando o fechamento for aprovado, e a FaturaMed analisa o motivo. O restante segue normalmente.',
        campos: [{nome: 'motivo', rotulo: 'Motivo da contestação', obrigatorio: true, placeholder: 'Ex.: exame não realizado / paciente não atendido neste dia'}], confirmar: 'Contestar etiqueta'});
      if (!r) return;
      await api(`/api/hospital-portal/fechamentos/${id}/itens/${contestar.dataset.contestar}/contestacao`, json('POST', {motivo: r.motivo}));
      marcarSalvo();
      msg('Etiqueta contestada.');
      await recarregar();
    } else if (retirar) {
      e.preventDefault();
      await api(`/api/hospital-portal/fechamentos/${id}/itens/${retirar.dataset.retirar}/contestacao`, {method: 'DELETE'});
      marcarSalvo();
      msg('Contestação retirada.');
      await recarregar();
    }
  }));

  document.querySelector('.hp-filtro').addEventListener('click', e => {
    const b = e.target.closest('[data-filtro]'); if (!b) return;
    filtro = b.dataset.filtro; desenhar();
  });
  $('#btn-conferir-todos').addEventListener('click', proteger(async () => {
    const faltam = atual.lotes.filter(l => !l.conferidoEm).length;
    const ok = await fmUi.confirmar({titulo: `Marcar ${faltam} lote(s) como conferidos?`,
      texto: 'Use quando já tiver revisado os lotes (por exemplo, pela planilha). As contestações feitas continuam valendo e você ainda pode desmarcar um lote depois.',
      confirmar: 'Marcar todos'});
    if (!ok) return;
    const r = await api(`/api/hospital-portal/fechamentos/${atual.detalhe.fechamento.id}/lotes/conferir-todos`, {method: 'POST'});
    marcarSalvo();
    msg(`${r.marcados} lote(s) marcados como conferidos.`);
    filtro = 'todos';
    await recarregar();
  }));
  $('#btn-continuar-depois').addEventListener('click', proteger(async () => {
    const faltam = atual.lotes.filter(l => !l.conferidoEm).length;
    msg(faltam ? `Progresso salvo. Faltam ${faltam} lote(s); é só abrir este fechamento de novo para continuar.` : 'Progresso salvo. Todos os lotes estão conferidos.');
    mostrar('lista');
    await listar();
  }));

  $('#btn-aprovar').addEventListener('click', proteger(async () => {
    const f = atual.detalhe.fechamento;
    const pend = atual.detalhe.etiquetas.filter(e => e.contestacao === 'PENDENTE').length;
    const ok = await fmUi.confirmar({titulo: 'Aprovar e liberar para emissão da NF?',
      texto: pend ? `${pend} etiqueta(s) contestada(s) serão retiradas e o valor será recalculado sem elas. A FaturaMed analisa as contestações à parte.`
        : `O fechamento nº ${f.id} de ${brl(f.total)} será liberado para a emissão da nota fiscal.`, confirmar: 'Aprovar e liberar'});
    if (!ok) return;
    const pronto = fmUi.carregando($('#btn-aprovar'), 'Liberando…');
    try { await api(`/api/hospital-portal/fechamentos/${f.id}/aprovar`, {method: 'POST'}); } finally { pronto(); }
    msg('Fechamento aprovado e liberado para a emissão da NF.');
    await recarregar();
  }));

  proteger(async () => {
    const eu = await api('/api/hospital-portal/eu');
    $('#hospital-nome').textContent = eu.hospital;
    await listar();
    const id = new URLSearchParams(location.search).get('fechamento');
    if (id) await abrir(+id);
  })();
})();
