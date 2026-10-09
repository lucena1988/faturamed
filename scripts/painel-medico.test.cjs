const {test} = require('node:test');
const assert = require('node:assert/strict');
const {valueOf, keyOf, summarize} = require('../src/main/resources/static/painel-medico.js');
const visit = (status, extra = {}) => ({status, medico: 'Dra Ana', original: {}, ...extra});
test('nao soma candidatos ambiguos nem reaproveita valor removido na revisao', () => {
  assert.equal(valueOf(visit('DIVERGENTE', {candidatos: [{repasse: 100}, {repasse: 200}]})), null);
  assert.equal(valueOf(visit('PAGA', {camposRevisados: true, repasse: null, hospital: {repasse: 100}})), null);
});
test('distingue zero, valor desconhecido e valor corrigido', () => {
  assert.equal(valueOf(visit('PAGA', {hospital: {repasse: 0}})), 0);
  assert.equal(valueOf(visit('PAGA', {hospital: {repasse: 'invalido'}})), null);
  assert.equal(valueOf(visit('PAGA', {camposRevisados: true, repasse: 80, hospital: {repasse: 100}})), 80);
});
test('cadastros homonimos sao distintos', () => {
  assert.notEqual(keyOf(visit('PAGA', {medicoCadastroId: 1})), keyOf(visit('PAGA', {medicoCadastroId: 2})));
  assert.equal(keyOf(visit('PAGA', {medico: ' DRA ANA '})), keyOf(visit('PAGA')));
});
test('resume por status sem tratar valores desconhecidos como zero', () => {
  const result = summarize([visit('PAGA', {hospital: {repasse: 100}}), visit('PENDENTE'), visit('DIVERGENTE', {camposRevisados: true, repasse: 25})]);
  assert.equal(result.PAGA.value, 100);
  assert.equal(result.PENDENTE.value, null);
  assert.equal(result.DIVERGENTE.value, 25);
  assert.equal(result.unknown, 1);
  assert.equal(summarize([]).PAGA.value, 0);
});
