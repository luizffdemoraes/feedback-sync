# Script PowerShell para iniciar o ambiente local com Docker Compose
# Uso: .\scripts\start-local.ps1

Write-Host "🚀 Iniciando ambiente local com Docker Compose..." -ForegroundColor Cyan

# Verifica se o Docker está rodando
try {
    docker info | Out-Null
} catch {
    Write-Host "❌ Docker não está rodando. Por favor, inicie o Docker Desktop." -ForegroundColor Red
    exit 1
}

# Verifica se o docker-compose está instalado
if (-not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    Write-Host "❌ docker-compose não está instalado." -ForegroundColor Red
    exit 1
}

# Para containers existentes (se houver)
Write-Host "🛑 Parando containers existentes..." -ForegroundColor Yellow
docker-compose down

# Inicia os serviços
Write-Host "📦 Iniciando serviços Azure (Cosmos DB, Azurite, Service Bus)..." -ForegroundColor Cyan
docker-compose up -d

# Aguarda os serviços ficarem prontos
Write-Host "⏳ Aguardando serviços ficarem prontos..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Verifica saúde dos serviços
Write-Host "🏥 Verificando saúde dos serviços..." -ForegroundColor Cyan

# Cosmos DB
try {
    docker exec cosmos-emulator curl -k -f https://localhost:8081/_explorer/emulator.pem | Out-Null
    Write-Host "✅ Cosmos DB Emulator está rodando" -ForegroundColor Green
} catch {
    Write-Host "⚠️  Cosmos DB Emulator ainda não está pronto (aguarde alguns segundos)" -ForegroundColor Yellow
}

# Azurite
try {
    Invoke-WebRequest -Uri "http://localhost:10000/devstoreaccount1" -UseBasicParsing | Out-Null
    Write-Host "✅ Azurite está rodando" -ForegroundColor Green
} catch {
    Write-Host "⚠️  Azurite ainda não está pronto (aguarde alguns segundos)" -ForegroundColor Yellow
}

# Service Bus
try {
    Invoke-WebRequest -Uri "http://localhost:8080/health" -UseBasicParsing | Out-Null
    Write-Host "✅ Service Bus Emulator está rodando" -ForegroundColor Green
} catch {
    Write-Host "⚠️  Service Bus Emulator ainda não está pronto (aguarde alguns segundos)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "✅ Ambiente local iniciado!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Serviços disponíveis:" -ForegroundColor Cyan
Write-Host "   - Cosmos DB: https://localhost:8081"
Write-Host "   - Azurite Blob: http://localhost:10000"
Write-Host "   - Service Bus: http://localhost:8080 (Management API)"
Write-Host ""
Write-Host "🔍 Para ver os logs: docker-compose logs -f" -ForegroundColor Yellow
Write-Host "🛑 Para parar: docker-compose down" -ForegroundColor Yellow
Write-Host ""
Write-Host "💡 Próximo passo: Execute a aplicação com:" -ForegroundColor Cyan
Write-Host "   .\mvnw.cmd quarkus:dev -Dquarkus.profile=local" -ForegroundColor White

