(function () {
  const originalFetch = window.fetch.bind(window);
  let tokenPromise;
  async function csrf() {
    tokenPromise ||= originalFetch('/api/auth/csrf', {cache: 'no-store'}).then(async response => {
      if (!response.ok) throw new Error('Não foi possível validar a sessao. Atualize a página.');
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
    if (!response.ok) throw new Error(data?.erro || 'Não foi possível concluir a operação');
    return data;
  }
  window.FaturamedAuth = {request, resetCsrf: () => { tokenPromise = null; }};
  document.addEventListener('DOMContentLoaded', () => {
    if(location.pathname!=='/login.html'){
      request('/api/auth/me').then(user => {
        const externo = ['MEDICO','EMPRESA','HOSPITAL'].includes(user.perfil);
        if (externo) {
          document.querySelectorAll('a[href="usuarios.html"],a[href="/usuarios.html"]').forEach(link=>link.remove());
        }
        // botao flutuante do Service Desk desativado: a equipe usa o item Central de chamados do menu
        if (externo) {
          document.querySelectorAll('a[href="chamados.html"],a[href="/chamados.html"]').forEach(link=>link.remove());
        }
      }).catch(()=>{});
      const css=document.createElement('link');css.rel='stylesheet';css.href='/notificacoes.css?v=20261029-ui';document.head.append(css);
      const script=document.createElement('script');script.src='/notificacoes.js?v=20261015-1';document.body.append(script);
    }
    document.querySelectorAll('[data-logout]').forEach(button => button.addEventListener('click', async () => {
      button.disabled = true;
      try {
        await request('/api/auth/logout', {method: 'POST'});
        // dados de pacientes em rascunho nao ficam no navegador depois de sair
        try { for (const s of [sessionStorage, localStorage]) Object.keys(s).filter(k => k.startsWith('faturamed-rascunho-')).forEach(k => s.removeItem(k)); } catch (_) {}
        location.replace('/login.html');
      }
      catch(error) { button.disabled = false; (window.fmAvisar || alert)(error.message); }
    }));
  });
})();
