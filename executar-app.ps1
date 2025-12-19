# Script para executar a aplicacao Quarkus
# Este script mostra todos os logs em tempo real

Write-Host "Iniciando aplicacao Quarkus..." -ForegroundColor Cyan
Write-Host ""
Write-Host "Verificando pre-requisitos..." -ForegroundColor Yellow

# Verifica se os containers estao rodando
try {
    $containerNames = docker ps --format "{{.Names}}" 2>$null
    $containers = $containerNames | Where-Object { $_ -match 'cosmos|azurite|servicebus' }
    if ($null -eq $containers -or $containers.Count -lt 3) {
        Write-Host "ATENCAO: Nem todos os containers estao rodando!" -ForegroundColor Yellow
        Write-Host "   Execute: docker-compose up -d" -ForegroundColor White
        Write-Host ""
    } else {
        Write-Host "OK: Containers Docker estao rodando" -ForegroundColor Green
        Write-Host ""
    }
} catch {
    Write-Host "ATENCAO: Nao foi possivel verificar containers Docker" -ForegroundColor Yellow
    Write-Host ""
}

Write-Host "Compilando e iniciando aplicacao..." -ForegroundColor Cyan
Write-Host "   (Isso pode levar 1-2 minutos na primeira vez)" -ForegroundColor Gray
Write-Host ""
Write-Host "DICA: Quando ver 'Listening on: http://localhost:7071', a aplicacao estara pronta!" -ForegroundColor Green
Write-Host ""

# Executa a aplicacao (nao em background para ver os logs)
# No PowerShell, o -D precisa estar entre aspas
$env:QUARKUS_PROFILE = "local"
.\mvnw.cmd quarkus:dev
