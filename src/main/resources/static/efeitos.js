// Efeitos visuais modernos: numeros que contam, brilho que segue o mouse, minigraficos, pulso de sucesso e modo foco.
(function () {
  if (window.fmEfeitos) return;
  const calmo = matchMedia('(prefers-reduced-motion: reduce)').matches;

  // ---------- 2) numeros que contam ate o valor ----------
  const BRL = /^R\$\s?([\d.]+,\d{2})$/;
  function lerNumero(t) {
    const s = t.trim();
    const m = s.match(BRL);
    if (m) return {valor: Number(m[1].replace(/\./g, '').replace(',', '.')), moeda: true};
    if (/^\d{1,3}(\.\d{3})*$|^\d+$/.test(s)) return {valor: Number(s.replace(/\./g, '')), moeda: false};
    return null;
  }
  const fmt = (v, moeda) => moeda ? v.toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'}) : Math.round(v).toLocaleString('pt-BR');
  function contar(el) {
    if (calmo || el.dataset.fmContando) return;
    const n = lerNumero(el.textContent);
    if (!n || n.valor === 0 || el.dataset.fmContado === el.textContent) return;
    const final = el.textContent;
    el.dataset.fmContando = '1';
    const inicio = performance.now(), dur = 650;
    const passo = agora => {
      const p = Math.min(1, (agora - inicio) / dur);
      const e = 1 - Math.pow(1 - p, 3);
      el.textContent = p < 1 ? fmt(n.valor * e, n.moeda) : final;
      if (p < 1) requestAnimationFrame(passo);
      else { el.dataset.fmContado = final; delete el.dataset.fmContando; }
    };
    requestAnimationFrame(passo);
  }

  // ---------- 4) brilho que segue o mouse ----------
  function iluminar(card) {
    if (card.querySelector(':scope > .fm-luz')) return;
    card.classList.add('fm-spot');
    card.insertAdjacentHTML('afterbegin', '<span class="fm-luz" aria-hidden="true"></span>');
  }
  document.addEventListener('pointermove', e => {
    const card = e.target.closest?.('.fm-spot');
    if (!card) return;
    const r = card.getBoundingClientRect();
    card.style.setProperty('--mx', `${e.clientX - r.left}px`);
    card.style.setProperty('--my', `${e.clientY - r.top}px`);
  }, {passive: true});

  // aplica nos cards e paineis que aparecem na pagina (inclusive os desenhados depois)
  function varrer() {
    document.querySelectorAll('main :is(.fm-card, .kpi-card, .panel)').forEach(iluminar);
    document.querySelectorAll('main :is(.fm-card, .kpi-card) strong').forEach(contar);
  }
  let agendado = false;
  const observar = () => new MutationObserver(() => {
    if (agendado) return;
    agendado = true;
    requestAnimationFrame(() => { agendado = false; varrer(); });
  }).observe(document.querySelector('main') || document.body, {childList: true, subtree: true, characterData: true});

  // ---------- 8) minigrafico ----------
  function sparkline(valores, {largura = 220, altura = 38} = {}) {
    if (!valores.length || valores.every(v => !v)) return '';
    const max = Math.max(...valores), min = Math.min(...valores, 0);
    const x = i => valores.length === 1 ? largura / 2 : (i / (valores.length - 1)) * largura;
    const y = v => altura - 4 - ((v - min) / ((max - min) || 1)) * (altura - 8);
    const pontos = valores.map((v, i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`);
    const linha = 'M' + pontos.join(' L');
    const area = `${linha} L${x(valores.length - 1).toFixed(1)},${altura} L0,${altura} Z`;
    const ult = pontos.at(-1).split(',');
    return `<svg class="fm-spark" viewBox="0 0 ${largura} ${altura}" preserveAspectRatio="none" aria-hidden="true"><path class="area" d="${area}"/><path class="linha" d="${linha}"/><circle cx="${ult[0]}" cy="${ult[1]}" r="3"/></svg>`;
  }
  const tendencia = (atual, anterior) => {
    if (!anterior) return '';
    const p = Math.round(((atual - anterior) / anterior) * 100);
    if (!isFinite(p) || p === 0) return '';
    return `<span class="fm-tendencia ${p > 0 ? 'sobe' : 'desce'}">${p > 0 ? '↑' : '↓'} ${Math.abs(p)}%</span>`;
  };
  // agrupa por mes (ultimos n meses, terminando no atual)
  function porMes(itens, dataDe, valorDe, meses = 6) {
    const hoje = new Date();
    const chaves = Array.from({length: meses}, (_, i) => {
      const d = new Date(hoje.getFullYear(), hoje.getMonth() - (meses - 1 - i), 1);
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    });
    const soma = Object.fromEntries(chaves.map(k => [k, 0]));
    for (const it of itens) { const k = String(dataDe(it) || '').slice(0, 7); if (k in soma) soma[k] += Number(valorDe(it)) || 0; }
    return chaves.map(k => soma[k]);
  }

  // ---------- 3) pulso de sucesso numa linha/elemento ----------
  function pulso(el) {
    if (!el || calmo) return;
    el.classList.remove('fm-pulso');
    void el.offsetWidth;
    el.classList.add('fm-pulso');
    setTimeout(() => el.classList.remove('fm-pulso'), 1300);
  }

  // ---------- 9) modo foco ----------
  function foco(ligar) {
    const raiz = document.documentElement;
    const ativo = ligar ?? !raiz.classList.contains('fm-foco');
    raiz.classList.toggle('fm-foco', ativo);
    document.querySelector('.fm-foco-aviso')?.remove();
    if (ativo) {
      document.body.insertAdjacentHTML('beforeend', '<div class="fm-foco-aviso" role="status">Modo foco <button type="button">Sair (Esc)</button></div>');
      document.querySelector('.fm-foco-aviso button').addEventListener('click', () => foco(false));
    }
  }
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && document.documentElement.classList.contains('fm-foco') && !document.querySelector('dialog[open]')) foco(false);
  });

  window.fmEfeitos = {sparkline, tendencia, porMes, pulso, foco, contar};
  const iniciar = () => { varrer(); observar(); };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar); else iniciar();
})();
