const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

class Element {
  constructor(){this.children=[];this.value='';this.dataset={};this.style={};this.textContent='';this.disabled=false;this.open=false;}
  append(...nodes){this.children.push(...nodes);}
  replaceChildren(...nodes){this.children=nodes;}
  setAttribute(key,value){this[key]=value;}
  showModal(){this.open=true;}
  close(){this.open=false;}
  scrollIntoView(){}
}
const root=path.resolve(__dirname,'..');
const html=fs.readFileSync(path.join(root,'outputs/dashboard-regressao.html'),'utf8');
const data=JSON.parse(html.match(/<script id="dashboard-data" type="application\/json">([\s\S]*?)<\/script>/)[1]);
const script=[...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].at(-1)[1];
function load(){
  const elements={};const get=id=>elements[id]||=(new Element());
  get('dashboard-data').textContent=JSON.stringify(data);
  const tabs=['Todas','HTTP','Java','JavaScript'].map(layer=>{const e=new Element();e.dataset.layer=layer;return e;});
  const document={getElementById:get,createElement:()=>new Element(),querySelectorAll:()=>tabs};
  vm.runInNewContext(script,{document,Intl});
  return {get,tabs};
}
test('indicadores correspondem aos resultados e nao escondem falhas',()=>{
  const {get}=load();
  assert.equal(get('total').textContent,new Intl.NumberFormat('pt-BR').format(data.tests.length));
  assert.equal(get('failed').textContent,String(data.tests.filter(t=>t.status==='REPROVADO').length));
  assert.equal(get('passed').textContent,new Intl.NumberFormat('pt-BR').format(data.tests.filter(t=>t.status==='APROVADO').length));
});
test('filtros por falha camada e pesquisa e limpeza funcionam',()=>{
  const {get,tabs}=load();
  get('status').value='REPROVADO';get('status').onchange();
  const failures=data.tests.filter(t=>t.status==='REPROVADO');
  assert.equal(get('rows').children.length,Math.min(25,failures.length)||1);
  if(failures.length)assert.equal(get('rows').children[0].children[0].textContent,failures[0].id);
  get('clear').onclick();tabs[3].onclick();
  assert.equal(get('rows').children.length,data.tests.filter(t=>t.layer==='JavaScript').length);
  get('search').value='usar saldo';get('search').oninput();
  assert.equal(get('rows').children.length,1);
  get('search').value='busca inexistente XYZ';get('search').oninput();
  assert.equal(get('range').textContent,'0 resultados');
  get('clear').onclick();assert.equal(get('rows').children.length,25);
});
test('paginacao e detalhes exibem motivo e evidencia original',()=>{
  const {get}=load();
  const first=get('rows').children[0].children[0].textContent;
  get('next').onclick();assert.notEqual(get('rows').children[0].children[0].textContent,first);
  get('prev').onclick();get('rows').children[0].children[5].children[0].onclick();
  const expected=data.tests.find(t=>t.id===first);
  assert.equal(get('detail').open,true);assert.equal(get('detail-title').textContent,expected.name);
  if(expected.error)assert(get('detail-body').children.some(e=>e.textContent.includes(expected.error)));
  assert(get('detail-source').href.endsWith(expected.source));
  get('close-detail').onclick();assert.equal(get('detail').open,false);
});
test('todos os links de evidencias apontam para arquivos existentes e excluem dumps',()=>{
  for(const file of data.evidence){
    assert(!file.path.split('/').includes('privado'));
    assert(fs.existsSync(path.join(root,'outputs',data.run,file.path)),file.path);
  }
});
