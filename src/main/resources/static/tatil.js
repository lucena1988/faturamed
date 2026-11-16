// Efeitos "tateis": avatares com cor estavel por pessoa, cards com parallax, confete e cubo de carregamento.
(function () {
  if (window.fmTatil) return;
  const calmo = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const plano = () => ['papel', 'contraste'].includes(document.documentElement.dataset.aparencia);
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[c]);

  // ---------- avatar: iniciais + cor estavel calculada pelo nome ----------
  const CORES = ['#5f84ab', '#75689a', '#5f9f7a', '#b5824f', '#4b93a6', '#b56a6a', '#7a8f3e', '#a0628f'];
  function cor(nome) { let h = 0; for (const c of String(nome)) h = (h * 31 + c.charCodeAt(0)) >>> 0; return CORES[h % CORES.length]; }
  function iniciais(nome) {
    const p = String(nome || '?').replace(/^(dr|dra)\.?\s+/i, '').trim().split(/\s+/).filter(x => !/^(de|da|do|dos|das|teste)$/i.test(x));
    return ((p[0] || '?')[0] + (p.length > 1 ? p.at(-1)[0] : '')).toUpperCase();
  }
  const avatar = nome => `<span class="tt-avatar" style="--av:${cor(nome)}" title="${esc(nome)}" aria-hidden="true">${esc(iniciais(nome))}</span>`;
  const pessoa = nome => nome ? `<span class="tt-pessoa">${avatar(nome)}<span>${esc(nome)}</span></span>` : '—';
  /** varios nomes separados por virgula: avatares empilhados + nomes */
  const pessoas = texto => {
    const nomes = String(texto || '').split(',').map(s => s.trim()).filter(Boolean);
    if (!nomes.length) return '—';
    if (nomes.length === 1) return pessoa(nomes[0]);
    return `<span class="tt-pessoas"><span class="tt-pilha">${nomes.slice(0, 3).map(avatar).join('')}</span><span>${esc(nomes.join(', '))}</span></span>`;
  };

  // ---------- parallax nos cards (intensidade baixa) ----------
  document.addEventListener('pointermove', e => {
    if (calmo || plano()) return;
    const card = e.target.closest?.('.fm-card');
    if (!card) return;
    card.classList.add('tt-tilt');
    const r = card.getBoundingClientRect();
    const px = (e.clientX - r.left) / r.width - .5, py = (e.clientY - r.top) / r.height - .5;
    card.style.setProperty('--ry', `${(px * 6).toFixed(2)}deg`);
    card.style.setProperty('--rx', `${(-py * 6).toFixed(2)}deg`);
  }, {passive: true});
  document.addEventListener('pointerout', e => {
    const card = e.target.closest?.('.fm-card');
    if (card && !card.contains(e.relatedTarget)) { card.style.setProperty('--rx', '0deg'); card.style.setProperty('--ry', '0deg'); }
  });

  // ---------- confete (so em marcos: aprovacao e NF) ----------
  function confete() {
    if (calmo || plano()) return;
    const raiz = getComputedStyle(document.body);
    const cores = [raiz.getPropertyValue('--mod-cor') || '#75689a', '#2e8b57', '#e0a24b', '#4b93a6', '#b56a6a'].map(c => c.trim()).filter(Boolean);
    const caixa = document.createElement('div');
    caixa.className = 'tt-confete';
    caixa.innerHTML = Array.from({length: 70}, () => {
      const x = Math.random() * 100, dx = (Math.random() - .5) * 240;
      return `<i style="left:${x}vw;background:${cores[Math.floor(Math.random() * cores.length)]};--x:${dx.toFixed(0)}px;--r:${(Math.random() * 900 - 450).toFixed(0)}deg;--d:${(.8 + Math.random() * .7).toFixed(2)}s;animation-delay:${(Math.random() * .15).toFixed(2)}s"></i>`;
    }).join('');
    document.body.append(caixa);
    setTimeout(() => caixa.remove(), 1800);
  }

  // ---------- cubo de carregamento (operacoes demoradas) ----------
  function carregando(texto = 'Processando…') {
    const el = document.createElement('div');
    el.className = 'tt-carregando';
    el.setAttribute('role', 'status');
    el.innerHTML = `<div><div class="tt-cubo" aria-hidden="true">${'<span>F</span>'.repeat(6)}</div><span>${esc(texto)}</span></div>`;
    document.body.append(el);
    return () => el.remove();
  }

  window.fmTatil = {avatar, pessoa, pessoas, confete, carregando, cor, iniciais};
})();
