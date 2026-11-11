// Som de aviso de nova mensagem do chat: duas notas ascendentes, curtas e com brilho
// (inspirado nos avisos de mensageiros classicos), sintetizadas com Web Audio, sem arquivo de audio.
(function () {
  if (window.fmChatSom) return;
  let ctx = null;
  // saida de som escolhida nas configuracoes do chat (Chrome/Edge suportam AudioContext.setSinkId)
  const saidaPreferida = () => { try { return JSON.parse(localStorage.getItem('fm-chat-audio') || '{}').saida || ''; } catch (_) { return ''; } };
  function definirSaida(id) {
    if (ctx && typeof ctx.setSinkId === 'function') ctx.setSinkId(id || '').catch(() => {});
  }
  const contexto = () => {
    if (!ctx) {
      const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return null;
      ctx = new AC();
      if (saidaPreferida()) definirSaida(saidaPreferida());
    }
    if (ctx.state === 'suspended') ctx.resume().catch(() => {});
    return ctx;
  };
  // navegadores so liberam audio depois de um clique ou tecla na pagina
  const liberar = () => { contexto(); };
  ['pointerdown', 'keydown'].forEach(ev => document.addEventListener(ev, liberar, {once: true, capture: true}));

  function nota(ac, freq, inicio, duracao, volume) {
    const ganho = ac.createGain();
    ganho.gain.setValueAtTime(0.0001, inicio);
    ganho.gain.exponentialRampToValueAtTime(volume, inicio + 0.012);
    ganho.gain.exponentialRampToValueAtTime(0.0001, inicio + duracao);
    ganho.connect(ac.destination);
    // fundamental + harmonico suave da o timbre de "sininho"
    [[freq, 'sine', 1], [freq * 2, 'triangle', 0.25], [freq * 3, 'sine', 0.08]].forEach(([f, tipo, nivel]) => {
      const osc = ac.createOscillator();
      const g = ac.createGain();
      osc.type = tipo; osc.frequency.setValueAtTime(f, inicio);
      g.gain.value = nivel;
      osc.connect(g); g.connect(ganho);
      osc.start(inicio); osc.stop(inicio + duracao + 0.05);
    });
  }

  /** Toca o aviso. chave evita tocar duas vezes o mesmo aviso em abas/janelas diferentes. */
  function tocar(chave) {
    try {
      if (chave) {
        const marca = 'fm-chat-som';
        const anterior = JSON.parse(localStorage.getItem(marca) || 'null');
        if (anterior && anterior.chave === String(chave) && Date.now() - anterior.em < 5000) return;
        localStorage.setItem(marca, JSON.stringify({chave: String(chave), em: Date.now()}));
      }
    } catch (_) {}
    const ac = contexto();
    if (!ac || ac.state !== 'running') return;
    const t = ac.currentTime + 0.02;
    nota(ac, 987.77, t, 0.22, 0.18);        // Si5
    nota(ac, 1318.51, t + 0.11, 0.42, 0.2); // Mi6
  }

  window.fmChatSom = {tocar, definirSaida};
})();
