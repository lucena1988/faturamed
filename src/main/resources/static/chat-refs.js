// Referencias no chat: "#lote 130", "#chamado 12", "#relatorio 6", "#div 6/10002" e links das telas
// do FaturaMed viram link e um cartao com status (usado no painel rapido e no chat completo).
(function () {
  if (window.fmChatRefs) return;
  const ATALHO = /(^|[\s(])#(lote|chamado|relatorio|div)\s?([0-9]{1,12}(?:\/[0-9A-Za-z.\-]{1,40})?)/gi;
  const LINKS = [
    [/producao\.html\?(?:[^\s#]*&)?folha=([0-9]{1,12})/i, m => 'lote:' + m[1]],
    [/chamados\.html\?(?:[^\s#]*&)?chamado=([0-9]{1,12})/i, m => 'chamado:' + m[1]],
    [/conciliacao\.html\?(?:[^\s#]*&)?relatorio=([0-9]{1,12})(?:[^\s#]*?&atendimento=([0-9A-Za-z.\-]{1,40}))?/i, m => m[2] ? `div:${m[1]}/${m[2]}` : 'relatorio:' + m[1]]
  ];
  const ICONES = {lote: '🗂️', chamado: '🛟', relatorio: '📊', div: '⚠️'};
  const cache = new Map();
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const destino = (tipo, valor) => {
    if (tipo === 'lote') return 'producao.html?folha=' + valor;
    if (tipo === 'chamado') return 'chamados.html?chamado=' + valor;
    const [rel, at] = String(valor).split('/');
    return 'conciliacao.html?relatorio=' + rel + (at ? '&atendimento=' + encodeURIComponent(at) : '');
  };

  /** Chaves citadas no texto (ex.: ["lote:130", "div:6/10002"]), sem repetir. */
  function extrair(texto) {
    const chaves = new Set();
    const t = String(texto || '');
    for (const m of t.matchAll(ATALHO)) chaves.add(`${m[2].toLowerCase()}:${m[3]}`);
    for (const pedaco of t.split(/\s+/)) for (const [re, fn] of LINKS) { const m = pedaco.match(re); if (m) chaves.add(fn(m)); }
    return [...chaves].slice(0, 5);
  }

  /** Recebe o texto JA escapado e transforma os atalhos em links. */
  function linkificar(html) {
    return String(html).replace(ATALHO, (tudo, antes, tipo, valor) =>
      `${antes}<a class="fm-ref-link" href="${destino(tipo.toLowerCase(), valor)}">#${tipo.toLowerCase()} ${valor}</a>`);
  }

  async function buscar(chaves) {
    const faltam = chaves.filter(c => !cache.has(c));
    if (faltam.length) {
      const qs = new URLSearchParams(); faltam.forEach(c => qs.append('c', c));
      try {
        const r = await fetch('/api/chat/referencias?' + qs, {headers: {Accept: 'application/json'}});
        const lista = r.ok ? await r.json() : [];
        faltam.forEach(c => cache.set(c, null));
        lista.forEach(ref => cache.set(ref.chave, ref));
      } catch (_) { return []; }
    }
    return chaves.map(c => cache.get(c)).filter(Boolean);
  }

  function cartao(ref) {
    const tipo = ref.chave.split(':')[0];
    return `<a class="fm-ref-cartao" href="${esc(ref.link)}" data-cor="${esc(ref.cor)}">
      <span class="fm-ref-icone" aria-hidden="true">${ICONES[tipo] || '🔗'}</span>
      <span class="fm-ref-corpo"><small>${esc(ref.tipo)}</small><strong>${esc(ref.titulo)}</strong><em>${esc(ref.detalhe)}</em></span>
      <span class="fm-ref-status">${esc(ref.status)}</span></a>`;
  }

  /** Coloca os cartoes das referencias de "texto" dentro de "alvo" (assincrono, com cache). */
  async function cartoes(alvo, texto) {
    const chaves = extrair(texto);
    if (!chaves.length || !alvo) return;
    const refs = await buscar(chaves);
    if (!refs.length || !alvo.isConnected) return;
    alvo.insertAdjacentHTML('beforeend', `<div class="fm-ref-lista">${refs.map(cartao).join('')}</div>`);
  }

  /** Para mensagens novas: limpa o cache da referencia (o status pode ter mudado). */
  function esquecer() { cache.clear(); }

  window.fmChatRefs = {extrair, linkificar, cartoes, esquecer};
})();
