const loginForm = document.querySelector('#login-form');
const loginMessage = document.querySelector('#login-message');
loginForm.addEventListener('submit', async event => {
  event.preventDefault(); const button = loginForm.querySelector('button'); button.disabled = true; loginMessage.textContent = 'Entrando...';
  try {
    const result = await FaturamedAuth.request('/api/auth/login', {method: 'POST', body: new URLSearchParams(new FormData(loginForm))});
    loginForm.reset(); FaturamedAuth.resetCsrf(); location.replace(result.destino);
  } catch(error) { loginMessage.textContent = error.message; loginMessage.className = 'access-error'; }
  finally { button.disabled = false; }
});
const loginHealth = document.querySelector('#login-health');
if (loginHealth) fetch('/api/health').then(response => {
  loginHealth.dataset.state = response.ok ? 'ok' : 'down';
  loginHealth.lastElementChild.textContent = response.ok ? 'Sistema operando normalmente' : 'Sistema indisponível no momento';
}).catch(() => { loginHealth.dataset.state = 'down'; loginHealth.lastElementChild.textContent = 'Sistema indisponível no momento'; });
// Duplica a lista de procedimentos para a rolagem continua nao ter emenda.
const loginFeed = document.querySelector('.login-feed-track');
if (loginFeed) loginFeed.append(...[...loginFeed.children].map(item => item.cloneNode(true)));

// Escolha de portal: so ajusta textos e destaque (o login e unico e o servidor leva cada perfil ao portal certo).
const PORTAIS = {
  EQUIPE: ['Acesse o painel de faturamento', 'nome@faturamed.com.br'],
  MEDICO: ['Acompanhe sua produção, repasses e demonstrativos', 'seu.email@exemplo.com.br'],
  EMPRESA: ['Veja os médicos e o financeiro da sua empresa', 'contato@empresa.com.br'],
  HOSPITAL: ['Confira a produção enviada pela FaturaMed', 'nome@hospital.com.br']
};
const portalSalvo = (() => { try { return localStorage.getItem('fm-login-portal'); } catch (_) { return null; } })();
function aplicarPortal(valor) {
  const [sub, email] = PORTAIS[valor] || PORTAIS.EQUIPE;
  document.querySelector('#login-subtitulo').textContent = sub;
  document.querySelector('#login-email').placeholder = email;
  document.body.dataset.portal = valor;
  try { localStorage.setItem('fm-login-portal', valor); } catch (_) {}
}
document.querySelectorAll('input[name="portal"]').forEach(r => {
  if (portalSalvo && r.value === portalSalvo) r.checked = true;
  r.addEventListener('change', () => { aplicarPortal(r.value); document.querySelector('#login-email').focus(); });
});
aplicarPortal(document.querySelector('input[name="portal"]:checked')?.value || 'EQUIPE');

// mostrar/ocultar senha
const verSenha = document.querySelector('.login-ver-senha');
if (verSenha) verSenha.addEventListener('click', () => {
  const campo = document.querySelector('#login-senha'), mostrar = campo.type === 'password';
  campo.type = mostrar ? 'text' : 'password';
  verSenha.setAttribute('aria-pressed', String(mostrar)); verSenha.setAttribute('aria-label', mostrar ? 'Ocultar senha' : 'Mostrar senha');
});

// Fundo: colunas de procedimentos (exemplos ilustrativos) rolando devagar atras do cartao
(function feedFundo() {
  const caixa = document.querySelector('.login-feed-fundo');
  if (!caixa) return;
  const ITENS = [
    ['Visita hospitalar · UTI adulto', 'success', 'Paga'], ['Consulta em pronto-socorro', 'warning', 'Pendente'],
    ['Colecistectomia videolaparoscópica', 'success', 'Paga'], ['Parecer de cardiologia · enfermaria', 'danger', 'Divergente'],
    ['Parto cesáreo · centro obstétrico', 'success', 'Paga'], ['Endoscopia digestiva alta', 'warning', 'Pendente'],
    ['Plantão de UTI neonatal', 'success', 'Paga'], ['Artroplastia total de joelho', 'danger', 'Divergente'],
    ['Hemodiálise · sessão', 'success', 'Paga'], ['Tomografia de crânio com contraste', 'warning', 'Pendente'],
    ['Apendicectomia', 'success', 'Paga'], ['Ecocardiograma transtorácico', 'success', 'Paga'],
    ['ECG convencional', 'warning', 'Pendente'], ['Hemograma completo', 'success', 'Paga'],
    ['Ressonância de joelho', 'danger', 'Divergente'], ['Visita de anestesiologia pré-operatória', 'success', 'Paga']
  ];
  function montar() {
    const colunas = Math.max(2, Math.min(7, Math.ceil(window.innerWidth / 300)));
    caixa.innerHTML = '';
    for (let c = 0; c < colunas; c++) {
      const lista = ITENS.slice(c * 3 % ITENS.length).concat(ITENS.slice(0, c * 3 % ITENS.length));
      const ol = document.createElement('ol');
      ol.className = 'login-feed-coluna' + (c % 2 ? ' sobe' : '');
      ol.style.setProperty('--dur', (46 + (c * 7) % 20) + 's');
      ol.style.setProperty('--atraso', (-c * 5) + 's');
      const html = lista.map(([t, cls, rot]) => `<li><span class="feed-icon"></span><span class="feed-text">${t}</span><b class="pill ${cls}">${rot}</b></li>`).join('');
      ol.innerHTML = html + html; // duplicado para a rolagem nao ter emenda
      caixa.append(ol);
    }
  }
  montar();
  let espera; window.addEventListener('resize', () => { clearTimeout(espera); espera = setTimeout(montar, 250); });
})();
