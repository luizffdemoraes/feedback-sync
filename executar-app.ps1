# Script para executar a aplicação Quarkus
# Este script mostra todos os logs em tempo real

Write-Host "🚀 Iniciando aplicação Quarkus..." -ForegroundColor Cyan
Write-Host ""
Write-Host "📋 Verificando pré-requisitos..." -ForegroundColor Yellow

# Verifica se os containers estão rodando
$containers = docker ps --format "{{.Names}}" | Where-Object { $_ -match "cosmos|azurite|servicebus" }
if ($containers.Count -lt 3) {
    Write-Host "⚠️  Nem todos os containers estão rodando!" -ForegroundColor Yellow
    Write-Host "   Execute: docker-compose up -d" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host "✅ Containers Docker estão rodando" -ForegroundColor Green
    Write-Host ""
}

Write-Host "🔧 Compilando e iniciando aplicação..." -ForegroundColor Cyan
Write-Host "   (Isso pode levar 1-2 minutos na primeira vez)" -ForegroundColor Gray
Write-Host ""
Write-Host "💡 Quando ver 'Listening on: http://localhost:7071', a aplicação estará pronta!" -ForegroundColor Green
Write-Host ""

# Executa a aplicação (não em background para ver os logs)
# No PowerShell, o -D precisa estar entre aspas
$env:QUARKUS_PROFILE = "local"
.\mvnw.cmd quarkus:dev

