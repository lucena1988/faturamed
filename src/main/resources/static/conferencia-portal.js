(() => {
  const $=id=>document.getElementById(id), form=$('conference-form'), node=(tag,text)=>{const e=document.createElement(tag);e.textContent=text;return e;};
  let report=null,state=null,generation=0,busy=false,highlighted=false;
  const date=v=>new Date(v).toLocaleString('pt-BR');
  function controls(){ $('conference-fields').disabled=busy||!state; $('conference-science').disabled=busy||!state||!!state.cienteEm; $('conference-refresh').disabled=busy||!report; }
  async function load(id){
    const token=++generation;report=id;state=null;form.reset();controls();form.elements.linha.replaceChildren();$('conference-visit').textContent='';$('conference-history').replaceChildren();$('conference-message').textContent=id?'Carregando conferencia...':'';
    if(!id)return;
    try{
      const data=await FaturamedAuth.request(`/api/medico/relatorios/${id}/conferencia`);
      const params=new URLSearchParams(location.search),focus=params.get('solicitacao');
      if(String(id)===params.get('relatorio')&&focus&&/^\d+$/.test(focus)&&!data.solicitacoes.some(item=>String(item.id)===focus)){
        data.solicitacoes.unshift(await FaturamedAuth.request(`/api/medico/relatorios/${id}/solicitacoes/${focus}`));
      }
      if(token!==generation)return;state=data;
      $('conference-message').textContent=`Publicacao #${data.publicacao}. `+(data.cienteEm?`Ciencia registrada em ${date(data.cienteEm)}.`:'Ciencia ainda nao registrada.');
      form.elements.linha.replaceChildren(new Option('Selecione uma visita',''),...data.visitas.map(v=>new Option(`Linha ${v.linhaMedico} | ${v.atendimento} | ${v.data||'Sem data'} | ${v.codigoProcedimento||''} ${v.procedimento||''}`,v.linhaMedico)));
      for(const item of data.solicitacoes){const li=node('li',`#${item.id} - Publicacao #${item.publicacao}${item.publicacaoAtual?'':' (anterior)'} - Linha ${item.linha} - Atendimento ${item.atendimento} - ${item.data||'Sem data'} - ${date(item.criadoEm)}`);li.id=`conference-request-${item.id}`;li.append(node('p',item.motivo),node('p',item.resposta?`Resposta (${date(item.respondidoEm)}): ${item.resposta}`:'Aguardando resposta'));$('conference-history').append(li);}
      if(!highlighted&&String(id)===params.get('relatorio')&&focus){const target=$(`conference-request-${focus}`);if(target){highlighted=true;target.tabIndex=-1;target.focus();target.scrollIntoView({block:'center'});}}
      if(!data.solicitacoes.length)$('conference-history').append(node('li','Nenhuma solicitacao registrada.'));
    }catch(e){if(token===generation)$('conference-message').textContent=e.message;}
    finally{controls();}
  }
  async function send(path,body){
    if(busy||!state)return;const token=generation,id=report;busy=true;controls();
    try{await FaturamedAuth.request(`/api/medico/relatorios/${id}/${path}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});if(token===generation)await load(id);}
    catch(e){if(token===generation){state=null;$('conference-message').textContent=e.message+' Atualize a conferencia antes de tentar novamente.';}}
    finally{busy=false;controls();}
  }
  $('conference-science').onclick=()=>{if(state&&confirm(`Registrar ciencia da publicacao #${state.publicacao}? Isto nao confirma pagamento nem aprovacao dos valores.`))send('ciencia',{publicacao:state.publicacao});};
  form.onsubmit=e=>{e.preventDefault();if(state)send('solicitacoes',{publicacao:state.publicacao,linha:Number(form.elements.linha.value),motivo:form.elements.motivo.value});};
  $('conference-refresh').onclick=()=>load(report);
  form.elements.linha.onchange=()=>{const v=state?.visitas.find(v=>v.linhaMedico===Number(form.elements.linha.value));$('conference-visit').textContent=v?`${v.status} | Repasse: ${v.repasse==null?'Nao informado':Number(v.repasse).toLocaleString('pt-BR',{style:'currency',currency:'BRL'})} | ${v.motivo||''}`:'';};
  window.FaturamedConferencia={carregar:load};
})();
