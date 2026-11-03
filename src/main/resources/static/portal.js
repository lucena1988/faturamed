(async function () {
  const $ = id => document.getElementById(id);
  let items = [], report = null, generation = 0;
  const optionList = (select, values) => { select.replaceChildren(...values.map(([value,label]) => new Option(label,value))); select.disabled = !values.length; };
  function render() {
    const period = $('portal-period').value;
    // The portal DTO contains only authorized, effective values, never hospital candidates.
    const visits = (report?.visitas || []).map(visit => ({...visit, camposRevisados: {}}));
    const scoped = visits.filter(v => !period || (period === 'SEM_DATA' ? !v.data : v.data?.startsWith(period)));
    FaturamedPainel.render(report ? {...report, visitas: visits} : null, scoped);
    const option = $('panel-doctor').options[1];
    if (option) { $('panel-doctor').value = option.value; $('panel-doctor').dispatchEvent(new Event('change')); }
  }
  async function load() {
    const token = ++generation; report = null; render(); $('portal-message').textContent = '';
    FaturamedExtrato.carregar(null);
    FaturamedConferencia.carregar(null);
    optionList($('portal-period'), []);
    const id = $('report-filter').value;
    if (!id) return;
    try {
      const [result,financeiro] = await Promise.all([FaturamedAuth.request(`/api/medico/relatorios/${encodeURIComponent(id)}`),FaturamedAuth.request(`/api/medico/relatorios/${encodeURIComponent(id)}/recebimentos`)]);
      for(const visit of result.visitas)visit.financeiro=financeiro.find(v=>v.linha===visit.linhaMedico)||null;
      if (token !== generation) return;
      report = result;
      const periods = [...new Set(report.visitas.filter(v => v.data).map(v => v.data.slice(0,7)))].sort().reverse();
      optionList($('portal-period'), [['','Todos os meses'], ...(report.visitas.some(v=>!v.data) ? [['SEM_DATA','Sem data informada']] : []), ...periods.map(p=>[p,new Date(p+'-01T12:00:00').toLocaleDateString('pt-BR',{month:'long',year:'numeric'})])]);
      render();
      FaturamedExtrato.carregar(id);
      FaturamedConferencia.carregar(id);
    } catch(error) { if (token === generation) $('portal-message').textContent = error.message; }
  }
  async function hospitals(preferred=null) {
    optionList($('report-filter'), items.filter(item=>item.hospital === $('portal-hospital').value).map(item=>[item.id,`#${item.id} - ${item.visitas} visitas`]));
    if(preferred&&items.some(item=>String(item.id)===String(preferred)))$('report-filter').value=String(preferred);
    await load();
  }
  $('portal-hospital').addEventListener('change', ()=>hospitals());
  $('report-filter').addEventListener('change', load);
  $('portal-period').addEventListener('change', render);
  try {
    const user = await FaturamedAuth.request('/api/auth/me'); $('portal-name').textContent = user.nome;
    items = await FaturamedAuth.request('/api/medico/relatorios');
    const preferred=new URLSearchParams(location.search).get('relatorio');
    if(preferred&&!/^\d+$/.test(preferred))throw new Error('Relatório inválido.');
    if(preferred&&!items.some(item=>String(item.id)===preferred)){
      const target=await FaturamedAuth.request(`/api/medico/relatorios/${preferred}`);items.push({id:target.id,hospital:target.hospital,visitas:target.visitas.length});
    }
    optionList($('portal-hospital'), [...new Set(items.map(item=>item.hospital))].map(name=>[name,name]));
    if(preferred)$('portal-hospital').value=items.find(item=>String(item.id)===preferred).hospital;
    await hospitals(preferred);
    if (!items.length) $('portal-message').textContent = 'Ainda não ha relatórios liberados para o seu cadastro.';
  } catch(error) { $('portal-message').textContent = error.message; }
})();
