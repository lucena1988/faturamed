(function () {
  const originalFetch = window.fetch.bind(window);
  let tokenPromise;
  async function csrf() {
    tokenPromise ||= originalFetch('/api/auth/csrf', {cache: 'no-store'}).then(async response => {
      if (!response.ok) throw new Error('Nao foi possivel validar a sessao. Atualize a pagina.');
      return response.json();
    }).catch(error => { tokenPromise = null; throw error; });
    return tokenPromise;
  }
  window.fetch = async (input, init = {}) => {
    const url = new URL(input instanceof Request ? input.url : input, location.href);
    if (url.origin !== location.origin) return originalFetch(input, init);
    const method = (init.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const token = await csrf();
      const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
      headers.set(token.header, token.token); init = {...init, headers};
    }
    const response = await originalFetch(input, init);
    if (response.status === 401 && location.pathname !== '/login.html') location.replace('/login.html');
    return response;
  };
  async function request(url, init = {}) {
    const response = await fetch(url, init);
    const data = response.status === 204 ? null : await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data?.erro || 'Nao foi possivel concluir a operacao');
    return data;
  }
  window.FaturamedAuth = {request, resetCsrf: () => { tokenPromise = null; }};
  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-logout]').forEach(button => button.addEventListener('click', async () => {
      button.disabled = true;
      try { await request('/api/auth/logout', {method: 'POST'}); location.replace('/login.html'); }
      catch(error) { button.disabled = false; alert(error.message); }
    }));
  });
})();
