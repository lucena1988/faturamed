param([ValidateSet("all")][string]$Tipo = "all")
$ErrorActionPreference = "Stop"
$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
if ($pythonCommand) {
    & $pythonCommand.Source (Join-Path $PSScriptRoot "test.py") $Tipo
} else {
    $launcher = Get-Command py -ErrorAction SilentlyContinue
    if (!$launcher) { throw "Instale Python 3.10+ e as dependencias de requirements-test.txt (veja README)." }
    & $launcher.Source -3 (Join-Path $PSScriptRoot "test.py") $Tipo
}
exit $LASTEXITCODE
