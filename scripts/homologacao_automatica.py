import concurrent.futures
import hashlib
import http.cookiejar
import io
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = Path(os.environ['QA_OUTPUT_DIR']) if os.environ.get('QA_OUTPUT_DIR') else ROOT / 'outputs' / ('evidencias-auto-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
OUTPUT.mkdir(parents=True, exist_ok=True)
BASE = 'http://127.0.0.1:18081'
RUN_ID = secrets.token_hex(3)
NETWORK = 'faturamed-qa-auto-network-' + RUN_ID
DATABASE = 'faturamed-qa-auto-db-' + RUN_ID
APPLICATION = 'faturamed-qa-auto-app-' + RUN_ID
RESTORE = 'faturamed-qa-auto-restore-' + RUN_ID
PASSWORD = secrets.token_urlsafe(24)
ADMIN = 'admin.qa@example.com'
cases = []
requests = []
current_case = 'PREPARACAO'
lock = threading.Lock()


def docker(*arguments, data=None):
    result = subprocess.run(['docker', *arguments], input=data, capture_output=True)
    if result.returncode:
        detail = result.stderr.decode(errors='replace').replace(PASSWORD, '[omitido]')
        raise RuntimeError(f'Docker {arguments[0]} falhou: {detail}')
    return result.stdout


def save(name, content):
    target = OUTPUT / name
    target.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(content, bytes):
        target.write_bytes(content)
    else:
        target.write_text(json.dumps(content, ensure_ascii=False, indent=2), encoding='utf-8')


class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.token = None

    def call(self, method, path, payload=None, expected=200, files=None, csrf=True, log=True, multipart=False, attachments=None):
        headers = {}
        if method not in ['GET', 'HEAD'] and csrf:
            if self.token is None:
                self.token = self.call('GET', '/api/auth/csrf', log=False)
            headers[self.token['header']] = self.token['token']
        body = None
        recorded_payload = payload
        if files or multipart:
            boundary = 'qa-' + secrets.token_hex(10)
            parts = []
            for key, value in (payload or {}).items():
                parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
            uploads = [('arquivo', files[0], files[1], 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')] if files else (attachments or [])
            for field, filename, content, mime in uploads:
                parts.extend([f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode(), content, b'\r\n'])
            parts.append(f'--{boundary}--\r\n'.encode())
            body = b''.join(parts)
            headers['Content-Type'] = 'multipart/form-data; boundary=' + boundary
            recorded_payload = {**(payload or {}), 'anexos': [{'campo': field, 'arquivo': name, 'sha256': hashlib.sha256(raw).hexdigest()} for field, name, raw, mime in uploads]}
            if files:
                recorded_payload = {**payload, 'arquivo': files[0], 'sha256_arquivo': hashlib.sha256(files[1]).hexdigest()}
        elif payload is not None:
            body = json.dumps(payload).encode()
            headers['Content-Type'] = 'application/json'
        started = time.perf_counter()
        request = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
        try:
            response = self.opener.open(request, timeout=45)
        except urllib.error.HTTPError as error:
            response = error
        raw = response.read()
        status = response.status
        content_type = response.headers.get('Content-Type', '')
        result = json.loads(raw) if raw and 'json' in content_type else raw
        if log:
            record = {'cenario': current_case, 'metodo': method, 'rota': path, 'entrada': recorded_payload, 'status': status,
                      'duracao_ms': round((time.perf_counter() - started) * 1000, 2),
                      'resposta': result if not isinstance(result, bytes) else {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest(), 'tipo': content_type}}
            with lock:
                requests.append(record)
        if expected is not None:
            assert status in ([expected] if isinstance(expected, int) else expected), f'{method} {path}: esperado {expected}; obtido {status}: {str(result)[:250]}'
        return result if expected is not None else (status, result)

    def login(self, email=ADMIN):
        token = self.call('GET', '/api/auth/csrf', log=False)
        request = urllib.request.Request(BASE + '/api/auth/login', data=urllib.parse.urlencode({'username': email, 'password': PASSWORD}).encode(), headers={token['header']: token['token']}, method='POST')
        with self.opener.open(request, timeout=30) as response:
            assert response.status == 200
        self.token = self.call('GET', '/api/auth/csrf', log=False)


def case(identifier, title, operation):
    global current_case
    current_case = identifier
    started = time.perf_counter()
    try:
        detail = operation()
        result = {'id': identifier, 'titulo': title, 'resultado': 'APROVADO', 'detalhes': detail}
    except Exception as error:
        result = {'id': identifier, 'titulo': title, 'resultado': 'REPROVADO', 'erro': str(error).replace(PASSWORD, '[omitido]')}
    result['duracao_s'] = round(time.perf_counter() - started, 3)
    cases.append(result)
    save('resultados.json', cases)
    print(identifier, result['resultado'], title, flush=True)
    if result['resultado'] != 'APROVADO':
        print(result.get('erro'), flush=True)
    return result


def fixture(name, rows):
    # Generate every input from scenario data; outputs is never an input dependency.
    from qa_fixtures import workbook_bytes
    result = workbook_bytes(rows)
    save('entradas/' + name, result)
    return name, result


HEADERS = ['Atendimento', 'Data Consumo', 'Medico', 'Cod Produto', 'Produto', 'Valor Tot', 'Regra', 'Vl. A Repassar', 'Setor']


def hospital_row(attendance, doctor, value=100, visit_date='2026-06-10'):
    return [attendance, visit_date, doctor, '10102019', 'Visita Hospitalar (Paciente Internado)', value, 1, value, 'Faturado']


def prepare():
    docker('network', 'create', NETWORK)
    docker('run', '-d', '--name', DATABASE, '--network', NETWORK, '--tmpfs', '/var/lib/postgresql/data', '-e', 'POSTGRES_DB=faturamed_qa', '-e', 'POSTGRES_USER=qa', '-e', 'POSTGRES_PASSWORD=' + PASSWORD, 'postgres:16')
    for attempt in range(40):
        result = subprocess.run(['docker', 'exec', DATABASE, 'pg_isready', '-U', 'qa', '-d', 'faturamed_qa'], capture_output=True)
        if result.returncode == 0:
            break
        time.sleep(1)
    docker('run', '-d', '--name', APPLICATION, '--network', NETWORK, '-p', '127.0.0.1:18081:8080', '-e', 'DB_URL=jdbc:postgresql://' + DATABASE + ':5432/faturamed_qa', '-e', 'DB_USER=qa', '-e', 'DB_PASSWORD=' + PASSWORD, '-e', 'ADMIN_INITIAL_EMAIL=' + ADMIN, '-e', 'ADMIN_INITIAL_PASSWORD=' + PASSWORD, os.environ.get('QA_APP_IMAGE', 'faturamed-app'))
    for attempt in range(60):
        try:
            if urllib.request.urlopen(BASE + '/api/health', timeout=2).status == 200:
                return
        except Exception:
            time.sleep(1)
    raise RuntimeError('Aplicacao isolada nao iniciou')


def run():
    prepare()
    admin = Client()
    admin.login()
    hospital = admin.call('POST', '/api/hospitais', {'nome': 'Hospital QA automatico ficticio', 'documento': '', 'cidade': 'Teste', 'uf': 'SP', 'ativo': True, 'versao': 0}, 201)['id']
    doctors = []
    for name, crm, active in [('Medica QA Alfa', '900001', True), ('Medico QA Beta', '900002', True), ('Medico QA Inativo', '900003', False)]:
        doctors.append(admin.call('POST', '/api/medicos', {'nome': name, 'crm': crm, 'uf': 'SP', 'ativo': active, 'aliases': [], 'versao': 0}, 201))
    alfa, beta, inactive = doctors
    params = {'hospitalId': hospital, 'competencia': '2026/06'}
    hospital_file = fixture('hospital-inicial.xlsx', [HEADERS, hospital_row('88000001', alfa['nome']), hospital_row('88000002', beta['nome'])])
    production = fixture('producao-alfa.xlsx', [['Atendimento', 'Dt.', 'Medico'], ['88000001', '2026-06-10', alfa['nome']]])
    admin.call('POST', '/api/base-hospitalar/hospital', params, files=hospital_file)
    report = admin.call('POST', '/api/base-hospitalar/producao', {**params, 'medicoId': alfa['id']}, files=production)['relatorioId']
    initial_ids = [item['id'] for item in admin.call('GET', f'/api/base-hospitalar/visitas?hospitalId={hospital}')]
    financial = f'/api/conciliacoes/visitas/{report}/recebimentos'
    admin.call('POST', financial + '/visitas/2', {'valor': 40, 'data': '2026-06-20', 'observacoes': 'Pagamento ficticio para teste automatico', 'versao': 0})

    def update_hospital():
        updated = fixture('hospital-atualizado.xlsx', [HEADERS, hospital_row('88000001', alfa['nome'], 150), hospital_row('88000002', beta['nome'])])
        result = admin.call('POST', '/api/base-hospitalar/hospital', {**params, 'modoHospital': 'ATUALIZAR'}, files=updated)
        latest = admin.call('GET', f'/api/base-hospitalar/visitas?hospitalId={hospital}')
        assert [item['id'] for item in latest] == initial_ids
        version = admin.call('GET', f'/api/conciliacoes/visitas/{report}/acompanhamento')['versao']
        newer = admin.call('POST', f'/api/base-hospitalar/relatorios/{report}/atualizar?versao={version}')['id']
        visit = admin.call('GET', f'/api/conciliacoes/visitas/{newer}/recebimentos')['visitas'][0]
        assert (visit['faturado'], visit['recebido'], visit['saldo']) == (150, 40, 110)
        assert admin.call('GET', f'/api/base-hospitalar/visitas/{initial_ids[-1]}/historico') is not None
        return {'relatorio_original': report, 'nova_versao': newer, 'ids_preservados': initial_ids, 'faturado': 150, 'recebido': 40, 'saldo': 110, 'resultado_importacao': result}

    updated_case = case('AUTO01', 'Atualizacao hospitalar preserva IDs e recebimento parcial', update_hospital)
    assert updated_case['resultado'] == 'APROVADO', 'Precondicao AUTO01 falhou'
    newer = updated_case['detalhes']['nova_versao']

    def invalid_imports():
        before = admin.call('GET', f'/api/base-hospitalar/arquivos?hospitalId={hospital}')
        invalid_cases = [
            ('cabecalho-incompleto.xlsx', [['Atendimento', 'Data Consumo'], ['88000999', '2026-06-10']]),
            ('data-invalida.xlsx', [HEADERS, hospital_row('88000998', alfa['nome']), hospital_row('88000999', alfa['nome'], visit_date='31/02/2026')]),
            ('sem-registros.xlsx', [HEADERS]),
        ]
        for name, rows in invalid_cases:
            admin.call('POST', '/api/base-hospitalar/hospital', params, expected=400, files=fixture(name, rows))
            assert admin.call('GET', f'/api/base-hospitalar/arquivos?hospitalId={hospital}') == before
            assert [item['id'] for item in admin.call('GET', f'/api/base-hospitalar/visitas?hospitalId={hospital}')] == initial_ids
        admin.call('POST', '/api/base-hospitalar/producao', {**params, 'medicoId': inactive['id']}, expected=400, files=production)
        return {'arquivos_invalidos': 3, 'medico_inativo_rejeitado': True, 'rollback': 'Nenhum arquivo nem visita acrescentados, inclusive primeira linha valida do arquivo com segunda linha invalida'}

    case('AUTO02', 'Planilhas validas estruturalmente com dados invalidos e medico inativo', invalid_imports)

    def unknown_doctor():
        upload = fixture('producao-desconhecido.xlsx', [['Atendimento', 'Dt.', 'Medico'], ['88000001', '2026-06-10', 'Nome QA Desconhecido']])
        result = admin.call('POST', '/api/base-hospitalar/producao', {**params, 'modoProducao': 'VARIOS'}, files=upload)
        visit = admin.call('GET', f"/api/conciliacoes/visitas/{result['relatorioId']}")['visitas'][0]
        assert visit['status'] == 'DIVERGENTE' and visit['hospital'] is None
        assert visit['original']['medico'] == 'Nome QA Desconhecido'
        return {'relatorio': result['relatorioId'], 'status': visit['status'], 'identidade_desconhecida_preservada': True}

    case('AUTO03', 'Nome desconhecido preenchido nao e sobrescrito por inferencia', unknown_doctor)
    beta_production = fixture('producao-beta.xlsx', [['Atendimento', 'Dt.', 'Medico'], ['88000002', '2026-06-10', beta['nome']]])
    beta_report = admin.call('POST', '/api/base-hospitalar/producao', {**params, 'medicoId': beta['id']}, files=beta_production)['relatorioId']

    def concurrent_review():
        clients = [Client(), Client()]
        for client in clients:
            client.login()
        version = admin.call('GET', f'/api/conciliacoes/visitas/{beta_report}/acompanhamento')['versao']
        barrier = threading.Barrier(2)
        payload = {'acao': 'MANTER_DIVERGENTE', 'responsavel': 'QA automatico', 'justificativa': 'Teste ficticio de concorrencia real entre duas sessoes', 'versao': version}
        def submit(client):
            barrier.wait(timeout=10)
            return client.call('POST', f'/api/conciliacoes/visitas/{beta_report}/visitas/2/revisao', payload, expected=None)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(submit, clients))
        statuses = sorted(item[0] for item in results)
        assert statuses == [200, 409], statuses
        detail = admin.call('GET', f'/api/conciliacoes/visitas/{beta_report}/visitas/2/revisao')
        return {'status_concorrentes': statuses, 'detalhe_final': detail}

    case('AUTO04', 'Duas sessoes salvando revisao simultaneamente', concurrent_review)
    accounts = []
    for doctor, email in [(alfa, 'alfa.qa@example.com'), (beta, 'beta.qa@example.com')]:
        accounts.append(admin.call('POST', '/api/usuarios', {'nome': doctor['nome'], 'email': email, 'senha': PASSWORD, 'perfil': 'MEDICO', 'medicoId': doctor['id']}, 201, log=False))
    physician = Client()
    physician.login('alfa.qa@example.com')
    other_physician = Client()
    other_physician.login('beta.qa@example.com')
    publication = {}

    def publish_cycle():
        version = admin.call('GET', f'/api/conciliacoes/visitas/{newer}/acompanhamento')['versao']
        released = admin.call('POST', f'/api/conciliacoes/visitas/{newer}/liberacao', {'versao': version, 'observacao': 'Publicacao QA ficticia', 'confirmaPendencias': True})
        first_publication = released['publicado']['id']
        physician.call('POST', f'/api/medico/relatorios/{newer}/ciencia', {'publicacao': first_publication})
        physician.call('POST', f'/api/medico/relatorios/{newer}/solicitacoes', {'publicacao': first_publication, 'linha': 2, 'motivo': 'Pedido ficticio para verificar preservacao entre publicacoes'})
        state = physician.call('GET', f'/api/medico/relatorios/{newer}/conferencia')
        request_id = state['solicitacoes'][0]['id']
        draft = admin.call('POST', f'/api/base-hospitalar/relatorios/{newer}/atualizar?versao={version}')['id']
        assert physician.call('GET', f'/api/medico/relatorios/{newer}')['id'] == newer
        physician.call('GET', f'/api/medico/relatorios/{draft}', expected=404)
        draft_version = admin.call('GET', f'/api/conciliacoes/visitas/{draft}/acompanhamento')['versao']
        second = admin.call('POST', f'/api/conciliacoes/visitas/{draft}/liberacao', {'versao': draft_version, 'observacao': 'Republicacao QA ficticia', 'confirmaPendencias': True})
        latest = physician.call('GET', f'/api/medico/relatorios/{draft}/conferencia')
        assert latest['cienteEm'] is None
        assert latest['publicacao'] != first_publication
        assert any(item['id'] == request_id for item in admin.call('GET', '/api/solicitacoes-medicos?situacao=TODAS'))
        admin.call('POST', f'/api/solicitacoes-medicos/{request_id}/resposta', {'resposta': 'Resposta ficticia apos republicacao; historico preservado'})
        physician.call('GET', f'/api/medico/relatorios/{newer}', expected=404)
        publication.update({'report': draft, 'request': request_id})
        return {'primeira_publicacao': first_publication, 'segunda_publicacao': second['publicado']['id'], 'relatorio_publicado': draft, 'ciencia_nao_reutilizada': True, 'pedido_preservado': request_id}

    case('AUTO05', 'Republicacao isola rascunho e preserva pedidos sem reutilizar ciencia', publish_cycle)

    def security():
        anonymous = Client()
        anonymous.call('GET', '/api/usuarios', expected=401)
        physician.call('GET', '/api/base-hospitalar/visitas?hospitalId=' + str(hospital), expected=403)
        physician.call('POST', f'/api/conciliacoes/visitas/{newer}/recebimentos/visitas/2', {'valor': 10, 'data': '2026-06-20', 'observacoes': '', 'versao': 0}, expected=403)
        other_physician.call('GET', f"/api/medico/relatorios/{publication['report']}/demonstrativo.pdf", expected=404)
        admin.call('POST', '/api/hospitais', {'nome': 'Nao deve ser criado', 'cidade': '', 'uf': '', 'ativo': True, 'versao': 0}, expected=403, csrf=False)
        users = admin.call('GET', '/api/usuarios')
        account = next(item for item in users if item['email'] == 'alfa.qa@example.com')
        admin.call('PUT', f"/api/usuarios/{account['id']}/situacao", {'ativo': False, 'versao': account['versao']})
        physician.call('GET', '/api/auth/me', expected=401)
        return {'anonimo': 401, 'medico_em_administracao': 403, 'escrita_medico': 403, 'outro_medico_pdf': 404, 'csrf_ausente': 403, 'sessao_revogada': 401}

    case('AUTO06', 'Autorizacao HTTP real CSRF isolamento e revogacao de sessao', security)

    def financial_guards():
        path = f"/api/conciliacoes/visitas/{publication['report']}/recebimentos"
        before = admin.call('GET', path)
        version = before['visitas'][0]['versao']
        for value in [0, -1, 111, 1.001]:
            admin.call('POST', path + '/visitas/2', {'valor': value, 'data': '2026-06-20', 'observacoes': 'Tentativa QA invalida', 'versao': version}, expected=400)
        assert admin.call('GET', path) == before
        return {'valores_rejeitados': [0, -1, 111, 1.001], 'historico_e_saldo_intactos': True}

    case('AUTO07', 'API rejeita valores invalidos sem depender da interface', financial_guards)

    def exports():
        report_id = publication['report']
        pdf = admin.call('GET', f'/api/conciliacoes/visitas/{report_id}/demonstrativo.pdf?medicoId=' + str(alfa['id']))
        save('exportacoes/demonstrativo-alfa.pdf', pdf)
        from pypdf import PdfReader
        text = '\n'.join(page.extract_text() for page in PdfReader(io.BytesIO(pdf)).pages)
        assert '150,00' in text and '40,00' in text and '110,00' in text
        assert beta['nome'] not in text
        xlsx = admin.call('GET', f'/api/conciliacoes/visitas/{report_id}/relatorio.xlsx?medicoId=' + str(alfa['id']))
        save('exportacoes/conciliacao-alfa.xlsx', xlsx)
        import openpyxl
        workbook = openpyxl.load_workbook(io.BytesIO(xlsx), data_only=True)
        assert not any(beta['nome'] in str(cell.value) for sheet in workbook for row in sheet for cell in row)
        return {'pdf_legivel_por_extracao': True, 'totais': [150, 40, 110], 'medico_beta_excluido_pdf_xlsx': True}

    case('AUTO08', 'Exportacoes reais preservam totais e isolamento por medico', exports)
    from homologacao_cenarios import executar
    executar(globals(), admin, alfa, beta)
    from homologacao_servicedesk import executar as executar_servicedesk
    executar_servicedesk(globals(), admin)
    persistence_before = admin.call('GET', f'/api/base-hospitalar/acompanhamento?hospitalId={hospital}')

    def persistence():
        docker('restart', APPLICATION)
        for attempt in range(45):
            try:
                if urllib.request.urlopen(BASE + '/api/health', timeout=2).status == 200:
                    break
            except Exception:
                time.sleep(1)
        admin.token = None
        admin.login()
        after = admin.call('GET', f'/api/base-hospitalar/acompanhamento?hospitalId={hospital}')
        assert after == persistence_before
        return {'acompanhamento_antes_e_depois_identico': True, 'reinicio': 'Somente app isolado de QA, porta 18081'}

    case('AUTO09', 'Reinicio do ambiente isolado preserva dados e valores', persistence)

    def backup_restore():
        backup = docker('exec', DATABASE, 'pg_dump', '-U', 'qa', '-d', 'faturamed_qa', '-Fc')
        private = OUTPUT / 'privado'
        private.mkdir(exist_ok=True)
        (private / 'backup-qa.dump').write_bytes(backup)
        docker('run', '-d', '--name', RESTORE, '--network', NETWORK, '--tmpfs', '/var/lib/postgresql/data', '-e', 'POSTGRES_DB=faturamed_qa', '-e', 'POSTGRES_USER=qa', '-e', 'POSTGRES_PASSWORD=' + PASSWORD, 'postgres:16')
        for attempt in range(40):
            if subprocess.run(['docker', 'exec', RESTORE, 'pg_isready', '-U', 'qa', '-d', 'faturamed_qa'], capture_output=True).returncode == 0:
                break
            time.sleep(1)
        docker('exec', '-i', RESTORE, 'pg_restore', '-U', 'qa', '-d', 'faturamed_qa', '--exit-on-error', data=backup)
        tables = docker('exec', DATABASE, 'psql', '-U', 'qa', '-d', 'faturamed_qa', '-Atc', "select tablename from pg_tables where schemaname='public' order by tablename").decode().splitlines()
        results = []
        for table in tables:
            query = f'''select count(*),md5(coalesce(string_agg(row_to_json(record)::text,E'\\n' order by row_to_json(record)::text),'')) from "{table}" record'''
            before = docker('exec', DATABASE, 'psql', '-U', 'qa', '-d', 'faturamed_qa', '-Atc', query)
            after = docker('exec', RESTORE, 'psql', '-U', 'qa', '-d', 'faturamed_qa', '-Atc', query)
            assert before == after, table
            results.append({'tabela': table, 'registros': int(before.decode().split('|')[0]), 'integridade_identica': True})
        save('restauracao-integridade.json', results)
        return {'tabelas_comparadas': len(tables), 'conteudo_integral_identico': True, 'backup_bytes': len(backup), 'aviso': 'Dump privado somente ficticio; nao incluir no guia da empresa'}

    case('AUTO10', 'Backup e restauracao isolada com comparacao integral das tabelas', backup_restore)


try:
    run()
except Exception as error:
    cases.append({'id': 'EXECUCAO', 'resultado': 'BLOQUEADO', 'erro': str(error).replace(PASSWORD, '[omitido]')})
    print('BLOQUEIO:', str(error).replace(PASSWORD, '[omitido]'), flush=True)
finally:
    save('resultados.json', cases)
    save('requisicoes-http.json', requests)
    for container in [APPLICATION, DATABASE, RESTORE]:
        subprocess.run(['docker', 'rm', '-f', container], capture_output=True)
    subprocess.run(['docker', 'network', 'rm', NETWORK], capture_output=True)
    manifest = []
    for file in OUTPUT.rglob('*'):
        if file.is_file() and 'privado' not in file.parts:
            raw = file.read_bytes()
            manifest.append({'arquivo': str(file.relative_to(OUTPUT)), 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()})
    save('manifesto.json', manifest)
    summary = ['# Evidencias da homologacao automatica', '', 'Execucao HTTP real em ambiente local isolado. Dados ficticios. Sem acesso ao banco dos testes manuais.', '', '| ID | Cenario | Resultado |', '|---|---|---|']
    summary.extend(f"| {item['id']} | {item.get('titulo', 'Execucao')} | {item['resultado']} |" for item in cases)
    summary.extend(['', '## Limites', '', '- Nao valida HTTPS, dominio, infraestrutura publica, carga ou aceite da empresa.', '- Nao houve teste de navegador automatizado nesta rodada; evidencias HTTP nao equivalem a prints da interface.', '- Tempo de cada chamada e medicao desta execucao, nao teste de desempenho.', '- Dumps em privado nao devem integrar o guia. Nao foram registrados senhas, cookies ou tokens CSRF nos arquivos de evidencias.', '- Containers exclusivos de QA foram parados; o ambiente dos testes manuais nao foi reiniciado.', '', 'Detalhes em resultados.json e requisicoes-http.json. Arquivos em entradas e exportacoes. Integridade em manifesto.json.'])
    (OUTPUT / 'RESUMO.md').write_text('\n'.join(summary), encoding='utf-8')
    print('EVIDENCIAS:', OUTPUT, flush=True)

raise SystemExit(0 if cases and all(item['resultado'] == 'APROVADO' for item in cases) else 1)
