// Seletor de periodo com dois meses lado a lado e atalhos (estilo sites de viagem).
// Uso: const p = FmPeriodo.criar(elemento, {aoMudar: ({inicio, fim}) => ...}); p.valor(); p.limpar();
// Datas trafegam como texto AAAA-MM-DD.
(() => {
  const MESES = ['janeiro','fevereiro','março','abril','maio','junho','julho','agosto','setembro','outubro','novembro','dezembro'];
  const SEMANA = ['D','S','T','Q','Q','S','S'];
  const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  const deIso = s => { const [a, m, d] = s.split('-').map(Number); return new Date(a, m - 1, d); };
  const br = s => s ? s.split('-').reverse().join('/') : '';
  const el = (tag, cls, txt) => { const e = document.createElement(tag); if (cls) e.className = cls; if (txt != null) e.textContent = txt; return e; };

  function atalhos() {
    const hoje = new Date(); hoje.setHours(0, 0, 0, 0);
    const dias = n => { const d = new Date(hoje); d.setDate(d.getDate() - n); return d; };
    const mes = (desloc) => [new Date(hoje.getFullYear(), hoje.getMonth() + desloc, 1), new Date(hoje.getFullYear(), hoje.getMonth() + desloc + 1, 0)];
    return [
      ['Hoje', hoje, hoje], ['Últimos 7 dias', dias(6), hoje], ['Últimos 30 dias', dias(29), hoje],
      ['Este mês', ...mes(0)], ['Mês passado', ...mes(-1)], ['Últimos 3 meses', new Date(hoje.getFullYear(), hoje.getMonth() - 2, 1), hoje],
      ['Este ano', new Date(hoje.getFullYear(), 0, 1), hoje]
    ];
  }

  function criar(alvo, {aoMudar = () => {}, rotulo = 'Período'} = {}) {
    let inicio = null, fim = null, passando = null;
    let base = new Date(); base = new Date(base.getFullYear(), base.getMonth() - 1, 1); // mostra mes anterior + atual

    const raiz = el('div', 'fm-periodo');
    const label = el('span', 'fm-periodo-rotulo', rotulo);
    const botao = el('button', 'fm-periodo-campo'); botao.type = 'button'; botao.setAttribute('aria-haspopup', 'dialog'); botao.setAttribute('aria-expanded', 'false');
    const texto = el('span', 'fm-periodo-texto'); const limpar = el('span', 'fm-periodo-limpar', '×'); limpar.title = 'Limpar período'; limpar.setAttribute('role', 'button');
    botao.append(el('span', 'fm-periodo-icone', '📅'), texto, limpar);
    const pop = el('div', 'fm-periodo-pop'); pop.hidden = true; pop.setAttribute('role', 'dialog'); pop.setAttribute('aria-label', 'Escolher período');
    raiz.append(label, botao, pop); alvo.replaceChildren(raiz);

    function atualizarTexto() {
      texto.textContent = inicio ? `${br(inicio)} → ${fim ? br(fim) : '…'}` : 'Qualquer data';
      texto.classList.toggle('vazio', !inicio); limpar.hidden = !inicio;
    }
    function emitir() { atualizarTexto(); aoMudar({inicio, fim: fim || inicio}); }

    function mes(ano, m) {
      const caixa = el('div', 'fm-periodo-mes');
      caixa.append(el('div', 'fm-periodo-titulo', `${MESES[m]} ${ano}`));
      const grade = el('div', 'fm-periodo-grade');
      SEMANA.forEach(d => grade.append(el('span', 'fm-periodo-dow', d)));
      const primeiro = new Date(ano, m, 1).getDay(), total = new Date(ano, m + 1, 0).getDate();
      for (let i = 0; i < primeiro; i++) grade.append(el('span'));
      const hoje = iso(new Date());
      const fimVisual = fim || (inicio && passando && passando >= inicio ? passando : null);
      for (let d = 1; d <= total; d++) {
        const dia = iso(new Date(ano, m, d));
        const b = el('button', 'fm-periodo-dia', d); b.type = 'button'; b.dataset.dia = dia; b.setAttribute('aria-label', br(dia));
        if (dia === hoje) b.classList.add('hoje');
        if (inicio && dia === inicio) b.classList.add('inicio');
        if (fimVisual && dia === fimVisual) b.classList.add('fim');
        if (inicio && fimVisual && dia > inicio && dia < fimVisual) b.classList.add('entre');
        grade.append(b);
      }
      caixa.append(grade); return caixa;
    }

    function desenhar() {
      pop.replaceChildren();
      const lado = el('div', 'fm-periodo-atalhos');
      for (const [nome, a, b] of atalhos()) {
        const x = el('button', null, nome); x.type = 'button';
        x.addEventListener('click', () => { inicio = iso(a); fim = iso(b); base = new Date(b.getFullYear(), b.getMonth() - 1, 1); emitir(); fechar(); });
        lado.append(x);
      }
      const corpo = el('div', 'fm-periodo-corpo');
      const nav = el('div', 'fm-periodo-nav');
      const ant = el('button', null, '‹'); ant.type = 'button'; ant.setAttribute('aria-label', 'Mês anterior');
      const prox = el('button', null, '›'); prox.type = 'button'; prox.setAttribute('aria-label', 'Próximo mês');
      ant.addEventListener('click', () => { base = new Date(base.getFullYear(), base.getMonth() - 1, 1); desenhar(); });
      prox.addEventListener('click', () => { base = new Date(base.getFullYear(), base.getMonth() + 1, 1); desenhar(); });
      nav.append(ant, prox);
      const meses = el('div', 'fm-periodo-meses');
      meses.append(mes(base.getFullYear(), base.getMonth()));
      const seg = new Date(base.getFullYear(), base.getMonth() + 1, 1); meses.append(mes(seg.getFullYear(), seg.getMonth()));
      const rodape = el('div', 'fm-periodo-rodape');
      rodape.append(el('span', 'fm-periodo-dica', !inicio || fim ? 'Clique na data inicial' : 'Agora clique na data final'));
      const ok = el('button', 'fm-periodo-ok', 'Aplicar'); ok.type = 'button'; ok.addEventListener('click', () => { if (inicio && !fim) { fim = inicio; emitir(); } fechar(); });
      rodape.append(ok);
      corpo.append(nav, meses, rodape);
      pop.append(lado, corpo);
    }

    pop.addEventListener('click', ev => {
      const b = ev.target.closest('.fm-periodo-dia'); if (!b) return;
      const dia = b.dataset.dia;
      if (!inicio || fim) { inicio = dia; fim = null; passando = null; atualizarTexto(); }
      else if (dia < inicio) { inicio = dia; atualizarTexto(); }
      else { fim = dia; emitir(); }
      desenhar();
    });
    pop.addEventListener('mouseover', ev => {
      const b = ev.target.closest('.fm-periodo-dia');
      if (!b || !inicio || fim || passando === b.dataset.dia) return;
      passando = b.dataset.dia; desenhar();
    });

    function abrir() {
      if (inicio) { const d = deIso(fim || inicio); base = new Date(d.getFullYear(), d.getMonth() - 1, 1); }
      desenhar(); pop.classList.remove('direita'); pop.hidden = false; botao.setAttribute('aria-expanded', 'true');
      // Abre para a esquerda quando nao cabe a direita do campo.
      if (pop.getBoundingClientRect().right > document.documentElement.clientWidth - 8) pop.classList.add('direita');
    }
    function fechar() { if (inicio && !fim) { fim = inicio; emitir(); } pop.hidden = true; passando = null; botao.setAttribute('aria-expanded', 'false'); }
    botao.addEventListener('click', ev => {
      if (ev.target === limpar) { inicio = fim = null; emitir(); fechar(); return; }
      pop.hidden ? abrir() : fechar();
    });
    document.addEventListener('mousedown', ev => { if (!pop.hidden && !raiz.contains(ev.target)) fechar(); });
    document.addEventListener('keydown', ev => { if (ev.key === 'Escape' && !pop.hidden) { fechar(); botao.focus(); } });

    atualizarTexto();
    return {
      valor: () => ({inicio, fim: fim || inicio}),
      limpar: () => { inicio = fim = null; atualizarTexto(); }
    };
  }
  window.FmPeriodo = {criar};
})();
