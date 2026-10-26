from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
import io
import hashlib


def executar(env, admin, alfa, beta):
    case, fixture, save = (env[key] for key in ['case', 'fixture', 'save'])
    HEADERS, hospital_row = env['HEADERS'], env['hospital_row']
    hoje = datetime.now(ZoneInfo('America/Sao_Paulo')).date()
    contexto = {}

    def montar(nome, hospital_rows, production_rows, competence='2026/07', mode='VARIOS', doctor=None):
        hospital = admin.call('POST', '/api/hospitais', {'nome': nome, 'documento': '', 'cidade': 'Teste', 'uf': 'SP', 'ativo': True, 'versao': 0}, 201)['id']
        params = {'hospitalId': hospital, 'competencia': competence}
        file = fixture(nome + '-hospital.xlsx', [HEADERS, *hospital_rows])
        imported = admin.call('POST', '/api/base-hospitalar/hospital', params, files=file)
        original = admin.call('GET', f"/api/base-hospitalar/arquivos/{imported['arquivoId']}/original")
        assert original == file[1]
        production = fixture(nome + '-producao.xlsx', [['Atendimento', 'Dt.', 'Medico'], *production_rows])
        result = admin.call('POST', '/api/base-hospitalar/producao', {**params, 'modoProducao': mode, **({'medicoId': doctor} if doctor else {})}, files=production)
        return hospital, result['relatorioId'], params, file, production

    def preparar_pagamentos():
        h, r, params, file, production = montar('QA lote e comprovantes',
            [hospital_row('77000001', alfa['nome'], 123.27), hospital_row('77000002', alfa['nome'], 134.10), hospital_row('77000003', beta['nome'], 120)],
            [['77000001', '2026-06-10', alfa['nome']], ['77000002', '2026-06-10', alfa['nome']], ['77000003', '2026-06-10', beta['nome']]])
        contexto.update(hospital=h, report=r, params=params, file=file, production=production)
        contexto['path'] = f'/api/conciliacoes/visitas/{r}/recebimentos'
        visits = admin.call('GET', contexto['path'])['visitas']
        assert len(visits) == 3 and sum(v['faturado'] for v in visits) == 377.37
        return {'visitas': 3, 'medicos_distintos': 2, 'original_download_identico': True}

    case('AUTO11', 'Visitas e recebimentos de medicos distintos permanecem separados', preparar_pagamentos)
    path = contexto['path']

    def lote(valor=150, referencia='QA-LOTE-001', linhas=((2, 100), (3, 50))):
        state = admin.call('GET', path)
        byline = {v['linha']: v for v in state['visitas']}
        return {'medicoId': alfa['id'], 'data': '2026-07-20', 'valorTotal': valor, 'referencia': referencia, 'observacoes': 'Teste ficticio automatico', 'versaoConciliacao': state['versaoConciliacao'], 'visitas': [{'linha': line, 'versao': byline[line]['versao'], 'valor': amount} for line, amount in linhas]}

    def invalidos_lote():
        before = admin.call('GET', path)
        for payload in [lote(100, linhas=((2, 20), (3, 50))), lote(180, linhas=((2, 130), (3, 50))), lote(10, linhas=()), lote(20, linhas=((2, 10), (2, 10))), lote(20, linhas=((2, 10), (4, 10)))]:
            admin.call('POST', path + '/pagamentos', payload, expected=(400, 409))
        assert before == admin.call('GET', path)
        return {'rejeitados': ['soma divergente', 'acima do saldo', 'selecao vazia', 'linha duplicada', 'medicos misturados'], 'nenhum_valor_salvo': True}

    case('AUTO12', 'Lotes invalidos nao gravam parcialmente nem misturam medicos', invalidos_lote)

    def parcial():
        payment = admin.call('POST', path + '/pagamentos', lote())
        contexto['payment'] = payment['pagamentoId']
        visits = {v['linha']: v for v in admin.call('GET', path)['visitas']}
        assert (visits[2]['recebido'], visits[2]['saldo']) == (100, 23.27)
        assert (visits[3]['recebido'], visits[3]['saldo']) == (50, 84.10)
        assert (visits[4]['recebido'], visits[4]['saldo']) == (0, 120)
        return {'pagamento': payment, 'saldos': [23.27, 84.10, 120], 'outro_medico_intacto': True}

    case('AUTO13', 'Pagamento parcial distribuido e valores por visita', parcial)

    def referencia_duplicada():
        before = admin.call('GET', path)
        admin.call('POST', path + '/pagamentos', lote(30, linhas=((2, 10), (3, 20))), expected=409)
        assert before == admin.call('GET', path)
        return {'referencia_repetida': 'QA-LOTE-001', 'bloqueio': 409, 'saldo_intacto': True}

    case('AUTO14', 'Referencia de pagamento repetida bloqueada', referencia_duplicada)

    def estorno():
        v = admin.call('GET', path)['visitas'][0]
        event = v['historico'][0]['id']
        url = path + f'/visitas/2/{event}/estorno'
        admin.call('POST', url, {'justificativa': '', 'versao': v['versao']}, expected=400)
        after = admin.call('POST', url, {'justificativa': 'Estorno ficticio isolado da primeira alocacao', 'versao': v['versao']})
        visits = {item['linha']: item for item in after['visitas']}
        assert (visits[2]['recebido'], visits[2]['saldo']) == (0, 123.27)
        assert visits[3]['recebido'] == 50
        admin.call('POST', url, {'justificativa': 'Tentativa ficticia de estorno repetido', 'versao': visits[2]['versao']}, expected=(400, 409))
        assert any(event['estornado'] for event in visits[2]['historico'])
        return {'justificativa_obrigatoria': True, 'linha_estornada': 2, 'outra_alocacao_preservada': 50, 'estorno_repetido_bloqueado': True}

    case('AUTO15', 'Estorno isolado exige justificativa e preserva historico', estorno)

    def filtros_pagamento():
        query = path + '/pagamentos?referencia=QA-LOTE-001'
        result = admin.call('GET', query + '&situacao=PARCIALMENTE_ESTORNADO')
        assert len(result['pagamentos']) == 1
        assert (result['valorOriginal'], result['estornos'], result['liquido']) == (150, 100, 50)
        for suffix in ['&situacao=ESTORNADO', '&de=2026-07-19&ate=2026-07-19']:
            assert admin.call('GET', query + suffix)['pagamentos'] == []
        assert len(admin.call('GET', query + '&de=2026-07-20&ate=2026-07-20')['pagamentos']) == 1
        admin.call('GET', query + '&de=2026-07-21&ate=2026-07-20', expected=400)
        assert len(admin.call('GET', path + '/pagamentos')['pagamentos']) == 1
        return {'original': 150, 'estornos': 100, 'liquido': 50, 'referencia_situacao_datas_limpeza': True}

    case('AUTO16', 'Busca por referencia situacao datas e limpeza de filtros', filtros_pagamento)

    def concorrencia_financeira():
        state = admin.call('GET', path)
        v = next(item for item in state['visitas'] if item['linha'] == 3)
        payload = {'valor': 10, 'data': '2026-07-20', 'observacoes': 'Concorrencia ficticia de recebimento', 'versao': v['versao']}
        admin.call('POST', path + '/visitas/3', payload)
        admin.call('POST', path + '/visitas/3', payload, expected=409)
        after = next(v for v in admin.call('GET', path)['visitas'] if v['linha'] == 3)
        assert (after['recebido'], after['saldo']) == (60, 74.10)
        admin.call('POST', path + '/visitas/3', {**payload, 'valor': 74.10, 'versao': after['versao']})
        final = next(v for v in admin.call('GET', path)['visitas'] if v['linha'] == 3)
        assert final['saldo'] == 0 and final['recebido'] == 134.10 and not final['podeRegistrar']
        admin.call('POST', path + '/visitas/3', {**payload, 'valor': 1, 'versao': final['versao']}, expected=(400, 409))
        return {'versao_obsoleta': 409, 'quitacao': 134.10, 'saldo': 0, 'nova_cobranca_bloqueada': True}

    case('AUTO17', 'Recebimento obsoleto bloqueado e quitacao integral limita novas gravacoes', concorrencia_financeira)

    def comprovantes():
        from pypdf import PdfWriter
        buffer = io.BytesIO()
        writer = PdfWriter()
        writer.add_blank_page(width=200, height=200)
        writer.write(buffer)
        content = buffer.getvalue()
        url = path + f"/pagamentos/{contexto['payment']}/comprovantes"
        added = admin.call('POST', url, {}, files=('comprovante-ficticio.pdf', content))
        file = added['comprovantes'][0]
        downloaded = admin.call('GET', url + '/' + str(file['id']))
        assert downloaded == content and hashlib.sha256(content).hexdigest() == file['sha256']
        save('exportacoes/comprovante-ficticio.pdf', downloaded)
        admin.call('POST', url, {}, expected=409, files=('renomeado.pdf', content))
        for filename, data in [('proibido.xlsx', content), ('disfarcado.pdf', b'Nao e um PDF'), ('grande.pdf', b'%PDF-' + b'X' * (5 * 1024 * 1024)), ('vazio.pdf', b'')]:
            admin.call('POST', url, {}, expected=400, files=(filename, data))
        admin.call('POST', url + f"/{file['id']}/retirada", {'justificativa': ''}, expected=400)
        removed = admin.call('POST', url + f"/{file['id']}/retirada", {'justificativa': 'Retirada ficticia apos validar upload e download'})
        assert removed['comprovantes'][0]['retiradoEm'] is not None
        admin.call('GET', url + '/' + str(file['id']), expected=404)
        return {'download_identico_sha256': file['sha256'], 'duplicado_renomeado_bloqueado': True, 'formato_conteudo_tamanho_vazio_rejeitados': True, 'retirada_auditada_download_bloqueado': True}

    case('AUTO18', 'Comprovante upload download duplicidade limites e retirada', comprovantes)

    def datas_e_publicacao():
        state = admin.call('GET', path)
        v = state['visitas'][0]
        admin.call('POST', path + '/visitas/2', {'valor': 10, 'data': str(hoje + timedelta(days=1)), 'observacoes': 'Teste ficticio de data futura', 'versao': v['versao']}, expected=400)
        report = contexto['report']
        pdf = f"/api/conciliacoes/visitas/{report}/demonstrativo.pdf?medicoId={alfa['id']}"
        admin.call('GET', pdf, expected=404)
        version = admin.call('GET', f'/api/conciliacoes/visitas/{report}/acompanhamento')['versao']
        admin.call('POST', f'/api/conciliacoes/visitas/{report}/liberacao', {'versao': version, 'observacao': 'Publicacao ficticia para exportar', 'confirmaPendencias': True})
        for query in ['&visitaDe=2026-07-02&visitaAte=2026-07-01', '&recebimentoDe=2026-07-21&recebimentoAte=2026-07-20']:
            admin.call('GET', pdf + query, expected=400)
        save('exportacoes/demonstrativo-lote.pdf', admin.call('GET', pdf))
        return {'data_futura_rejeitada': True, 'pdf_nao_publicado_rejeitado': True, 'publicacao_permite_pdf': True, 'periodos_invertidos_rejeitados': True}

    case('AUTO19', 'Data futura e publicacao obrigatoria para PDF', datas_e_publicacao)

    def preparar_cobranca():
        h, r, params, file, production = montar('QA cobrancas ficticias', [hospital_row('66000000', alfa['nome'])],
            [['66000001', '2026-06-10', ''], ['66000002', '2026-06-10', ''], ['66000003', '2026-06-10', '']])
        contexto.update(cobranca_hospital=h, cobranca_report=r, cobranca_params=params)
        queue = admin.call('GET', f'/api/conciliacoes/visitas/{r}/cobranca')
        assert len(queue['visitas']) == 3
        return {'visitas_sem_hospital': 3, 'nomes_nao_inferidos': True}

    case('AUTO20', 'Producao sem correspondente hospitalar permanece na fila', preparar_cobranca)
    url = f"/api/conciliacoes/visitas/{contexto['cobranca_report']}/cobranca"

    def lote_cobranca():
        visits = [{'linha': 2, 'versao': 0}, {'linha': 3, 'versao': 0}]
        payload = {'visitas': visits, 'situacao': 'AGUARDANDO_RETORNO', 'dataCobranca': str(hoje - timedelta(days=2)), 'proximoContato': str(hoje + timedelta(days=1)), 'resposta': '', 'observacoes': 'Teste ficticio de cobranca em lote'}
        assert admin.call('POST', url + '/lote', payload)['registradas'] == 2
        for line in [2, 3]:
            state = admin.call('GET', url + f'/visitas/{line}')
            assert len(state['historico']) == 1 and state['atual']['observacoes'] == payload['observacoes']
        admin.call('POST', url + '/lote', payload, expected=409)
        for filter, count in [('NAO_COBRADAS', 1), ('AGUARDANDO_RETORNO', 2)]:
            assert len(admin.call('GET', url + '?prioridade=' + filter)['visitas']) == count
        return {'historico_independente': True, 'nao_cobradas': 1, 'aguardando_retorno': 2, 'lote_obsoleto_bloqueado': True}

    case('AUTO21', 'Cobranca em lote audita por visita e rejeita repeticao obsoleta', lote_cobranca)

    def prioridades():
        for line, contact in [(2, hoje), (3, hoje - timedelta(days=1))]:
            state = admin.call('GET', url + f'/visitas/{line}')
            current = state['atual']
            admin.call('POST', url + f'/visitas/{line}', {**current, 'proximoContato': str(contact)})
        for filter, line in [('HOJE', 2), ('ATRASADAS', 3)]:
            state = admin.call('GET', url + '?prioridade=' + filter)
            assert [v['linha'] for v in state['visitas']] == [line]
            save(f'exportacoes/cobranca-{filter.lower()}.xlsx', admin.call('GET', url.replace('/cobranca', '/cobranca.xlsx') + '?prioridade=' + filter))
        return {'hoje': [2], 'atrasadas': [3], 'exportacoes_filtradas': True}

    case('AUTO22', 'Prioridades hoje atrasadas e exportacoes filtradas', prioridades)

    def retorno():
        state = admin.call('GET', url + '/visitas/2')
        payload = {**state['atual'], 'situacao': 'RETORNO_RECEBIDO', 'resposta': ''}
        admin.call('POST', url + '/visitas/2', payload, expected=400)
        payload.update(resposta='Hospital ficticio informou que verificara a visita', proximoContato=str(hoje + timedelta(days=1)))
        after = admin.call('POST', url + '/visitas/2', payload)
        assert len(after['historico']) == 3
        assert any(v['linha'] == 2 for v in admin.call('GET', url)['visitas'])
        invalid = {**after['atual'], 'proximoContato': str(hoje - timedelta(days=3))}
        admin.call('POST', url + '/visitas/2', invalid, expected=400)
        return {'resposta_obrigatoria': True, 'retorno_nao_resolve_faturamento': True, 'historicos': 3, 'proximo_contato_anterior_bloqueado': True}

    case('AUTO23', 'Resposta do hospital preserva pendencia e valida datas', retorno)

    def complemento():
        params = contexto['cobranca_params']
        admin.call('POST', '/api/base-hospitalar/hospital', params, files=fixture('complemento-cobranca.xlsx', [HEADERS, hospital_row('66000001', alfa['nome'], 85)]))
        report = contexto['cobranca_report']
        version = admin.call('GET', f'/api/conciliacoes/visitas/{report}/acompanhamento')['versao']
        updated = admin.call('POST', f'/api/base-hospitalar/relatorios/{report}/atualizar?versao={version}')['id']
        newurl = f'/api/conciliacoes/visitas/{updated}/cobranca'
        assert [v['linha'] for v in admin.call('GET', newurl)['visitas']] == [3, 4]
        history = admin.call('GET', newurl + '/visitas/2')
        assert not history['podeEditar'] and len(history['historico']) == 3
        admin.call('POST', newurl + '/visitas/2', history['atual'], expected=409)
        admin.call('GET', url, expected=409)
        return {'linha_resolvida_fora_da_fila': 2, 'historico_somente_consulta': True, 'fila_historica_bloqueada': True}

    case('AUTO24', 'Complemento resolve visita e preserva cobranca fora da fila', complemento)

    def reenvio():
        before = admin.call('GET', f"/api/base-hospitalar/visitas?hospitalId={contexto['hospital']}")
        result = admin.call('POST', '/api/base-hospitalar/hospital', contexto['params'], files=contexto['file'])
        assert result['repetido']
        assert before == admin.call('GET', f"/api/base-hospitalar/visitas?hospitalId={contexto['hospital']}")
        for endpoint in ['hospital', 'producao']:
            params = {**contexto['params'], 'competencia': '2026/13', 'medicoId': alfa['id']}
            admin.call('POST', '/api/base-hospitalar/' + endpoint, params, expected=400, files=contexto['file'])
            params['competencia'] = '2026/07'
            admin.call('POST', '/api/base-hospitalar/' + endpoint, params, expected=400, files=('corrompida.xlsx', b'conteudo ficticio invalido'))
        return {'reenvio_identico_sem_duplicar': True, 'competencia_invalida_e_arquivo_corrompido_rejeitados_nos_dois_fluxos': True}

    case('AUTO25', 'Reenvio competencia invalida e arquivos corrompidos', reenvio)

    def duplicidade():
        h, r, params, file, production = montar('QA duplicidade paga', [hospital_row('55000001', alfa['nome'], 100, '2026-01-15')], [['55000001', '2026-01-15', alfa['nome']]], '2026/01', 'UNICO', alfa['id'])
        financial = f'/api/conciliacoes/visitas/{r}/recebimentos'
        paid = admin.call('POST', financial + '/visitas/2', {'valor': 100, 'data': '2026-01-31', 'observacoes': 'Quitacao ficticia anterior a reapresentacao', 'versao': 0})
        original = admin.call('GET', f'/api/base-hospitalar/visitas?hospitalId={h}')[0]['id']
        imported = admin.call('POST', '/api/base-hospitalar/hospital', {**params, 'competencia': '2026/02'}, files=file)
        assert imported['divergencias'] == 1
        occurrence = next(item for item in admin.call('GET', f'/api/base-hospitalar/visitas?hospitalId={h}') if item['id'] != original)
        decision = {'acao': 'DUPLICADO_PAGO_OUTRO_MES', 'anteriorId': original, 'justificativa': 'Mesma visita ficticia ja quitada em janeiro', 'evidencia': ''}
        admin.call('POST', f"/api/base-hospitalar/visitas/{occurrence['id']}/decisao", decision, expected=400)
        decision['evidencia'] = f'Visita original {original}, relatorio {r}, recebimento de 100 em 31/01/2026, saldo zero'
        admin.call('POST', f"/api/base-hospitalar/visitas/{occurrence['id']}/decisao", decision, expected=204)
        repeat = admin.call('POST', '/api/base-hospitalar/producao', {**params, 'competencia': '2026/02', 'medicoId': alfa['id']}, files=production)['relatorioId']
        visit = admin.call('GET', f'/api/conciliacoes/visitas/{repeat}')['visitas'][0]
        assert visit['status'] == 'DIVERGENTE' and visit['hospital'] is None
        assert 'outro mes' in visit['motivo'].lower()
        blocked = admin.call('GET', f'/api/conciliacoes/visitas/{repeat}/recebimentos')['visitas'][0]
        assert blocked['faturado'] is None and blocked['saldo'] is None and not blocked['podeRegistrar']
        admin.call('POST', f'/api/conciliacoes/visitas/{repeat}/recebimentos/visitas/2', {'valor': 100, 'data': '2026-02-20', 'observacoes': 'Tentativa ficticia duplicada', 'versao': blocked['versao']}, expected=(400, 409))
        assert paid == admin.call('GET', financial)
        assert admin.call('GET', f"/api/base-hospitalar/visitas/{occurrence['id']}/historico")
        return {'original': original, 'ocorrencia': occurrence['id'], 'evidencia_obrigatoria': True, 'relatorio_repetido': repeat, 'sem_novo_saldo': True, 'financeiro_original_intacto': True}

    case('AUTO26', 'Duplicado pago em outro mes excluido sem novo recebimento', duplicidade)

    def ambiguidades():
        h, r, params, file, production = montar('QA ambiguidade ficticia',
            [hospital_row('44000001', alfa['nome'], 100), hospital_row('44000001', beta['nome'], 120)],
            [['44000001', '2026-06-10', ''], ['44000001', '2026-06-10', '']])
        original = admin.call('GET', f'/api/conciliacoes/visitas/{r}')['visitas']
        assert all(v['status'] == 'DIVERGENTE' and v['hospital'] is None for v in original)
        detail = admin.call('GET', f'/api/conciliacoes/visitas/{r}/visitas/2/revisao')
        candidate = next(v for v in original[0]['candidatos'] if v['medico'] == alfa['nome'])
        admin.call('POST', f'/api/conciliacoes/visitas/{r}/visitas/2/revisao', {'acao': 'AJUSTAR_DADOS', 'linhaHospital': candidate['linha'], 'responsavel': 'QA', 'justificativa': 'Cenario ficticio linha dois corresponde a medica Alfa', 'versao': detail['versao'], 'ajuste': {'data': '2026-06-10', 'atendimento': '44000001', 'medico': alfa['nome'], 'medicoCadastroId': alfa['id'], 'procedimento': candidate['procedimento'], 'codigoProcedimento': candidate['codigo'], 'valorHospital': candidate['valorTotal'], 'repasse': candidate['repasse'], 'status': 'PAGA', 'motivo': 'Correspondencia ficticia confirmada'}})
        visits = admin.call('GET', f'/api/conciliacoes/visitas/{r}')['visitas']
        assert all(v['status'] == 'PAGA' for v in visits)
        assert {v['medicoCadastroId'] for v in visits} == {alfa['id'], beta['id']}, f"Cadastro medico nao propagado apos exclusividade: {[(v['linhaMedico'], v['status'], v['medicoCadastroId']) for v in visits]}"
        assert len({v['hospital']['linha'] for v in visits}) == 2
        return {'ambiguas_iniciais': 2, 'confirmacao_manual': 1, 'outra_resolvida_por_exclusao': True, 'medicos_separados': True}

    case('AUTO27', 'Duas visitas ambiguas resolvidas por revisao e exclusividade', ambiguidades)

    def inferencia():
        h, r, params, file, production = montar('QA inferencia unica', [hospital_row('33000001', alfa['nome'], 85)], [['33000001', '2026-06-10', '']])
        visit = admin.call('GET', f'/api/conciliacoes/visitas/{r}')['visitas'][0]
        assert visit['medicoCadastroId'] == alfa['id'] and visit['status'] == 'PAGA'
        assert visit['original']['medico'] == ''
        return {'nome_inferido_correspondencia_unica': True, 'celula_original_preservada_vazia': True, 'repasse': visit['repasse']}

    case('AUTO28', 'Producao coletiva sem nome usa somente correspondencia unica', inferencia)

    def portal_exportacoes():
        account = next(user for user in admin.call('GET', '/api/usuarios') if user['email'] == 'alfa.qa@example.com')
        admin.call('PUT', f"/api/usuarios/{account['id']}/situacao", {'ativo': True, 'versao': account['versao']})
        physician = env['Client']()
        physician.login('alfa.qa@example.com')
        report = contexto['report']
        base = f'/api/medico/relatorios/{report}'
        full = physician.call('GET', base + '/extrato')
        assert (full['resumo']['faturado'], full['resumo']['recebidoAcumulado'], full['resumo']['saldoAtual']) == (257.37, 134.10, 123.27)
        assert len(full['visitas']) == 2 and len(full['movimentos']) == 5
        filtered = physician.call('GET', base + '/extrato?recebimentoDe=2026-07-19&recebimentoAte=2026-07-19')
        assert filtered['movimentos'] == []
        assert filtered['resumo']['saldoAtual'] == full['resumo']['saldoAtual']
        empty = physician.call('GET', base + '/extrato?visitaDe=2026-12-01&visitaAte=2026-12-31')
        assert empty['visitas'] == [] and empty['movimentos'] == [] and empty['resumo']['faturado'] == 0
        physician.call('GET', base + '/extrato?visitaDe=2026-07-02&visitaAte=2026-07-01', expected=400)
        xlsx = physician.call('GET', base + '/extrato.xlsx')
        save('exportacoes/extrato-portal.xlsx', xlsx)
        import openpyxl
        workbook = openpyxl.load_workbook(io.BytesIO(xlsx), data_only=True)
        assert {'Resumo', 'Visitas', 'Movimentos'}.issubset(set(workbook.sheetnames))
        assert not any(beta['nome'] in str(c.value) for sheet in workbook for row in sheet for c in row)
        from pypdf import PdfReader
        portal_pdf = physician.call('GET', base + '/demonstrativo.pdf')
        admin_pdf = admin.call('GET', f'/api/conciliacoes/visitas/{report}/demonstrativo.pdf?medicoId=' + str(alfa['id']))
        def extract(raw):
            return '\n'.join(p.extract_text() for p in PdfReader(io.BytesIO(raw)).pages)
        for raw in [portal_pdf, admin_pdf]:
            text = extract(raw)
            assert all(value in text for value in ['257,37', '134,10', '123,27']) and beta['nome'] not in text
        save('exportacoes/demonstrativo-portal.pdf', portal_pdf)
        physician.call('POST', '/api/auth/logout', expected=204)
        physician.call('GET', base + '/demonstrativo.pdf', expected=401)
        return {'faturado': 257.37, 'recebido_liquido': 134.10, 'saldo': 123.27, 'movimentos': 5, 'periodo_vazio_nao_apaga_saldo': True, 'pdf_admin_portal_totais_equivalentes': True, 'xlsx_tres_abas': True, 'logout_revoga_exportacao': True}

    case('AUTO29', 'Portal extrato PDF XLSX filtros e logout', portal_exportacoes)
