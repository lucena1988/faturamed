"""Compatibilidade: o dashboard unico e gerado a partir de evidencias reais."""
import runpy
from pathlib import Path

if __name__ == '__main__':
    runpy.run_path(str(Path(__file__).with_name('gerar_dashboard_regressao.py')), run_name='__main__')
