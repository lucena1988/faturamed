# ==============================================================================
# Faturamed - Executor de Testes Automatizados para Windows (PowerShell)
# ==============================================================================
param(
    [ValidateSet("all", "unit", "integration", "js")]
    [string]$Tipo = "all"
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (Test-Path "$ScriptDir\faturamed\pom.xml") {
    $AppDir = (Resolve-Path "$ScriptDir\faturamed").Path
} else {
    $AppDir = (Resolve-Path "$ScriptDir").Path
}

$M2Dir = "$HOME\.m2"
if (!(Test-Path $M2Dir)) {
    New-Item -ItemType Directory -Force -Path $M2Dir | Out-Null
}

# Converte caminhos para formato compatível com Docker
$AppMount = $AppDir -replace '\\', '/'
$M2Mount = ((Resolve-Path $M2Dir).Path) -replace '\\', '/'

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Faturamed - Execução de Testes Automatizados (Windows)" -ForegroundColor Cyan
Write-Host "  Diretório: $AppDir" -ForegroundColor Gray
Write-Host "  Modo: $Tipo" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor Cyan

function Run-Unit {
    Write-Host "`n▶ [1/3] Executando Testes Unitários Java (Maven)..." -ForegroundColor Yellow
    docker run --rm `
        -v "${M2Mount}:/root/.m2" `
        -v "${AppMount}:/app" `
        -w /app `
        maven:3.9-eclipse-temurin-21 `
        mvn test
    if ($LASTEXITCODE -ne 0) { throw "Falha nos testes unitarios" }
}

function Run-Integration {
    Write-Host "`n▶ [2/3] Executando Testes de Integração com Banco (PostgreSQL)..." -ForegroundColor Yellow
    docker run --rm `
        --network faturamed_default `
        -v "${M2Mount}:/root/.m2" `
        -v "${AppMount}:/app" `
        -w /app `
        -e BASE_HOSPITALAR_TEST_DB=true `
        -e DB_URL=jdbc:postgresql://postgres:5432/faturamed `
        -e DB_USER=faturamed `
        -e DB_PASSWORD=faturamed `
        maven:3.9-eclipse-temurin-21 `
        mvn test -Dtest=BaseHospitalarIntegracaoTest,ChamadoAvisosIntegracaoTest,PerfilIntegracaoTest
    if ($LASTEXITCODE -ne 0) { throw "Falha nos testes de integracao" }
}

function Run-Js {
    Write-Host "`n▶ [3/3] Executando Testes Frontend JavaScript (Node.js)..." -ForegroundColor Yellow
    docker run --rm `
        -v "${AppMount}:/app" `
        -w /app `
        node:20 `
        node --test `
            src/test/js/demonstrativo.test.cjs `
            src/test/js/selecao.test.cjs `
            src/test/js/servicedesk.test.cjs `
            scripts/painel-medico.test.cjs
    if ($LASTEXITCODE -ne 0) { throw "Falha nos testes javascript" }
}

switch ($Tipo) {
    "unit" { Run-Unit }
    "integration" { Run-Integration }
    "js" { Run-Js }
    "all" {
        Run-Unit
        Run-Integration
        Run-Js
        Write-Host "`n==========================================================" -ForegroundColor Green
        Write-Host "  ✔ Todos os testes foram executados com sucesso!" -ForegroundColor Green
        Write-Host "==========================================================" -ForegroundColor Green
    }
}

try {
    if (Test-Path "\scripts\gerar_dashboard_testes.py") {
        python "\scripts\gerar_dashboard_testes.py"
    }
} catch {}
