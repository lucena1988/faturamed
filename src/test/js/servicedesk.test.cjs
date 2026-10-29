const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Executa o widget real. Este DOM minimo nao simula layout nem validacao HTML nativa.
class Element {
  constructor(tag) {
    Object.assign(this, {tag, children: [], listeners: {}, style: {}, value: '', textContent: '', files: [], hidden: false});
    this.classList = {toggle() {}};
  }
  append(...items) { this.children.push(...items); }
  replaceChildren(...items) { this.children = items; }
  setAttribute(name, value) { this[name] = value; }
  addEventListener(name, fn) { this.listeners[name] = fn; }
  dispatch(name) { return this.listeners[name]?.({preventDefault() {}, target: this}); }
  click() { return this.dispatch('click'); }
  reset() { walk(this).forEach(e => { e.value = ''; e.files = []; }); }
  showModal() { this.open = true; }
  close() { this.open = false; }
}
const walk = node => [node, ...node.children.flatMap(walk)];
const flush = async () => { for (let i=0; i<8; i++) await new Promise(resolve => setImmediate(resolve)); };

async function carregar({perfil='OPERADOR', fail=false, avisos=[], detalheFalha=false, login=false}={}) {
  const body = new Element('body'), calls = [];
  let pendentes = [...avisos];
  const detalhe = {chamado: {id: 7, protocolo:'CH-QA-7', tipoNome:'Erro', titulo:'QA', descricao:'QA',
    status:'ABERTO', filaDestino:'DEV', slaStatus:'NO_PRAZO', minutosRestantes:240, solicitanteId:1}, mensagens:[], anexos:[]};
  const auth = {async request(url, options={}) {
    calls.push({url, ...options});
    if(url==='/api/auth/me') return {id:1, perfil};
    if(url==='/api/chamados/tipos') return [{codigo:'ERRO', nome:'Erro bloqueante', descricao:'Falha que impede o uso.', slaHoras:4}];
    if(url==='/api/chamados?meus=true') return {chamados:[]};
    if(url==='/api/chamados/notificacoes') return {naoLidas:pendentes.length, itens:[...pendentes]};
    if(url==='/api/chamados' && options.method==='POST') {
      if(fail) throw new Error('Falha de envio QA');
      return detalhe;
    }
    if(url==='/api/chamados/7') {
      if(detalheFalha) throw new Error('Falha ao carregar QA');
      return detalhe;
    }
    if(url.startsWith('/api/chamados/7/notificacoes/leitura?ateId=')) {
      const limite = Number(url.split('=')[1]);
      pendentes=pendentes.filter(a=>a.chamadoId!==7 || a.id>limite);
      return;
    }
    throw new Error('Requisicao inesperada: '+url);
  }};
  const location = {pathname: login ? '/login.html' : '/', hash:'#dashboard'};
  const document = {body, hidden:false, getElementById:id=>walk(body).find(e=>e.id===id), createElement:tag=>new Element(tag)};
  const context = {document, location, FaturamedAuth:auth, FormData, console, setInterval() {},
    Option:function(text,value){const e=new Element('option'); e.textContent=text; e.value=value; return e;}};
  vm.runInNewContext(readFileSync(path.join(__dirname,'../../main/resources/static/chamados-widget.js'),'utf8'),context);
  await flush();
  const find = predicate => {const result=walk(body).find(predicate); assert.ok(result,'Elemento esperado ausente'); return result;};
  return {body,calls,location,find,all:()=>walk(body)};
}

test('Service Desk: categoria vazia e prazo somente apos escolha; origem nao editavel', async()=>{
  const ui=await carregar();
  const categoria=ui.find(e=>e.name==='tipoCodigo');
  assert.equal(categoria.required,true);
  assert.equal(categoria.value,'');
  assert.equal(categoria.children[0].value,'');
  const ajuda=ui.find(e=>e.id==='hd-category-help');
  assert.equal(ajuda.hidden,true);
  categoria.value='ERRO'; await categoria.dispatch('change');
  assert.equal(ajuda.hidden,false);
  assert.ok(walk(ajuda).some(e=>e.textContent==='Prazo previsto: 4 horas'));
  assert.ok(!ui.all().some(e=>e.name==='paginaOrigem'));
  assert.equal(ui.find(e=>e.name==='titulo').required,true);
  assert.equal(ui.find(e=>e.name==='descricao').required,true);
});

test('Service Desk: funcionario recebe acesso de acompanhamento e tecnicos ao painel',async()=>{
  for(const perfil of ['OPERADOR','ADMIN','DEV']) {
    const ui=await carregar({perfil});
    const link=ui.find(e=>e.className==='hd-kanban-link');
    assert.equal(link.hidden,false);
    assert.equal(link.textContent,perfil==='OPERADOR' ? 'Acompanhar chamados' : 'Painel de atendimento ↗');
  }
});

test('Service Desk: envio inclui origem atual e anexos, limpa formulario e abre protocolo',async()=>{
  const ui=await carregar();
  ui.find(e=>e.name==='tipoCodigo').value='ERRO';
  ui.find(e=>e.name==='titulo').value='  Assunto QA  ';
  ui.find(e=>e.name==='descricao').value='  Descricao QA  ';
  const file=new File(['%PDF-1.4 QA'],'qa.pdf',{type:'application/pdf'});
  const input=ui.find(e=>e.type==='file'); input.files=[file]; await input.dispatch('change');
  assert.equal(ui.find(e=>e.className==='hd-upload-files').textContent,'qa.pdf');
  ui.location.hash='#financeiro';
  await ui.find(e=>e.tag==='form').dispatch('submit'); await flush();
  const envio=ui.calls.find(c=>c.url==='/api/chamados' && c.method==='POST');
  assert.ok(envio);
  assert.equal(envio.body.get('titulo'),'Assunto QA');
  assert.equal(envio.body.get('descricao'),'Descricao QA');
  assert.equal(envio.body.get('tipoCodigo'),'ERRO');
  assert.equal(envio.body.get('paginaOrigem'),'/#financeiro');
  assert.equal(envio.body.get('arquivos').name,'qa.pdf');
  assert.equal(ui.find(e=>e.name==='titulo').value,'');
  assert.equal(ui.find(e=>e.className==='hd-upload-files').textContent,'');
  assert.ok(ui.calls.some(c=>c.url==='/api/chamados/7'));
  assert.ok(ui.all().some(e=>e.textContent==='CH-QA-7'));
});

test('Service Desk: falha preserva texto e libera nova tentativa',async()=>{
  const ui=await carregar({fail:true});
  ui.find(e=>e.name==='titulo').value='Nao perder este texto';
  await ui.find(e=>e.tag==='form').dispatch('submit');
  assert.equal(ui.find(e=>e.name==='titulo').value,'Nao perder este texto');
  assert.equal(ui.find(e=>e.textContent==='Enviar chamado').disabled,false);
  assert.equal(ui.find(e=>e.className==='hd-status-banner err').textContent,'Falha de envio QA');
  assert.ok(!ui.calls.some(c=>c.url==='/api/chamados/7'));
});

test('Service Desk: abrir aviso le apenas chamado aberto e atualiza contador',async()=>{
  const ui=await carregar({avisos:[{id:11,chamadoId:7,tipo:'RETORNO_SOLICITADO',protocolo:'CH-7',titulo:'QA'},
    {id:12,chamadoId:8,tipo:'MENSAGEM_SUPORTE',protocolo:'CH-8',titulo:'Outro'}]});
  assert.equal(ui.find(e=>e.className==='hd-fab-badge').textContent,'2');
  await ui.find(e=>e.className==='hd-aviso' && e.textContent.includes('CH-7')).click();
  const leituras=ui.calls.filter(c=>c.method==='POST');
  assert.equal(leituras.length,1);
  assert.equal(leituras[0].url,'/api/chamados/7/notificacoes/leitura?ateId=12');
  assert.equal(ui.find(e=>e.className==='hd-fab-badge').textContent,'1');
  assert.ok(ui.all().some(e=>e.className==='hd-aviso' && e.textContent.includes('CH-8')));
});

test('Service Desk: erro ao abrir chamado nao marca aviso como lido',async()=>{
  const ui=await carregar({detalheFalha:true,avisos:[{id:11,chamadoId:7,protocolo:'CH-7',titulo:'QA'}]});
  await ui.find(e=>e.className==='hd-aviso').click();
  assert.ok(!ui.calls.some(c=>c.method==='POST'));
  assert.equal(ui.find(e=>e.className==='hd-fab-badge').textContent,'1');
});

test('Service Desk: widget nao aparece no login',async()=>{
  const ui=await carregar({login:true});
  assert.equal(ui.body.children.length,0);
  assert.equal(ui.calls.length,0);
});
