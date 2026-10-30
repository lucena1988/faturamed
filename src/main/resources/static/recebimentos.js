(() => {
  const $=id=>document.getElementById(id),money=new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'});
  const amount=v=>v==null?'Não informado':money.format(v),date=v=>v?v.split('-').reverse().join('/'):'Não informada';
  const node=(tag,text)=>{const e=document.createElement(tag);e.textContent=text;return e;};
  let reports=[],result=null,detail=null,generation=0,saving=false;
  const selected=new Map(),{centavos,moeda}=FaturamedPagamentoValores;
  let paymentVisits=[],paymentReport=null,paymentDoctor=null,paymentHospital='',paymentStale=false,paymentVersion=null;
  const reportId=()=>$('financial-report').value;
  function eligible(){return (result?.visitas||[]).filter(v=>v.podeRegistrar&&(!$('financial-doctor').value||String(v.medicoId)===$('financial-doctor').value));}
  function selectionChanged(){const visits=eligible(),all=$('payment-select-all'),mixed=new Set(visits.map(v=>v.medicoId)).size>1;all.disabled=!visits.length||visits.length>500||mixed;all.checked=!!visits.length&&selected.size===visits.length;all.indeterminate=!!selected.size&&!all.checked;all.title=mixed?'Selecione um médico ou marque uma visita primeiro':visits.length>500?'Selecione até 500 visitas por pagamento':'Selecionar as visitas disponíveis deste médico';
    $('payment-selected').textContent=`${selected.size} visitas selecionadas`;$('payment-open').disabled=!selected.size;$('payment-unselect').disabled=!selected.size;
    for(const input of document.querySelectorAll('[data-payment-select]'))input.checked=selected.has(Number(input.dataset.paymentSelect));
  }
  function clearSelection(){selected.clear();selectionChanged();}
  function options(){const current=reportId();$('financial-report').replaceChildren(new Option('Selecione um relatório',''),...reports.filter(r=>!$('financial-hospital').value||r.hospital===$('financial-hospital').value).map(r=>new Option(`#${r.id} - ${r.hospital}`,r.id)));if([...$('financial-report').options].some(o=>o.value===current))$('financial-report').value=current;}
  function render(){
    FaturamedDemonstrativo.selecionar(result?reportId():null,$('financial-doctor').value,result?.hospital||'',$('financial-doctor').selectedOptions[0]?.textContent||'');
    FaturamedConsultaPagamentos.carregar(result?reportId():null,$('financial-doctor').value);
    const visits=(result?.visitas||[]).filter(v=>!$('financial-doctor').value||String(v.medicoId||'sem')===$('financial-doctor').value);
    $('financial-rows').replaceChildren();$('financial-stats').replaceChildren();
    const known=visits.filter(v=>v.faturado!=null),unknown=visits.filter(v=>v.faturado==null).length;
    for(const [label,value] of [['Faturado',amount(known.reduce((s,v)=>s+Number(v.faturado),0))],['Recebido',amount(visits.reduce((s,v)=>s+Number(v.recebido),0))],['A receber',amount(known.reduce((s,v)=>s+Math.max(0,Number(v.saldo)),0))],['Sem faturamento confirmado',unknown],['Financeiro em conferencia',visits.filter(v=>v.inconsistente).length]]){const e=node('div',label);e.append(node('strong',value));$('financial-stats').append(e);}
    for(const v of visits){const tr=node('tr','');const values=[v.linha,v.atendimento,date(v.data),v.medico||'Não identificado',amount(v.faturado),amount(v.recebido),amount(v.saldo),v.inconsistente?'Financeiro em conferência':v.faturado==null?'Sem faturamento confirmado':Number(v.saldo)<=0?'Recebido':Number(v.recebido)>0?'Recebido parcialmente':'A receber'];
      values.forEach((value,i)=>{const td=node('td',value);td.dataset.label=document.querySelectorAll('th')[i].textContent;tr.append(td);});
      const label=node('label','');label.className='charge-selection';const checkbox=document.createElement('input');checkbox.type='checkbox';checkbox.dataset.paymentSelect=v.linha;checkbox.disabled=!v.podeRegistrar;checkbox.setAttribute('aria-label',`Selecionar visita da linha ${v.linha}`);checkbox.title=checkbox.disabled?result.historico?'Versao historica: somente consulta':v.inconsistente?'Financeiro em conferência':v.medicoId==null?'Médico não vinculado ao cadastro':v.faturado==null?'Sem faturamento confirmado':'Visita sem saldo a receber':'Selecionar visita para pagamento';label.title=checkbox.title;
      checkbox.onchange=()=>{if(checkbox.checked){if(selected.size>=500){checkbox.checked=false;alert('Selecione até 500 visitas.');return;}if(!$('financial-doctor').value){$('financial-doctor').value=String(v.medicoId);selected.clear();selected.set(v.linha,v);render();return;}selected.set(v.linha,v);}else selected.delete(v.linha);selectionChanged();};label.append(checkbox,node('span',v.linha));tr.firstChild.replaceChildren(label);
      const td=node('td','');td.dataset.label='Ações';const button=node('button',v.podeRegistrar?'Registrar':'Histórico');button.type='button';button.onclick=()=>open(v);td.append(button);tr.append(td);$('financial-rows').append(tr);
    }
    selectionChanged();
  }
  async function load(){const token=++generation;result=null;clearSelection();render();$('financial-message').textContent=reportId()?'Carregando...':'Selecione um relatório.';if(!reportId())return;
    try{const data=await FaturamedAuth.request(`/api/conciliacoes/visitas/${reportId()}/recebimentos`);if(token!==generation)return;result=data;
      const previous=$('financial-doctor').value,doctors=new Map();data.visitas.forEach(v=>doctors.set(String(v.medicoId||'sem'),v.medico||'Não identificado'));
      $('financial-doctor').replaceChildren(new Option('Todos os médicos',''),...[...doctors].map(([id,name])=>new Option(name,id)));if(doctors.has(previous))$('financial-doctor').value=previous;
      $('financial-message').textContent=data.historico?'Versao historica: somente consulta.':'';render();
    }catch(e){if(token===generation)$('financial-message').textContent=e.message;}
  }
  function open(v){detail=v;$('contact-title').textContent=`${v.medico||'Nao identificado'} - atendimento ${v.atendimento} - linha ${v.linha}`;
    $('financial-form').reset();$('financial-fields').disabled=!v.podeRegistrar;$('financial-save').disabled=!v.podeRegistrar;$('financial-form').elements.valor.max=v.saldo||'';
    $('financial-detail-message').textContent=v.inconsistente?'Dados da conciliação alterados: confira ou estorne o recebimento.':v.podeRegistrar?`Saldo: ${amount(v.saldo)}`:'Registro disponível somente para visitas faturadas, com saldo e médico cadastrado.';
    $('financial-history').replaceChildren();for(const e of v.historico){const li=node('li',`${date(e.data)} - ${amount(e.valor)}${e.estornoDe?' - Estorno':e.estornado?' - Estornado':''}`);if(e.pagamentoId)li.append(node('p',`Pagamento #${e.pagamentoId} - ${e.referencia}`));li.append(node('p',e.observacoes),node('p',`${e.responsavel} - ${new Date(e.criadoEm).toLocaleString('pt-BR')}`));
      if(!e.estornoDe&&!e.estornado&&!result.historico){const button=node('button','Estornar');button.type='button';button.onclick=async()=>{const justificativa=prompt('Justificativa do estorno:');if(!justificativa?.trim()||!confirm(`Estornar ${amount(e.valor)} da linha ${v.linha}?`))return;await save(`/visitas/${v.linha}/${e.id}/estorno`,{justificativa,versao:v.versao});};li.append(button);}$('financial-history').append(li);
    }if(!v.historico.length)$('financial-history').append(node('li','Nenhum recebimento registrado.'));$('contact-dialog').showModal();
  }
  async function save(path,body){if(saving)return;saving=true;$('financial-fields').disabled=true;$('financial-save').disabled=true;$('financial-close').disabled=true;for(const b of $('financial-history').querySelectorAll('button'))b.disabled=true;
    try{await FaturamedAuth.request(`/api/conciliacoes/visitas/${reportId()}/recebimentos${path}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});$('contact-dialog').close();await load();$('financial-message').textContent='Registro financeiro salvo.';}
    catch(e){$('financial-detail-message').textContent=e.message+' Feche e atualize a tela antes de tentar novamente.';}
    finally{saving=false;$('financial-close').disabled=false;}
  }
  $('financial-form').onsubmit=async e=>{e.preventDefault();if(!detail?.podeRegistrar||saving)return;const f=e.target.elements;const body={valor:f.valor.value,data:f.data.value,observacoes:f.observacoes.value,versao:detail.versao};if(confirm(`Registrar ${amount(body.valor)} recebido por ${detail.medico}, no hospital ${result.hospital}, linha ${detail.linha}?`))await save(`/visitas/${detail.linha}`,body);};
  $('financial-close').onclick=()=>{if(!saving)$('contact-dialog').close();};$('contact-dialog').oncancel=e=>{if(saving)e.preventDefault();};
  function distribution(){const fields=$('payment-form').elements,total=centavos(fields.valorTotal.value);let sum=0n,valid=!!paymentVisits.length;const allocations=[];
    for(const v of paymentVisits){const input=$('payment-rows').querySelector(`[data-allocation-line="${v.linha}"]`),value=centavos(input.value),saldo=centavos(String(v.saldo));
      input.setCustomValidity(value==null||value<=0n?'Informe valor positivo com até duas casas decimais':saldo==null||value>saldo?'Valor excede o saldo da visita':'');
      if(value==null||value<=0n||saldo==null||value>saldo)valid=false;if(value!=null)sum+=value;
      allocations.push({linha:v.linha,versao:v.versao,valor:input.value});
    }
    $('payment-summary').replaceChildren();for(const [label,value] of [['Total recebido',total],['Distribuido',sum],['Falta distribuir',total==null?null:total-sum]]){const e=node('div',label);e.append(node('strong',moeda(value)));$('payment-summary').append(e);}
    $('payment-save').disabled=saving||paymentStale||!valid||total==null||total<=0n||sum!==total;
    return {total,sum,valid,allocations};
  }
  function renderDistribution(){const body=$('payment-rows');body.replaceChildren();for(const v of paymentVisits){const tr=node('tr','');[v.linha,v.atendimento,date(v.data),amount(v.saldo)].forEach((value,i)=>{const td=node('td',value);td.dataset.label=body.closest('table').querySelectorAll('th')[i].textContent;tr.append(td);});
      const value=node('td','');value.dataset.label='Valor para a visita (R$)';const input=document.createElement('input');input.type='number';input.min='0.01';input.step='0.01';input.max=v.saldo;input.required=true;input.dataset.allocationLine=v.linha;input.setAttribute('aria-label',`Valor para a linha ${v.linha}`);input.oninput=distribution;value.append(input);tr.append(value);
      const actions=node('td','');actions.dataset.label='Ações';actions.className='charge-row-actions';const saldo=node('button','Usar saldo');saldo.type='button';saldo.onclick=()=>{input.value=v.saldo;distribution();};const remove=node('button','Remover');remove.type='button';remove.onclick=()=>{const values=new Map([...body.querySelectorAll('[data-allocation-line]')].map(e=>[Number(e.dataset.allocationLine),e.value]));paymentVisits=paymentVisits.filter(x=>x.linha!==v.linha);selected.delete(v.linha);selectionChanged();renderDistribution();for(const e of body.querySelectorAll('[data-allocation-line]'))e.value=values.get(Number(e.dataset.allocationLine))||'';distribution();};const controls=node('div','');controls.append(saldo,remove);actions.append(controls);tr.append(actions);body.append(tr);
    }distribution();
  }
  $('payment-select-all').onchange=()=>{selected.clear();const visits=eligible();if($('payment-select-all').checked&&visits.length<=500&&new Set(visits.map(v=>v.medicoId)).size===1){if(!$('financial-doctor').value)$('financial-doctor').value=String(visits[0].medicoId);for(const v of visits)selected.set(v.linha,v);}render();};$('payment-unselect').onclick=clearSelection;
  $('payment-open').onclick=()=>{if(!selected.size)return;paymentVisits=[...selected.values()];paymentReport=reportId();paymentDoctor=paymentVisits[0].medicoId;paymentHospital=result.hospital;paymentVersion=result.versaoConciliacao;paymentStale=false;
    $('payment-form').reset();$('payment-fields').disabled=false;$('payment-reload').hidden=true;$('payment-message').textContent='';$('payment-title').textContent=`${paymentVisits[0].medico} - ${paymentHospital} - relatório #${paymentReport}`;renderDistribution();$('payment-dialog').showModal();};
  $('payment-form').elements.valorTotal.oninput=distribution;
  $('payment-form').onsubmit=async e=>{e.preventDefault();if(saving||paymentStale)return;const d=distribution();if(!d.valid||d.total==null||d.total!==d.sum)return;const fields=e.target.elements;
    const body={medicoId:paymentDoctor,data:fields.data.value,valorTotal:fields.valorTotal.value,referencia:fields.referencia.value.trim(),observacoes:fields.observacoes.value,visitas:d.allocations,versaoConciliacao:paymentVersion};
    if(!confirm(`Registrar pagamento de ${moeda(d.total)} para ${paymentVisits[0].medico}, hospital ${paymentHospital}, relatório #${paymentReport}, em ${paymentVisits.length} visitas? Referencia: ${body.referencia}`))return;
    saving=true;$('payment-fields').disabled=true;$('payment-save').disabled=true;$('payment-close').disabled=true;$('payment-reload').disabled=true;$('payment-message').textContent='Salvando pagamento...';
    try{const response=await fetch(`/api/conciliacoes/visitas/${paymentReport}/recebimentos/pagamentos`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});const data=await response.json();if(!response.ok){const error=new Error(data.erro||'Não foi possível registrar o pagamento.');error.status=response.status;throw error;}
      $('payment-dialog').close();await load();$('financial-message').textContent=`Pagamento #${data.pagamentoId} registrado em ${data.visitas} visitas.`;
    }catch(error){paymentStale=error.status!==400;$('payment-message').textContent=error.message+(paymentStale?' Atualize os saldos antes de tentar novamente.':'');$('payment-reload').hidden= !paymentStale;}
    finally{saving=false;$('payment-close').disabled=false;$('payment-reload').disabled=false;$('payment-fields').disabled=paymentStale;distribution();}
  };
  $('payment-close').onclick=()=>{if(!saving)$('payment-dialog').close();};$('payment-dialog').oncancel=e=>{if(saving)e.preventDefault();};$('payment-reload').onclick=()=>{if(!saving&&confirm('Atualizar saldos e descartar a distribuição não salva?')){$('payment-dialog').close();load();}};
  $('financial-hospital').onchange=()=>{options();load();};$('financial-report').onchange=load;$('financial-doctor').onchange=()=>{clearSelection();render();};$('financial-reload').onclick=load;
  FaturamedAuth.request('/api/conciliacoes/visitas').then(data=>{reports=data.filter(r=>!r.proxima_id);$('financial-hospital').replaceChildren(new Option('Todos os hospitais',''),...[...new Set(reports.map(r=>r.hospital))].map(h=>new Option(h,h)));options();const id=new URLSearchParams(location.search).get('relatorio');if(reports.some(r=>String(r.id)===id))$('financial-report').value=id;load();}).catch(e=>$('financial-message').textContent=e.message);
})();
