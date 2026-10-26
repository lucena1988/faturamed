const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Element {
  constructor(tag = 'div') { this.tag = tag; this.children = []; this.dataset = {}; this.value = ''; this.textContent = ''; this.disabled = false; this.checked = false; this.open = false; this.listeners = {}; this.classList = { add() {} }; }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.children = children; }
  get options() { return this.children; }
  get firstChild() { return this.children[0]; }
  get selectedOptions() { return this.children.filter(c => c.value === this.value); }
  setAttribute(key, value) { this[key] = value; }
  setCustomValidity(value) { this.validationMessage = value; }
  querySelectorAll(selector) {
    if (selector === 'th') return Array.from({length: 10}, () => new Element('th'));
    const descendants = [];
    const walk = node => { if (!(node instanceof Element)) return; if (node.dataset.allocationLine != null) descendants.push(node); node.children.forEach(walk); };
    this.children.forEach(walk);
    return descendants;
  }
  querySelector(selector) { const line = selector.match(/="(\d+)"/)?.[1]; return this.querySelectorAll(selector).find(e => String(e.dataset.allocationLine) === line); }
  addEventListener(name, callback) { this.listeners[name] = callback; }
  dispatch(name) { return (this.listeners[name] || this['on' + name])?.({ target: this, preventDefault() {} }); }
  closest() { return this; }
  reset() { for (const element of Object.values(this.elements || {})) element.value = ''; }
  showModal() { this.open = true; }
  close() { this.open = false; }
}

async function load(script, count = 2) {
  const elements = {}, calls = [];
  const get = id => elements[id] ||= new Element();
  for (const id of ['contact-form', 'financial-form', 'payment-form']) get(id).elements = new Proxy({}, { get(target, key) { return target[key] ||= new Element('input'); } });
  const all = () => {
    const result = [];
    const walk = node => { if (!(node instanceof Element)) return; result.push(node); node.children.forEach(walk); };
    Object.values(elements).forEach(walk);
    return [...new Set(result)];
  };
  const document = { getElementById: get, createElement: tag => new Element(tag), querySelectorAll(selector) {
    if (selector === 'th' || selector.includes(' th')) return Array.from({length: 20}, () => new Element('th'));
    if (selector === '[data-select-line]') return all().filter(e => e.dataset.selectLine != null);
    if (selector === '[data-payment-select]') return all().filter(e => e.dataset.paymentSelect != null);
    return [];
  }};
  const visits = Array.from({length: count}, (_, i) => ({linha: i + 2, atendimento: '990' + i, data: '2026-01-01', medico: i % 2 ? 'Beta' : 'Alfa', medicoId: i % 2 + 1, status: 'PENDENTE', faturado: 100, recebido: 0, saldo: 100, podeRegistrar: true, historico: []}));
  const auth = { async request(url) {
    calls.push(url);
    if (url === '/api/conciliacoes/visitas') return [{id: 1, hospital: 'Hospital QA', numero_versao: 1}];
    if (url.endsWith('/acompanhamento')) return [];
    if (url.includes('/cobranca?')) return {hospital: 'Hospital QA', referencia: '2026-10-26', visitas: visits, medicos: [], prioridades: {TODAS: count}, totalSemCorrespondencia: count, repasseInformado: 0, semValor: count, semMedico: count, semData: 0};
    return {hospital: 'Hospital QA', historico: false, visitas: visits, versaoConciliacao: 0};
  }};
  const context = {document, window: {FaturamedAuth: auth}, FaturamedAuth: auth, FaturamedDemonstrativo: {selecionar() {}}, FaturamedConsultaPagamentos: {carregar() {}}, location: {search: '?relatorio=1', href: 'http://localhost/cobrancas.html?relatorio=1'}, history: {replaceState() {}}, URL, URLSearchParams, Intl, Date, console, alert() {}, confirm: () => false, Option: function(text, value) { const e = new Element('option'); e.textContent = text; e.value = String(value); return e; }, fetch() { throw new Error('Nenhuma escrita HTTP esperada neste teste'); }};
  context.FaturamedPagamentoValores = require('../../main/resources/static/pagamento-valores.js');
  vm.runInNewContext(readFileSync(path.join(__dirname, '../../main/resources/static', script), 'utf8'), context);
  for (let i = 0; i < 8; i++) await new Promise(resolve => setImmediate(resolve));
  const message = get(script === 'cobrancas.js' ? 'charge-message' : 'financial-message').textContent;
  assert.doesNotMatch(message, /is not|Cannot|undefined|Carregando/);
  return {get, calls, document};
}

test('cobranca limpar selecao desmarca e nao inicia requisicao nem altera filtros', async () => {
  const {get, calls, document} = await load('cobrancas.js');
  const select = get('charge-select-all'); select.checked = true; select.dispatch('change');
  assert.equal(get('charge-selected').textContent, '2 visitas selecionadas');
  const before = calls.length;
  get('charge-unselect').dispatch('click');
  assert.equal(get('charge-selected').textContent, '0 visitas selecionadas');
  assert.equal(calls.length, before);
  assert.equal(get('charge-report').value, '1');
  assert.equal(get('charge-batch').disabled, true);
  assert(document.querySelectorAll('[data-select-line]').every(e => !e.checked));
});

test('cobranca selecionar todas respeita limite de 500', async () => {
  const {get} = await load('cobrancas.js', 501);
  assert.equal(get('charge-select-all').disabled, true);
  get('charge-select-all').checked = true; get('charge-select-all').dispatch('change');
  assert.equal(get('charge-selected').textContent, '0 visitas selecionadas');
});

test('cobranca fechar lote sem salvar preserva selecao e nao grava', async () => {
  const {get, calls} = await load('cobrancas.js');
  get('charge-select-all').checked = true; get('charge-select-all').dispatch('change');
  get('charge-batch').dispatch('click');
  assert.equal(get('contact-dialog').open, true);
  const before = calls.length;
  get('contact-close').dispatch('click');
  assert.equal(get('contact-dialog').open, false);
  assert.equal(calls.length, before);
  assert.equal(get('charge-selected').textContent, '2 visitas selecionadas');
});

test('recebimentos primeira selecao filtra medico e impede lote misto', async () => {
  const {get, document} = await load('recebimentos.js');
  assert.equal(get('payment-select-all').disabled, true);
  const first = document.querySelectorAll('[data-payment-select]')[0];
  first.checked = true; first.dispatch('change');
  assert.equal(get('financial-doctor').value, '1');
  assert.equal(get('payment-selected').textContent, '1 visitas selecionadas');
  assert.equal(document.querySelectorAll('[data-payment-select]').length, 1);
});

test('recebimentos limpar selecao nao recarrega nem muda medico', async () => {
  const {get, calls, document} = await load('recebimentos.js');
  const first = document.querySelectorAll('[data-payment-select]')[0]; first.checked = true; first.dispatch('change');
  const before = calls.length;
  get('payment-unselect').dispatch('click');
  assert.equal(get('payment-selected').textContent, '0 visitas selecionadas');
  assert.equal(get('financial-doctor').value, '1');
  assert.equal(calls.length, before);
});

test('recebimentos trocar medico limpa selecao anterior', async () => {
  const {get, document} = await load('recebimentos.js');
  const first = document.querySelectorAll('[data-payment-select]')[0]; first.checked = true; first.dispatch('change');
  get('financial-doctor').value = '2'; get('financial-doctor').dispatch('change');
  assert.equal(get('payment-selected').textContent, '0 visitas selecionadas');
  assert.equal(get('payment-open').disabled, true);
});

test('usar saldo preenche distribuicao e remover ultima visita bloqueia confirmacao', async () => {
  const {get, calls, document} = await load('recebimentos.js');
  const first = document.querySelectorAll('[data-payment-select]')[0]; first.checked = true; first.dispatch('change');
  get('payment-open').dispatch('click');
  assert.equal(get('payment-dialog').open, true);
  const row = get('payment-rows').children[0];
  const buttons = row.children[5].children[0].children;
  buttons[0].dispatch('click');
  const allocation = get('payment-rows').querySelectorAll('[data-allocation-line]')[0];
  assert.equal(Number(allocation.value), 100);
  get('payment-form').elements.valorTotal.value = '100';
  get('payment-form').elements.valorTotal.dispatch('input');
  assert.equal(get('payment-save').disabled, false);
  const before = calls.length;
  buttons[1].dispatch('click');
  assert.equal(get('payment-rows').children.length, 0);
  assert.equal(get('payment-save').disabled, true);
  assert.equal(get('payment-selected').textContent, '0 visitas selecionadas');
  assert.equal(calls.length, before);
});

test('fechar distribuicao de pagamento nao grava recebimento', async () => {
  const {get, calls, document} = await load('recebimentos.js');
  const first = document.querySelectorAll('[data-payment-select]')[0]; first.checked = true; first.dispatch('change');
  get('payment-open').dispatch('click');
  const before = calls.length;
  get('payment-close').dispatch('click');
  assert.equal(get('payment-dialog').open, false);
  assert.equal(calls.length, before);
});
