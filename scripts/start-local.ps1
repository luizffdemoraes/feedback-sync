# Script PowerShell para iniciar o ambiente local com Docker Compose
# Uso: .\scripts\start-local.ps1

Write-Host "🚀 Iniciando ambiente local com Docker Compose..." -ForegroundColor Cyan

# Verifica se o Docker está rodando
Write-Host "🔍 Verificando se o Docker está rodando..." -ForegroundColor Cyan

$maxRetries = 3
$retryCount = 0
$dockerReady = $false

while ($retryCount -lt $maxRetries -and -not $dockerReady) {
    try {
        $dockerInfo = docker info 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0) {
            $dockerReady = $true
            Write-Host "✅ Docker está rodando" -ForegroundColor Green
        } else {
            throw "Docker não está acessível"
        }
    } catch {
        $retryCount++
        if ($retryCount -lt $maxRetries) {
            Write-Host "⚠️  Docker não está acessível. Tentativa $retryCount/$maxRetries..." -ForegroundColor Yellow
            
            # Tenta encontrar e iniciar o Docker Desktop
            $dockerPaths = @(
                "C:\Program Files\Docker\Docker\Docker Desktop.exe",
                "${env:ProgramFiles}\Docker\Docker\Docker Desktop.exe",
                "${env:ProgramFiles(x86)}\Docker\Docker\Docker Desktop.exe",
                "$env:LOCALAPPDATA\Docker\Docker Desktop.exe"
            )
            
            $dockerFound = $false
            foreach ($path in $dockerPaths) {
                if (Test-Path $path) {
                    Write-Host "   Tentando iniciar o Docker Desktop..." -ForegroundColor Cyan
                    Start-Process $path -ErrorAction SilentlyContinue
                    $dockerFound = $true
                    Write-Host "   Aguarde 30 segundos para o Docker iniciar..." -ForegroundColor Yellow
                    Start-Sleep -Seconds 30
                    break
                }
            }
            
            if (-not $dockerFound) {
                Write-Host "❌ Docker Desktop não encontrado!" -ForegroundColor Red
                Write-Host ""
                Write-Host "💡 Soluções:" -ForegroundColor Yellow
                Write-Host "   1. Instale o Docker Desktop: https://www.docker.com/products/docker-desktop" -ForegroundColor White
                Write-Host "   2. Abra o Docker Desktop manualmente" -ForegroundColor White
                Write-Host "   3. Aguarde até que o Docker esteja completamente iniciado" -ForegroundColor White
                Write-Host "   4. Execute este script novamente" -ForegroundColor White
                Write-Host ""
                Write-Host "   Para verificar manualmente: docker info" -ForegroundColor Gray
                exit 1
            }
        } else {
            Write-Host "❌ Docker não está rodando ou não está acessível!" -ForegroundColor Red
            Write-Host ""
            Write-Host "💡 Soluções:" -ForegroundColor Yellow
            Write-Host "   1. Abra o Docker Desktop manualmente" -ForegroundColor White
            Write-Host "   2. Aguarde até que o Docker esteja completamente iniciado (ícone verde na bandeja)" -ForegroundColor White
            Write-Host "   3. Execute este script novamente" -ForegroundColor White
            Write-Host ""
            Write-Host "   Para verificar manualmente: docker info" -ForegroundColor Gray
            Write-Host "   Para verificar serviços: Get-Service *docker*" -ForegroundColor Gray
            exit 1
        }
    }
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

