"""Gera um dashboard HTML independente de servidor a partir das evidencias existentes."""
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / 'scripts/templates/dashboard-regressao.html'


def read_json(path, default):
    return json.loads(path.read_text(encoding='utf-8')) if path.exists() else default


def load(folder):
    tests = []
    for item in read_json(folder / 'resultados.json', []):
        tests.append({'id': item['id'], 'name': item.get('titulo', 'Execução interrompida'), 'layer': 'HTTP', 'status': item['resultado'], 'seconds': item.get('duracao_s'), 'error': item.get('erro', ''), 'details': item.get('detalhes', {}), 'source': 'resultados.json'})
    java = read_json(folder / 'java-cenarios.json', [])
    for i, item in enumerate(java, 1):
        error = ''
        report = folder / 'java' / ('TEST-' + item['classe'] + '.xml')
        if item['resultado'] == 'REPROVADO' and report.exists():
            for test in ET.parse(report).getroot().findall('testcase'):
                if test.get('name') == item['teste']:
                    for node in list(test):
                        if node.tag in ['failure', 'error']:
                            error += node.get('message', '') + '\n' + (node.text or '')
        tests.append({'id': f'JAVA{i:03}', 'name': item['teste'], 'layer': 'Java', 'status': item['resultado'], 'suite': item['classe'].split('.')[-1], 'error': error, 'source': 'java/' + report.name if report.exists() else 'java-cenarios.json'})
    js_path = folder / 'javascript-execucao.log'
    js_text = re.sub(r'\x1b\[[0-9;]*m', '', js_path.read_text(encoding='utf-8', errors='replace')) if js_path.exists() else ''
    for i, match in enumerate(re.finditer(r'^[ \t]*([✔✖﹣])\s+(.+?)(?:\s+\(([\d.]+)ms\))?\s*$', js_text, re.MULTILINE), 1):
        tests.append({'id': f'JS{i:03}', 'name': match[2], 'layer': 'JavaScript', 'status': {'✔': 'APROVADO', '✖': 'REPROVADO', '﹣': 'IGNORADO'}[match[1]], 'seconds': float(match[3]) / 1000 if match[3] else None, 'source': 'javascript-execucao.log'})
    js_total = re.search(r'(?m)^.*?\btests\s+(\d+)\s*$', js_text)
    js_parsed = sum(t['layer'] == 'JavaScript' for t in tests)
    warnings = []
    execution = read_json(folder / 'execucao.json', {})
    for stage, code in execution.items():
        if code != 0:
            warnings.append(f'Etapa {stage} terminou com codigo {code}; consulte os logs desta rodada.')
    if not java:
        warnings.append('Relatórios Java ausentes nesta execução. Não equivale a zero testes aprovados.')
    if not js_text:
        warnings.append('Log JavaScript ausente nesta execução.')
    elif not js_total or int(js_total[1]) != js_parsed:
        warnings.append('Log JavaScript incompleto ou em formato desconhecido. Confira o log original.')
    evidence = []
    for file in sorted(folder.rglob('*')):
        if file.is_file() and 'privado' not in file.relative_to(folder).parts and file.suffix.lower() in ['.pdf', '.xlsx', '.png', '.md', '.json', '.log']:
            evidence.append({'name': file.name, 'path': file.relative_to(folder).as_posix(), 'size': file.stat().st_size, 'kind': 'Print manual' if file.suffix.lower() == '.png' else 'Exportação' if file.suffix.lower() in ['.pdf', '.xlsx'] and file.parent.name == 'exportacoes' else 'Dados de entrada' if file.parent.name == 'entradas' else 'Relatório / log'})
    calls = read_json(folder / 'requisicoes-http.json', [])
    return {'run': folder.name, 'tests': tests, 'warnings': warnings, 'evidence': evidence, 'calls': calls}


def generate(folder):
    data = load(folder)
    data['history'] = []
    for previous in sorted((ROOT / 'outputs').glob('evidencias-auto-*'), reverse=True):
        if not previous.is_dir() or not (previous / 'resultados.json').exists():
            continue
        old = read_json(previous / 'resultados.json', [])
        data['history'].append({'run': previous.name, 'http': len(old), 'fail': sum(t['resultado'] != 'APROVADO' for t in old), 'available': previous == folder or (previous / 'dashboard.html').exists()})
    template = TEMPLATE.read_text(encoding='utf-8')
    def html(base):
        payload = json.dumps({**data, 'base': base}, ensure_ascii=False).replace('<', '\\u003c').replace('\u2028', '\\u2028').replace('\u2029', '\\u2029')
        return template.replace('__DASHBOARD_DATA__', payload)
    (folder / 'dashboard.html').write_text(html(''), encoding='utf-8')
    (ROOT / 'outputs/dashboard-regressao.html').write_text(html(folder.name + '/'), encoding='utf-8')
    for alias, destination in [(ROOT / 'dashboard-testes.html', 'outputs/dashboard-regressao.html'), (ROOT / 'outputs/dashboard-testes.html', 'dashboard-regressao.html')]:
        alias.write_text('<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=' + destination + '"><a href="' + destination + '">Abrir dashboard de regressao</a>', encoding='utf-8')
    print(json.dumps({'dashboard': str(folder / 'dashboard.html'), 'atalho': str(ROOT / 'outputs/dashboard-regressao.html'), 'testes': len(data['tests']), 'avisos': data['warnings']}, ensure_ascii=False))
    return data


if __name__ == '__main__':
    folder = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else sorted(p for p in (ROOT / 'outputs').glob('evidencias-auto-*') if p.is_dir())[-1]
    generate(folder)
