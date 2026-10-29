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
