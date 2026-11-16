(() => {
  const $ = id => document.getElementById(id);
  const form = $('repasse-form');
  const tipos = { VISITA: 'Visita', EXAME: 'Exame', CONSULTA: 'Consulta', OUTRO: 'Outro' };
  const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  const data = v => v ? v.split('-').reverse().join('/') : '';
  let itens = [];
  let procedimentos = new Map();
  const rotulo = codigo => procedimentos.has(codigo) ? `${codigo} — ${procedimentos.get(codigo).descricao}` : codigo;
  // Aceita "codigo — descricao" (escolhido na lista), so o codigo ou o nome exato do procedimento.
  function codigoEscolhido(texto) {
    const valor = texto.trim();
    if (!valor) return null;
    const codigo = valor.split(' — ')[0].trim();
    if (procedimentos.has(codigo)) return codigo;
    const porNome = [...procedimentos.values()].find(p => (p.descricao || '').toLowerCase() === valor.toLowerCase());
    return porNome ? porNome.codigo : codigo;
  }
  let hospitalares = [], tuss = [], buscaTimer = null, buscaGeracao = 0;
  function montarLista() {
    const vistos = new Set();
    const opcoes = [];
    for (const p of [...hospitalares, ...tuss]) {
      if (vistos.has(p.codigo)) continue; vistos.add(p.codigo);
      const o = document.createElement('option');
      o.value = `${p.codigo} — ${p.descricao || ''}`;
      o.label = p.tipo ? `${tipos[p.tipo] || ''} · já veio na planilha` : 'TUSS';
      opcoes.push(o);
    }
    $('repasse-procedimentos').replaceChildren(...opcoes);
  }
  function lembrar(lista) { for (const p of lista) if (!procedimentos.has(p.codigo) || !procedimentos.get(p.codigo).descricao) procedimentos.set(p.codigo, p); }
  async function carregarProcedimentos() {
    const hospital = form.elements.hospitalId.value;
    hospitalares = await FaturamedAuth.request(`/api/fechamento-faturado/repasses/procedimentos${hospital ? `?hospitalId=${hospital}` : ''}`);
    lembrar(hospitalares); montarLista();
  }
  // Busca na TUSS enquanto digita (codigo ou nome).
  function buscarTuss() {
    clearTimeout(buscaTimer);
    const texto = form.elements.codigoProcedimento.value.split(' — ')[0].trim();
    if (texto.length < 2) { tuss = []; montarLista(); return; }
    buscaTimer = setTimeout(async () => {
      const g = ++buscaGeracao;
      try {
        const lista = await FaturamedAuth.request(`/api/catalogos/busca?q=${encodeURIComponent(texto)}`);
        if (g !== buscaGeracao) return;
        tuss = lista; lembrar(lista); montarLista();
      } catch { /* sem TUSS importada: fica so a lista dos hospitais */ }
    }, 250);
  }
  async function nomesTuss(codigos) {
    const faltando = codigos.filter(c => c && !procedimentos.get(c)?.descricao);
    if (!faltando.length) return;
    const params = new URLSearchParams(); faltando.forEach(c => params.append('codigos', c));
    lembrar(await FaturamedAuth.request(`/api/catalogos/busca?${params}`).catch(() => []));
  }
  async function statusTuss() {
    const catalogos = await FaturamedAuth.request('/api/catalogos').catch(() => []);
    const atual = catalogos.find(c => c.tabela === 'TUSS');
    $('tuss-status').textContent = atual ? `${atual.versao} · ${Number(atual.procedimentos).toLocaleString('pt-BR')} procedimentos` : 'não importada';
  }

  function limpar() {
    form.reset(); form.elements.id.value = ''; form.elements.ativo.checked = true;
  }

  function editar(r) {
    form.elements.id.value = r.id;
    form.elements.hospitalId.value = r.hospitalId ?? '';
    form.elements.codigoProcedimento.value = r.codigoProcedimento ? rotulo(r.codigoProcedimento) : '';
    form.elements.tipoProcedimento.value = r.tipoProcedimento ?? '';
    form.elements.forma.value = r.valorFixo != null ? 'VALOR' : 'PERCENTUAL';
    form.elements.valor.value = r.valorFixo ?? r.percentual;
    form.elements.vigenciaInicio.value = r.vigenciaInicio ?? '';
    form.elements.vigenciaFim.value = r.vigenciaFim ?? '';
    form.elements.observacao.value = r.observacao ?? '';
    form.elements.ativo.checked = r.ativo;
    form.scrollIntoView({ behavior: 'smooth' });
  }

  function render() {
    const rows = $('repasse-rows');
    rows.replaceChildren();
    if (!itens.length) {
      const tr = document.createElement('tr'); const td = document.createElement('td');
      td.colSpan = 8; td.textContent = 'Nenhum repasse cadastrado. Sem repasse na planilha, a linha fica divergente.';
      tr.append(td); rows.append(tr); return;
    }
    for (const r of itens) {
      const tr = document.createElement('tr');
      const valores = [r.hospital || 'Todos', r.codigoProcedimento ? rotulo(r.codigoProcedimento) : 'Todos do tipo', tipos[r.tipoProcedimento] || '—',
        r.valorFixo != null ? money.format(r.valorFixo) : `${Number(r.percentual).toLocaleString('pt-BR')}%`,
        `${data(r.vigenciaInicio)}${r.vigenciaFim ? ' a ' + data(r.vigenciaFim) : ' em diante'}`,
        r.ativo ? 'Ativo' : 'Inativo', r.observacao || ''];
      for (const v of valores) { const td = document.createElement('td'); td.textContent = v; tr.append(td); }
      const td = document.createElement('td'); const b = document.createElement('button');
      b.type = 'button'; b.className = 'text-button'; b.textContent = 'Editar'; b.addEventListener('click', () => editar(r));
      td.append(b); tr.append(td); rows.append(tr);
    }
  }

  async function carregar() {
    try {
      const [hospitais, repasses] = await Promise.all([FaturamedAuth.request('/api/hospitais'), FaturamedAuth.request('/api/fechamento-faturado/repasses')]);
      const select = $('repasse-hospital');
      for (const h of hospitais) { const o = document.createElement('option'); o.value = h.id; o.textContent = h.nome; select.append(o); }
      await carregarProcedimentos().catch(() => {});
      await nomesTuss(repasses.map(r => r.codigoProcedimento));
      itens = repasses; render();
      statusTuss();
    } catch (e) { $('repasse-message').textContent = e.message; }
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const f = form.elements; const valor = f.valor.value === '' ? null : Number(f.valor.value);
    const corpo = {
      hospitalId: f.hospitalId.value ? Number(f.hospitalId.value) : null,
      codigoProcedimento: codigoEscolhido(f.codigoProcedimento.value),
      tipoProcedimento: f.tipoProcedimento.value || null,
      percentual: f.forma.value === 'PERCENTUAL' ? valor : null,
      valorFixo: f.forma.value === 'VALOR' ? valor : null,
      vigenciaInicio: f.vigenciaInicio.value || null, vigenciaFim: f.vigenciaFim.value || null,
      ativo: f.ativo.checked, observacao: f.observacao.value
    };
    const id = f.id.value;
    $('repasse-salvar').disabled = true;
    try {
      await FaturamedAuth.request(id ? `/api/fechamento-faturado/repasses/${id}` : '/api/fechamento-faturado/repasses',
        { method: id ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corpo) });
      $('repasse-message').textContent = 'Repasse salvo. Vale para as próximas conciliações.';
      limpar(); itens = await FaturamedAuth.request('/api/fechamento-faturado/repasses'); render();
    } catch (e) { $('repasse-message').textContent = e.message; }
    finally { $('repasse-salvar').disabled = false; }
  });
  $('repasse-limpar').addEventListener('click', limpar);
  $('repasse-hospital').addEventListener('change', () => carregarProcedimentos().catch(() => {}));
  form.elements.codigoProcedimento.addEventListener('input', buscarTuss);
  $('tuss-form').addEventListener('submit', async event => {
    event.preventDefault();
    const dados = new FormData($('tuss-form')); dados.append('tabela', 'TUSS');
    $('tuss-enviar').disabled = true; $('tuss-message').textContent = 'Importando... tabelas grandes levam alguns segundos.';
    try {
      const r = await FaturamedAuth.request('/api/catalogos/planilha', { method: 'POST', body: dados });
      $('tuss-message').textContent = `TUSS ${r.versao} importada: ${Number(r.procedimentos).toLocaleString('pt-BR')} procedimentos.`;
      $('tuss-form').reset(); statusTuss();
    } catch (e) { $('tuss-message').textContent = e.message; }
    finally { $('tuss-enviar').disabled = false; }
  });
  carregar();
})();
