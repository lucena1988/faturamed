"""Cenarios HTTP do Service Desk; executados somente na instancia QA existente."""
import hashlib
import io
from datetime import datetime


def executar(env, admin):
    case, Client = env['case'], env['Client']
    users = {}

    def usuario(perfil):
        if perfil not in users:
            email = f'servicedesk-{perfil.lower()}-{env["RUN_ID"]}@example.com'
            conta = admin.call('POST', '/api/usuarios', {
                'nome': 'QA Service Desk ' + perfil, 'email': email,
                'senha': env['PASSWORD'], 'perfil': perfil}, 201, log=False)
            client = Client()
            client.login(email)
            users[perfil] = (client, conta)
        return users[perfil]

    def abrir(client=None, **campos):
        client = client or usuario('OPERADOR')[0]
        return client.call('POST', '/api/chamados', {
            'tipoCodigo': 'ERRO_BLOQUEANTE', 'titulo': 'QA Service Desk',
            'descricao': 'Problema ficticio para regressao', 'paginaOrigem': '/#dashboard',
            **campos}, 201, multipart=True)

    def mensagem(client, id, texto='Mensagem ficticia', **kwargs):
        return client.call('POST', f'/api/chamados/{id}/mensagens',
                           {'conteudo': texto}, multipart=True, **kwargs)

    def status(client, id, novo, **kwargs):
        return client.call('POST', f'/api/chamados/{id}/status', {'status': novo, **kwargs})

    def avisos(client, id):
        return [a for a in client.call('GET', '/api/chamados/notificacoes')['itens'] if a['chamadoId'] == id]

    def persistido(client, id):
        detalhe = client.call('GET', f'/api/chamados/{id}')
        # Estes campos sao calculados a cada consulta, nao persistidos.
        for campo in ['minutosRestantes', 'slaStatus']:
            detalhe['chamado'].pop(campo, None)
        return detalhe

    def abertura():
        funcionario, conta = usuario('OPERADOR')
        tipo = next(t for t in funcionario.call('GET', '/api/chamados/tipos') if t['codigo'] == 'ERRO_BLOQUEANTE')
        c = abrir()['chamado']
        assert c['status'] == 'ABERTO' and c['solicitanteId'] == conta['id']
        assert c['protocolo'].startswith('CH-') and c['paginaOrigem'] == '/#dashboard'
        assert c['filaDestino'] == tipo['filaPadrao'] and c['prioridade'] == tipo['prioridadePadrao']
        assert c['slaHoras'] == tipo['slaHoras']
        prazo = datetime.fromisoformat(c['prazoSlaEm'].replace('Z', '+00:00'))
        criado = datetime.fromisoformat(c['criadoEm'].replace('Z', '+00:00'))
        assert abs((prazo-criado).total_seconds() - tipo['slaHoras']*3600) < 1
        painel = funcionario.call('GET', '/api/chamados?meus=true&busca=' + c['protocolo'])
        assert [t['id'] for t in painel['chamados']] == [c['id']]
        return {'protocolo': c['protocolo'], 'sla_horas': tipo['slaHoras'], 'fila_automatica': c['filaDestino'], 'busca_e_meus': True}

    case('AUTO30', 'Service Desk: abertura, protocolo, categoria, SLA e busca', abertura)

    def ciclo():
        funcionario, conta = usuario('OPERADOR')
        tecnico, tech = usuario('DEV')
        id = abrir()['chamado']['id']
        c = status(tecnico, id, 'EM_ATENDIMENTO', assumir=True)['chamado']
        assert c['responsavelId'] == tech['id'] and c['primeiroAtendimentoEm']
        status(tecnico, id, 'AGUARDANDO_USUARIO')
        status(tecnico, id, 'AGUARDANDO_USUARIO')
        assert [a['tipo'] for a in avisos(funcionario, id)] == ['RETORNO_SOLICITADO']
        assert mensagem(funcionario, id)['chamado']['status'] == 'EM_ATENDIMENTO'
        assert [a['tipo'] for a in avisos(tecnico, id)] == ['RESPOSTA_SOLICITANTE']
        assert not avisos(admin, id), 'Resposta atribuida nao deve avisar outro tecnico'
        mensagem(tecnico, id)
        assert avisos(funcionario, id)[0]['tipo'] == 'MENSAGEM_SUPORTE'
        status(tecnico, id, 'RESOLVIDO')
        assert status(funcionario, id, 'EM_ATENDIMENTO')['chamado']['resolvidoEm'] is None
        status(tecnico, id, 'RESOLVIDO')
        c = funcionario.call('POST', f'/api/chamados/{id}/avaliacao', {'nota': 5, 'comentario': 'QA concluido'})['chamado']
        assert c['status'] == 'FECHADO' and c['notaAvaliacao'] == 5 and c['fechadoEm']
        antes = persistido(funcionario, id)
        mensagem(funcionario, id, expected=409)
        assert persistido(funcionario, id) == antes
        return {'id': id, 'retorno_sem_duplicacao': True, 'resposta_ao_responsavel': True, 'reabertura_avaliacao_encerramento': True}

    case('AUTO31', 'Service Desk: atendimento, retorno, respostas, reabertura e avaliacao', ciclo)

    def fila():
        funcionario, _ = usuario('OPERADOR')
        tecnico, _ = usuario('DEV')
        id = abrir()['chamado']['id']
        mensagem(funcionario, id)
        assert [a['tipo'] for a in avisos(admin, id)] == ['RESPOSTA_SOLICITANTE']
        assert [a['tipo'] for a in avisos(tecnico, id)] == ['RESPOSTA_SOLICITANTE']
        assert not avisos(funcionario, id)
        return {'id': id, 'sem_responsavel_avisa_admin_e_dev': True, 'sem_autoaviso': True}

    case('AUTO32', 'Service Desk: resposta sem responsavel avisa equipe tecnica', fila)

    def leitura():
        funcionario, _ = usuario('OPERADOR')
        id, outro = abrir()['chamado']['id'], abrir()['chamado']['id']
        for i in range(55):
            mensagem(admin, id, f'Mensagem QA {i}')
        mensagem(admin, outro)
        caixa = funcionario.call('GET', '/api/chamados/notificacoes')
        assert len(caixa['itens']) == 50 and caixa['naoLidas'] >= 56
        limite = max(a['id'] for a in caixa['itens'])
        aviso = next(a for a in caixa['itens'] if a['chamadoId'] == id)
        admin.call('POST', f'/api/chamados/notificacoes/{aviso["id"]}/leitura', expected=404)
        admin.call('POST', f'/api/chamados/{id}/notificacoes/leitura?ateId={limite}', expected=204)
        assert funcionario.call('GET', '/api/chamados/notificacoes')['naoLidas'] == caixa['naoLidas']
        mensagem(admin, id, 'Mensagem posterior ao limite de leitura')
        for _ in range(2):
            funcionario.call('POST', f'/api/chamados/{id}/notificacoes/leitura?ateId={limite}', expected=204)
        depois = funcionario.call('GET', '/api/chamados/notificacoes')
        assert depois['naoLidas'] == caixa['naoLidas'] + 1 - 55
        assert len(avisos(funcionario, id)) == 1 and avisos(funcionario, id)[0]['id'] > limite
        assert len(avisos(funcionario, outro)) == 1
        return {'mais_de_50': True, 'novos_e_outros_chamados_preservados': True, 'leitura_idempotente_e_isolada': True}

    case('AUTO33', 'Service Desk: leitura de mais de 50 avisos com isolamento e corte temporal', leitura)

    def anexos():
        funcionario, _ = usuario('OPERADOR')
        id = abrir()['chamado']['id']
        from pypdf import PdfWriter
        writer, buffer = PdfWriter(), io.BytesIO()
        writer.add_blank_page(width=200, height=200)
        writer.write(buffer)
        raw = buffer.getvalue()
        upload = ('arquivos', 'evidencia.pdf', raw, 'application/pdf')
        det = mensagem(funcionario, id, '', attachments=[upload])
        anexo = det['anexos'][0]
        assert anexo['nomeArquivo'] == 'evidencia.pdf'
        baixado = funcionario.call('GET', anexo['url'])
        assert baixado == raw
        env['save']('exportacoes/servicedesk-evidencia.pdf', baixado)
        for uploads in [[('arquivos', 'falso.pdf', b'nao e pdf', 'application/pdf')],
                        [('arquivos', 'grande.pdf', raw + b'x'*(5*1024*1024), 'application/pdf')],
                        [upload]*6]:
            antes = persistido(funcionario, id)
            mensagem(funcionario, id, attachments=uploads, expected=400)
            assert persistido(funcionario, id) == antes
        return {'download_sha256': hashlib.sha256(baixado).hexdigest(), 'formato_tamanho_quantidade_rejeitados_sem_gravacao': True}

    case('AUTO34', 'Service Desk: anexos, download e rejeicao sem gravacao parcial', anexos)

    def permissoes():
        funcionario, _ = usuario('OPERADOR')
        privado = abrir(admin)['chamado']['id']
        id = abrir()['chamado']['id']
        Client().call('GET', '/api/chamados', expected=401)
        funcionario.call('GET', f'/api/chamados/{privado}', expected=403)
        assert privado not in [c['id'] for c in funcionario.call('GET', '/api/chamados')['chamados']]
        funcionario.call('GET', '/api/chamados/atendentes', expected=403)
        funcionario.call('PUT', '/api/chamados/tipos/ERRO_BLOQUEANTE', {'slaHoras': 8, 'filaPadrao': 'DEV', 'prioridadePadrao': 'ALTA'}, expected=403)
        antes = persistido(funcionario, id)
        for novo in ['EM_ATENDIMENTO', 'AGUARDANDO_USUARIO', 'RESOLVIDO', 'FECHADO']:
            funcionario.call('POST', f'/api/chamados/{id}/status', {'status': novo, 'assumir': True}, expected=403)
        admin.call('POST', f'/api/chamados/{id}/status', {'status': 'RESOLVIDO'}, expected=403, csrf=False)
        assert persistido(funcionario, id) == antes
        return {'anonimo_401': True, 'funcionario_sem_poder_tecnico': True, 'chamado_tecnico_privado': True, 'csrf': True}

    case('AUTO35', 'Service Desk: permissoes HTTP, privacidade e CSRF', permissoes)

    def validacao():
        funcionario, _ = usuario('OPERADOR')
        antes = funcionario.call('GET', '/api/chamados?meus=true')
        base = {'tipoCodigo': 'ERRO_BLOQUEANTE', 'titulo': 'QA', 'descricao': 'Teste'}
        for invalido in [{'tipoCodigo': ''}, {'tipoCodigo': 'INEXISTENTE'}, {'titulo': ' '}, {'titulo': 'x'*181}, {'descricao': ' '}, {'descricao': 'x'*4001}]:
            funcionario.call('POST', '/api/chamados', {**base, **invalido}, expected=400, multipart=True)
        # SLA restante varia com o relogio; comparar identidades, nao o painel inteiro.
        assert [c['id'] for c in funcionario.call('GET', '/api/chamados?meus=true')['chamados']] == [c['id'] for c in antes['chamados']]
        id = abrir()['chamado']['id']
        mensagem(funcionario, id, '', expected=400)
        funcionario.call('POST', f'/api/chamados/{id}/avaliacao', {'nota': 5}, expected=400)
        status(admin, id, 'RESOLVIDO')
        for nota in [0, 6]:
            funcionario.call('POST', f'/api/chamados/{id}/avaliacao', {'nota': nota}, expected=400)
        admin.call('POST', f'/api/chamados/{id}/avaliacao', {'nota': 5}, expected=403)
        assert funcionario.call('GET', f'/api/chamados/{id}')['chamado']['notaAvaliacao'] is None
        return {'categoria_e_campos_obrigatorios': True, 'limites_texto': True, 'mensagem_vazia': True, 'avaliacao_apenas_solicitante_e_nota_1_a_5': True}

    case('AUTO36', 'Service Desk: entradas invalidas e regras de avaliacao', validacao)
