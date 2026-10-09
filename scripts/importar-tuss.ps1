param(
    [string]$ApiUrl = 'http://localhost:8080',
    [string]$Versao = ('ANS-OCL-consulta-' + (Get-Date -Format 'yyyy-MM-dd'))
)
$ErrorActionPreference = 'Stop'
$base = 'https://consulta-ocl.apps.sa-1a.mendixcloud.com/rest/oclservice/ANS'
$sources = Invoke-RestMethod "$base/source" -TimeoutSec 60
$expected = ($sources | Where-Object Codigo -eq 'tuss-22').Total_sources
if (-not $expected) { throw 'Contagem oficial da TUSS 22 indisponivel.' }
$items = [System.Collections.Generic.List[object]]::new()
$seen = [System.Collections.Generic.HashSet[string]]::new()
for ($page = 1; $page -le 1000; $page++) {
    Write-Progress -Activity 'Importando TUSS 22' -Status "Pagina $page, $($items.Count) de $expected codigos" -PercentComplete ([Math]::Min(100, 100 * $items.Count / $expected))
    try {
        $response = Invoke-RestMethod "$base/concepts/tuss-22?page=$page" -TimeoutSec 30
    } catch {
        throw "Falha na pagina $page da ANS. Nenhum catalogo foi gravado. $($_.Exception.Message)"
    }
    $batch = @($response | ForEach-Object { $_ })
    if ($batch.Count -eq 0) { break }
    foreach ($item in $batch) {
        if ($item.source -ne 'tuss-22' -or -not $seen.Add([string]$item.id)) {
            throw "Resposta inesperada ou codigo repetido na pagina $page."
        }
        $start = if ($item.extras.inicio_vigencia -and $item.extras.inicio_vigencia -ne '-') {
            ([datetime]::ParseExact($item.extras.inicio_vigencia, 'yyyy-MM-dd', [cultureinfo]::InvariantCulture)).ToString('yyyy-MM-dd')
        } else { $null }
        $end = if ($item.extras.fim_vigencia -and $item.extras.fim_vigencia -ne '-') {
            ([datetime]::ParseExact($item.extras.fim_vigencia, 'yyyy-MM-dd', [cultureinfo]::InvariantCulture)).ToString('yyyy-MM-dd')
        } else { $null }
        $items.Add(@{codigo = [string]$item.id; descricao = $item.display_name; inicioVigencia = $start; fimVigencia = $end})
    }
    if ($items.Count -ge $expected) { break }
}
Write-Progress -Activity 'Importando TUSS 22' -Completed
$sourcesAfter = Invoke-RestMethod "$base/source" -TimeoutSec 60
$expectedAfter = ($sourcesAfter | Where-Object Codigo -eq 'tuss-22').Total_sources
if ($items.Count -ne $expected -or $expectedAfter -ne $expected) {
    throw "Carga incompleta ou fonte alterada: recebidos $($items.Count), esperados $expected."
}
$body = @{tabela = 'TUSS'; versao = $Versao; fonte = "$base/concepts/tuss-22";
    consultadoEm = (Get-Date -Format 'yyyy-MM-dd'); procedimentos = $items.ToArray()} | ConvertTo-Json -Depth 6 -Compress
Invoke-RestMethod "$ApiUrl/api/catalogos" -Method Post -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 120
