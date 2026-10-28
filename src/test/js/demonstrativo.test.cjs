const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');
const vm = require('node:vm');

function carregar() {
  const elements = {
    'demonstrativo-form': { reset() {} },
    'demonstrativo-fields': {},
    'demonstrativo-message': { textContent: '' },
    'demonstrativo-scope': { textContent: '' },
  };
  const context = { document: { getElementById: id => elements[id] }, window: {} };
  vm.runInNewContext(readFileSync(path.join(__dirname, '../../main/resources/static/demonstrativo.js'), 'utf8'), context);
  return { elements, selecionar: context.window.FaturamedDemonstrativo.selecionar };
}

test('selecao nao afirma que o relatorio foi liberado', () => {
  const { elements, selecionar } = carregar();
  selecionar('46', '8', 'Hospital teste', 'Andreia');
  const scope = elements['demonstrativo-scope'].textContent;
  assert.match(scope, /Andreia.*Hospital teste.*Relatorio #46/);
  assert.match(scope, /PDF exige uma publicacao liberada/);
  assert.match(scope, /disponibilidade sera verificada ao baixar/);
  assert.doesNotMatch(scope, /Copia liberada para o medico/);
  assert.equal(elements['demonstrativo-fields'].disabled, false);
});

test('sem medico selecionado orienta selecao e bloqueia exportacao', () => {
  const { elements, selecionar } = carregar();
  selecionar('46', '', 'Hospital teste', 'Todos os medicos');
  assert.equal(elements['demonstrativo-scope'].textContent, 'Selecione um relatorio e um medico cadastrado.');
  assert.equal(elements['demonstrativo-fields'].disabled, true);
});

test('troca de relatorio atualiza contexto e limpa erro anterior', () => {
  const { elements, selecionar } = carregar();
  selecionar('43', '8', 'Hospital teste', 'Andreia');
  elements['demonstrativo-message'].textContent = 'Erro anterior';
  selecionar('46', '8', 'Hospital teste', 'Andreia');
  assert.match(elements['demonstrativo-scope'].textContent, /Relatorio #46/);
  assert.doesNotMatch(elements['demonstrativo-scope'].textContent, /Relatorio #43/);
  assert.equal(elements['demonstrativo-message'].textContent, '');
});
