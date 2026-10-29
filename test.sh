#!/usr/bin/env bash
# ==============================================================================
# Faturamed - Executor de Testes Automatizados via Linha de Comando (Docker)
# ==============================================================================
set -e

# Garante que roda a partir do diretorio do script
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -d "$ROOT_DIR/faturamed" ]; then
  APP_DIR="$ROOT_DIR/faturamed"
else
  APP_DIR="$ROOT_DIR"
fi

cd "$APP_DIR"

TIPO="${1:-all}"

echo "=========================================================="
echo "  Faturamed - Execução de Testes Automatizados"
echo "  Diretório: $APP_DIR"
echo "  Modo: $TIPO"
echo "=========================================================="

run_unit() {
  echo ""
  echo "▶ [1/3] Executando Testes Unitários Java (Maven)..."
  docker run --rm \
    -v ~/.m2:/root/.m2 \
    -v "$APP_DIR":/app \
    -w /app \
    maven:3.9-eclipse-temurin-21 \
    mvn test
}

run_integration() {
  echo ""
  echo "▶ [2/3] Executando Testes de Integração com Banco (PostgreSQL)..."
  docker run --rm \
    --network faturamed_default \
    -v ~/.m2:/root/.m2 \
    -v "$APP_DIR":/app \
    -w /app \
    -e BASE_HOSPITALAR_TEST_DB=true \
    -e DB_URL=jdbc:postgresql://postgres:5432/faturamed \
    -e DB_USER=faturamed \
    -e DB_PASSWORD=faturamed \
    maven:3.9-eclipse-temurin-21 \
    mvn test -Dtest=BaseHospitalarIntegracaoTest,ChamadoAvisosIntegracaoTest,PerfilIntegracaoTest
}

run_js() {
  echo ""
  echo "▶ [3/3] Executando Testes Frontend JavaScript (Node.js)..."
  docker run --rm \
    -v "$APP_DIR":/app \
    -w /app \
    node:20 \
    node --test \
      src/test/js/demonstrativo.test.cjs \
      src/test/js/selecao.test.cjs \
      src/test/js/servicedesk.test.cjs \
      scripts/painel-medico.test.cjs
}

case "$TIPO" in
  unit)
    run_unit
    ;;
  integration)
    run_integration
    ;;
  js)
    run_js
    ;;
  all)
    run_unit
    run_integration
    run_js
    echo ""
    echo "=========================================================="
    echo "  ✔ Todos os testes foram executados com sucesso!"
    echo "=========================================================="
    python3 "/scripts/gerar_dashboard_testes.py" 2>/dev/null || python3 "/scripts/gerar_dashboard_testes.py" 2>/dev/null || true
    ;;
  *)
    echo "Uso: ./test.sh [unit | integration | js | all]"
    exit 1
    ;;
esac
