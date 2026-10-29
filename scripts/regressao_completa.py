"""Executa todas as camadas uma vez e preserva evidencias por execucao."""
import importlib.util
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time
from datetime import datetime

root = Path(__file__).resolve().parents[1]

def command(args, **kwargs):
    return subprocess.run(args, cwd=root, capture_output=True, **kwargs)

def report_results(folder, statuses, secret=''):
    """Mostra as causas no console, inclusive quando o dashboard nao foi gerado."""
    lines = ['Resumo da execucao:']
    for stage, code in statuses.items():
        lines.append(f'- {stage}: {"APROVADO" if code == 0 else "FALHOU"} (codigo {code})')
        if code != 0:
            for log in [folder / f'{stage}-execucao.log', folder / f'{stage}-infraestrutura.log']:
                if log.exists():
                    lines.append(f'Ultimas linhas de {log.name}:')
                    lines.extend(log.read_text(encoding='utf-8', errors='replace').splitlines()[-30:])
    results = folder / 'resultados.json'
    if results.exists():
        try:
            for case in json.loads(results.read_text(encoding='utf-8')):
                if case.get('resultado') != 'APROVADO':
                    lines.append(f"{case.get('id')}: {case.get('titulo', '')} — {case.get('erro', case.get('resultado'))}")
        except (ValueError, TypeError, AttributeError):
            lines.append('Nao foi possivel interpretar resultados.json; consulte o artefato.')
    text = '\n'.join(lines)
    if secret:
        text = text.replace(secret, '[omitido]')
    print(text, flush=True)
    (folder / 'resumo-execucao.txt').write_text(text, encoding='utf-8')

def main():
    if not shutil.which('docker'):
        raise SystemExit('Docker nao encontrado no PATH.')
    for module in ['openpyxl', 'pypdf', 'tzdata']:
        if importlib.util.find_spec(module) is None:
            raise SystemExit('Instale as dependencias: python -m pip install -r requirements-test.txt')
    if command(['docker', 'info']).returncode:
        raise SystemExit('Docker indisponivel. Inicie o Docker Desktop.')
    suffix = secrets.token_hex(4)
    folder = root / 'outputs' / ('evidencias-auto-' + datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + suffix)
    folder.mkdir(parents=True)
    network = 'faturamed-regressao-' + suffix
    database, tests = network + '-db', network + '-testes'
    password = secrets.token_urlsafe(24)
    image, builder = network + '-app', network + '-build'
    statuses = {}
    mount = ['-v', f'{root.as_posix()}:/app', '-w', '/app']
    def execute(name, args, **kwargs):
        print('Executando:', name, flush=True)
        result = command(args, **kwargs)
        (folder / (name + '-execucao.log')).write_bytes(result.stdout + result.stderr)
        statuses[name] = result.returncode
        print(f'Etapa {name}: codigo {result.returncode}', flush=True)
        return result
    try:
        for tag, extra in [(builder, ['--target', 'build']), (image, [])]:
            if execute('build-java' if extra else 'build-app', ['docker', 'build', *extra, '-t', tag, '.']).returncode:
                raise RuntimeError('Build falhou; consulte o resumo e os logs')
        execute('http', [sys.executable, 'scripts/homologacao_automatica.py'],
                env={**os.environ, 'QA_APP_IMAGE': image, 'QA_OUTPUT_DIR': str(folder)})
        try:
            subprocess.run(['docker', 'network', 'create', network], check=True, capture_output=True)
            subprocess.run(['docker', 'run', '-d', '--name', database, '--network', network,
                '--tmpfs', '/var/lib/postgresql/data', '-e', 'POSTGRES_DB=faturamed_test_v2',
                '-e', 'POSTGRES_USER=faturamed_test', '-e', 'POSTGRES_PASSWORD=' + password,
                'postgres:16'], check=True, capture_output=True)
            for attempt in range(60):
                if command(['docker', 'exec', database, 'pg_isready', '-U', 'faturamed_test', '-d', 'faturamed_test_v2']).returncode == 0:
                    break
                time.sleep(1)
            else:
                raise RuntimeError('PostgreSQL de testes nao ficou pronto')
            execute('java', ['docker', 'run', '--name', tests, '--network', network,
                '-e', 'BASE_HOSPITALAR_TEST_DB=true',
                '-e', 'DB_URL=jdbc:postgresql://' + database + ':5432/faturamed_test_v2',
                '-e', 'DB_USER=faturamed_test', '-e', 'DB_PASSWORD=' + password,
                builder, 'mvn', '-B', 'test'])
            (folder / 'java').mkdir(exist_ok=True)
            if command(['docker', 'cp', tests + ':/app/target/surefire-reports/.', str(folder / 'java')]).returncode:
                statuses['java'] = 1
                (folder / 'java-infraestrutura.log').write_text('Falha ao copiar relatorios Surefire do container.', encoding='utf-8')
        except Exception as error:
            statuses['java'] = 1
            (folder / 'java-infraestrutura.log').write_text(str(error), encoding='utf-8')
        execute('javascript', ['docker', 'run', '--rm', *mount, 'node:20', 'node', '--test',
            '--test-reporter=spec', 'src/test/js/demonstrativo.test.cjs', 'src/test/js/selecao.test.cjs',
            'src/test/js/servicedesk.test.cjs', 'scripts/painel-medico.test.cjs'])
        (folder / 'execucao.json').write_text(json.dumps(statuses), encoding='utf-8')
        execute('consolidacao', [sys.executable, 'scripts/consolidar_regressao.py', str(folder)])
        execute('dashboard', ['docker', 'run', '--rm', *mount, 'node:20', 'node', '--test',
            'scripts/test_dashboard_regressao.cjs'])
    finally:
        for container in [tests, database]:
            command(['docker', 'rm', '-f', container])
        command(['docker', 'network', 'rm', network])
        for tag in [image, builder]:
            command(['docker', 'image', 'rm', tag])
        (folder / 'execucao.json').write_text(json.dumps(statuses), encoding='utf-8')
        report_results(folder, statuses, password)
        print('Evidencias:', folder, flush=True)
    print('Dashboard:', root / 'outputs/dashboard-regressao.html', flush=True)
    return 0 if statuses and all(code == 0 for code in statuses.values()) else 1

if __name__ == '__main__':
    raise SystemExit(main())
