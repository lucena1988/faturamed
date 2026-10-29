import json
import hashlib
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
folder = sorted(p for p in (root / 'outputs').glob('evidencias-auto-*') if p.is_dir())[-1]
sources = []
for source_root in [root / 'src', root / 'scripts']:
    for file in sorted(source_root.rglob('*')):
        if file.is_file() and file.suffix in ['.java', '.js', '.cjs', '.py'] and '__pycache__' not in file.parts:
            sources.append({'arquivo': str(file.relative_to(root)), 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()})
(folder / 'codigo-validado.json').write_text(json.dumps(sources, ensure_ascii=False, indent=2), encoding='utf-8')
node = Path.home() / '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'
js = subprocess.run([str(node), '--test', 'src/test/js/demonstrativo.test.cjs', 'src/test/js/selecao.test.cjs', 'src/test/js/servicedesk.test.cjs', 'scripts/painel-medico.test.cjs'], cwd=root, capture_output=True)
(folder / 'javascript-execucao.log').write_bytes(js.stdout + js.stderr)
totals = {name: 0 for name in ['tests', 'failures', 'errors', 'skipped']}
java_cases = []
for file in (folder / 'java').glob('TEST-*.xml'):
    suite = ET.parse(file).getroot()
    for name in totals:
        totals[name] += int(suite.get(name, '0'))
    for test in suite.findall('testcase'):
        java_cases.append({'classe': test.get('classname'), 'teste': test.get('name'), 'resultado': 'REPROVADO' if test.find('failure') is not None or test.find('error') is not None else 'IGNORADO' if test.find('skipped') is not None else 'APROVADO'})
(folder / 'java-cenarios.json').write_text(json.dumps(java_cases, ensure_ascii=False, indent=2), encoding='utf-8')
results = json.loads((folder / 'resultados.json').read_text(encoding='utf-8'))
matrix = (root / 'docs/regressao-homologacao.md').read_text(encoding='utf-8')
(folder / 'MATRIZ-DE-COBERTURA.md').write_text(matrix, encoding='utf-8')
issues = ['# Pendencias encontradas nesta execucao', '']
for result in results:
    if result['resultado'] != 'APROVADO':
        issues.extend([f"## {result['id']} — {result.get('titulo', '')}", '', result.get('erro') or 'Verificar a resposta HTTP registrada para este cenario.', ''])
        if result['id'] == 'AUTO27':
            issues.extend(['Apos ajuste manual da primeira linha, a segunda foi conciliada por exclusividade, mas medicoCadastroId permaneceu vazio. Esperado: vinculo seguro com o cadastro correspondente, permitindo isolamento e fluxo financeiro corretos. Evidencia: requisicoes-http.json, cenario AUTO27, ultima consulta do relatorio. Os demais cenarios nao substituem esta verificacao.', ''])
(folder / 'PENDENCIAS.md').write_text('\n'.join(issues), encoding='utf-8')
with (folder / 'RESUMO.md').open('a', encoding='utf-8') as output:
    output.write(f"\n\n## Regressao ampliada\n\n- HTTP: {sum(r['resultado']=='APROVADO' for r in results)} aprovados em {len(results)} cenarios.\n- Java: {totals['tests']} testes; falhas {totals['failures']}; erros {totals['errors']}; ignorados {totals['skipped']}.\n- JavaScript: codigo de saida {js.returncode}; detalhes em javascript-execucao.log. Usa DOM simulado, sem navegador real.\n- Pendencias em PENDENCIAS.md; correspondencia com testes manuais em MATRIZ-DE-COBERTURA.md.\n")
subprocess.run([sys.executable, 'scripts/gerar_dashboard_regressao.py', str(folder)], cwd=root, check=True)
dashboard_test = subprocess.run([str(node), '--test', 'scripts/test_dashboard_regressao.cjs'], cwd=root, capture_output=True)
(folder / 'dashboard-verificacao.log').write_bytes(dashboard_test.stdout + dashboard_test.stderr)
subprocess.run([sys.executable, 'scripts/organizar_evidencias.py'], cwd=root, check=True)
print(json.dumps({'http': len(results), 'http_aprovados': sum(r['resultado'] == 'APROVADO' for r in results), 'java': totals, 'javascript_exit': js.returncode}, ensure_ascii=False))
if dashboard_test.returncode:
    raise SystemExit('Falha na verificacao do dashboard; consulte dashboard-verificacao.log')
