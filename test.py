"""Entrada unica da regressao: HTTP, Java e JavaScript em ambiente QA isolado."""
import argparse
import runpy
from pathlib import Path

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('modo', nargs='?', default='all', choices=['all'])
    parser.parse_args()
    runpy.run_path(str(Path(__file__).resolve().parent / 'scripts/regressao_completa.py'), run_name='__main__')
