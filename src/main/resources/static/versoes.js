(() => {
const selects=[...document.querySelectorAll('[data-version-base]')];
async function request(url){const r=await fetch(url);const data=await r.json();if(!r.ok)throw new Error(data.erro||`Falha (${r.status})`);return data;}
async function load(){const reports=await request('/api/conciliacoes/visitas');for(const select of selects){const current=select.value;
 select.replaceChildren(new Option('Novo relatorio',''),...reports.filter(r=>!r.proxima_id).map(r=>new Option(`Atualizar #${r.id} - v${r.numero_versao||1} - ${r.hospital}`,r.id)));
 if([...select.options].some(o=>o.value===current))select.value=current;
}}
for(const select of selects) select.addEventListener('change',async()=>{
 const id=select.value;if(!id)return;
 try{const r=await request(`/api/conciliacoes/visitas/${id}`);if(select.value!==id)return;
 const form=select.closest('form');const name=form.querySelector('[data-hospital-name]'),hospital=form.querySelector('[data-hospital-select]');
 if(name)name.value=r.hospital;
 if(hospital&&r.hospitalCadastroId!=null){hospital.value=String(r.hospitalCadastroId);if(hospital.value!==String(r.hospitalCadastroId))throw new Error('O hospital da versao base nao esta ativo na lista. Confira o cadastro antes de atualizar.');}
 else if(hospital)hospital.value='';
 if(name)name.readOnly=!!hospital?.value;
 }catch(e){select.value='';alert(e.message);}
});
window.FaturamedVersoes={load,async prepare(form){const data=new FormData(form),id=data.get('relatorioBaseId');if(!id){data.delete('relatorioBaseId');return data;}
 if(!confirm(`Criar nova versao do relatorio #${id}? Envie a producao completa atualizada. Conciliacoes e revisoes anteriores serao preservadas.`))return null;
 const current=await request(`/api/conciliacoes/visitas/${id}/acompanhamento`);data.set('versaoBase',String(current.versao));return data;
},message(result){return result.semAlteracoes?'Arquivos identicos: a versao existente foi mantida.':result.numeroVersao?`Versao ${result.numeroVersao}: ${result.preservadas} visitas preservadas, ${result.reavaliadas} reavaliadas e ${result.novas} novas.`:`Relatorio #${result.id} salvo.`;}};
load().catch(e=>{for(const select of selects)select.title=e.message;});
})();
