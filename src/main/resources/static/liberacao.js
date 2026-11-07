(() => {
  const $=id=>document.getElementById(id),node=(tag,text)=>{const e=document.createElement(tag);e.textContent=text;return e;};let report=null,state=null,generation=0,saving=false,lastVersion=null;
  function render(data){if(lastVersion!=null&&lastVersion!==data.versao)$('release-form').elements.confirmaPendencias.checked=false;lastVersion=data.versao;state=data;$('release-fields').disabled=saving||!data.podeLiberar;$('release-save').disabled=saving||!data.podeLiberar;
    const published=data.publicado;$('release-status').textContent=(data.situacao==='LIBERADO'?'Liberado para o médico.':'Em revisão.')+(published?` Publicação atual: relatório #${published.relatorio}, revisão ${published.versao}, por ${published.responsavel} em ${new Date(published.criadoEm).toLocaleString('pt-BR',{timeZone:'America/Sao_Paulo'})}.`:' Nenhuma versao desta cadeia foi liberada.');
    $('release-summary').textContent=`${data.visitas} visitas; ${data.pendentes} pendentes; ${data.divergentes} divergentes; ${data.semMedico} sem médico cadastrado.`;
    const alerta=!!(data.pendentes||data.divergentes||data.semMedico);$('release-ack').hidden=!alerta;$('release-form').elements.confirmaPendencias.required=alerta;
    $('release-history').replaceChildren();for(const e of data.historico){const li=node('li',`Relatório #${e.relatorio} - revisão ${e.versao} - ${e.responsavel} - ${new Date(e.criadoEm).toLocaleString('pt-BR',{timeZone:'America/Sao_Paulo'})}`);if(e.observacao)li.append(node('p',e.observacao));$('release-history').append(li);}
    if(!data.historico.length)$('release-history').append(node('li','Nenhuma liberação registrada.'));
  }
  async function load(id){const token=++generation;if(report!==String(id)){$('release-form').reset();lastVersion=null;}report=String(id);state=null;$('release-fields').disabled=true;$('release-save').disabled=true;$('release-status').textContent='Carregando liberação...';$('release-summary').textContent='';$('release-history').replaceChildren();
    try{const data=await FaturamedAuth.request(`/api/conciliacoes/visitas/${id}/liberacao`);if(token===generation)render(data);}catch(e){if(token===generation)$('release-status').textContent=e.message;}
  }
  $('release-form').onsubmit=async e=>{e.preventDefault();if(saving||!state?.podeLiberar)return;const id=report,token=generation,fields=e.target.elements;const body={versao:state.versao,observacao:fields.observacao.value,confirmaPendencias:fields.confirmaPendencias.checked};
    if(!await fmConfirmar(`Liberar o relatório #${id}, revisão ${state.versao}, com ${state.visitas} visitas para os médicos vinculados? ${state.semMedico} visitas sem médico não aparecerao no portal.`))return;
    saving=true;$('release-fields').disabled=true;$('release-save').disabled=true;$('release-refresh').disabled=true;
    try{const data=await FaturamedAuth.request(`/api/conciliacoes/visitas/${id}/liberacao`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});if(token===generation){saving=false;render(data);e.target.reset();}}
    catch(error){if(token===generation){state=null;$('release-status').textContent=error.message+' Atualize a liberação antes de tentar novamente.';}}
    finally{saving=false;$('release-refresh').disabled=false;if(state){$('release-fields').disabled=!state.podeLiberar;$('release-save').disabled=!state.podeLiberar;}}
  };
  // Empresa da producao: define em qual dashboard de empresa a publicacao aparece (vale para toda a cadeia de versoes).
  let companies=null,companyToken=0;
  async function loadCompany(id){const token=++companyToken,select=$('company-assign-select');select.disabled=true;$('company-assign-save').disabled=true;$('company-assign-status').textContent='';
    try{companies??=await FaturamedAuth.request('/api/empresas-clientes');const atual=await FaturamedAuth.request(`/api/empresas-clientes/conciliacoes/${id}`);if(token!==companyToken)return;
      select.replaceChildren(new Option('Sem empresa',''),...companies.filter(c=>c.ativo||c.id===atual.empresaId).map(c=>new Option(c.nome+(c.ativo?'':' (inativa)'),c.id)));select.value=atual.empresaId??'';select.disabled=false;$('company-assign-save').disabled=false;
      $('company-assign').hidden=!companies.length;
    }catch(e){if(token===companyToken)$('company-assign-status').textContent=e.message;}}
  $('company-assign').addEventListener('submit',async event=>{event.preventDefault();if(!report)return;const button=$('company-assign-save');button.disabled=true;
    try{const value=$('company-assign-select').value;await FaturamedAuth.request(`/api/empresas-clientes/conciliacoes/${report}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({empresaId:value?Number(value):null})});$('company-assign-status').textContent='Empresa salva para todas as versões e publicações deste relatório.';}
    catch(e){$('company-assign-status').textContent=e.message;}finally{button.disabled=false;}});
  $('release-refresh').onclick=()=>{if(!saving&&report)load(report);};window.FaturamedLiberacao={carregar:id=>{load(id);if(id)loadCompany(id);}};
})();
