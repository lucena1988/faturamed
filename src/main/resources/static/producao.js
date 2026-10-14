(() => {
  const forms=[...document.querySelectorAll('form')].filter(f=>f.querySelector('[data-production-mode]'));
  let doctors=[];
  const ready=fetch('/api/medicos').then(async r=>{if(!r.ok)throw new Error('Nao foi possivel carregar os medicos');return r.json();}).then(items=>{
    doctors=items.filter(m=>m.ativo);
    for(const form of forms){const select=form.querySelector('[data-production-doctor]');select.replaceChildren(new Option('Selecione um medico',''),...doctors.map(m=>new Option(`${m.nome} - CRM ${m.crm}/${m.uf}`,m.id)));update(form);}
  }).catch(error=>{for(const form of forms){form.querySelector('[data-production-message]').textContent=error.message;form.querySelector('[data-production-mode] option[value=UNICO]').disabled=true;}throw error;});
  ready.catch(()=>{});
  function update(form){
    const single=form.querySelector('[data-production-mode]').value==='UNICO';
    const select=form.querySelector('[data-production-doctor]');
    form.querySelector('[data-production-doctor-label]').hidden=!single;
    select.disabled=!single||form.dataset.productionLocked==='true';select.required=single&&!select.disabled;
  }
  for(const form of forms){form.querySelector('[data-production-mode]').addEventListener('change',()=>update(form));form.addEventListener('reset',()=>setTimeout(()=>{form.dataset.productionLocked='false';form.querySelector('[data-production-mode]').disabled=false;update(form);},0));}
  window.FaturamedProducao={async set(form,id,locked){await ready;
    const mode=form.querySelector('[data-production-mode]'),select=form.querySelector('[data-production-doctor]');
    if(id!=null&&!doctors.some(m=>String(m.id)===String(id)))throw new Error('O medico da producao base esta inativo. Confira o cadastro antes de atualizar.');
    form.dataset.productionLocked=String(!!locked);mode.value=id==null?'VARIOS':'UNICO';mode.disabled=!!locked;select.value=id==null?'':String(id);update(form);
  },async prepare(form,data){await ready;
    const mode=form.querySelector('[data-production-mode]').value;data.set('modoProducao',mode);
    if(mode==='UNICO'){const id=form.querySelector('[data-production-doctor]').value;if(!id)throw new Error('Selecione o medico da producao');data.set('medicoProducaoId',id);}else data.delete('medicoProducaoId');
  }};
})();
