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
