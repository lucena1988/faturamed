(() => {
const selects=[...document.querySelectorAll('[data-version-base]')];
for(const select of selects){
 const form=select.closest('form'),label=document.createElement('label');label.hidden=true;label.dataset.versionKindLabel='';label.textContent='Atualizacao';
 const kind=document.createElement('select');kind.name='tipoAtualizacao';kind.dataset.versionKind='';kind.append(new Option('Producao e faturamento completos','COMPLETA'),new Option('Somente faturamento completo do hospital','HOSPITAL'));label.append(kind);select.closest('label').after(label);
 const update=()=>{label.hidden=!select.value;const hospital=!!select.value&&kind.value==='HOSPITAL';const file=form.querySelector('[name=producao]');file.disabled=hospital;file.required=!hospital;file.closest('label').dataset.versionProduction='';file.closest('label').hidden=hospital;};
 update();
 kind.addEventListener('change',update);select.addEventListener('change',()=>{kind.value='COMPLETA';update();});form.addEventListener('reset',()=>setTimeout(update,0));
}
let metadata=[];
function showSummary(result){
 if(!result.resumoAtualizacao&&!result.semAlteracoes)return;
 const dialog=document.createElement('dialog');dialog.className='diagnostic-dialog';dialog.setAttribute('aria-labelledby','update-summary-title');
 const title=document.createElement('h2');title.id='update-summary-title';title.textContent=result.semAlteracoes?'Versao existente mantida':`Atualizacao concluida - versao ${result.numeroVersao}`;
 dialog.append(title);
 const s=result.resumoAtualizacao,money=new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'});
 if(s){
  const summary=document.createElement('dl');summary.className='diagnostic-summary';
  for(const [label,value] of [['Visitas preservadas',result.preservadas],['Novas visitas faturadas',s.novasPagas],['Repasse das novas faturadas',money.format(s.valorNovasPagas)],['Pendentes',s.pendentes],['Repasse pendente informado',money.format(s.valorPendenteInformado)],['Divergentes',s.divergentes],['Repasse divergente informado',money.format(s.valorDivergenteInformado)]]){
   const item=document.createElement('div'),dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=label;dd.textContent=value;item.append(dt,dd);summary.append(item);
  }
  dialog.append(summary);const missing=document.createElement('p');missing.textContent=`Sem valor de repasse informado: ${s.pendentesSemValor} pendentes e ${s.divergentesSemValor} divergentes.`;dialog.append(missing);
 }else{const unchanged=document.createElement('p');unchanged.textContent='Arquivos identicos. Nenhuma nova versao foi criada.';dialog.append(unchanged);}
 const actions=document.createElement('div');actions.className='diagnostic-actions';
 const link=document.createElement('a');link.href=`/conciliacao.html?relatorio=${result.id}`;link.textContent='Abrir conciliacao';
 const close=document.createElement('button');close.type='button';close.textContent='Fechar';close.onclick=()=>dialog.close();
 actions.append(link,close);dialog.append(actions);dialog.addEventListener('close',()=>dialog.remove(),{once:true});document.body.append(dialog);dialog.showModal();
}
async function request(url){const r=await fetch(url);const data=await r.json();if(!r.ok)throw new Error(data.erro||`Falha (${r.status})`);return data;}
async function load(){const reports=await request('/api/conciliacoes/visitas');metadata=reports;for(const select of selects){const current=select.value;
 select.replaceChildren(new Option('Novo relatorio',''),...reports.filter(r=>!r.proxima_id).map(r=>new Option(`Atualizar #${r.id} - v${r.numero_versao||1} - ${r.hospital}`,r.id)));
 if([...select.options].some(o=>o.value===current))select.value=current;
 else if(current)select.dispatchEvent(new Event('change'));
}}
for(const select of selects) select.addEventListener('change',async()=>{
 const id=select.value;if(!id){await window.FaturamedProducao?.set(select.closest('form'),null,false);return;}
 try{const r=await request(`/api/conciliacoes/visitas/${id}`);if(select.value!==id)return;
 const form=select.closest('form');const name=form.querySelector('[data-hospital-name]'),hospital=form.querySelector('[data-hospital-select]');
 await window.FaturamedProducao?.set(form,metadata.find(item=>String(item.id)===id)?.medico_producao_id??null,true);
 if(name)name.value=r.hospital;
 if(hospital&&r.hospitalCadastroId!=null){hospital.value=String(r.hospitalCadastroId);if(hospital.value!==String(r.hospitalCadastroId))throw new Error('O hospital da versao base nao esta ativo na lista. Confira o cadastro antes de atualizar.');}
 else if(hospital)hospital.value='';
 if(name)name.readOnly=!!hospital?.value;
 }catch(e){select.value='';await window.FaturamedProducao?.set(select.closest('form'),null,false);alert(e.message);}
});
window.FaturamedVersoes={load,showSummary,async prepare(form){const data=new FormData(form),id=data.get('relatorioBaseId');await window.FaturamedProducao?.prepare(form,data);if(window.FaturamedDiagnostico&&!await window.FaturamedDiagnostico.confirm(form,data))return null;if(!id){data.delete('relatorioBaseId');return data;}
 if(!confirm(`Criar nova versao do relatorio #${id}? ${data.get('tipoAtualizacao')==='HOSPITAL'?'A producao salva sera reutilizada.':'A producao completa enviada sera utilizada.'} Conciliacoes e revisoes anteriores serao preservadas.`))return null;
 const current=await request(`/api/conciliacoes/visitas/${id}/acompanhamento`);data.set('versaoBase',String(current.versao));return data;
},message(result){const text=result.semAlteracoes?'Arquivos identicos: a versao existente foi mantida.':result.numeroVersao?`Versao ${result.numeroVersao}: ${result.preservadas} visitas preservadas, ${result.reavaliadas} reavaliadas e ${result.novas} novas.`:`Relatorio #${result.id} salvo.`;
 const s=result.resumoAtualizacao,money=new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'});
 const summary=s?` ${s.novasPagas} novas visitas faturadas (${money.format(s.valorNovasPagas)}). Pendentes: ${s.pendentes}, repasse informado ${money.format(s.valorPendenteInformado)} (${s.pendentesSemValor} sem valor). Divergentes: ${s.divergentes}, repasse informado ${money.format(s.valorDivergenteInformado)} (${s.divergentesSemValor} sem valor).`:'';
 return text+summary+(result.identificacaoProducao?` Medico preenchido em ${result.identificacaoProducao.preenchidas} linhas; ${result.identificacaoProducao.conflitos} linhas com medico diferente para conferencia.`:'');}};
load().catch(e=>{for(const select of selects)select.title=e.message;});
})();
