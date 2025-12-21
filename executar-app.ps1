# Script para executar a aplicacao Quarkus
# Este script mostra todos os logs em tempo real

Write-Host "Iniciando aplicacao Quarkus..." -ForegroundColor Cyan
Write-Host ""
Write-Host "Verificando pre-requisitos..." -ForegroundColor Yellow

# Verifica se os containers estao rodando
Write-Host "Verificando containers Docker..." -ForegroundColor Yellow
try {
    $containerNames = docker ps --format "{{.Names}}" 2>$null
    $cosmos = $containerNames | Where-Object { $_ -match 'cosmos' }
    $azurite = $containerNames | Where-Object { $_ -match 'azurite' }
    $servicebus = $containerNames | Where-Object { $_ -match 'servicebus' }
    $sqlserver = $containerNames | Where-Object { $_ -match 'sqlserver' }
    
    $todosRodando = $true
    
    if (-not $cosmos) {
        Write-Host "   ✗ Cosmos DB não está rodando" -ForegroundColor Red
        $todosRodando = $false
    } else {
        Write-Host "   ✓ Cosmos DB: rodando" -ForegroundColor Green
    }
    
    if (-not $azurite) {
        Write-Host "   ✗ Azurite não está rodando" -ForegroundColor Red
        $todosRodando = $false
    } else {
        Write-Host "   ✓ Azurite: rodando" -ForegroundColor Green
    }
    
    if (-not $sqlserver) {
        Write-Host "   ✗ SQL Server não está rodando" -ForegroundColor Red
        $todosRodando = $false
    } else {
        Write-Host "   ✓ SQL Server: rodando" -ForegroundColor Green
    }
    
    if (-not $servicebus) {
        Write-Host "   ✗ Service Bus não está rodando" -ForegroundColor Red
        $todosRodando = $false
    } else {
        Write-Host "   ✓ Service Bus: rodando" -ForegroundColor Green
    }
    
    if (-not $todosRodando) {
        Write-Host ""
        Write-Host "ATENCAO: Nem todos os containers estao rodando!" -ForegroundColor Yellow
        Write-Host "   Execute: .\scripts\iniciar-ambiente-local.ps1" -ForegroundColor White
        Write-Host "   Ou: docker compose up -d" -ForegroundColor White
        Write-Host ""
        Write-Host "Deseja continuar mesmo assim? (S/N)" -ForegroundColor Yellow
        $continuar = Read-Host
        if ($continuar -ne "S" -and $continuar -ne "s") {
            Write-Host "Execução cancelada" -ForegroundColor Yellow
            exit 0
        }
        Write-Host ""
    } else {
        Write-Host ""
        Write-Host "OK: Todos os containers Docker estao rodando" -ForegroundColor Green
        Write-Host ""
    }
} catch {
    Write-Host "ATENCAO: Nao foi possivel verificar containers Docker" -ForegroundColor Yellow
    Write-Host "   Certifique-se de que o Docker está rodando" -ForegroundColor White
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
