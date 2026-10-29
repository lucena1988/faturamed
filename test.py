#!/usr/bin/env python3
"""
==============================================================================
Faturamed - Executor Híbrido de Testes Automatizados (Cross-Platform)
Compatível com: Windows, macOS e Linux
Requisitos: Docker instalado e em execução

Uso:
    python test.py [all|unit|integration|js]
    (ou no Windows: python test.py, no Mac: ./test.py ou python3 test.py)
==============================================================================
"""

import sys
import subprocess
from pathlib import Path

# Identifica diretório base da aplicação
ROOT_DIR = Path(__file__).resolve().parent
if (ROOT_DIR / "faturamed" / "pom.xml").exists():
    APP_DIR = (ROOT_DIR / "faturamed").resolve()
else:
    APP_DIR = ROOT_DIR.resolve()

M2_DIR = (Path.home() / ".m2").resolve()
M2_DIR.mkdir(parents=True, exist_ok=True)

# Formata caminhos em padrão POSIX para volumes do Docker (compatível com Windows e Unix)
APP_MOUNT = APP_DIR.as_posix()
M2_MOUNT = M2_DIR.as_posix()

def run_cmd(args, desc):
    print(f"\n{desc}")
    print("-" * 60)
    result = subprocess.run(args, cwd=str(APP_DIR))
    if result.returncode != 0:
        print(f"\n❌ Falha na etapa: {desc}")
        sys.exit(result.returncode)

def run_unit():
    cmd = [
        "docker", "run", "--rm",
        "-v", f"{M2_MOUNT}:/root/.m2",
        "-v", f"{APP_MOUNT}:/app",
        "-w", "/app",
        "maven:3.9-eclipse-temurin-21",
        "mvn", "test"
    ]
    run_cmd(cmd, "▶ [1/3] Executando Testes Unitários Java (Maven)...")

def run_integration():
    cmd = [
        "docker", "run", "--rm",
        "--network", "faturamed_default",
        "-v", f"{M2_MOUNT}:/root/.m2",
        "-v", f"{APP_MOUNT}:/app",
        "-w", "/app",
        "-e", "BASE_HOSPITALAR_TEST_DB=true",
        "-e", "DB_URL=jdbc:postgresql://postgres:5432/faturamed",
        "-e", "DB_USER=faturamed",
        "-e", "DB_PASSWORD=faturamed",
        "maven:3.9-eclipse-temurin-21",
        "mvn", "test",
        "-Dtest=BaseHospitalarIntegracaoTest,ChamadoAvisosIntegracaoTest,PerfilIntegracaoTest"
    ]
    run_cmd(cmd, "▶ [2/3] Executando Testes de Integração com Banco (PostgreSQL)...")

def run_js():
    cmd = [
        "docker", "run", "--rm",
        "-v", f"{APP_MOUNT}:/app",
        "-w", "/app",
        "node:20",
        "node", "--test",
        "src/test/js/demonstrativo.test.cjs",
        "src/test/js/selecao.test.cjs",
        "src/test/js/servicedesk.test.cjs",
        "scripts/painel-medico.test.cjs"
    ]
    run_cmd(cmd, "▶ [3/3] Executando Testes Frontend JavaScript (Node.js)...")

def main():
    target = sys.argv[1].lower() if len(sys.argv) > 1 else "all"

    print("=" * 60)
    print("  Faturamed - Execução de Testes Automatizados (Multiplataforma)")
    print(f"  Diretório da aplicação: {APP_DIR}")
    print(f"  Alvo: {target}")
    print("=" * 60)

    if target == "unit":
        run_unit()
    elif target == "integration":
        run_integration()
    elif target == "js":
        run_js()
    elif target == "all":
        run_unit()
        run_integration()
        run_js()
        print("\n" + "=" * 60)
        print("  ✔ Todos os testes foram executados com sucesso!")
        print("=" * 60)
    else:
        print(f"Alvo desconhecido: '{target}'. Use: all, unit, integration ou js")
        sys.exit(1)

    try:
        gen = ROOT_DIR / 'scripts' / 'gerar_dashboard_testes.py'
        if not gen.exists():
            gen = APP_DIR / 'scripts' / 'gerar_dashboard_testes.py'
        if gen.exists():
            subprocess.run([sys.executable, str(gen)], check=False)
    except Exception:
        pass

if __name__ == "__main__":
    main()
