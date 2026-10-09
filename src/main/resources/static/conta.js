(async function () {
  const message=document.querySelector('#account-message');
  try {const user=await FaturamedAuth.request('/api/auth/me');document.querySelector('#account-name').textContent=`${user.nome} - ${user.email}`;document.querySelector('#account-back').href=user.perfil==='ADMIN'?'/':'/portal.html';}
  catch(error){message.textContent=error.message;}
  const form=document.querySelector('#account-password');
  form.addEventListener('submit',async event=>{
    event.preventDefault();
    if(form.novaSenha.value!==form.confirmacao.value){message.textContent='As senhas nao conferem.';return;}
    const button=form.querySelector('button');button.disabled=true;
    try {await FaturamedAuth.request('/api/auth/password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({senhaAtual:form.senhaAtual.value,novaSenha:form.novaSenha.value})});form.reset();location.replace('/login.html');}
    catch(error){message.textContent=error.message;button.disabled=false;}
  });
})();
