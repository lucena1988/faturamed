(() => {
const root=document.querySelector('[data-medicos]');
if(!root)return;
const $=selector=>root.querySelector(selector);
let medicos=[],empresas=[],editing=null,saving=false;
for(const uf of ['AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO']) $('#uf').add(new Option(uf,uf));
async function request(url,options){const r=await fetch(url,options);const data=await r.json();if(!r.ok)throw new Error(data.erro||`Falha (${r.status})`);return data;}
const normalize=v=>v.normalize('NFD').replace(/[̀-ͯ]/g,'').toLowerCase();
const nomesEmpresas=m=>(m.empresas||[]).map(id=>empresas.find(e=>e.id===id)?.nome||`#${id}`);
function renderEmpresas(){
 const box=$('#medico-empresas'),marcadas=new Set(editing?.empresas||[...box.querySelectorAll('input:checked')].map(i=>Number(i.value)));box.replaceChildren();
 // Empresas inativas continuam visiveis quando ja vinculadas, para nao perder o vinculo ao editar.
 const visiveis=empresas.filter(e=>e.ativo||marcadas.has(e.id));
 if(!visiveis.length){const span=document.createElement('span');span.textContent='Nenhuma empresa cadastrada. Cadastre em Cadastros > Empresas.';box.append(span);}
 for(const e of visiveis){const label=document.createElement('label');const input=document.createElement('input');input.type='checkbox';input.value=e.id;input.checked=marcadas.has(e.id);label.append(input,` ${e.nome}${e.ativo?'':' (inativa)'}`);box.append(label);}
 const filtro=$('#medico-empresa'),atual=filtro.value;filtro.replaceChildren(new Option('Todas',''),new Option('Sem empresa','sem'),...empresas.map(e=>new Option(e.nome,e.id)));if([...filtro.options].some(o=>o.value===atual))filtro.value=atual;
}
function render(){const search=normalize($('#medico-search').value);const status=$('#medico-status').value;const empresa=$('#medico-empresa').value;const rows=$('#medicos-rows');rows.replaceChildren();
 const selected=medicos.filter(m=>(!status||String(m.ativo)===status)&&(!empresa||(empresa==='sem'?!(m.empresas||[]).length:(m.empresas||[]).includes(Number(empresa))))&&normalize(`${m.nome} ${m.crm||""} ${m.uf||""} ${m.email||""} ${m.aliases.join(' ')} ${nomesEmpresas(m).join(' ')}`).includes(search));
 for(const m of selected){const tr=document.createElement('tr');for(const value of [m.nome,m.crm||'A preencher',m.uf||'A preencher',(m.email||'Não informado').replaceAll(';','; '),nomesEmpresas(m).join(', ')||'Sem empresa',m.aliases.join(', '),m.ativo?'Ativo':'Inativo',m.cadastroCompleto?'Completo':'Pendente (médico)']){const td=document.createElement('td');td.textContent=value;tr.append(td);}const td=document.createElement('td');const b=document.createElement('button');b.type='button';b.textContent='Editar';b.setAttribute('aria-label',`Editar médico ${m.id}`);b.addEventListener('click',()=>{if(saving)return;editing=m;$('#nome').value=m.nome;$('#crm').value=m.crm||'';$('#uf').value=m.uf||'';$('#email').value=m.email||'';$('#aliases').value=m.aliases.join('\n');$('#ativo').checked=m.ativo;renderEmpresas();$('#form-title').textContent=`Editar médico #${m.id}`;$('#nome').focus();});td.append(b);
  // O convite so faz sentido enquanto o medico ainda nao completou o cadastro.
  if(!m.cadastroCompleto){const c=document.createElement('button');c.type='button';c.textContent='Gerar convite';c.setAttribute('aria-label',`Gerar convite para o médico ${m.id}`);c.addEventListener('click',()=>convidar(m,c));td.append(' ',c);}
  tr.append(td);rows.append(tr);}
 if(!selected.length){const tr=document.createElement('tr');const td=document.createElement('td');td.colSpan=9;td.textContent='Nenhum médico encontrado.';tr.append(td);rows.append(tr);}$('#count').textContent=`${selected.length} médicos`;}
async function convidar(m,button){
 if(!confirm(`Gerar um link de convite para ${m.nome}? Um convite anterior ainda não usado deixa de valer.`))return;
 button.disabled=true;
 try{const convite=await request(`/api/medicos/${m.id}/convite`,{method:'POST'});const dialog=$('#convite-dialog');
  $('#convite-medico').textContent=`${m.nome} · ${(m.email||'').replaceAll(';','; ')} · válido até ${new Date(convite.expiraEm).toLocaleString('pt-BR')}`;
  $('#convite-link').value=`${location.origin}/convite.html?token=${encodeURIComponent(convite.token)}`;$('#convite-status').textContent='';dialog.showModal();$('#convite-link').select();
 }catch(e){$('#message').textContent=e.message;}finally{button.disabled=false;}}
$('#convite-copiar').addEventListener('click',async()=>{const link=$('#convite-link');try{await navigator.clipboard.writeText(link.value);$('#convite-status').textContent='Link copiado.';}catch{link.select();$('#convite-status').textContent='Selecione e copie o link (Ctrl+C).';}});
async function load(){medicos=await request('/api/medicos');render();}
function reset(){editing=null;$('#medico-form').reset();renderEmpresas();$('#form-title').textContent='Novo médico';}
document.addEventListener('faturamed:empresas',event=>{empresas=event.detail;renderEmpresas();render();});
$('#new').addEventListener('click',()=>{if(!saving)reset();});
$('#reload').addEventListener('click',()=>load().catch(e=>$('#message').textContent=e.message));
$('#medico-search').addEventListener('input',render);$('#medico-status').addEventListener('change',render);$('#medico-empresa').addEventListener('change',render);
$('#medico-form').addEventListener('submit',async event=>{event.preventDefault();if(saving)return;const current=editing;saving=true;$('#save').disabled=true;$('#new').disabled=true;
 try{const aliases=$('#aliases').value.split(/\r?\n/).map(v=>v.trim()).filter(Boolean);if(aliases.length>30||aliases.some(v=>v.length>180))throw new Error('Informe até 30 aliases de até 180 caracteres.');
 const vinculos=[...$('#medico-empresas').querySelectorAll('input:checked')].map(i=>Number(i.value));
 const saved=await request(current?`/api/medicos/${current.id}`:'/api/medicos',{method:current?'PUT':'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({nome:$('#nome').value.trim(),crm:$('#crm').value.trim(),uf:$('#uf').value,email:$('#email').value.trim(),ativo:$('#ativo').checked,aliases,versao:current?.versao||0,empresas:vinculos})});reset();await load();document.dispatchEvent(new Event('faturamed:medico-salvo'));$('#message').textContent=`Médico #${saved.id} salvo.`;
 }catch(e){$('#message').textContent=e.message;}finally{saving=false;$('#save').disabled=false;$('#new').disabled=false;}});
load().catch(e=>$('#message').textContent=e.message);
})();
