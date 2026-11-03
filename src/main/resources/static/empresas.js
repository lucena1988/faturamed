(() => {
const root=document.querySelector('[data-empresas]');
if(!root)return;
const $=selector=>root.querySelector(selector);
let empresas=[],editing=null,saving=false;
const normalize=v=>(v||'').normalize('NFD').replace(/[̀-ͯ]/g,'').toLowerCase();
async function request(url,options){const r=await fetch(url,options);const data=await r.json();if(!r.ok)throw new Error(data.erro||`Falha (${r.status})`);return data;}
function render(){const search=normalize($('#e-search').value),status=$('#e-status').value;const rows=$('#e-rows');rows.replaceChildren();
 const selected=empresas.filter(e=>(!status||String(e.ativo)===status)&&normalize(`${e.nome} ${e.documento||''}`).includes(search));
 for(const e of selected){const tr=document.createElement('tr');for(const value of [e.nome,e.documento||'Não informado',e.medicos,e.ativo?'Ativa':'Inativa']){const td=document.createElement('td');td.textContent=value;tr.append(td);}const td=document.createElement('td');const b=document.createElement('button');b.type='button';b.textContent='Editar';b.setAttribute('aria-label',`Editar empresa ${e.id}`);b.addEventListener('click',()=>{if(saving)return;editing=e;$('#e-nome').value=e.nome;$('#e-documento').value=e.documento||'';$('#e-ativo').checked=e.ativo;$('#e-form-title').textContent=`Editar empresa #${e.id}`;$('#e-nome').focus();});td.append(b);tr.append(td);rows.append(tr);}
 if(!selected.length){const tr=document.createElement('tr');const td=document.createElement('td');td.colSpan=5;td.textContent='Nenhuma empresa encontrada.';tr.append(td);rows.append(tr);}$('#e-count').textContent=`${selected.length} empresas`;}
async function load(){empresas=await request('/api/empresas-clientes');render();document.dispatchEvent(new CustomEvent('faturamed:empresas',{detail:empresas}));}
function reset(){editing=null;$('#empresa-form').reset();$('#e-form-title').textContent='Nova empresa';}
$('#e-new').addEventListener('click',()=>{if(!saving)reset();});
$('#e-reload').addEventListener('click',()=>load().catch(e=>$('#e-message').textContent=e.message));
$('#e-search').addEventListener('input',render);$('#e-status').addEventListener('change',render);
// Medicos vinculados mudam a contagem: recarrega quando o cadastro de medicos salvar.
document.addEventListener('faturamed:medico-salvo',()=>load().catch(()=>{}));
$('#empresa-form').addEventListener('submit',async event=>{event.preventDefault();if(saving)return;const current=editing;saving=true;$('#e-save').disabled=true;$('#e-new').disabled=true;
 try{const saved=await request(current?`/api/empresas-clientes/${current.id}`:'/api/empresas-clientes',{method:current?'PUT':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({nome:$('#e-nome').value.trim(),documento:$('#e-documento').value.trim(),ativo:$('#e-ativo').checked,versao:current?.versao||0})});reset();await load();$('#e-message').textContent=`Empresa #${saved.id} salva.`;
 }catch(e){$('#e-message').textContent=e.message;}finally{saving=false;$('#e-save').disabled=false;$('#e-new').disabled=false;}});
load().catch(e=>$('#e-message').textContent=e.message);
})();
