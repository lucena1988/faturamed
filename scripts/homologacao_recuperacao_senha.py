"""Cenario HTTP da recuperacao de senha por codigo; executado somente na instancia QA existente.

O container de QA sobe com RECUPERACAO_CODIGO_NO_LOG=true e sem SMTP: o codigo e lido do log do
proprio container, fazendo o papel da caixa de entrada.
"""
import re


def executar(env, admin):
    case, Client, PASSWORD, RUN_ID, docker, APPLICATION = (env[k] for k in ['case', 'Client', 'PASSWORD', 'RUN_ID', 'docker', 'APPLICATION'])

    def codigo(email, anteriores=()):
        logs = docker('logs', APPLICATION).decode(errors='replace')
        encontrados = re.findall(r'codigo (\d{6}) para ' + re.escape(email), logs)
        novos = [c for c in encontrados if c not in anteriores]
        assert novos, 'Codigo de recuperacao nao encontrado no log do QA'
        return novos[-1]

    def fluxo():
        email = f'recuperacao-{RUN_ID}@example.com'
        # A senha inicial difere de PASSWORD; o cenario redefine para PASSWORD e entra com ela.
        admin.call('POST', '/api/usuarios', {'nome': 'QA Recuperacao', 'email': email, 'senha': PASSWORD + 'Antiga', 'perfil': 'OPERADOR'}, 201, log=False)
        anonimo = Client()
        inexistente = anonimo.call('POST', '/api/auth/recuperacao', {'email': f'ninguem-{RUN_ID}@example.com'}, expected=202)
        real = anonimo.call('POST', '/api/auth/recuperacao', {'email': email.upper()}, expected=202)
        assert inexistente == real, 'A resposta nao pode revelar se o e-mail existe'
        anonimo.call('POST', '/api/auth/recuperacao', {'email': email}, expected=403, csrf=False)
        primeiro = codigo(email)
        confirmar = '/api/auth/recuperacao/confirmar'
        errado = '000000' if primeiro != '000000' else '111111'
        anonimo.call('POST', confirmar, {'email': email, 'codigo': errado, 'senha': PASSWORD}, expected=400)
        anonimo.call('POST', confirmar, {'email': email, 'codigo': primeiro, 'senha': 'curta'}, expected=400)
        anonimo.call('POST', confirmar, {'email': email, 'codigo': primeiro, 'senha': PASSWORD}, expected=204)
        anonimo.call('POST', confirmar, {'email': email, 'codigo': primeiro, 'senha': PASSWORD}, expected=400)
        conta = Client()
        conta.login(email)
        assert conta.call('GET', '/api/auth/me')['email'] == email

        # Cinco codigos errados invalidam o codigo, mesmo que o certo venha depois.
        anonimo.call('POST', '/api/auth/recuperacao', {'email': email}, expected=202)
        segundo = codigo(email, (primeiro,))
        for tentativa in range(5):
            anonimo.call('POST', confirmar, {'email': email, 'codigo': f'{(int(segundo) + 1 + tentativa) % 1000000:06d}', 'senha': PASSWORD}, expected=400)
        anonimo.call('POST', confirmar, {'email': email, 'codigo': segundo, 'senha': PASSWORD}, expected=400)
        # Limite de 3 pedidos em 15 minutos: o quarto nao gera codigo novo (mesma resposta 202).
        anonimo.call('POST', '/api/auth/recuperacao', {'email': email}, expected=202)
        antes = re.findall(r'codigo (\d{6}) para ' + re.escape(email), docker('logs', APPLICATION).decode(errors='replace'))
        anonimo.call('POST', '/api/auth/recuperacao', {'email': email}, expected=202)
        depois = re.findall(r'codigo (\d{6}) para ' + re.escape(email), docker('logs', APPLICATION).decode(errors='replace'))
        assert len(depois) == len(antes), 'Pedido acima do limite nao pode gerar codigo'
        return {'resposta_igual_para_email_inexistente': True, 'csrf_obrigatorio': 403, 'codigo_errado': 400, 'senha_fraca': 400,
                'codigo_uso_unico': True, 'login_com_nova_senha': True, 'cinco_erros_invalidam': True, 'limite_de_pedidos': 3}

    case('AUTO47', 'Recuperacao de senha por codigo: envio, uso unico, tentativas e limite', fluxo)
