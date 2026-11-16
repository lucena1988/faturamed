// Cadastro completado pelo proprio medico: pelo link de convite (sem login, cria a senha)
// ou no primeiro acesso de quem ja tinha conta (logado).
(function () {
  const form = document.querySelector('#cadastro-form');
  if (!form) return;
  const $ = id => document.getElementById(id);
  const convite = form.dataset.modo === 'convite';
  const token = new URLSearchParams(location.search).get('token') || '';
  const message = $('cadastro-message');
  const ufs = ['AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'];
  form.uf.replaceChildren(new Option('Selecione', ''), ...ufs.map(uf => new Option(uf, uf)));

  async function csrf() {
    const response = await fetch('/api/auth/csrf', { cache: 'no-store' });
    if (!response.ok) throw new Error('Não foi possível validar a sessão. Atualize a página.');
    return response.json();
  }
  async function request(url, init = {}) {
    if (init.method && init.method !== 'GET') {
      const token = await csrf();
      init.headers = { ...(init.headers || {}), [token.header]: token.token };
    }
    const response = await fetch(url, init);
    const data = response.status === 204 ? null : await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data?.erro || 'Não foi possível concluir a operação');
    return data;
  }
  const digits = value => value.replace(/\D/g, '');
  function mask(input, pattern) {
    input.addEventListener('input', () => {
      const raw = digits(input.value); let out = '', i = 0;
      for (const ch of pattern) { if (i >= raw.length) break; out += ch === '#' ? raw[i++] : ch; }
      input.value = out;
    });
  }
  mask(form.cpf, '###.###.###-##');
  mask(form.empresaCnpj, '##.###.###/####-##');

  function preencher(dados) {
    form.nome.value = dados.nome || '';
    form.crm.value = dados.crm || '';
    form.uf.value = dados.uf || '';
    form.emails.value = (dados.email || '').replaceAll(';', '; ');
    if (dados.cpf) form.cpf.value = dados.cpf.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
    if (dados.empresaNome) form.empresaNome.value = dados.empresaNome;
    if (dados.empresaCnpj) form.empresaCnpj.value = dados.empresaCnpj.replace(/(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, '$1.$2.$3/$4-$5');
  }
  // Mesma regra do servidor, so para avisar antes de enviar.
  function validar() {
    const nomes = form.nome.value.trim().split(/\s+/).filter(p => p.replace(/[^\p{L}]/gu, '').length >= 2);
    if (nomes.length < 2) return 'Informe o nome completo, com pelo menos nome e sobrenome.';
    if (digits(form.cpf.value).length !== 11) return 'Informe o CPF com 11 dígitos.';
    if (digits(form.empresaCnpj.value).length !== 14) return 'Informe o CNPJ com 14 dígitos.';
    if (convite && form.senha.value !== form.confirmacao.value) return 'A confirmação da senha não confere.';
    return '';
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    const erro = validar();
    if (erro) { message.dataset.type = 'error'; message.textContent = erro; return; }
    const button = form.querySelector('[type=submit]'); button.disabled = true;
    message.dataset.type = 'info'; message.textContent = 'Salvando cadastro...';
    const dados = { nome: form.nome.value, crm: form.crm.value, uf: form.uf.value, cpf: form.cpf.value,
      empresaNome: form.empresaNome.value, empresaCnpj: form.empresaCnpj.value, emails: form.emails.value };
    try {
      if (convite) {
        await request(`/api/convites/${encodeURIComponent(token)}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...dados, senha: form.senha.value }) });
        form.hidden = true;
        message.dataset.type = 'success';
        message.replaceChildren('Cadastro concluído. Entre com o primeiro e-mail da lista e a senha que você criou. ');
        const link = document.createElement('a'); link.href = '/login.html'; link.textContent = 'Ir para o login'; message.append(link);
      } else {
        await request('/api/medico/cadastro', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(dados) });
        location.replace('/portal.html');
      }
    } catch (error) { message.dataset.type = 'error'; message.textContent = error.message; button.disabled = false; }
  });

  (async () => {
    try {
      preencher(await request(convite ? `/api/convites/${encodeURIComponent(token)}` : '/api/medico/cadastro'));
      form.querySelector('fieldset').disabled = false;
      message.textContent = '';
    } catch (error) { message.dataset.type = 'error'; message.textContent = error.message; }
  })();
})();
