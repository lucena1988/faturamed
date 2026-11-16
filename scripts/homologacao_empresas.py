"""Cenarios HTTP de empresas clientes e do painel da empresa; executados somente na instancia QA existente."""


def executar(env, admin):
    case, Client, fixture = env['case'], env['Client'], env['fixture']
    HEADERS, hospital_row, PASSWORD, RUN_ID = env['HEADERS'], env['hospital_row'], env['PASSWORD'], env['RUN_ID']
    ctx = {}

    def empresa(nome, documento='', ativo=True):
        return admin.call('POST', '/api/empresas-clientes', {'nome': nome, 'documento': documento, 'ativo': ativo, 'versao': 0}, 201)

    def medico(nome, crm, empresas=None):
        payload = {'nome': nome, 'crm': crm, 'uf': 'SP', 'ativo': True, 'aliases': [], 'versao': 0, 'email': f'crm{crm}@example.com'}
        if empresas is not None:
            payload['empresas'] = empresas
        return admin.call('POST', '/api/medicos', payload, 201)

    def atualizar_medico(m, **campos):
        payload = {'nome': m['nome'], 'crm': m['crm'], 'uf': m['uf'], 'ativo': m['ativo'], 'aliases': m['aliases'], 'versao': m['versao'], **campos}
        return admin.call('PUT', f"/api/medicos/{m['id']}", payload)

    def hospital(nome):
        return admin.call('POST', '/api/hospitais', {'nome': nome, 'documento': '', 'cidade': 'Teste', 'uf': 'SP', 'ativo': True, 'versao': 0}, 201)['id']

    def importar_hospital(h, linhas, nome, modo='NOVO'):
        return admin.call('POST', '/api/base-hospitalar/hospital', {'hospitalId': h, 'competencia': '2026/08', 'modoHospital': modo}, files=fixture(nome, [HEADERS, *linhas]))

    def producao(h, linhas, nome, medico_id=None, empresa_id=None, modo='UNICO', expected=200):
        params = {'hospitalId': h, 'competencia': '2026/08', 'modoProducao': modo}
        if medico_id:
            params['medicoId'] = medico_id
        if empresa_id:
            params['empresaId'] = empresa_id
        return admin.call('POST', '/api/base-hospitalar/producao', params, files=fixture(nome, [['Atendimento', 'Dt.', 'Medico'], *linhas]), expected=expected)

    def publicar(relatorio):
        versao = admin.call('GET', f'/api/conciliacoes/visitas/{relatorio}/acompanhamento')['versao']
        return admin.call('POST', f'/api/conciliacoes/visitas/{relatorio}/liberacao', {'versao': versao, 'observacao': 'Publicacao QA empresa', 'confirmaPendencias': True})

    def atribuida(relatorio):
        return admin.call('GET', f'/api/empresas-clientes/conciliacoes/{relatorio}')['empresaId']

    def relatorios():
        return admin.call('GET', '/api/conciliacoes/visitas')

    def cadastro():
        clinica = empresa('QA Clinica Vida ' + RUN_ID, '11.222.333/0001-44')
        grupo = empresa('QA Grupo Saude ' + RUN_ID)
        # O CNPJ e comparado normalizado: outra mascara do mesmo numero e duplicidade.
        admin.call('POST', '/api/empresas-clientes', {'nome': 'QA duplicada', 'documento': '11222333000144', 'ativo': True, 'versao': 0}, expected=409)
        admin.call('POST', '/api/empresas-clientes', {'nome': '   ', 'documento': '', 'ativo': True, 'versao': 0}, expected=400)
        admin.call('POST', '/api/empresas-clientes', {'nome': 'QA versao invalida', 'documento': '', 'ativo': True, 'versao': 3}, expected=400)
        editada = admin.call('PUT', f"/api/empresas-clientes/{grupo['id']}", {'nome': grupo['nome'], 'documento': '', 'ativo': True, 'versao': grupo['versao']})
        admin.call('PUT', f"/api/empresas-clientes/{grupo['id']}", {'nome': 'QA concorrente', 'documento': '', 'ativo': True, 'versao': grupo['versao']}, expected=409)
        admin.call('PUT', '/api/empresas-clientes/999999', {'nome': 'QA inexistente', 'documento': '', 'ativo': True, 'versao': 0}, expected=404)
        inativa = empresa('QA Empresa Inativa ' + RUN_ID, ativo=False)
        nomes = [e['nome'] for e in admin.call('GET', '/api/empresas-clientes')]
        assert 'QA duplicada' not in nomes and 'QA concorrente' not in nomes
        ctx.update(clinica=clinica['id'], grupo=grupo['id'], inativa=inativa['id'])
        return {'criadas': 3, 'cnpj_normalizado_duplicado': 409, 'nome_vazio': 400, 'versao_obsoleta': 409, 'inexistente': 404, 'versao_apos_edicao': editada['versao']}

    case('AUTO37', 'Empresas: cadastro, CNPJ duplicado normalizado e concorrencia', cadastro)

    def vinculos():
        clinica, grupo = ctx['clinica'], ctx['grupo']
        ana = medico('QA Ana Empresa', '910001', [clinica, grupo])
        bruno = medico('QA Bruno Empresa', '910002', [clinica])
        carla = medico('QA Carla Empresa', '910003', [grupo])
        sem = medico('QA Davi Sem Empresa', '910004')
        assert sorted(ana['empresas']) == sorted([clinica, grupo]) and sem['empresas'] == []
        admin.call('PUT', f"/api/medicos/{sem['id']}", {'nome': sem['nome'], 'crm': sem['crm'], 'uf': 'SP', 'ativo': True, 'aliases': [], 'versao': sem['versao'], 'empresas': [999999]}, expected=400)
        assert next(m for m in admin.call('GET', '/api/medicos') if m['id'] == sem['id'])['empresas'] == []
        # Cliente antigo (sem o campo empresas) nao apaga vinculos existentes.
        ana = atualizar_medico(ana, aliases=['Dra QA Ana'])
        assert sorted(ana['empresas']) == sorted([clinica, grupo])
        contagem = {e['id']: e['medicos'] for e in admin.call('GET', '/api/empresas-clientes')}
        assert (contagem[clinica], contagem[grupo]) == (2, 2)
        ctx.update(ana=ana, bruno=bruno, carla=carla, sem=sem)
        return {'medico_em_duas_empresas': True, 'empresa_inexistente_rejeitada': 400, 'sem_campo_preserva_vinculos': True, 'contagem_por_empresa': [2, 2]}

    case('AUTO38', 'Empresas: vinculos medico-empresa e compatibilidade de clientes antigos', vinculos)

    def importacao():
        clinica, grupo, inativa = ctx['clinica'], ctx['grupo'], ctx['inativa']
        ana, bruno, carla = ctx['ana'], ctx['bruno'], ctx['carla']
        h = hospital('QA Hospital Empresas ' + RUN_ID)
        importar_hospital(h, [hospital_row('61000001', ana['nome'], 120, '2026-08-05'), hospital_row('61000002', bruno['nome'], 90, '2026-08-06'),
                              hospital_row('61000003', carla['nome'], 70, '2026-08-07'), hospital_row('61000004', ana['nome'], 130, '2026-09-02')], 'empresas-hospital.xlsx')
        antes = len(relatorios())
        producao(h, [['61000001', '2026-08-05', ana['nome']]], 'ana-sem-empresa.xlsx', ana['id'], expected=400)
        producao(h, [['61000002', '2026-08-06', bruno['nome']]], 'bruno-empresa-errada.xlsx', bruno['id'], grupo, expected=400)
        producao(h, [['61000001', '2026-08-05', ana['nome']]], 'ana-empresa-inativa.xlsx', ana['id'], inativa, expected=400)
        producao(h, [['61000001', '2026-08-05', ana['nome']]], 'ana-empresa-inexistente.xlsx', ana['id'], 999999, expected=(400, 404))
        assert len(relatorios()) == antes, 'Importacao rejeitada nao pode criar relatorio'
        # Ana: duas visitas faturadas e uma sem correspondencia hospitalar (pendente).
        ana_clinica = producao(h, [['61000001', '2026-08-05', ana['nome']], ['61000004', '2026-09-02', ana['nome']], ['61009999', '2026-08-20', ana['nome']]], 'ana-clinica.xlsx', ana['id'], clinica)['relatorioId']
        bruno_auto = producao(h, [['61000002', '2026-08-06', bruno['nome']]], 'bruno-auto.xlsx', bruno['id'])['relatorioId']
        carla_auto = producao(h, [['61000003', '2026-08-07', carla['nome']]], 'carla-auto.xlsx', carla['id'])['relatorioId']
        assert (atribuida(ana_clinica), atribuida(bruno_auto), atribuida(carla_auto)) == (clinica, clinica, grupo)
        h2 = hospital('QA Hospital Coletivo Empresas ' + RUN_ID)
        importar_hospital(h2, [hospital_row('62000001', ana['nome'], 200, '2026-08-10'), hospital_row('62000002', carla['nome'], 50, '2026-08-11')], 'coletivo-hospital.xlsx')
        coletivo = producao(h2, [['62000001', '2026-08-10', ana['nome']], ['62000002', '2026-08-11', carla['nome']]], 'coletivo-grupo.xlsx', empresa_id=grupo, modo='VARIOS')['relatorioId']
        assert atribuida(coletivo) == grupo
        h3 = hospital('QA Hospital Sem Empresa ' + RUN_ID)
        importar_hospital(h3, [hospital_row('63000001', ctx['sem']['nome'], 60, '2026-08-12')], 'sem-empresa-hospital.xlsx')
        sem = producao(h3, [['63000001', '2026-08-12', ctx['sem']['nome']]], 'sem-empresa.xlsx', ctx['sem']['id'])['relatorioId']
        assert atribuida(sem) is None
        ctx.update(hospital=h, hospital_coletivo=h2, ana_clinica=ana_clinica, rel_bruno=bruno_auto, rel_carla=carla_auto, coletivo=coletivo, sem_empresa=sem)
        return {'varias_empresas_exige_escolha': 400, 'empresa_que_nao_e_do_medico': 400, 'empresa_inativa': 400, 'rejeicoes_sem_gravacao': True,
                'unica_empresa_automatica': True, 'coletiva_com_empresa': True, 'medico_sem_empresa_fica_sem': True}

    case('AUTO39', 'Empresas: atribuicao da producao na importacao e rejeicoes sem gravacao', importacao)

    def publicacao():
        for chave in ['ana_clinica', 'rel_bruno', 'rel_carla', 'coletivo', 'sem_empresa']:
            publicar(ctx[chave])
        # Nova versao da producao (hospital atualizado) herda a empresa e substitui a publicacao anterior.
        ana = ctx['ana']
        importar_hospital(ctx['hospital'], [hospital_row('61000001', ana['nome'], 150, '2026-08-05')], 'empresas-hospital-atualizado.xlsx', 'ATUALIZAR')
        versao = admin.call('GET', f"/api/conciliacoes/visitas/{ctx['ana_clinica']}/acompanhamento")['versao']
        nova = admin.call('POST', f"/api/base-hospitalar/relatorios/{ctx['ana_clinica']}/atualizar?versao={versao}")['id']
        assert atribuida(nova) == ctx['clinica']
        publicar(nova)
        ctx['ana_clinica_v2'] = nova
        lista = {r['id']: r for r in relatorios()}
        assert lista[nova]['empresa_id'] == ctx['clinica']
        return {'publicacoes': 6, 'nova_versao': nova, 'nova_versao_herda_empresa': True}

    case('AUTO40', 'Empresas: publicacao e nova versao herdam a empresa da producao', publicacao)

    contas = {}

    def conta(nome, empresa_id):
        email = f'empresa-{nome}-{RUN_ID}@example.com'
        criada = admin.call('POST', '/api/usuarios', {'nome': 'QA ' + nome, 'email': email, 'senha': PASSWORD, 'perfil': 'EMPRESA', 'empresaId': empresa_id}, 201, log=False)
        client = Client()
        client.login(email)
        contas[nome] = (client, criada, email)
        return client

    def painel():
        clinica = conta('clinica', ctx['clinica'])
        grupo = conta('grupo', ctx['grupo'])
        ana, bruno, carla = ctx['ana'], ctx['bruno'], ctx['carla']
        geral = clinica.call('GET', '/api/empresa/painel')
        medicos = {v['medicoId'] for v in geral['visitas']}
        assert medicos == {ana['id'], bruno['id']}, medicos
        assert {m['id'] for m in geral['medicos']} == {ana['id'], bruno['id']}
        r = geral['resumo']
        # Ana: 150 (versao atualizada) + 130 faturadas e 1 pendente; Bruno: 90. Publicacao anterior nao duplica.
        assert (r['visitas'], r['pagas'], r['pendentes']) == (4, 3, 1), r
        assert r['faturado'] == 370, r
        assert len(geral['porMedico']) == 2 and geral['porMedico'][0]['chave'] == str(ana['id'])
        assert all(p['relatorioId'] != ctx['ana_clinica'] for p in geral['publicacoes'])
        so_ana = clinica.call('GET', f"/api/empresa/painel?medicoId={ana['id']}")
        assert {v['medicoId'] for v in so_ana['visitas']} == {ana['id']} and so_ana['resumo']['faturado'] == 280
        agosto = clinica.call('GET', '/api/empresa/painel?de=2026-08-01&ate=2026-08-31')
        assert agosto['resumo']['faturado'] == 240 and [g['chave'] for g in agosto['porMes']] == ['2026-08']
        vazio = clinica.call('GET', '/api/empresa/painel?hospital=' + 'Hospital%20inexistente')
        assert vazio['visitas'] == [] and vazio['resumo']['faturado'] == 0
        clinica.call('GET', f"/api/empresa/painel?medicoId={carla['id']}", expected=400)
        clinica.call('GET', '/api/empresa/painel?de=data-invalida', expected=400)
        # Mesmo medico em duas empresas: a producao atribuida a Clinica nao aparece no Grupo.
        do_grupo = grupo.call('GET', '/api/empresa/painel')
        assert {v['medicoId'] for v in do_grupo['visitas']} == {ana['id'], carla['id']}
        assert do_grupo['resumo']['faturado'] == 320, do_grupo['resumo']
        # Recebimento registrado pela FaturaMed reflete no painel da empresa.
        caminho = f"/api/conciliacoes/visitas/{ctx['ana_clinica_v2']}/recebimentos"
        visita = next(v for v in admin.call('GET', caminho)['visitas'] if v['faturado'] == 150)
        admin.call('POST', f"{caminho}/visitas/{visita['linha']}", {'valor': 100, 'data': '2026-09-10', 'observacoes': 'Recebimento QA empresa', 'versao': visita['versao']})
        depois = clinica.call('GET', '/api/empresa/painel')['resumo']
        assert (depois['recebido'], depois['saldo']) == (100, 270), depois
        return {'faturado_clinica': 370, 'faturado_grupo': 320, 'filtro_medico': 280, 'filtro_periodo': 240, 'medico_de_outra_empresa': 400,
                'mesmo_medico_isolado_por_empresa': True, 'publicacao_vigente_sem_duplicar': True, 'recebido': 100, 'saldo': 270}

    case('AUTO41', 'Empresas: painel consolidado, filtros e isolamento entre empresas', painel)

    def seguranca():
        clinica, conta_clinica, email = contas['clinica']
        admin.call('POST', '/api/usuarios', {'nome': 'QA sem empresa', 'email': f'sem-empresa-{RUN_ID}@example.com', 'senha': PASSWORD, 'perfil': 'EMPRESA'}, expected=400, log=False)
        admin.call('POST', '/api/usuarios', {'nome': 'QA empresa inativa', 'email': f'inativa-{RUN_ID}@example.com', 'senha': PASSWORD, 'perfil': 'EMPRESA', 'empresaId': ctx['inativa']}, expected=400, log=False)
        admin.call('POST', '/api/usuarios', {'nome': 'QA misto', 'email': f'misto-{RUN_ID}@example.com', 'senha': PASSWORD, 'perfil': 'EMPRESA', 'empresaId': ctx['clinica'], 'medicoId': ctx['ana']['id']}, expected=400, log=False)
        admin.call('POST', '/api/usuarios', {'nome': 'QA operador com empresa', 'email': f'opemp-{RUN_ID}@example.com', 'senha': PASSWORD, 'perfil': 'OPERADOR', 'empresaId': ctx['clinica']}, expected=400, log=False)
        # Funcionario FaturaMed cria e administra acessos de empresa.
        op_email = f'operador-empresas-{RUN_ID}@example.com'
        admin.call('POST', '/api/usuarios', {'nome': 'QA Operador Empresas', 'email': op_email, 'senha': PASSWORD, 'perfil': 'OPERADOR'}, 201, log=False)
        operador = Client()
        operador.login(op_email)
        criada = operador.call('POST', '/api/usuarios', {'nome': 'QA criada por operador', 'email': f'empresa-op-{RUN_ID}@example.com', 'senha': PASSWORD, 'perfil': 'EMPRESA', 'empresaId': ctx['grupo']}, 201, log=False)
        assert any(u['id'] == criada['id'] for u in operador.call('GET', '/api/usuarios'))
        # A empresa so le o proprio painel; o parametro empresaId da URL e ignorado.
        proprio = clinica.call('GET', f"/api/empresa/painel?empresaId={ctx['grupo']}")
        assert proprio['empresaId'] == ctx['clinica']
        negados = {}
        for rota in ['/api/medicos', '/api/empresas-clientes', '/api/usuarios', '/api/conciliacoes/visitas', f"/api/medico/relatorios", '/api/chamados',
                     f"/api/base-hospitalar/visitas?hospitalId={ctx['hospital']}", f"/api/conciliacoes/visitas/{ctx['ana_clinica_v2']}/recebimentos"]:
            clinica.call('GET', rota, expected=403)
            negados[rota] = 403
        clinica.call('PUT', f"/api/empresas-clientes/conciliacoes/{ctx['rel_carla']}", {'empresaId': ctx['clinica']}, expected=403)
        assert atribuida(ctx['rel_carla']) == ctx['grupo']
        medico_email = f'medico-empresa-{RUN_ID}@example.com'
        admin.call('POST', '/api/usuarios', {'nome': 'QA medico', 'email': medico_email, 'senha': PASSWORD, 'perfil': 'MEDICO', 'medicoId': ctx['ana']['id']}, 201, log=False)
        medico = Client()
        medico.login(medico_email)
        medico.call('GET', '/api/empresa/painel', expected=403)
        Client().call('GET', '/api/empresa/painel', expected=401)
        clinica.call('POST', '/api/auth/logout', expected=204, csrf=True)
        clinica.call('GET', '/api/empresa/painel', expected=401)
        clinica.login(email)
        # Desativar a empresa encerra a sessao e impede novo login; reativar devolve o acesso.
        dados = next(e for e in admin.call('GET', '/api/empresas-clientes') if e['id'] == ctx['clinica'])
        dados = admin.call('PUT', f"/api/empresas-clientes/{ctx['clinica']}", {'nome': dados['nome'], 'documento': dados['documento'], 'ativo': False, 'versao': dados['versao']})
        clinica.call('GET', '/api/empresa/painel', expected=401)
        try:
            Client().login(email)
            raise AssertionError('Login de empresa inativa nao deveria ser aceito')
        except AssertionError:
            raise
        except Exception:
            pass
        admin.call('PUT', f"/api/empresas-clientes/{ctx['clinica']}", {'nome': dados['nome'], 'documento': dados['documento'], 'ativo': True, 'versao': dados['versao']})
        clinica = Client()
        clinica.login(email)
        conta_atual = next(u for u in admin.call('GET', '/api/usuarios') if u['id'] == conta_clinica['id'])
        admin.call('PUT', f"/api/usuarios/{conta_atual['id']}/situacao", {'ativo': False, 'versao': conta_atual['versao']})
        clinica.call('GET', '/api/empresa/painel', expected=401)
        conta_atual = next(u for u in admin.call('GET', '/api/usuarios') if u['id'] == conta_clinica['id'])
        admin.call('PUT', f"/api/usuarios/{conta_atual['id']}/situacao", {'ativo': True, 'versao': conta_atual['versao']})
        clinica = Client()
        clinica.login(email)
        contas['clinica'] = (clinica, conta_clinica, email)
        return {'vinculo_obrigatorio': 400, 'empresa_inativa_no_cadastro': 400, 'perfil_misto': 400, 'operador_cria_empresa': 201,
                'parametro_empresa_ignorado': True, 'rotas_negadas': negados, 'medico_no_painel_empresa': 403, 'anonimo': 401,
                'logout': 401, 'empresa_desativada_revoga_sessao_e_login': True, 'acesso_desativado_revoga_sessao': 401}

    case('AUTO42', 'Empresas: permissoes HTTP, vinculo obrigatorio e revogacao de sessao', seguranca)

    def reatribuicao():
        clinica = contas['clinica'][0]
        grupo = contas['grupo'][0]
        # Producao sem empresa (anterior a V24 ou medico sem vinculo) nao aparece em nenhum painel.
        sem = ctx['sem']
        assert all(v['medicoId'] != sem['id'] for v in grupo.call('GET', '/api/empresa/painel')['visitas'])
        admin.call('PUT', f"/api/empresas-clientes/conciliacoes/{ctx['sem_empresa']}", {'empresaId': ctx['grupo']}, expected=400)
        atualizar_medico(next(m for m in admin.call('GET', '/api/medicos') if m['id'] == sem['id']), empresas=[ctx['grupo']])
        admin.call('PUT', f"/api/empresas-clientes/conciliacoes/{ctx['sem_empresa']}", {'empresaId': ctx['grupo']}, expected=204)
        assert any(v['medicoId'] == sem['id'] for v in grupo.call('GET', '/api/empresa/painel')['visitas'])
        # Reatribuir a cadeia move todas as versoes e publicacoes da Ana para o Grupo.
        admin.call('PUT', f"/api/empresas-clientes/conciliacoes/{ctx['ana_clinica']}", {'empresaId': ctx['grupo']}, expected=204)
        assert atribuida(ctx['ana_clinica_v2']) == ctx['grupo']
        assert clinica.call('GET', f"/api/empresa/painel?medicoId={ctx['ana']['id']}")['visitas'] == []
        assert grupo.call('GET', '/api/empresa/painel')['resumo']['faturado'] == 320 + 280 + 60
        admin.call('PUT', f"/api/empresas-clientes/conciliacoes/{ctx['ana_clinica_v2']}", {'empresaId': None}, expected=204)
        assert atribuida(ctx['ana_clinica']) is None
        admin.call('PUT', f"/api/empresas-clientes/conciliacoes/{ctx['ana_clinica']}", {'empresaId': ctx['clinica']}, expected=204)
        admin.call('PUT', f"/api/empresas-clientes/conciliacoes/{ctx['rel_carla']}", {'empresaId': ctx['inativa']}, expected=400)
        admin.call('PUT', '/api/empresas-clientes/conciliacoes/999999', {'empresaId': ctx['clinica']}, expected=404)
        admin.call('GET', '/api/empresas-clientes/conciliacoes/999999', expected=404)
        assert clinica.call('GET', '/api/empresa/painel')['resumo']['faturado'] == 370
        return {'medico_fora_da_empresa': 400, 'sem_empresa_atribuida_depois': True, 'cadeia_inteira_reatribuida': True,
                'remocao_oculta_dos_paineis': True, 'empresa_inativa': 400, 'relatorio_inexistente': 404}

    case('AUTO43', 'Empresas: reatribuicao de conciliacoes antigas move toda a cadeia', reatribuicao)

    def desvinculo():
        clinica = contas['clinica'][0]
        bruno = next(m for m in admin.call('GET', '/api/medicos') if m['crm'] == '910002')
        atualizar_medico(bruno, empresas=[])
        painel = clinica.call('GET', '/api/empresa/painel')
        assert all(v['medicoId'] != bruno['id'] for v in painel['visitas']) and all(m['id'] != bruno['id'] for m in painel['medicos'])
        clinica.call('GET', f"/api/empresa/painel?medicoId={bruno['id']}", expected=400)
        bruno = next(m for m in admin.call('GET', '/api/medicos') if m['crm'] == '910002')
        atualizar_medico(bruno, empresas=[ctx['clinica']])
        assert any(v['medicoId'] == bruno['id'] for v in clinica.call('GET', '/api/empresa/painel')['visitas'])
        return {'desvinculado_some_do_painel': True, 'filtro_rejeitado': 400, 'revinculado_volta': True}

    case('AUTO44', 'Empresas: desvinculo de medico oculta historico e revinculo restaura', desvinculo)
