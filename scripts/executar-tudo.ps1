# Script para executar tudo - Bypass de política de execução
# Uso: powershell -ExecutionPolicy Bypass -File .\scripts\executar-tudo.ps1

Write-Host "🚀 Iniciando ambiente completo..." -ForegroundColor Cyan
Write-Host ""

# Passo 1: Verificar Docker
Write-Host "1️⃣ Verificando Docker..." -ForegroundColor Yellow
try {
    $dockerInfo = docker info 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   ✅ Docker está rodando" -ForegroundColor Green
    } else {
        throw "Docker não está acessível"
    }
} catch {
    Write-Host "   ❌ Docker não está rodando" -ForegroundColor Red
    Write-Host "   💡 Abra o Docker Desktop manualmente e aguarde iniciar" -ForegroundColor Yellow
    Write-Host "   Pressione qualquer tecla após o Docker iniciar..." -ForegroundColor Yellow
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
}

# Passo 2: Iniciar serviços
Write-Host ""
Write-Host "2️⃣ Iniciando serviços Azure..." -ForegroundColor Yellow
docker-compose down
docker-compose up -d

Write-Host "   ⏳ Aguardando serviços iniciarem (30 segundos)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Passo 3: Verificar serviços
Write-Host ""
Write-Host "3️⃣ Verificando serviços..." -ForegroundColor Yellow
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

Write-Host ""
Write-Host "✅ Ambiente pronto!" -ForegroundColor Green
Write-Host ""
Write-Host "💡 Próximos passos:" -ForegroundColor Cyan
Write-Host "   1. Em outro terminal, execute:" -ForegroundColor White
Write-Host "      .\mvnw.cmd quarkus:dev -Dquarkus.profile=local" -ForegroundColor Gray
Write-Host ""
Write-Host "   2. Após a aplicação iniciar, teste com:" -ForegroundColor White
Write-Host "      .\scripts\test-api.ps1" -ForegroundColor Gray
Write-Host ""

