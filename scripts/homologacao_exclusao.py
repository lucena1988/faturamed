"""Cenario HTTP da exclusao de relatorio confirmada por senha; executado somente na instancia QA existente."""


def executar(env, admin):
    case, Client, fixture = env['case'], env['Client'], env['fixture']
    HEADERS, hospital_row, PASSWORD, RUN_ID = env['HEADERS'], env['hospital_row'], env['PASSWORD'], env['RUN_ID']

    def conta(perfil, nome, **vinculo):
        email = f'exclusao-{nome}-{RUN_ID}@example.com'
        admin.call('POST', '/api/usuarios', {'nome': 'QA Exclusao ' + nome, 'email': email, 'senha': PASSWORD, 'perfil': perfil, **vinculo}, 201, log=False)
        client = Client()
        client.login(email)
        return client

    def relatorio(nome, medico, atendimento, valor):
        h = admin.call('POST', '/api/hospitais', {'nome': nome, 'documento': '', 'cidade': 'Teste', 'uf': 'SP', 'ativo': True, 'versao': 0}, 201)['id']
        params = {'hospitalId': h, 'competencia': '2026/10'}
        admin.call('POST', '/api/base-hospitalar/hospital', params, files=fixture(nome + '-hospital.xlsx', [HEADERS, hospital_row(atendimento, medico['nome'], valor, '2026-10-05')]))
        producao = fixture(nome + '-producao.xlsx', [['Atendimento', 'Dt.'], [atendimento, '2026-10-05']])
        rel = admin.call('POST', '/api/base-hospitalar/producao', {**params, 'modoProducao': 'UNICO', 'medicoId': medico['id']}, files=producao)['relatorioId']
        versao = admin.call('GET', f'/api/conciliacoes/visitas/{rel}/acompanhamento')['versao']
        admin.call('POST', f'/api/conciliacoes/visitas/{rel}/liberacao', {'versao': versao, 'observacao': 'Publicacao QA exclusao', 'confirmaPendencias': True})
        caminho = f'/api/conciliacoes/visitas/{rel}/recebimentos'
        visita = admin.call('GET', caminho)['visitas'][0]
        admin.call('POST', f"{caminho}/visitas/{visita['linha']}", {'valor': 30, 'data': '2026-10-20', 'observacoes': 'Recebimento QA exclusao', 'versao': visita['versao']})
        return rel, params, producao

    def exclusao():
        medico = admin.call('POST', '/api/medicos', {'nome': 'QA Medico Exclusao', 'crm': '920001', 'uf': 'SP', 'ativo': True, 'aliases': [], 'versao': 0}, 201)
        rel_admin, params, producao = relatorio('QA Exclusao Admin ' + RUN_ID, medico, '64000001', 100)
        rel_operador, _, _ = relatorio('QA Exclusao Operador ' + RUN_ID, medico, '64000002', 80)
        portal = conta('MEDICO', 'medico', medicoId=medico['id'])
        operador = conta('OPERADOR', 'operador')
        assert portal.call('GET', f'/api/medico/relatorios/{rel_admin}')['id'] == rel_admin

        caminho = f'/api/conciliacoes/visitas/{rel_admin}/exclusao'
        admin.call('POST', caminho, {'senha': 'senha-incorreta'}, expected=400)
        admin.call('POST', caminho, {'senha': ''}, expected=400)
        admin.call('POST', caminho, {}, expected=400)
        admin.call('POST', caminho, {'senha': PASSWORD}, expected=403, csrf=False)
        admin.call('DELETE', f'/api/conciliacoes/visitas/{rel_admin}', expected=(404, 405))
        portal.call('POST', caminho, {'senha': PASSWORD}, expected=403)
        Client().call('POST', caminho, {'senha': PASSWORD}, expected=(401, 403))
        assert admin.call('GET', f'/api/conciliacoes/visitas/{rel_admin}')['visitas'], 'Tentativas recusadas nao podem apagar o relatorio'
        assert admin.call('GET', f'/api/conciliacoes/visitas/{rel_admin}/recebimentos')['visitas'][0]['recebido'] == 30

        apagado = admin.call('POST', caminho, {'senha': PASSWORD})
        assert apagado['relatorios'] == [rel_admin] and apagado['publicacoes'] == 1 and apagado['recebimentos'] == 1, apagado
        admin.call('GET', f'/api/conciliacoes/visitas/{rel_admin}', expected=404)
        portal.call('GET', f'/api/medico/relatorios/{rel_admin}', expected=404)
        assert all(item['id'] != rel_admin for item in admin.call('GET', '/api/conciliacoes/visitas'))
        admin.call('POST', caminho, {'senha': PASSWORD}, expected=404)
        # A mesma planilha volta a ser aceita e concilia a visita do hospital, que foi preservada.
        nova = admin.call('POST', '/api/base-hospitalar/producao', {**params, 'modoProducao': 'UNICO', 'medicoId': medico['id']}, files=producao)
        assert not nova['repetido'] and admin.call('GET', f"/api/conciliacoes/visitas/{nova['relatorioId']}")['visitas'][0]['status'] == 'PAGA'

        # Funcionario FaturaMed tambem exclui, sempre com a propria senha.
        operador.call('POST', f'/api/conciliacoes/visitas/{rel_operador}/exclusao', {'senha': PASSWORD})
        admin.call('GET', f'/api/conciliacoes/visitas/{rel_operador}', expected=404)

        # Cinco senhas erradas bloqueiam a conta por 15 minutos, como no login.
        bloqueado = conta('OPERADOR', 'bloqueio')
        alvo = f"/api/conciliacoes/visitas/{nova['relatorioId']}/exclusao"
        for _ in range(5):
            bloqueado.call('POST', alvo, {'senha': 'errada'}, expected=400)
        bloqueado.call('POST', alvo, {'senha': PASSWORD}, expected=(401, 403))
        assert admin.call('GET', f"/api/conciliacoes/visitas/{nova['relatorioId']}")['visitas']
        return {'senha_incorreta_ou_vazia': 400, 'sem_csrf': 403, 'rota_antiga_sem_senha': 'indisponivel', 'medico': 403,
                'anonimo': 'negado', 'cascata_publicacao_e_recebimento': True, 'portal_medico_404': True,
                'reimportacao_da_mesma_planilha': True, 'operador_exclui_com_senha': True, 'bloqueio_apos_5_erros': True}

    case('AUTO45', 'Exclusao de relatorio exige senha, apaga a cadeia e permite reimportar', exclusao)
