// Som de aviso do chat no estilo monitor de UTI: dois bipes de batimento (mensagem) ou tres bipes de alerta (mencao)
// sintetizados com Web Audio, sem arquivo de audio.
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

  // saida mestre: ganho maior (o aviso estava baixo) com limitador para nao distorcer
  let mestre = null;
  const volumeEscolhido = () => { try { const v = parseFloat(localStorage.getItem('fm-chat-volume')); return Number.isFinite(v) ? Math.min(1, Math.max(0, v)) : 1; } catch (_) { return 1; } };
  function saidaMestre(ac) {
    if (!mestre) {
      const limitador = ac.createDynamicsCompressor();
      limitador.threshold.value = -6; limitador.knee.value = 6; limitador.ratio.value = 12; limitador.attack.value = 0.002; limitador.release.value = 0.15;
      mestre = ac.createGain(); mestre.connect(limitador); limitador.connect(ac.destination);
    }
    mestre.gain.value = 1.6 * volumeEscolhido();
    return mestre;
  }

  function nota(ac, freq, inicio, duracao, volume) {
    const ganho = ac.createGain();
    ganho.gain.setValueAtTime(0.0001, inicio);
    ganho.gain.exponentialRampToValueAtTime(volume, inicio + 0.012);
    ganho.gain.exponentialRampToValueAtTime(0.0001, inicio + duracao);
    ganho.connect(saidaMestre(ac));
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

  // bipe de monitor multiparametrico: tom quase puro, ataque e corte rapidos (sem o brilho de sininho)
  function bipe(ac, freq, inicio, duracao, volume) {
    const ganho = ac.createGain();
    ganho.gain.setValueAtTime(0.0001, inicio);
    ganho.gain.exponentialRampToValueAtTime(volume, inicio + 0.006);
    ganho.gain.setValueAtTime(volume, inicio + duracao - 0.02);
    ganho.gain.exponentialRampToValueAtTime(0.0001, inicio + duracao);
    ganho.connect(saidaMestre(ac));
    [[freq, 'sine', 1], [freq * 2, 'sine', 0.06]].forEach(([fr, tipo, nivel]) => {
      const osc = ac.createOscillator(), g = ac.createGain();
      osc.type = tipo; osc.frequency.setValueAtTime(fr, inicio); g.gain.value = nivel;
      osc.connect(g); g.connect(ganho); osc.start(inicio); osc.stop(inicio + duracao + 0.02);
    });
  }

  /** Toca o aviso. chave evita tocar duas vezes o mesmo aviso em abas/janelas diferentes. */
  function tocar(chave) {
    // silencio geral escolhido no painel do chat (vale para todas as abas)
    try { if (localStorage.getItem('fm-chat-mudo') === '1') return; } catch (_) {}
    // nao perturbe por horario: so as mencoes passam
    if (emNaoPerturbe() && !String(chave || '').startsWith('mencao')) return;
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
    if (String(chave || '').startsWith('mencao')) {
      // mencao: tres bipes no padrao de alerta do monitor (agudo, tom de prioridade media)
      [0, 0.2, 0.4].forEach(d => bipe(ac, 1046.5, t + d, 0.13, 0.5));
    } else {
      // mensagem: dois bipes curtos de batimento, como o do oximetro no monitor de UTI
      bipe(ac, 932.33, t, 0.09, 0.45);
      bipe(ac, 932.33, t + 0.32, 0.09, 0.45);
    }
  }

  // "nao perturbe" por horario (ex.: 18:00 as 08:00) e, se marcado, no fim de semana
  function naoPerturbe() { try { return JSON.parse(localStorage.getItem('fm-chat-nao-perturbe') || '{}'); } catch (_) { return {}; } }
  function emNaoPerturbe(agora = new Date()) {
    const c = naoPerturbe(); if (!c.ativo) return false;
    if (c.fimDeSemana && (agora.getDay() === 0 || agora.getDay() === 6)) return true;
    const min = s => { const [h, m] = String(s || '0:0').split(':').map(Number); return h * 60 + (m || 0); };
    const ini = min(c.inicio || '18:00'), fim = min(c.fim || '08:00'), t = agora.getHours() * 60 + agora.getMinutes();
    return ini <= fim ? (t >= ini && t < fim) : (t >= ini || t < fim);
  }

  window.fmChatSom = {tocar, definirSaida, naoPerturbe, emNaoPerturbe};
})();
