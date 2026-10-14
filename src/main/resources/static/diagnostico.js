(() => {
  const dialog=document.createElement('dialog');dialog.className='diagnostic-dialog';dialog.setAttribute('aria-labelledby','diagnostic-title');
  const title=document.createElement('h2');title.id='diagnostic-title';title.textContent='Diagnostico das planilhas';
  const content=document.createElement('div');
  const actions=document.createElement('div');actions.className='diagnostic-actions';
  const cancel=document.createElement('button');cancel.type='button';cancel.textContent='Voltar';
  const proceed=document.createElement('button');proceed.type='button';proceed.textContent='Continuar conciliacao';
  actions.append(cancel,proceed);dialog.append(title,content,actions);document.body.append(dialog);
  function text(tag,value){const el=document.createElement(tag);el.textContent=value;return el;}
  function date(value){return value?value.split('-').reverse().join('/'):'Nao informada';}
  function render(data){
    content.replaceChildren();
    const summary=document.createElement('dl');summary.className='diagnostic-summary';
    for(const [label,value] of [['Visitas na producao',data.producao.linhas],['Registros no hospital',data.hospital.linhas],['Correspondencias seguras',data.correspondenciasSeguras],['Sem correspondencia',data.semCorrespondencia],['A conferir',data.ambiguas+data.dadosDivergentes]]){
      const item=document.createElement('div');item.append(text('dt',label),text('dd',value));summary.append(item);
    }
    content.append(summary,text('p',`Producao: ${date(data.producao.inicio)} a ${date(data.producao.fim)}. Hospital: ${date(data.hospital.inicio)} a ${date(data.hospital.fim)}.`));
    content.append(text('p',`IDs repetidos: ${data.producao.idsRepetidos} na producao (${data.producao.linhasComIdRepetido} linhas) e ${data.hospital.idsRepetidos} no hospital (${data.hospital.linhasComIdRepetido} linhas).`));
    content.append(text('p',`Sem medico preenchido no arquivo original: ${data.producao.semMedico} linhas da producao.`));
    if(!data.alertas.length)content.append(text('p','Nenhum alerta encontrado.'));
    for(const a of data.alertas){const section=document.createElement('section');section.append(text('h3',`${a.descricao}: ${a.quantidade}`),text('p',`Linhas do arquivo ${a.codigo.includes('HOSPITAL')&&a.codigo.startsWith('ID_')?'hospitalar':'de producao'}: ${a.linhas.join(', ')}${a.quantidade>a.linhas.length?' (primeiras 20)':''}.`));content.append(section);}
    content.append(text('p','Os alertas podem se sobrepor. ID repetido nao identifica sozinho uma visita. Sem correspondencia nao comprova falta de pagamento.'));
  }
  let pending=false;
  async function inspect(form,data,confirm){
    if(pending)throw new Error('Aguarde o diagnostico atual.');pending=true;
    try{
      const result=await window.FaturamedAuth.request('/api/conciliacoes/visitas/diagnostico',{method:'POST',body:data});
      render(result);proceed.hidden=!confirm;
      return await new Promise(resolve=>{
        const close=()=>{dialog.removeEventListener('close',close);resolve(dialog.returnValue==='continuar');};
        cancel.onclick=()=>dialog.close('cancelar');proceed.onclick=()=>dialog.close('continuar');
        dialog.returnValue='cancelar';dialog.addEventListener('close',close);dialog.showModal();
      });
    }finally{pending=false;}
  }
  window.FaturamedDiagnostico={confirm:(form,data)=>inspect(form,data,true)};
  for(const form of [...document.querySelectorAll('form')].filter(f=>f.querySelector('[data-production-mode]'))){
    const button=text('button','Analisar planilhas');button.type='button';button.className='diagnostic-button';
    form.querySelector('[type=submit]').before(button);
    button.addEventListener('click',async()=>{if(!form.reportValidity())return;button.disabled=true;
      const message=form.querySelector('[data-production-message]');message.textContent='Analisando planilhas...';
      try{const data=new FormData(form);await window.FaturamedProducao.prepare(form,data);await inspect(form,data,false);message.textContent='Diagnostico concluido. Nenhum relatorio foi salvo.';}
      catch(error){message.textContent=error.message;}finally{button.disabled=false;}
    });
  }
})();
