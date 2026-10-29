import hashlib
import json
from pathlib import Path
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
folder = sorted(p for p in (root / 'outputs').glob('evidencias-auto-*') if p.is_dir())[-1]
temp = Path('C:/Users/Bruno/AppData/Local/Temp')
sources = [
    ('81a4ccf2-3344-48e8-8c76-f4798d91c530', '01-recebimento-separado-por-medico'),
    ('8fda50b8-3225-4a79-8939-1c45ee68f350', '02-visitas-legitimas-mesmo-atendimento-data'),
    ('5b7ea634-63ec-4249-a717-909d4a91ceca', '03-duplicidade-categoria-e-motivo'),
    ('d7524dcf-a4e5-421c-8abb-b05d69baa511', '04-bloqueio-financeiro-da-duplicidade'),
    ('0663eefb-d02f-4d86-9623-dbdbd22f0cd0', '05-pdf-sem-publicacao-bloqueado'),
    ('d0cc7b3d-f6e4-435f-8414-da782e069876', '06-historico-da-decisao-duplicidade'),
    ('5cb32702-74a0-4f53-a67f-43a4a766835b', '07-pagamento-original-preservado'),
    ('3048b29f-a1ee-44e5-ab1c-71e834f3dd82', '08-concorrencia-entre-abas'),
    ('4eecccc5-ed8a-44ce-95f0-428bbbece928', '09-ciencia-do-medico'),
    ('6ec74bef-82f0-4a9b-9656-6137e5660fd0', '10-resposta-ao-pedido-do-medico'),
    ('70544e9d-ce88-4fb6-9987-a3265056512d', '11-comprovante-acima-do-limite'),
    ('e4019f89-f768-479e-a62c-e8c26e084526', '12-retirada-de-comprovante-com-historico'),
]
index = ['# Prints dos testes manuais', '', 'Evidencias enviadas pelo usuario durante os testes anteriores. Nao sao capturas da execucao automatica atual. Dados declarados ficticios pelo usuario. Os prints podem conter dados da conta de teste e devem ser revisados antes de compartilhar externamente.', '']
destination = folder / 'prints-manuais'
destination.mkdir(exist_ok=True)
for identifier, title in sources:
    source = temp / ('codex-clipboard-' + identifier + '.png')
    if source.exists():
        target = destination / (title + '.png')
        shutil.copy2(source, target)
        index.append(f'- [{title}]({target.name}) — origem: {source.name}')
    else:
        index.append(f'- {title}: arquivo de origem indisponivel; captura nao incluida.')
(destination / 'INDICE.md').write_text('\n'.join(index), encoding='utf-8')
results = json.loads((folder / 'resultados.json').read_text(encoding='utf-8'))
http = json.loads((folder / 'requisicoes-http.json').read_text(encoding='utf-8'))
summary = folder / 'RESUMO.md'
with summary.open('a', encoding='utf-8') as stream:
    stream.write(f'\n\n## Material para o futuro guia\n\n- {len(results)} cenarios registrados e {len(http)} chamadas HTTP com evidencias.\n- Prints anteriores organizados em prints-manuais/INDICE.md.\n- Exportacoes PDF e XLSX reais da execucao em exportacoes.\n- Dados e assercoes por cenario em resultados.json.\n- A restauracao foi comparada diretamente em todas as tabelas; nao foi feito acesso pela interface a uma aplicacao restaurada.\n- Os resultados aprovam somente os cenarios executados; nao constituem aceite completo para producao.\n')
manifest = []
for file in sorted(folder.rglob('*')):
    if file.is_file() and 'privado' not in file.parts and file.name != 'manifesto.json':
        data = file.read_bytes()
        manifest.append({'arquivo': str(file.relative_to(folder)), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
(folder / 'manifesto.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
package = folder.with_suffix('.zip')
with zipfile.ZipFile(package, 'w', zipfile.ZIP_DEFLATED) as archive:
    for file in folder.rglob('*'):
        if file.is_file() and 'privado' not in file.parts:
            archive.write(file, file.relative_to(folder))
print(json.dumps({'pasta': str(folder), 'pacote': str(package), 'prints': len(list(destination.glob('*.png'))), 'cenarios': len(results), 'chamadas_http': len(http)}, ensure_ascii=False))
