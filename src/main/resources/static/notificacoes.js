(() => {
  const host=document.querySelector('header .session-actions')||document.querySelector('.topbar-actions')||document.querySelector('header.account-heading')||document.querySelector('header');
  if(!host||document.getElementById('notification-toggle'))return;
  const node=(tag,text)=>{const e=document.createElement(tag);if(text!=null)e.textContent=text;return e;};
  const icon=(name,paths)=>`<svg class="lucide lucide-${name}" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`;
  const button=node('button');button.id='notification-toggle';button.className='notification-toggle';button.type='button';button.title='Notificações';button.setAttribute('aria-label','Notificações');button.setAttribute('aria-haspopup','dialog');
  button.innerHTML=icon('bell','<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/>');
  const badge=node('span');badge.className='notification-badge';badge.hidden=true;badge.setAttribute('aria-hidden','true');button.append(badge);host.prepend(button);
  const dialog=node('dialog');dialog.id='notification-dialog';dialog.setAttribute('aria-labelledby','notification-title');
  const heading=node('header'),title=node('h2','Notificações');title.id='notification-title';const close=node('button');close.type='button';close.className='notification-icon';close.title='Fechar notificações';close.setAttribute('aria-label',close.title);close.innerHTML=icon('x','<path d="m18 6-12 12M6 6l12 12"/>');heading.append(title,close);
  const toolbar=node('div');toolbar.className='notification-toolbar';const label=node('label'),filter=node('input');filter.type='checkbox';label.append(filter,document.createTextNode(' Somente não lidas'));const refresh=node('button','Atualizar');refresh.type='button';refresh.className='text-button';toolbar.append(label,refresh);
  const message=node('p');message.id='notification-message';message.setAttribute('role','status');message.setAttribute('aria-live','polite');const list=node('ol');list.id='notification-list';
  const footer=node('footer'),previous=node('button'),next=node('button'),position=node('span');previous.type=next.type='button';previous.className=next.className='notification-icon';previous.title='Página anterior';next.title='Próxima página';previous.setAttribute('aria-label',previous.title);next.setAttribute('aria-label',next.title);previous.innerHTML=icon('chevron-left','<path d="m15 18-6-6 6-6"/>');next.innerHTML=icon('chevron-right','<path d="m9 18 6-6-6-6"/>');footer.append(previous,position,next);dialog.append(heading,toolbar,message,list,footer);document.body.append(dialog);
  let page=0,generation=0,busy=false,loading=false,total=0,loaded=false;
  function count(value){badge.hidden=value===0;badge.textContent=value>99?'99+':String(value);button.setAttribute('aria-label',`Notificações, ${value} não lidas`);button.title=`Notificações (${value} não lidas)`;}
  function controls(){filter.disabled=refresh.disabled=busy||loading;previous.disabled=busy||loading||page===0;next.disabled=busy||loading||(page+1)*20>=total;}
  async function read(item,destination){if(busy||loading)return;busy=true;controls();try{await FaturamedAuth.request(`/api/auth/notificacoes/${item.tipo}/${item.evento}/leitura`,{method:'POST'});if(destination){location.assign(destination);return;}page=0;await load();}catch(e){message.textContent=e.message;}finally{busy=false;controls();}}
  async function load(){const token=++generation;loading=true;controls();try{const data=await FaturamedAuth.request(`/api/auth/notificacoes?naoLidas=${filter.checked}&pagina=${page}`);if(token!==generation)return;loaded=true;count(data.naoLidas);total=data.total;
      if(page>0&&page*20>=total){page=Math.max(0,Math.ceil(total/20)-1);return load();}
      message.textContent=total?(data.naoLidas===1?'1 não lida.':`${data.naoLidas} não lidas.`):'Nenhuma notificação neste filtro.';list.replaceChildren();position.textContent=`Página ${page+1} de ${Math.max(1,Math.ceil(total/20))}`;
      for(const item of data.itens){const li=node('li');li.className=item.lidoEm?'notification-read':'notification-unread';const link=node('a',item.titulo);link.href=item.destino;link.onclick=e=>{if(e.ctrlKey||e.metaKey||e.shiftKey||e.altKey)return;e.preventDefault();read(item,item.destino);};li.append(link,node('p',`${item.hospital} | Relatório #${item.relatorio}${item.solicitacao?` | Pedido #${item.solicitacao}`:''}`),node('time',new Date(item.criadoEm).toLocaleString('pt-BR')));if(!item.lidoEm){const mark=node('button','Marcar como lida');mark.type='button';mark.className='text-button';mark.onclick=()=>read(item);li.append(mark);}else li.append(node('small','Lida'));list.append(li);}controls();
    }catch(e){if(token===generation){message.textContent=e.message;if(!loaded)button.title='Notificações indisponíveis';}}finally{if(token===generation){loading=false;controls();}}
  }
  button.onclick=()=>{if(!dialog.open)dialog.showModal();load();};close.onclick=()=>dialog.close();refresh.onclick=()=>load();filter.onchange=()=>{page=0;load();};previous.onclick=()=>{page--;load();};next.onclick=()=>{page++;load();};
  document.addEventListener('visibilitychange',()=>{if(!document.hidden&&!dialog.open&&!busy)load();});
  setInterval(()=>{if(!document.hidden&&!dialog.open&&!busy)load();},60000);load();
})();
