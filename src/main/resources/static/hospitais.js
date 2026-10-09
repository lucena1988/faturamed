(() => {
const root=document.querySelector('[data-hospitais]');
const $=selector=>root?.querySelector(selector);
let hospitais=[],editing=null,saving=false;
const normalize=v=>(v||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
async function request(url,options){const r=await fetch(url,options);const data=await r.json();if(!r.ok)throw new Error(data.erro||`Falha (${r.status})`);return data;}
function selections(){for(const select of document.querySelectorAll('[data-hospital-select]')){
 const previous=select.value;select.replaceChildren(new Option('Informar nome manualmente',''),...hospitais.filter(h=>h.ativo).map(h=>new Option(`${h.nome}${h.uf?' - '+h.uf:''}`,h.id)));
 if([...select.options].some(o=>o.value===previous))select.value=previous;select.disabled=false;
 const input=select.closest('form').querySelector('[data-hospital-name]');const h=hospitais.find(h=>String(h.id)===select.value);input.readOnly=!!h;if(h)input.value=h.nome;
}}
function render(){if(!root)return;const search=normalize($('#h-search').value),status=$('#h-status').value;const rows=$('#h-rows');rows.replaceChildren();
 const selected=hospitais.filter(h=>(!status||String(h.ativo)===status)&&normalize(`${h.nome} ${h.documento||''} ${h.cidade} ${h.uf}`).includes(search));
 for(const h of selected){const tr=document.createElement('tr');for(const value of [h.nome,h.documento||'Nao informado',h.cidade||'Nao informada',h.uf||'Nao informada',h.ativo?'Ativo':'Inativo']){const td=document.createElement('td');td.textContent=value;tr.append(td);}const td=document.createElement('td');const b=document.createElement('button');b.type='button';b.textContent='Editar';b.setAttribute('aria-label',`Editar hospital ${h.id}`);b.addEventListener('click',()=>{if(saving)return;editing=h;$('#h-nome').value=h.nome;$('#h-documento').value=h.documento||'';$('#h-cidade').value=h.cidade;$('#h-uf').value=h.uf;$('#h-ativo').checked=h.ativo;$('#h-form-title').textContent=`Editar hospital #${h.id}`;$('#h-nome').focus();});td.append(b);tr.append(td);rows.append(tr);}
 if(!selected.length){const tr=document.createElement('tr');const td=document.createElement('td');td.colSpan=6;td.textContent='Nenhum hospital encontrado.';tr.append(td);rows.append(tr);}$('#h-count').textContent=`${selected.length} hospitais`;}
async function load(){hospitais=await request('/api/hospitais');render();selections();}
function reset(){editing=null;$('#hospital-form').reset();$('#h-form-title').textContent='Novo hospital';}
for(const select of document.querySelectorAll('[data-hospital-select]')) select.addEventListener('change',()=>{
 const h=hospitais.find(h=>String(h.id)===select.value);const input=select.closest('form').querySelector('[data-hospital-name]');input.readOnly=!!h;if(h)input.value=h.nome;
});
if(root){for(const uf of ['AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'])$('#h-uf').add(new Option(uf,uf));
 $('#h-new').addEventListener('click',()=>{if(!saving)reset();});$('#h-reload').addEventListener('click',()=>load().catch(e=>$('#h-message').textContent=e.message));
 $('#h-search').addEventListener('input',render);$('#h-status').addEventListener('change',render);
 $('#hospital-form').addEventListener('submit',async event=>{event.preventDefault();if(saving)return;const current=editing;saving=true;$('#h-save').disabled=true;$('#h-new').disabled=true;
 try{const saved=await request(current?`/api/hospitais/${current.id}`:'/api/hospitais',{method:current?'PUT':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({nome:$('#h-nome').value.trim(),documento:$('#h-documento').value.trim(),cidade:$('#h-cidade').value.trim(),uf:$('#h-uf').value,ativo:$('#h-ativo').checked,versao:current?.versao||0})});reset();await load();$('#h-message').textContent=`Hospital #${saved.id} salvo.`;
 }catch(e){$('#h-message').textContent=e.message;}finally{saving=false;$('#h-save').disabled=false;$('#h-new').disabled=false;}});
}
load().catch(e=>{if(root)$('#h-message').textContent=e.message;else console.warn('Cadastro de hospitais indisponivel',e.message);});
})();
