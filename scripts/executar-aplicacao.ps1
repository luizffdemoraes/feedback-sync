# Script para executar a aplicacao Quarkus
# Este script mostra todos os logs em tempo real

Write-Host "Iniciando aplicacao Quarkus..." -ForegroundColor Cyan
Write-Host ""
Write-Host "Verificando pre-requisitos..." -ForegroundColor Yellow

# Verifica se os containers estao rodando
Write-Host "Verificando containers Docker..." -ForegroundColor Yellow
try {
    $containerNames = docker ps --format "{{.Names}}" 2>$null
    $azurite = $containerNames | Where-Object { $_ -match 'azurite' }
    
    if (-not $azurite) {
        Write-Host "   [X] Azurite nao esta rodando (Table Storage, Blob Storage)" -ForegroundColor Red
        Write-Host ""
        Write-Host "ATENCAO: Container Docker nao esta rodando!" -ForegroundColor Yellow
        Write-Host "   Execute: docker compose up -d" -ForegroundColor White
        Write-Host ""
        Write-Host "Deseja continuar mesmo assim? (S/N)" -ForegroundColor Yellow
        $continuar = Read-Host
        if ($continuar -ne "S" -and $continuar -ne "s") {
            Write-Host "Execucao cancelada" -ForegroundColor Yellow
            exit 0
        }
        Write-Host ""
    } else {
        Write-Host "   [OK] Azurite: rodando (Table Storage, Blob Storage)" -ForegroundColor Green
        Write-Host ""
        Write-Host "OK: Container Docker esta rodando" -ForegroundColor Green
        Write-Host ""
    }
} catch {
    Write-Host "ATENCAO: Nao foi possivel verificar containers Docker" -ForegroundColor Yellow
    Write-Host "   Certifique-se de que o Docker esta rodando" -ForegroundColor White
    Write-Host ""
}

Write-Host "Compilando e iniciando aplicacao..." -ForegroundColor Cyan
Write-Host "   (Isso pode levar 1-2 minutos na primeira vez)" -ForegroundColor Gray
Write-Host ""
Write-Host "DICA: Quando ver 'Listening on: http://localhost:7071', a aplicacao estara pronta!" -ForegroundColor Green
Write-Host ""

# Executa a aplicacao (nao em background para ver os logs)
# No PowerShell, o -D precisa estar entre aspas
$env:QUARKUS_PROFILE = 'local'
.\mvnw.cmd quarkus:dev
