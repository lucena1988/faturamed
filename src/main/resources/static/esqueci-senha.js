// Recuperacao de senha sem login: pede o codigo por e-mail e redefine a senha com ele.
(function () {
  const pedido = document.querySelector('#pedido-form'), redefinir = document.querySelector('#redefinir-form');
  const message = document.querySelector('#recuperacao-message');
  let email = '';
  const mostrar = (tipo, texto) => { message.dataset.type = tipo; message.textContent = texto; };
  async function enviar(url, corpo) {
    const csrf = await fetch('/api/auth/csrf', { cache: 'no-store' }).then(r => r.json());
    const response = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.header]: csrf.token }, body: JSON.stringify(corpo) });
    const data = response.status === 204 ? null : await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data?.erro || 'Não foi possível concluir a operação');
    return data;
  }
  async function pedirCodigo(botao) {
    botao.disabled = true; mostrar('info', 'Enviando...');
    try {
      const resposta = await enviar('/api/auth/recuperacao', { email });
      pedido.hidden = true; redefinir.hidden = false;
      document.querySelector('#redefinir-intro').textContent = `${resposta.mensagem}. Confira a caixa de entrada e o spam de ${email}.`;
      mostrar('', ''); redefinir.codigo.focus();
    } catch (error) { mostrar('error', error.message); }
    finally { botao.disabled = false; }
  }
  pedido.addEventListener('submit', event => {
    event.preventDefault();
    email = pedido.email.value.trim();
    if (!pedido.email.checkValidity() || !email) { mostrar('error', 'Informe um e-mail válido.'); return; }
    pedirCodigo(pedido.querySelector('[type=submit]'));
  });
  document.querySelector('#reenviar').addEventListener('click', event => pedirCodigo(event.currentTarget));
  redefinir.codigo.addEventListener('input', () => { redefinir.codigo.value = redefinir.codigo.value.replace(/\D/g, '').slice(0, 6); });
  redefinir.addEventListener('submit', async event => {
    event.preventDefault();
    if (!/^\d{6}$/.test(redefinir.codigo.value)) { mostrar('error', 'Informe os 6 dígitos do código.'); return; }
    if (redefinir.senha.value.length < 12) { mostrar('error', 'A nova senha deve ter no mínimo 12 caracteres.'); return; }
    if (redefinir.senha.value !== redefinir.confirmacao.value) { mostrar('error', 'A confirmação da senha não confere.'); return; }
    const botao = redefinir.querySelector('[type=submit]'); botao.disabled = true; mostrar('info', 'Redefinindo...');
    try {
      await enviar('/api/auth/recuperacao/confirmar', { email, codigo: redefinir.codigo.value, senha: redefinir.senha.value });
      redefinir.hidden = true;
      mostrar('success', 'Senha redefinida. Entre com a nova senha.');
      const link = document.createElement('a'); link.href = 'login.html'; link.textContent = ' Ir para o login'; message.append(link);
    } catch (error) { mostrar('error', error.message); botao.disabled = false; }
  });
})();
