(() => {
  const find = id => document.getElementById(id);
  const request = window.FaturamedAuth.request;
  const SITUACOES = {PAGA: 'Paga', PENDENTE: 'Aguardando hospital', DIVERGENTE: 'Divergente', EM_ANALISE: 'Em análise'};
  const ROTULOS = {...SITUACOES, DUPLICADA: 'Duplicada'};
  const CLASSES = {PAGA: 'chip-pago', PENDENTE: 'chip-pendente', DIVERGENTE: 'chip-divergente', EM_ANALISE: 'chip-analise', DUPLICADA: 'chip-duplicada'};
  let visitaAtual = null;
  let visitas = [], semProducao = [], grupos = [], arquivosProducao = [], arquivosHospital = [], medicos = [], empresas = [], busy = false, geracao = 0;

  const element = (tag, text) => { const item = document.createElement(tag); if (text != null) item.textContent = text; return item; };
  const mesAno = comp => comp ? comp.split('-').reverse().join('/') : '—';
  const dataBr = data => data ? String(data).slice(0, 10).split('-').reverse().join('/') : '—';
  function showMessage(type, ...nodes) {
    const box = find('message');
    box.dataset.type = type;
    box.replaceChildren(...nodes.map(node => typeof node === 'string' ? element('span', node) : node));
    box.scrollIntoView({behavior: 'smooth', block: 'nearest'});
  }
  async function perform(action) {
    if (busy) return;
    busy = true;
    document.querySelectorAll('button, input, select').forEach(control => { control.disabled = true; });
    try { await action(); }
    finally { busy = false; document.querySelectorAll('button, input, select').forEach(control => { control.disabled = false; }); }
  }
  const TIPOS = {VISITA: 'Visita', EXAME: 'Exame', CONSULTA: 'Consulta', OUTRO: 'Outro'};
  // O sistema chama tudo de "visita" por heranca; o texto segue o tipo: exames, consultas, visitas, ou
  // "procedimentos" quando o conjunto e misto, sem tipo ou de outro tipo.
  const SUBSTANTIVOS = {VISITA: ['visita', 'visitas'], EXAME: ['exame', 'exames'], CONSULTA: ['consulta', 'consultas']};
  function quantidade(n, tipos) {
    n = Number(n);
    const unicos = [...new Set((tipos || []).filter(Boolean))];
    const par = unicos.length === 1 && SUBSTANTIVOS[unicos[0]] ? SUBSTANTIVOS[unicos[0]] : ['procedimento', 'procedimentos'];
    return `${n} ${n === 1 ? par[0] : par[1]}`;
  }
  // Codigo do exame em destaque discreto e o nome embaixo, como vieram na planilha.
  function celulaExame(codigo, nome) {
    const td = element('td');
    if (!codigo && !nome) { td.textContent = 'Não informado'; return td; }
    if (codigo) { const c = element('div', codigo); c.className = 'muted'; td.append(c); }
    td.append(element('div', nome || '—'));
    return td;
  }
  const nomeMedico = visita => medicos.find(m => m.id === visita.medico_id)?.nome || visita.medico_nome || '';

  function renderCards() {
    const porComp = new Map();
    for (const v of visitas) {
      if (v.situacao === 'DUPLICADA') continue; // duplicadas ficam fora dos totais
      const item = porComp.get(v.competencia) || {total: 0, PAGA: 0, PENDENTE: 0, DIVERGENTE: 0, EM_ANALISE: 0, tipos: new Set()};
      item.total++; item[v.situacao]++; item.tipos.add(v.tipo_procedimento || 'SEM'); porComp.set(v.competencia, item);
    }
    const atual = find('competencia-filter').value;
    find('comp-cards').replaceChildren();
    if (!porComp.size) {
      const vazio = element('p', find('hospital').value ? 'Nenhuma produção importada para este hospital.' : 'Selecione um hospital.');
      vazio.className = 'muted'; find('comp-cards').append(vazio); return;
    }
    for (const comp of [...porComp.keys()].sort().reverse()) {
      const dados = porComp.get(comp);
      // div com role=button: o estilo global de button sobrescreve cores de cartoes.
      const card = element('div'); card.className = 'comp-card'; card.setAttribute('role', 'button'); card.tabIndex = 0; card.setAttribute('aria-pressed', String(atual === comp));
      card.append(element('h3', mesAno(comp)), element('span', quantidade(dados.total, [...dados.tipos])));
      card.lastChild.className = 'total';
      for (const chave of Object.keys(SITUACOES)) {
        const linha = element('div'); linha.className = 'linha'; linha.append(element('span', SITUACOES[chave]), element('strong', dados[chave])); card.append(linha);
      }
      const alternar = () => { find('competencia-filter').value = atual === comp ? '' : comp; renderCards(); renderVisits(); };
      card.addEventListener('click', alternar);
      card.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); alternar(); } });
      find('comp-cards').append(card);
    }
  }
  function fillFilters() {
    const comps = [...new Set(visitas.map(v => v.competencia))].sort().reverse();
    const selComp = find('competencia-filter'), compAtual = selComp.value;
    selComp.replaceChildren(new Option('Todas', ''), ...comps.map(c => new Option(mesAno(c), c)));
    selComp.value = comps.includes(compAtual) ? compAtual : '';
    const nomes = [...new Set(visitas.map(nomeMedico).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'pt-BR'));
    const selMed = find('medico-filter'), medAtual = selMed.value;
    selMed.replaceChildren(new Option('Todos', ''), ...nomes.map(n => new Option(n, n)), new Option('Não identificado', '__SEM__'));
    selMed.value = [...selMed.options].some(o => o.value === medAtual) ? medAtual : '';
  }
  function renderVisits() {
    const comp = find('competencia-filter').value, status = find('status').value, med = find('medico-filter').value, busca = find('search').value.trim(), tipo = find('tipo-filter').value;
    const filtradas = visitas.filter(v => (!comp || v.competencia === comp) && (!status || v.situacao === status) && (!tipo || v.tipo_procedimento === tipo)
      && (!med || (med === '__SEM__' ? !nomeMedico(v) : nomeMedico(v) === med)) && v.atendimento.includes(busca));
    find('visits').replaceChildren();
    for (const v of filtradas) {
      const row = element('tr');
      row.append(element('td', v.id));
      const atend = element('td'); atend.append(element('div', v.atendimento || 'Sem atendimento')); const sub = element('div', dataBr(v.data_visita)); sub.className = 'muted'; atend.append(sub); row.append(atend);
      const medico = element('td', nomeMedico(v) || 'Não identificado');
      if (v.medico_inferido) { const tag = element('span', 'inferido'); tag.className = 'chip chip-inferido'; tag.title = 'Médico identificado pelo registro único do hospital'; medico.append(tag); }
      row.append(medico);
      const cell = element('td', mesAno(v.competencia));
      if (v.competencia !== v.competencia_derivada) { const tag = element('div', `Original ${mesAno(v.competencia_derivada)}`); tag.className = 'muted'; cell.append(tag); }
      row.append(cell);
      row.append(celulaExame(v.codigo_procedimento, v.procedimento));
      row.append(element('td', TIPOS[v.tipo_procedimento] || '—'));
      const situacao = element('td'); const chip = element('span', ROTULOS[v.situacao] || v.situacao); chip.className = `chip ${CLASSES[v.situacao] || ''}`; situacao.append(chip); row.append(situacao);
      const motivo = element('td', v.motivo); motivo.className = 'motivo';
      if (v.relatorio_id) {
        const abrir = element('a', 'Abrir no relatório'); abrir.href = `/conciliacao.html?relatorio=${v.relatorio_id}&linha=${v.linha_relatorio}`;
        const bloco = element('div'); bloco.append(abrir); motivo.prepend(bloco);
      }
      row.append(motivo);
      const acoes = element('td'); acoes.className = 'acoes';
      const botao = (texto, acao) => { const b = element('button', texto); b.type = 'button'; b.addEventListener('click', () => acao(v)); acoes.append(b); };
      if (v.decisao === 'EM_ANALISE') botao('Analisar repetição', abrirRepeticao);
      if (v.decisao !== 'DUPLICADA') botao('Corrigir competência', abrirCompetencia);
      botao('Histórico', abrirHistorico);
      row.append(acoes);
      find('visits').append(row);
    }
    find('count').textContent = quantidade(filtradas.length, filtradas.map(v => v.tipo_procedimento || 'SEM'));
  }
  const resumoVisita = v => `ID ${v.id} · atendimento ${v.atendimento || 'sem número'} · ${dataBr(v.data_visita)} · ${nomeMedico(v) || 'médico não identificado'}`;
  function abrirCompetencia(v) {
    visitaAtual = v; find('competencia-form').reset(); find('competencia-mensagem').textContent = '';
    find('competencia-resumo').textContent = `${resumoVisita(v)}. Competência atual: ${mesAno(v.competencia)}${v.competencia !== v.competencia_derivada ? ` (pela data: ${mesAno(v.competencia_derivada)})` : ''}.`;
    // A competencia de um relatorio e uma so: a correcao vale para todas as visitas dele.
    const aviso = find('competencia-relatorio');
    aviso.hidden = !v.relatorio_id;
    if (v.relatorio_id) aviso.textContent = `Esta visita está no relatório ${v.relatorio_id}, com ${v.visitas_no_relatorio} ${Number(v.visitas_no_relatorio) === 1 ? 'visita' : 'visitas'}. A competência é do relatório inteiro: a correção vale para todas elas. Liberação ao médico e recebimentos não mudam.`;
    find('competencia-dialog').showModal();
  }
  function abrirRepeticao(v) {
    visitaAtual = v; find('repeticao-form').reset(); find('repeticao-mensagem').textContent = '';
    find('repeticao-resumo').textContent = `${resumoVisita(v)}. Possível original: ID ${v.anterior_id || 'não informado'}.`;
    find('repeticao-dialog').showModal();
  }
  async function abrirHistorico(v) {
    try {
      const itens = await request(`/api/conciliacao-unica/visitas/${v.id}/historico`);
      find('historico-lista').replaceChildren(...itens.map(i => element('li', `${i.acao} · ${i.antes} → ${i.depois} · ${i.responsavel} · ${String(i.criado_em).slice(0, 16).replace('T', ' ')} · ${i.justificativa}`)));
      if (!itens.length) find('historico-lista').append(element('li', 'Nenhum ajuste manual registrado.'));
      find('historico-dialog').showModal();
    } catch (error) { showMessage('error', error.message); }
  }
  async function enviarAjuste(dialogo, mensagem, caminho, corpo, sucesso) {
    try {
      await perform(async () => {
        await request(`/api/conciliacao-unica/visitas/${visitaAtual.id}/${caminho}`, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(corpo)});
        find(dialogo).close(); showMessage('success', sucesso); await load();
      });
    } catch (error) { find(mensagem).textContent = error.message; }
  }
  find('competencia-form').addEventListener('submit', event => {
    event.preventDefault();
    const dados = Object.fromEntries(new FormData(event.currentTarget));
    const noRelatorio = Boolean(visitaAtual.relatorio_id);
    enviarAjuste('competencia-dialog', 'competencia-mensagem', 'competencia', {competencia: dados.competencia.trim() || null, justificativa: dados.justificativa},
      (dados.competencia.trim() ? 'Competência corrigida' : 'Competência restaurada pela data do atendimento') + (noRelatorio ? ' em todo o relatório.' : '.') + ' O ajuste ficou no histórico.');
  });
  find('repeticao-form').addEventListener('submit', event => {
    event.preventDefault();
    const dados = Object.fromEntries(new FormData(event.currentTarget));
    enviarAjuste('repeticao-dialog', 'repeticao-mensagem', 'repeticao', {acao: dados.acao, justificativa: dados.justificativa},
      dados.acao === 'DUPLICADA' ? 'Repetição marcada como duplicada e retirada da conciliação.' : 'Visita confirmada como distinta; ela volta a conciliar com o hospital.');
  });
  for (const [botao, dialogo] of [['competencia-fechar', 'competencia-dialog'], ['repeticao-fechar', 'repeticao-dialog'], ['historico-fechar', 'historico-dialog']])
    find(botao).addEventListener('click', () => find(dialogo).close());
  function renderOrphans() {
    find('orphans').replaceChildren();
    for (const h of semProducao) {
      const row = element('tr');
      for (const value of [h.id, h.atendimento, dataBr(h.data_visita), mesAno(h.competencia_exame), h.medico_nome || 'Não identificado']) row.append(element('td', value));
      row.append(celulaExame(h.codigo_procedimento, h.procedimento));
      find('orphans').append(row);
    }
    const aba = document.querySelector('[data-tab="tab-sem-producao"]');
    aba.textContent = `Hospital sem produção${semProducao.length ? ` (${semProducao.length})` : ''}`;
  }
  function renderReports() {
    find('reports').replaceChildren();
    for (const g of grupos) {
      const row = element('tr');
      row.append(element('td', mesAno(g.competencia)));
      row.append(element('td', g.medico || 'Médico não identificado'));
      for (const campo of ['pagas', 'pendentes', 'divergentes', 'em_analise']) row.append(element('td', g[campo]));
      const acao = element('td');
      for (const relatorio of String(g.relatorios || '').split(',').filter(Boolean)) {
        const abrir = element('a', `Abrir relatório ${relatorio}`); abrir.href = `/conciliacao.html?relatorio=${relatorio}`;
        const bloco = element('div'); bloco.append(abrir); acao.append(bloco);
      }
      if (!g.medico_id) acao.append(element('div', 'Identifique o médico das visitas para gerar o relatório.'));
      else if (g.disponiveis > 0) {
        // Medico que atende por mais de uma empresa precisa indicar a empresa da producao.
        const doutor = medicos.find(m => m.id === g.medico_id);
        const opcoes = empresas.filter(e => e.ativo && (doutor?.empresas || []).includes(e.id));
        let escolha = null;
        if (opcoes.length > 1) {
          escolha = document.createElement('select'); escolha.setAttribute('aria-label', 'Empresa da produção');
          escolha.append(new Option('Selecione a empresa', ''), ...opcoes.map(e => new Option(e.nome, e.id)));
          acao.append(escolha);
        }
        const gerar = element('button', `Gerar relatório (${g.disponiveis})`); gerar.type = 'button';
        gerar.addEventListener('click', async () => {
          if (escolha && !escolha.value) { showMessage('error', 'Selecione a empresa da produção.'); return; }
          const plural = n => `${n} ${n === 1 ? 'visita' : 'visitas'}`;
          const texto = `Gerar o relatório de ${g.medico} para ${mesAno(g.competencia)} com ${plural(g.disponiveis)}? Elas seguem para revisão, liberação e recebimentos.`;
          if (!await (window.fmConfirmar ? window.fmConfirmar(texto) : Promise.resolve(confirm(texto)))) return;
          try {
            await perform(async () => {
              const r = await request('/api/conciliacao-unica/relatorios', {method: 'POST', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({hospitalId: Number(find('hospital').value), medicoId: g.medico_id, competencia: g.competencia, empresaId: escolha ? Number(escolha.value) : null})});
              const abrir = element('a', 'Abrir relatório'); abrir.href = `/conciliacao.html?relatorio=${r.relatorioId}`;
              showMessage('success', `Relatório gerado com ${plural(r.visitas)}. `, abrir);
              await load();
            });
          } catch (error) { showMessage('error', error.message); }
        });
        acao.append(gerar);
      }
      row.append(acao); find('reports').append(row);
    }
    const aba = document.querySelector('[data-tab="tab-relatorios"]');
    const aGerar = grupos.filter(g => g.medico_id && g.disponiveis > 0).length;
    aba.textContent = `Relatórios ao médico${aGerar ? ` (${aGerar} a gerar)` : ''}`;
  }
  // Arquivos importados: producao desta tela e planilhas do hospital. Relatorios gerados se excluem em Relatorios e revisoes.
  function renderFiles() {
    find('files').replaceChildren();
    const itens = [
      ...arquivosProducao.map(a => ({tipo: 'Produção', producao: true, id: a.id, nome: a.nome, criado: a.criado_em,
        detalhe: `${quantidade(a.visitas, String(a.tipos || '').split(',').filter(Boolean).map(p => p.split(':')[0]))}${a.medico ? ` · ${a.medico}` : ' · vários médicos'} · competências ${String(a.competencias || '—').split(',').map(mesAno).join(', ')}${Number(a.em_relatorio) ? ` · ${a.em_relatorio} em relatório` : ''}`})),
      ...arquivosHospital.map(a => ({tipo: 'Hospital', producao: false, id: a.id, nome: a.nome, criado: a.criado_em,
        detalhe: `${a.quantidade_registros ?? '—'} registros · faturamento ${mesAno(a.competencia)}${a.modo_hospital === 'ATUALIZAR' ? ' · atualização' : ''}`}))
    ].sort((x, y) => String(y.criado).localeCompare(String(x.criado)));
    if (!itens.length) { const linha = element('tr'); const cel = element('td', 'Nenhum arquivo importado.'); cel.colSpan = 5; linha.append(cel); find('files').append(linha); }
    for (const item of itens) {
      const row = element('tr');
      for (const valor of [item.tipo, item.nome, item.detalhe, String(item.criado).slice(0, 16).replace('T', ' ')]) row.append(element('td', valor));
      const acoes = element('td'); acoes.className = 'acoes';
      const excluir = element('button', 'Excluir'); excluir.type = 'button'; excluir.className = 'danger-link';
      excluir.addEventListener('click', () => excluirArquivo(item));
      acoes.append(excluir); row.append(acoes); find('files').append(row);
    }
    document.querySelector('[data-tab="tab-arquivos"]').textContent = `Arquivos importados (${itens.length})`;
  }
  async function excluirArquivo(item) {
    if (busy) return;
    const texto = item.producao
      ? `A importação de produção "${item.nome}" e todas as suas visitas serão excluídas definitivamente. Registros do hospital que estavam conciliados com elas voltam a ficar disponíveis. Repetições que dependiam dela passam a ser a visita original.`
      : `A planilha do hospital "${item.nome}" e os procedimentos que ela criou serão excluídos definitivamente. Só é permitido se nenhum deles estiver conciliado. Depois você pode importá-la de novo.`;
    const resposta = await window.fmUi.perguntar({titulo: 'Excluir arquivo importado', texto, campos: [{nome: 'senha', rotulo: 'Confirme com sua senha', tipo: 'password', obrigatorio: true}], confirmar: 'Excluir definitivamente', perigo: true});
    if (!resposta?.senha) return;
    try {
      await perform(async () => {
        const url = item.producao ? `/api/conciliacao-unica/arquivos/${item.id}/exclusao` : `/api/base-hospitalar/arquivos/${item.id}/exclusao`;
        const r = await request(url, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({senha: resposta.senha})});
        showMessage('success', item.producao
          ? `Importação excluída (${quantidade(r.visitas, [])}${r.repeticoesPromovidas ? `; ${r.repeticoesPromovidas} repetição(ões) passaram a ser a original` : ''}). Você pode importar a planilha de novo.`
          : 'Planilha do hospital excluída. Importe-a novamente na competência correta.');
        await load();
      });
    } catch (error) { showMessage('error', error.message); }
  }
  // Repeticoes do hospital em analise se decidem na tela de acompanhamento (mesma decisao e historico de sempre).
  function renderAvisoHospital(repeticoes) {
    const aviso = find('aviso-hospital');
    aviso.hidden = !repeticoes;
    if (!repeticoes) return;
    aviso.replaceChildren(element('span', `${repeticoes} ${repeticoes === 1 ? 'registro do hospital repetido aguarda' : 'registros do hospital repetidos aguardam'} análise e ${repeticoes === 1 ? 'não entra' : 'não entram'} na conciliação até a decisão. `));
    // O ID do hospital vem de um seletor (texto do DOM): vira numero e e codificado antes de entrar no link.
    const hospitalId = Number.parseInt(find('hospital').value, 10);
    const abrir = element('a', 'Analisar no acompanhamento da base');
    abrir.href = `base-hospitalar.html?hospitalId=${encodeURIComponent(Number.isSafeInteger(hospitalId) ? hospitalId : '')}`;
    aviso.append(abrir, element('span', '. Depois, use Atualizar aqui para conciliar de novo.'));
  }
  async function load() {
    const id = ++geracao, hospitalId = find('hospital').value;
    let repeticoes = 0;
    if (!hospitalId) { visitas = []; semProducao = []; grupos = []; arquivosProducao = []; arquivosHospital = []; } else {
      const [lista, orfaos, porGrupo, hospitalVisitas, arquivosProd, arquivosTodos] = await Promise.all([request(`/api/conciliacao-unica/visitas?hospitalId=${hospitalId}`), request(`/api/conciliacao-unica/hospital-sem-producao?hospitalId=${hospitalId}`), request(`/api/conciliacao-unica/relatorios?hospitalId=${hospitalId}`), request(`/api/base-hospitalar/visitas?hospitalId=${hospitalId}`), request(`/api/conciliacao-unica/arquivos?hospitalId=${hospitalId}`), request(`/api/base-hospitalar/arquivos?hospitalId=${hospitalId}`)]);
      if (id !== geracao) return;
      visitas = lista; semProducao = orfaos; grupos = porGrupo; arquivosProducao = arquivosProd;
      arquivosHospital = arquivosTodos.filter(item => item.tipo === 'HOSPITAL');
      repeticoes = hospitalVisitas.filter(item => item.decisao === 'EM_ANALISE').length;
    }
    fillFilters(); renderCards(); renderVisits(); renderOrphans(); renderReports(); renderFiles(); renderAvisoHospital(repeticoes);
  }
  function resumoConciliacao(c) {
    if (!c) return '';
    return ` Conciliação: ${c.vinculadas} vinculadas, ${c.divergentes} divergentes, ${c.pendentes} aguardando${c.medicosInferidos ? `, ${c.medicosInferidos} médicos identificados pelo hospital` : ''}.`;
  }

  find('production-form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget, data = new FormData(form);
    if (!find('hospital').value) { showMessage('error', 'Selecione um hospital ativo.'); return; }
    if (!data.get('medicoId')) data.delete('medicoId');
    if (!data.get('tipoProcedimento')) data.delete('tipoProcedimento');
    const tipoImportado = data.get('tipoProcedimento');
    data.set('hospitalId', find('hospital').value);
    try {
      await perform(async () => {
        showMessage('info', 'Processando a produção. Aguarde a confirmação.');
        const r = await request('/api/conciliacao-unica/producao', {method: 'POST', body: data});
        if (r.repetido) showMessage('warning', 'Arquivo já importado para este hospital e médico. A importação anterior foi mantida.');
        else {
          const meses = Object.entries(r.competencias || {}).map(([comp, total]) => `${mesAno(comp)}: ${total}`).join(' · ');
          const aviso = [r.emAnalise ? `${r.emAnalise} repetidas em análise.` : '', r.semMedico ? `${r.semMedico} sem médico identificado.` : ''].filter(Boolean).join(' ');
          showMessage(r.emAnalise || r.semMedico ? 'warning' : 'success', `Importação concluída: ${quantidade(r.visitas, tipoImportado ? [tipoImportado] : [])} — competências: ${meses}. ${aviso}${resumoConciliacao(r.conciliacao)}`);
        }
        form.querySelector('[type=file]').value = '';
        await load();
      });
    } catch (error) { showMessage('error', error.message); }
  });
  find('hospital-form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget, data = new FormData(form);
    if (!find('hospital').value) { showMessage('error', 'Selecione um hospital ativo.'); return; }
    data.set('hospitalId', find('hospital').value);
    if (!data.get('tipoProcedimento')) data.delete('tipoProcedimento');
    try {
      await perform(async () => {
        showMessage('info', 'Processando o arquivo do hospital. Aguarde a confirmação.');
        const r = await request('/api/conciliacao-unica/hospital', {method: 'POST', body: data});
        if (r.repetido) showMessage('warning', 'Arquivo já importado nesta competência. A importação anterior foi mantida.');
        else {
          const atualizados = r.relatoriosAtualizados || [], falhas = atualizados.filter(item => item.erro), liberados = atualizados.filter(item => !item.erro && item.liberada);
          const relatorios = atualizados.length ? ` Relatórios atualizados: ${atualizados.length - falhas.length} de ${atualizados.length}.` : '';
          const aviso = [liberados.length ? `${liberados.length} já estavam liberados ao médico: confira e libere novamente.` : '', ...falhas.map(item => `${item.nome}: não atualizado — ${item.erro}.`)].filter(Boolean).join(' ');
          showMessage(r.divergencias > 0 || falhas.length || liberados.length ? 'warning' : 'success', `${r.visitas} registros do hospital processados; ${r.atualizadas || 0} atualizados mantendo o ID; ${r.divergencias} repetições para análise.${relatorios} ${aviso}${resumoConciliacao(r.conciliacao)}`);
        }
        form.querySelector('[type=file]').value = '';
        await load();
      });
    } catch (error) { showMessage('error', error.message); }
  });
  document.querySelectorAll('.base-tabs [data-tab]').forEach(tab => tab.addEventListener('click', () => {
    document.querySelectorAll('.base-tabs [data-tab]').forEach(item => { item.setAttribute('aria-selected', String(item === tab)); find(item.dataset.tab).hidden = item !== tab; });
  }));
  for (const id of ['competencia-filter', 'status', 'medico-filter', 'tipo-filter', 'search']) find(id).addEventListener('input', () => { renderCards(); renderVisits(); });
  find('hospital').addEventListener('change', () => load().catch(error => showMessage('error', error.message)));
  // Atualizar repete a conciliacao (idempotente), util se uma importacao foi interrompida.
  find('reload').addEventListener('click', async () => {
    try {
      if (find('hospital').value) await request(`/api/conciliacao-unica/conciliar?hospitalId=${find('hospital').value}`, {method: 'POST'});
      await load();
    } catch (error) { showMessage('error', error.message); }
  });

  async function initialize() {
    let hospitais;
    [hospitais, medicos, empresas] = await Promise.all([request('/api/hospitais'), request('/api/medicos'), request('/api/empresas-clientes')]);
    find('hospital').replaceChildren(new Option('Selecione um hospital', ''), ...hospitais.filter(item => item.ativo).map(item => new Option(item.nome, item.id)));
    find('medico').replaceChildren(new Option('Vários médicos (identificar por linha)', ''), ...medicos.filter(item => item.ativo).map(item => new Option(`${item.nome} · CRM ${item.crm || 'a preencher'}/${item.uf || '--'}`, item.id)));
    const params = new URLSearchParams(location.search);
    if (hospitais.some(item => item.ativo && String(item.id) === params.get('hospitalId'))) find('hospital').value = params.get('hospitalId');
    await load();
  }
  initialize().catch(error => showMessage('error', error.message));
})();
