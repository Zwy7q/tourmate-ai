param([string]$BackendUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$documents = Get-Content -LiteralPath (Join-Path $projectRoot 'data/itinerary-attractions.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($document in $documents) {
    $body = $document | ConvertTo-Json -Compress
    $saved = Invoke-RestMethod "$BackendUrl/api/knowledge/documents" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 120
    Write-Output "已导入：$($saved.title)"
}
Write-Output '这些景点、票价和开放规则均为虚构演示资料，不可用于实际购票。固定编号使重复导入更新同一份演示资料。'
