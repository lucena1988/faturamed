(() => {
  const $=id=>document.getElementById(id), money=new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'});
  let reports=[],generation=0;
  const selected=new Map();let visibleVisits=[],referenceDate='',batchMode=false,batchStale=false,batchSelection=[];
  function selectionChanged(){
    $('charge-selected').textContent=`${selected.size} visitas selecionadas`;
    $('charge-batch').disabled=!selected.size;$('charge-unselect').disabled=!selected.size;
    const all=$('charge-select-all');all.disabled=!visibleVisits.length||visibleVisits.length>500;all.checked=!!visibleVisits.length&&selected.size===visibleVisits.length;all.indeterminate=!!selected.size&&!all.checked;
    all.title=visibleVisits.length>500?'Filtre a fila para selecionar até 500 visitas':'';
    for(const input of document.querySelectorAll('[data-select-line]'))input.checked=selected.has(Number(input.dataset.selectLine));
  }
  const contactLabels={NAO_COBRADA:'Não cobrada',ENVIADA:'Enviada',AGUARDANDO_RETORNO:'Aguardando retorno',RETORNO_RECEBIDO:'Retorno recebido'};
  const priorityLabels={TODAS:'Todas',NAO_COBRADAS:'Não cobradas',HOJE:'Contato para hoje',ATRASADAS:'Contatos atrasados',AGUARDANDO_RETORNO:'Aguardando retorno'};
  let priority=new URLSearchParams(location.search).get('prioridade')||'TODAS';
  if(!Object.hasOwn(priorityLabels,priority))priority='TODAS';
  const date=value=>value?value.split('-').reverse().join('/'):'Não informado';
  let contactDetail=null,contactReport=null,contactLine=null,contactGeneration=0,contactSaving=false;
  const node=(tag,value)=>{const el=document.createElement(tag);el.textContent=value;return el;};
  function clear(){selected.clear();visibleVisits=[];selectionChanged();generation++;$('charge-priorities').replaceChildren();$('charge-rows').replaceChildren();$('charge-archive-rows').replaceChildren();$('charge-archive-message').textContent='Nenhum acompanhamento fora da fila.';$('charge-stats').replaceChildren();$('charge-export').hidden=true;$('charge-doctor').disabled=true;$('charge-reference').textContent='';}
  function reportOptions(){
    const current=$('charge-report').value;
    const filtered=reports.filter(r=>!$('charge-hospital').value||r.hospital===$('charge-hospital').value);
    $('charge-report').replaceChildren(new Option('Selecione um relatório',''),...filtered.map(r=>new Option(`#${r.id} - v${r.numero_versao||1} - ${r.hospital} - ${r.arquivo_medico}`,r.id)));
    if(filtered.some(r=>String(r.id)===current))$('charge-report').value=current;
  }
  async function refreshReports(){
    reports=(await window.FaturamedAuth.request('/api/conciliacoes/visitas')).filter(r=>!r.proxima_id);
    const hospital=$('charge-hospital').value;
    $('charge-hospital').replaceChildren(new Option('Todos os hospitais',''),...[...new Set(reports.map(r=>r.hospital))].sort().map(h=>new Option(h,h)));
    if([...$('charge-hospital').options].some(o=>o.value===hospital))$('charge-hospital').value=hospital;
    reportOptions();
  }
  async function load(){
    clear();const id=$('charge-report').value,token=generation;
    if(!id){$('charge-message').textContent='Selecione um relatório.';return;}
    $('charge-message').textContent='Carregando fila...';
    const params=new URLSearchParams({medico:$('charge-doctor').value,minDias:$('charge-age').value,prioridade:priority});
    try{
      const [result,contacts]=await Promise.all([window.FaturamedAuth.request(`/api/conciliacoes/visitas/${id}/cobranca?${params}`),window.FaturamedAuth.request(`/api/conciliacoes/visitas/${id}/cobranca/acompanhamento`)]);
      if(token!==generation)return;
      referenceDate=result.referencia;visibleVisits=result.visitas.map(v=>({linha:v.linha,versao:contacts.find(c=>c.linha===v.linha)?.atual?.versao||0}));selectionChanged();
      const previous=$('charge-doctor').value;
      $('charge-doctor').replaceChildren(new Option('Todos os médicos',''),...result.medicos.map(m=>new Option(`${m.medico||'Medico nao identificado'}${m.crm?' - CRM '+m.crm:''} (${m.visitas})`,m.chave)));
      if([...$('charge-doctor').options].some(o=>o.value===previous))$('charge-doctor').value=previous;
      else if(previous){await load();return;}
      $('charge-doctor').disabled=false;
      for(const [key,label] of Object.entries(priorityLabels)){
        const count=result.prioridades?.[key]||0,button=node('button',label);button.type='button';button.dataset.priority=key;
        button.append(node('strong',count));button.setAttribute('aria-pressed',String(priority===key));button.disabled=key!=='TODAS'&&!count;
        button.onclick=()=>{priority=key;load();};$('charge-priorities').append(button);
      }
      for(const [label,value] of [['Visitas nos filtros',result.visitas.length],['Repasse informado',money.format(result.repasseInformado)],['Sem valor informado',result.semValor],['Sem medico',result.semMedico],['Sem data',result.semData]]){
        const item=node('div',label);item.append(node('strong',value));$('charge-stats').append(item);
      }
      $('charge-reference').textContent=`${result.hospital} - referencia ${result.referencia.split('-').reverse().join('/')} - ${result.totalSemCorrespondencia} visitas sem correspondência neste relatório.`;
      $('charge-message').textContent=result.visitas.length?`${result.visitas.length} visitas nos filtros selecionados.`:'Nenhuma visita sem correspondência nos filtros selecionados.';
      for(const v of result.visitas){
        const contact=contacts.find(c=>c.linha===v.linha)?.atual;
        const row=document.createElement('tr');
        const values=[v.linha,v.data?v.data.split('-').reverse().join('/'):'Não informada',v.atendimento,v.medico||'Não identificado',v.codigo||'Não informado',v.procedimento||'Não informado',v.dias==null?'Sem data':v.dias<0?'Data futura':v.dias,v.repasse==null?'Não informado':money.format(v.repasse),contactLabels[contact?.situacao||'NAO_COBRADA'],date(contact?.proximoContato)];
        values.forEach((value,index)=>{const td=node('td','');td.append(node('span',value));
          if(index===0){const label=node('label','');label.className='charge-selection';const input=document.createElement('input');input.type='checkbox';input.dataset.selectLine=v.linha;input.setAttribute('aria-label',`Selecionar visita da linha ${v.linha}`);input.onchange=()=>{if(input.checked){if(selected.size>=500){input.checked=false;alert('Selecione até 500 visitas por lote.');return;}selected.set(v.linha,{linha:v.linha,versao:contact?.versao||0});}else selected.delete(v.linha);selectionChanged();};label.append(input,node('span',value));td.replaceChildren(label);}
          if(index===8&&contact?.responsavel){const actor=node('small',contact.responsavel);actor.title='Responsavel pelo último acompanhamento';td.append(actor);}
          if(index===9&&contact?.proximoContato){td.dataset.contactDate=contact.proximoContato;if(contact.proximoContato<result.referencia)td.classList.add('charge-overdue');else if(contact.proximoContato===result.referencia)td.classList.add('charge-due-today');}
          td.dataset.label=document.querySelectorAll('.charge-table th')[index].textContent;row.append(td);});
        const action=node('td','');action.dataset.label='Ações';action.className='charge-row-actions';const link=node('a','Revisar');link.href=`/conciliacao.html?${new URLSearchParams({relatorio:id,atendimento:v.atendimento,linha:v.linha})}`;
        const button=node('button','Acompanhar');button.type='button';button.onclick=()=>openContact(id,v.linha);action.append(link,button);row.append(action);$('charge-rows').append(row);
      }
      const archived=contacts.filter(c=>!c.naFila);
      $('charge-archive-message').textContent=archived.length?`${archived.length} acompanhamentos preservados fora da fila atual do relatório.`:'Nenhum acompanhamento fora da fila.';
      for(const c of archived){const row=node('tr','');
        const values=[c.linha,c.atendimento,c.medico||'Não identificado',c.statusConciliacao==='PAGA'?'Consta no faturamento':c.statusConciliacao==='PENDENTE'?'Pendente':'Divergente',contactLabels[c.atual.situacao]];
        values.forEach((value,index)=>{const td=node('td','');td.append(node('span',value));td.dataset.label=document.querySelectorAll('.charge-archive th')[index].textContent;row.append(td);});
        const td=node('td','');td.dataset.label='Histórico';const button=node('button','Ver histórico');button.type='button';button.onclick=()=>openContact(id,c.linha);td.append(button);row.append(td);$('charge-archive-rows').append(row);
      }
      const url=new URL(location.href);url.searchParams.set('relatorio',id);url.searchParams.set('prioridade',priority);history.replaceState(null,'',url);
      $('conciliation-link').href=`/conciliacao.html?relatorio=${id}`;
      $('charge-export').href=`/api/conciliacoes/visitas/${id}/cobranca.xlsx?${params}`;$('charge-export').hidden=!result.visitas.length;
    }catch(error){if(token===generation)$('charge-message').textContent=error.message;}
  }
  function updateContactRequired(){
    const form=$('contact-form');const status=form.elements.situacao.value;
    form.elements.dataCobranca.required=status!=='NAO_COBRADA';form.elements.resposta.required=status==='RETORNO_RECEBIDO';
  }
  function renderContact(detail){
    contactDetail=detail;const form=$('contact-form');
    $('contact-title').textContent=`Atendimento ${detail.atendimento} - linha ${detail.linha}`;
    for(const name of ['situacao','dataCobranca','resposta','observacoes','proximoContato'])form.elements[name].value=detail.atual[name]||'';
    $('contact-fields').disabled=!detail.podeEditar;$('contact-save').disabled=!detail.podeEditar;
    $('contact-message').textContent=detail.podeEditar?'':'Acompanhamento somente para consulta: visita fora da fila ou versao historica.';
    $('contact-history').replaceChildren();
    for(const record of detail.historico){const li=node('li','');
      li.append(node('strong',contactLabels[record.situacao]),node('p',`${record.responsavel} - ${new Date(record.criadoEm).toLocaleString('pt-BR',{timeZone:'America/Sao_Paulo'})}`),node('p',`Cobranca: ${date(record.dataCobranca)}. Proximo contato: ${date(record.proximoContato)}.`));
      if(record.resposta)li.append(node('p',`Resposta: ${record.resposta}`));if(record.observacoes)li.append(node('p',`Observacoes: ${record.observacoes}`));$('contact-history').append(li);
    }
    if(!detail.historico.length)$('contact-history').append(node('li','Nenhum contato registrado.'));
    updateContactRequired();
  }
  async function openContact(id,linha){
    batchMode=false;batchStale=false;$('contact-history').closest('section').hidden=false;
    const token=++contactGeneration;contactReport=id;contactLine=linha;contactDetail=null;
    $('contact-form').reset();$('contact-fields').disabled=true;$('contact-save').disabled=true;$('contact-reload').hidden=true;$('contact-history').replaceChildren();$('contact-message').textContent='Carregando acompanhamento...';
    $('contact-title').textContent='Acompanhamento da cobrança';if(!$('contact-dialog').open)$('contact-dialog').showModal();
    try{const detail=await window.FaturamedAuth.request(`/api/conciliacoes/visitas/${id}/cobranca/visitas/${linha}`);if(token===contactGeneration&&$('contact-dialog').open)renderContact(detail);}
    catch(error){if(token===contactGeneration){$('contact-message').textContent=error.message;$('contact-reload').hidden=false;}}
  }
  $('contact-form').elements.situacao.addEventListener('change',()=>{if($('contact-form').elements.situacao.value==='NAO_COBRADA')$('contact-form').elements.dataCobranca.value='';updateContactRequired();});
  $('contact-close').addEventListener('click',()=>{if(!contactSaving){contactGeneration++;$('contact-dialog').close();}});
  $('contact-dialog').addEventListener('cancel',event=>{if(contactSaving)event.preventDefault();else contactGeneration++;});
  $('charge-select-all').addEventListener('change',()=>{selected.clear();if($('charge-select-all').checked&&visibleVisits.length<=500)for(const v of visibleVisits)selected.set(v.linha,v);selectionChanged();});
  $('charge-unselect').addEventListener('click',()=>{selected.clear();selectionChanged();});
  $('charge-batch').addEventListener('click',()=>{
    if(!selected.size)return;contactGeneration++;batchMode=true;batchStale=false;contactDetail=null;contactReport=$('charge-report').value;batchSelection=[...selected.values()];
    const form=$('contact-form');form.reset();form.elements.situacao.value='ENVIADA';form.elements.dataCobranca.value=referenceDate;
    $('contact-title').textContent=`${batchSelection.length} visitas - ${reports.find(r=>String(r.id)===contactReport)?.hospital} - relatório #${contactReport}`;
    $('contact-fields').disabled=false;$('contact-save').disabled=false;$('contact-reload').hidden=true;$('contact-message').textContent='';$('contact-history').closest('section').hidden=true;updateContactRequired();$('contact-dialog').showModal();
  });
  $('contact-reload').addEventListener('click',()=>{if(!contactSaving&&confirm('Recarregar acompanhamento? Alterações não salvas serao descartadas.')){if(batchMode){$('contact-dialog').close();load();}else openContact(contactReport,contactLine);}});
  $('contact-form').addEventListener('submit',async event=>{
    event.preventDefault();if(contactSaving||(batchMode?batchStale:!contactDetail?.podeEditar))return;
    const fields=$('contact-form').elements,body=batchMode?{visitas:batchSelection}:{versao:contactDetail.atual.versao};for(const name of ['situacao','dataCobranca','resposta','observacoes','proximoContato'])body[name]=fields[name].value||null;
    if(batchMode&&!confirm(`Registrar ${contactLabels[body.situacao]} para ${$('contact-title').textContent}?`))return;
    contactSaving=true;$('contact-fields').disabled=true;$('contact-save').disabled=true;$('contact-close').disabled=true;$('contact-reload').disabled=true;$('contact-message').textContent='Salvando acompanhamento...';
    try{const response=await fetch(`/api/conciliacoes/visitas/${contactReport}/cobranca/${batchMode?'lote':`visitas/${contactLine}`}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});const result=await response.json();if(!response.ok){if(batchMode&&response.status===409)batchStale=true;throw new Error(result.erro||'Não foi possível salvar o acompanhamento.');}const count=batchMode?result.registradas:null;$('contact-dialog').close();await load();if(count)$('charge-message').textContent=`${count} acompanhamentos registrados.`;}
    catch(error){$('contact-message').textContent=error.message;$('contact-reload').hidden=false;}
    finally{contactSaving=false;const disabled=batchMode?batchStale:!contactDetail?.podeEditar;$('contact-fields').disabled=disabled;$('contact-save').disabled=disabled;$('contact-close').disabled=false;$('contact-reload').disabled=false;}
  });
  $('charge-hospital').addEventListener('change',()=>{reportOptions();$('charge-doctor').value='';load();});
  $('charge-report').addEventListener('change',()=>{$('charge-doctor').value='';load();});
  for(const id of ['charge-doctor','charge-age'])$(id).addEventListener('change',load);
  $('charge-reload').addEventListener('click',async()=>{try{await refreshReports();await load();}catch(error){clear();$('charge-message').textContent=error.message;}});
  refreshReports().then(()=>{const id=new URLSearchParams(location.search).get('relatorio');if([...$('charge-report').options].some(o=>o.value===id))$('charge-report').value=id;return load();}).catch(error=>{$('charge-message').textContent=error.message;});
})();
