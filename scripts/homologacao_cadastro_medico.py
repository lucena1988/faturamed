"""Cenario HTTP do cadastro medico em duas etapas (CH-2026-0002); executado somente na instancia QA existente."""


def executar(env, admin):
    case, Client, PASSWORD, RUN_ID, cpf_valido = env['case'], env['Client'], env['PASSWORD'], env['RUN_ID'], env['cpf_valido']

    def dados(nome, crm, cpf, emails):
        return {'nome': nome, 'crm': crm, 'uf': 'RJ', 'cpf': cpf, 'empresaNome': 'QA Clinica Convite', 'empresaCnpj': '11.444.777/0001-61', 'emails': emails}

    def fluxo():
        admin.call('POST', '/api/medicos', {'nome': 'QA Sem Email', 'crm': '', 'uf': 'SP', 'ativo': True, 'aliases': [], 'versao': 0}, expected=400)
        medico = admin.call('POST', '/api/medicos', {'nome': 'QA Convite', 'crm': '', 'uf': '', 'ativo': True, 'aliases': [], 'versao': 0,
                                                     'email': f'convite-{RUN_ID}@example.com'}, 201)
        assert medico['crm'] is None and medico['uf'] is None and not medico['cadastroCompleto']
        convite = admin.call('POST', f"/api/medicos/{medico['id']}/convite", None, 201)
        token, rota = convite['token'], f"/api/convites/{convite['token']}"

        anonimo = Client()
        previa = anonimo.call('GET', rota)
        assert previa['email'] == f'convite-{RUN_ID}@example.com' and previa['uf'] is None
        login = f'medico-convite-{RUN_ID}@example.com'
        correto = {**dados('QA Convite Completo', '930001', cpf_valido(10), f'{login}; pessoal-{RUN_ID}@example.com'), 'senha': PASSWORD}
        anonimo.call('POST', rota, correto, expected=403, csrf=False)
        for invalido in [{**correto, 'nome': 'Luiz'}, {**correto, 'cpf': '111.111.111-11'}, {**correto, 'empresaCnpj': '11.444.777/0001-60'},
                         {**correto, 'emails': 'sem-arroba'}, {**correto, 'crm': ''}, {**correto, 'senha': 'curta'}]:
            anonimo.call('POST', rota, invalido, expected=400)
        assert anonimo.call('GET', rota)['email'], 'Tentativas recusadas nao podem consumir o convite'
        completo = anonimo.call('POST', rota, correto)
        assert completo['cadastroCompleto'] and completo['cpf'] == cpf_valido(10) and completo['uf'] == 'RJ' and completo['crm'] == '930001'
        assert completo['empresas'], 'A empresa informada deve ser vinculada pelo CNPJ'
        anonimo.call('POST', rota, correto, expected=409)
        anonimo.call('GET', '/api/convites/' + 'x' * 43, expected=404)

        portal = Client()
        portal.login(login)
        portal.call('GET', '/api/medico/relatorios')
        admin.call('POST', f"/api/medicos/{medico['id']}/convite", None, expected=409)
        portal.call('POST', f"/api/medicos/{medico['id']}/convite", None, expected=403)

        # Medico que ja tinha acesso antes da mudanca: portal bloqueado ate completar.
        antigo = admin.call('POST', '/api/medicos', {'nome': 'QA Medico Antigo', 'crm': '930002', 'uf': 'SP', 'ativo': True, 'aliases': [], 'versao': 0,
                                                     'email': f'antigo-{RUN_ID}@example.com'}, 201)
        admin.call('POST', '/api/usuarios', {'nome': antigo['nome'], 'email': f'antigo-{RUN_ID}@example.com', 'senha': PASSWORD, 'perfil': 'MEDICO', 'medicoId': antigo['id']}, 201, log=False)
        pendente = Client()
        pendente.login(f'antigo-{RUN_ID}@example.com')
        bloqueio = pendente.call('GET', '/api/medico/relatorios', expected=403)
        assert 'Complete seu cadastro' in bloqueio['erro']
        assert pendente.call('GET', '/api/medico/cadastro')['id'] == antigo['id']
        pendente.call('PUT', '/api/medico/cadastro', dados('QA', '930002', cpf_valido(11), f'antigo-{RUN_ID}@example.com'), expected=400)
        pendente.call('PUT', '/api/medico/cadastro', dados('QA Medico Antigo', '930002', cpf_valido(10), f'antigo-{RUN_ID}@example.com'), expected=409)
        pendente.call('PUT', '/api/medico/cadastro', dados('QA Medico Antigo', '930002', cpf_valido(11), f'antigo-{RUN_ID}@example.com'))
        pendente.call('GET', '/api/medico/relatorios')
        assert next(m for m in admin.call('GET', '/api/medicos') if m['id'] == antigo['id'])['cadastroCompleto']
        return {'email_obrigatorio_na_faturamed': 400, 'crm_opcional_na_faturamed': True, 'convite_publico_exige_csrf': 403,
                'dados_invalidos_nao_consomem_convite': 6, 'convite_uso_unico': 409, 'empresa_vinculada_por_cnpj': True,
                'login_com_primeiro_email': True, 'medico_antigo_bloqueado_ate_completar': 403, 'cpf_repetido': 409}

    case('AUTO46', 'Cadastro medico em duas etapas: convite, dados obrigatorios e bloqueio ate completar', fluxo)
