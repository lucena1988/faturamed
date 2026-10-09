(function () {
  const form = document.querySelector('#access-form'), message = document.querySelector('#access-message');
  const rows = document.querySelector('#access-rows'), dialog = document.querySelector('#password-dialog');
  let doctors = [], resetting = null;
  const api = FaturamedAuth.request;
  const json = (method, body) => ({method, headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)});
  function profile() { const medical = form.perfil.value === 'MEDICO'; document.querySelector('#access-doctor-label').hidden = !medical; form.medicoId.disabled = !medical; form.medicoId.required = medical; }
  async function load() {
    const users = await api('/api/usuarios');
    rows.replaceChildren();
    for (const user of users) {
      const tr = document.createElement('tr');
      const doctor = doctors.find(d=>d.id===user.medicoId);
      const values = [user.nome,user.email,user.perfil==='ADMIN'?'Administrador':'Medico',doctor?`${doctor.nome} - CRM ${doctor.crm}/${doctor.uf}`:'-',user.ativo?'Ativo':'Inativo'];
      values.forEach((value,index)=>{const td=document.createElement('td');td.textContent=value;td.dataset.label=rows.closest('table').querySelectorAll('th')[index].textContent;tr.append(td);});
      const actions = document.createElement('td'); actions.dataset.label='Acoes';
      const toggle = document.createElement('button'); toggle.type='button'; toggle.className='text-button'; toggle.textContent=user.ativo?'Desativar':'Ativar';
      toggle.addEventListener('click',async()=>{
        if (!confirm(`${toggle.textContent} o acesso de ${user.nome}?`)) return;
        toggle.disabled=true;
        try {await api(`/api/usuarios/${user.id}/situacao`,json('PUT',{ativo:!user.ativo,versao:user.versao})); await load();message.textContent='Situacao atualizada.';}
        catch(error){message.textContent=error.message;toggle.disabled=false;}
      });
      const reset=document.createElement('button');reset.type='button';reset.className='text-button';reset.textContent='Redefinir senha';
      reset.addEventListener('click',()=>{resetting=user;document.querySelector('#reset-user').textContent=user.email;document.querySelector('#reset-form').reset();document.querySelector('#reset-message').textContent='';dialog.showModal();});
      actions.append(toggle,reset);tr.append(actions);rows.append(tr);
    }
  }
  form.perfil.addEventListener('change',profile);
  form.addEventListener('submit',async event=>{
    event.preventDefault();const button=form.querySelector('button');button.disabled=true;message.textContent='Criando acesso...';
    try {await api('/api/usuarios',json('POST',{nome:form.nome.value,email:form.email.value,senha:form.senha.value,perfil:form.perfil.value,medicoId:form.perfil.value==='MEDICO'?Number(form.medicoId.value):null}));form.reset();profile();await load();message.textContent='Acesso criado.';}
    catch(error){message.textContent=error.message;}
    finally {button.disabled=false;}
  });
  document.querySelector('#reset-cancel').addEventListener('click',()=>dialog.close());
  dialog.addEventListener('close',()=>document.querySelector('#reset-form').reset());
  document.querySelector('#reset-form').addEventListener('submit',async event=>{
    event.preventDefault();const button=event.currentTarget.querySelector('[type=submit]');button.disabled=true;
    try {await api(`/api/usuarios/${resetting.id}/senha`,json('POST',{senha:document.querySelector('#reset-password').value,versao:resetting.versao}));dialog.close();await load();message.textContent='Senha redefinida. As sessoes anteriores foram encerradas.';}
    catch(error){document.querySelector('#reset-message').textContent=error.message;}
    finally{button.disabled=false;}
  });
  (async()=>{try{doctors=await api('/api/medicos');form.medicoId.replaceChildren(new Option('Selecione um medico',''),...doctors.filter(d=>d.ativo).map(d=>new Option(`${d.nome} - CRM ${d.crm}/${d.uf}`,d.id)));profile();await load();}catch(error){message.textContent=error.message;}})();
})();
