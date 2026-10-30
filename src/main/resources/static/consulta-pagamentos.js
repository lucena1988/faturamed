(() => {
  const $=id=>document.getElementById(id),money=new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'});
  const date=v=>v?v.split('-').reverse().join('/'):'Não informada',format=v=>money.format(v);
  const labels={SEM_ESTORNO:'Sem estorno',PARCIALMENTE_ESTORNADO:'Parcialmente estornado',ESTORNADO:'Estornado',CONFERENCIA:'Em conferência'};
  const node=(tag,text)=>{const e=document.createElement(tag);e.textContent=text;return e;};let report=null,lastReport=null,doctor='',generation=0,detailGeneration=0;
  let attachmentReport=null,attachmentPayment=null,attachmentBusy=false,attachmentEditable=false;
  function row(id,values){const tr=node('tr','');values.forEach((v,i)=>{const td=node('td',v);td.dataset.label=$(id).closest('table').querySelectorAll('th')[i].textContent;tr.append(td);});$(id).append(tr);return tr;}
  function stats(id,data){$(id).replaceChildren();for(const [label,value] of [['Total original',data.valorOriginal],['Estornos acumulados',data.estornos],['Valor liquido atual',data.liquido]]){const item=node('div',label);item.append(node('strong',format(value)));$(id).append(item);}}
  async function load(){const token=++generation;$('payments-rows').replaceChildren();$('payments-stats').replaceChildren();$('payments-filter').disabled=!report;$('payments-clear').disabled=!report;
    if(!report){$('payments-message').textContent='Selecione um relatório.';return;}if(doctor==='sem'){$('payments-message').textContent='Nenhum pagamento para médico não identificado.';return;}
    const params=new URLSearchParams(new FormData($('payments-filters')));if(doctor)params.set('medicoId',doctor);for(const [k,v] of [...params])if(!v)params.delete(k);
    $('payments-message').textContent='Carregando pagamentos...';const id=report;
    try{const data=await FaturamedAuth.request(`/api/conciliacoes/visitas/${id}/recebimentos/pagamentos?${params}`);if(token!==generation)return;stats('payments-stats',data);$('payments-message').textContent=`${data.pagamentos.length} pagamentos nos filtros - ${data.hospital}.`;
      for(const p of data.pagamentos){const tr=row('payments-rows',[`#${p.id} - ${p.referencia}`,date(p.data),p.medico,p.visitas.length,format(p.valorOriginal),format(p.estornos),format(p.liquido),labels[p.situacao]]);const td=node('td','');td.dataset.label='Ações';const button=node('button','Ver pagamento');button.type='button';button.onclick=()=>open(id,p.id);td.append(button);tr.append(td);}
      if(!data.pagamentos.length)row('payments-rows',['Nenhum pagamento nos filtros.','','','','','','','','']);
    }catch(e){if(token===generation)$('payments-message').textContent=e.message;}
  }
  async function open(id,payment){const token=++detailGeneration;attachmentReport=id;attachmentPayment=payment;attachmentEditable=false;$('comprovante-form').reset();$('comprovante-fields').disabled=true;$('comprovantes-list').replaceChildren();$('comprovantes-message').textContent='';$('payment-consult-title').textContent=`Pagamento #${payment}`;$('payment-consult-message').textContent='Carregando...';$('payment-consult-info').textContent='';$('payment-consult-visits').replaceChildren();$('payment-consult-history').replaceChildren();$('payment-consult-stats').replaceChildren();$('payment-consult-dialog').showModal();
    try{const p=await FaturamedAuth.request(`/api/conciliacoes/visitas/${id}/recebimentos/pagamentos/${payment}`);if(token!==detailGeneration||!$('payment-consult-dialog').open)return;
      $('payment-consult-title').textContent=`Pagamento #${p.id} - ${p.referencia}`;$('payment-consult-message').textContent=`${p.medico} - ${date(p.data)} - ${labels[p.situacao]} - relatório de origem #${p.relatorioOrigem}`;stats('payment-consult-stats',p);
      $('payment-consult-info').textContent=`${p.responsavel} - ${new Date(p.criadoEm).toLocaleString('pt-BR',{timeZone:'America/Sao_Paulo'})}${p.observacoes?'\n'+p.observacoes:''}`;
      for(const v of p.visitas)row('payment-consult-visits',[v.lancamento,v.linha,v.atendimento,date(v.dataVisita),v.codigo,format(v.valor),format(v.estornos),format(v.liquido)]);
      for(const e of p.historico){const li=node('li',`${date(e.data)} - ${e.estornoDe?'Estorno do lancamento #'+e.estornoDe:'Recebimento #'+e.id} - linha ${e.linha} - ${format(e.valor)}`);li.append(node('p',e.observacoes),node('p',`${e.responsavel} - ${new Date(e.criadoEm).toLocaleString('pt-BR',{timeZone:'America/Sao_Paulo'})}`));$('payment-consult-history').append(li);}
      await loadAttachments(token);
    }catch(e){if(token===detailGeneration)$('payment-consult-message').textContent=e.message;}
  }
  function attachmentPath(){return `/api/conciliacoes/visitas/${attachmentReport}/recebimentos/pagamentos/${attachmentPayment}/comprovantes`;}
  function renderAttachments(data){attachmentEditable=data.podeAlterar;$('comprovante-fields').disabled=attachmentBusy||!attachmentEditable;$('comprovantes-list').replaceChildren();
    for(const c of data.comprovantes){const li=node('li','');li.append(node('strong',c.nome),node('p',`${Math.ceil(c.tamanho/1024)} KB - ${c.responsavel} - ${new Date(c.criadoEm).toLocaleString('pt-BR',{timeZone:'America/Sao_Paulo'})}`));
      if(c.retiradoEm)li.append(node('p',`Retirado de uso por ${c.retiradoPor} em ${new Date(c.retiradoEm).toLocaleString('pt-BR',{timeZone:'America/Sao_Paulo'})}: ${c.motivoRetirada}`));
      else {const link=node('a','Baixar comprovante');link.href=attachmentPath()+'/'+c.id;li.append(link);if(attachmentEditable){const remove=node('button','Retirar de uso');remove.type='button';remove.dataset.attachmentAction='true';remove.disabled=attachmentBusy;remove.onclick=async()=>{const justificativa=prompt(`Justificativa para retirar ${c.nome} de uso:`);if(!justificativa?.trim()||!confirm(`Retirar ${c.nome} de uso? O histórico sera preservado.`))return;await changeAttachment('/'+c.id+'/retirada',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({justificativa})});};li.append(remove);}}
      $('comprovantes-list').append(li);
    }
    if(!data.comprovantes.length)$('comprovantes-list').append(node('li','Nenhum comprovante anexado.'));
    $('comprovantes-message').textContent=attachmentEditable?'':'Versao historica: comprovantes somente para consulta.';
  }
  async function loadAttachments(token){$('comprovantes-message').textContent='Carregando comprovantes...';try{const data=await FaturamedAuth.request(attachmentPath());if(token===detailGeneration&&$('payment-consult-dialog').open)renderAttachments(data);}catch(e){if(token===detailGeneration)$('comprovantes-message').textContent=e.message;}}
  async function changeAttachment(path,options){if(attachmentBusy||!attachmentEditable)return;attachmentBusy=true;$('comprovante-fields').disabled=true;$('payment-consult-close').disabled=true;for(const b of $('comprovantes-list').querySelectorAll('button'))b.disabled=true;$('comprovantes-message').textContent='Salvando comprovante...';
    try{const data=await FaturamedAuth.request(attachmentPath()+path,options);renderAttachments(data);$('comprovante-form').reset();$('comprovantes-message').textContent='Comprovantes atualizados.';}catch(e){$('comprovantes-message').textContent=e.message;}
    finally{attachmentBusy=false;$('payment-consult-close').disabled=false;$('comprovante-fields').disabled=!attachmentEditable;for(const b of $('comprovantes-list').querySelectorAll('button'))b.disabled=!attachmentEditable;}
  }
  $('comprovante-form').onsubmit=async e=>{e.preventDefault();const file=e.target.elements.arquivo.files[0];if(!file||file.size>5*1024*1024){$('comprovantes-message').textContent='Envie um arquivo de até 5 MB.';return;}const body=new FormData();body.append('arquivo',file);await changeAttachment('',{method:'POST',body});};
  $('payment-consult-close').onclick=()=>{if(!attachmentBusy){detailGeneration++;$('payment-consult-dialog').close();}};$('payment-consult-dialog').oncancel=e=>{if(attachmentBusy)e.preventDefault();else detailGeneration++;};
  $('payments-filters').onsubmit=e=>{e.preventDefault();load();};$('payments-clear').onclick=()=>{$('payments-filters').reset();load();};
  window.FaturamedConsultaPagamentos={carregar(id,medico){if(id&&lastReport!==id)$('payments-filters').reset();if(id)lastReport=id;report=id;doctor=medico;load();}};
})();
