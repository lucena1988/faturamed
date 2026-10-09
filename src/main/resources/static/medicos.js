(() => {
const root=document.querySelector('[data-medicos]');
if(!root)return;
const $=selector=>root.querySelector(selector);
let medicos=[],editing=null,saving=false;
for(const uf of ['AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO']) $('#uf').add(new Option(uf,uf));
async function request(url,options){const r=await fetch(url,options);const data=await r.json();if(!r.ok)throw new Error(data.erro||`Falha (${r.status})`);return data;}
const normalize=v=>v.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
function render(){const search=normalize($('#medico-search').value);const status=$('#medico-status').value;const rows=$('#medicos-rows');rows.replaceChildren();
 const selected=medicos.filter(m=>(!status||String(m.ativo)===status)&&normalize(`${m.nome} ${m.crm} ${m.uf} ${m.aliases.join(' ')}`).includes(search));
 for(const m of selected){const tr=document.createElement('tr');for(const value of [m.nome,m.crm,m.uf,m.aliases.join(', '),m.ativo?'Ativo':'Inativo']){const td=document.createElement('td');td.textContent=value;tr.append(td);}const td=document.createElement('td');const b=document.createElement('button');b.type='button';b.textContent='Editar';b.setAttribute('aria-label',`Editar medico ${m.id}`);b.addEventListener('click',()=>{if(saving)return;editing=m;$('#nome').value=m.nome;$('#crm').value=m.crm;$('#uf').value=m.uf;$('#aliases').value=m.aliases.join('\n');$('#ativo').checked=m.ativo;$('#form-title').textContent=`Editar medico #${m.id}`;$('#nome').focus();});td.append(b);tr.append(td);rows.append(tr);}
 if(!selected.length){const tr=document.createElement('tr');const td=document.createElement('td');td.colSpan=6;td.textContent='Nenhum medico encontrado.';tr.append(td);rows.append(tr);}$('#count').textContent=`${selected.length} medicos`;}
async function load(){medicos=await request('/api/medicos');render();}
function reset(){editing=null;$('#medico-form').reset();$('#form-title').textContent='Novo medico';}
$('#new').addEventListener('click',()=>{if(!saving)reset();});
$('#reload').addEventListener('click',()=>load().catch(e=>$('#message').textContent=e.message));
$('#medico-search').addEventListener('input',render);$('#medico-status').addEventListener('change',render);
$('#medico-form').addEventListener('submit',async event=>{event.preventDefault();if(saving)return;const current=editing;saving=true;$('#save').disabled=true;$('#new').disabled=true;
 try{const aliases=$('#aliases').value.split(/\r?\n/).map(v=>v.trim()).filter(Boolean);if(aliases.length>30||aliases.some(v=>v.length>180))throw new Error('Informe ate 30 aliases de ate 180 caracteres.');
 const saved=await request(current?`/api/medicos/${current.id}`:'/api/medicos',{method:current?'PUT':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({nome:$('#nome').value.trim(),crm:$('#crm').value.trim(),uf:$('#uf').value,ativo:$('#ativo').checked,aliases,versao:current?.versao||0})});reset();await load();$('#message').textContent=`Medico #${saved.id} salvo.`;
 }catch(e){$('#message').textContent=e.message;}finally{saving=false;$('#save').disabled=false;$('#new').disabled=false;}});
load().catch(e=>$('#message').textContent=e.message);
})();
