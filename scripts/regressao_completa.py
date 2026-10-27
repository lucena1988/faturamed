"""Executa a regressao local com bancos exclusivos e consolida as evidencias."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time

root = Path(__file__).resolve().parents[1]
runtime = Path.home() / '.cache/codex-runtimes/codex-primary-runtime/dependencies'
node = runtime / 'node/bin/node.exe'
suffix = secrets.token_hex(4)
network = 'faturamed-regressao-' + suffix
database = network + '-db'
tests = network + '-testes'
password = secrets.token_urlsafe(24)
image = 'faturamed-qa-runtime'
builder = 'faturamed-qa-build'


def command(args, **kwargs):
    return subprocess.run(args, cwd=root, capture_output=True, **kwargs)


for args in [['docker', 'build', '--target', 'build', '-t', builder, '.'], ['docker', 'build', '-t', image, '.']]:
    result = command(args)
    if result.returncode:
        print(result.stderr.decode(errors='replace')[-3000:])
        raise SystemExit('Build de QA falhou; ambiente manual preservado')
environment = {**os.environ, 'QA_APP_IMAGE': image}
http = command([sys.executable, 'scripts/homologacao_automatica.py'], env=environment)
folder = sorted(p for p in (root / 'outputs').glob('evidencias-auto-*') if p.is_dir())[-1]
(folder / 'http-execucao.log').write_bytes(http.stdout + http.stderr)
java_status = 1
try:
    subprocess.run(['docker', 'network', 'create', network], check=True, capture_output=True)
    subprocess.run(['docker', 'run', '-d', '--name', database, '--network', network, '--tmpfs', '/var/lib/postgresql/data', '-e', 'POSTGRES_DB=faturamed_test_v2', '-e', 'POSTGRES_USER=faturamed_test', '-e', 'POSTGRES_PASSWORD=' + password, 'postgres:16'], check=True, capture_output=True)
    for attempt in range(40):
        if command(['docker', 'exec', database, 'pg_isready', '-U', 'faturamed_test', '-d', 'faturamed_test_v2']).returncode == 0:
            break
        time.sleep(1)
    result = command(['docker', 'run', '--name', tests, '--network', network, '-e', 'BASE_HOSPITALAR_TEST_DB=true', '-e', 'DB_URL=jdbc:postgresql://' + database + ':5432/faturamed_test_v2', '-e', 'DB_USER=faturamed_test', '-e', 'DB_PASSWORD=' + password, builder, 'mvn', '-B', 'test'])
    java_status = result.returncode
    (folder / 'java-execucao.log').write_bytes(result.stdout + result.stderr)
    (folder / 'java').mkdir(exist_ok=True)
    command(['docker', 'cp', tests + ':/app/target/surefire-reports/.', str(folder / 'java')])
finally:
    for container in [tests, database]:
        command(['docker', 'stop', container])
js = command([str(node), '--test', 'src/test/js/demonstrativo.test.cjs', 'src/test/js/selecao.test.cjs', 'scripts/painel-medico.test.cjs'])
(folder / 'javascript-execucao.log').write_bytes(js.stdout + js.stderr)
consolidated = command([sys.executable, 'scripts/consolidar_regressao.py'])
if consolidated.returncode:
    print('Falha ao consolidar evidencias:', consolidated.stderr.decode(errors='replace')[-2000:])
print('Evidencias:', folder)
print('Dashboard:', root / 'outputs/dashboard-regressao.html')
raise SystemExit(0 if http.returncode == java_status == js.returncode == consolidated.returncode == 0 else 1)
