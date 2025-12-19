# Script para diagnosticar e corrigir problemas do Docker
# Uso: .\scripts\fix-docker.ps1

Write-Host "🔧 Diagnosticando problemas do Docker..." -ForegroundColor Cyan
Write-Host ""

# 1. Verificar se o Docker está instalado
Write-Host "1️⃣ Verificando instalação do Docker..." -ForegroundColor Yellow
if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "   ✅ Comando 'docker' encontrado" -ForegroundColor Green
    docker --version
} else {
    Write-Host "   ❌ Docker não está instalado ou não está no PATH" -ForegroundColor Red
    Write-Host "   💡 Instale o Docker Desktop: https://www.docker.com/products/docker-desktop" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# 2. Verificar se o Docker Desktop está rodando
Write-Host "2️⃣ Verificando se o Docker Desktop está rodando..." -ForegroundColor Yellow
try {
    $dockerInfo = docker info 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   ✅ Docker está rodando e acessível" -ForegroundColor Green
        docker version --format "   Server: {{.Server.Version}}"
        docker version --format "   Client: {{.Client.Version}}"
    } else {
        throw "Docker não está acessível"
    }
} catch {
    Write-Host "   ❌ Docker não está acessível" -ForegroundColor Red
    Write-Host ""
    
    # 3. Verificar serviços Docker
    Write-Host "3️⃣ Verificando serviços Docker..." -ForegroundColor Yellow
    $dockerServices = Get-Service | Where-Object { $_.Name -like "*docker*" }
    if ($dockerServices) {
        Write-Host "   Serviços Docker encontrados:" -ForegroundColor Cyan
        $dockerServices | ForEach-Object {
            $status = if ($_.Status -eq "Running") { "✅" } else { "❌" }
            Write-Host "   $status $($_.Name): $($_.Status)" -ForegroundColor $(if ($_.Status -eq "Running") { "Green" } else { "Red" })
        }
    } else {
        Write-Host "   ⚠️  Nenhum serviço Docker encontrado" -ForegroundColor Yellow
    }
    
    Write-Host ""
    
    # 4. Tentar encontrar e iniciar o Docker Desktop
    Write-Host "4️⃣ Tentando iniciar o Docker Desktop..." -ForegroundColor Yellow
    $dockerPaths = @(
        "C:\Program Files\Docker\Docker\Docker Desktop.exe",
        "${env:ProgramFiles}\Docker\Docker\Docker Desktop.exe",
        "${env:ProgramFiles(x86)}\Docker\Docker\Docker Desktop.exe",
        "$env:LOCALAPPDATA\Docker\Docker Desktop.exe"
    )
    
    $dockerFound = $false
    foreach ($path in $dockerPaths) {
        if (Test-Path $path) {
            Write-Host "   ✅ Docker Desktop encontrado em: $path" -ForegroundColor Green
            Write-Host "   🚀 Iniciando Docker Desktop..." -ForegroundColor Cyan
            
            # Verifica se já está rodando
            $process = Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue
            if ($process) {
                Write-Host "   ⚠️  Docker Desktop já está em execução" -ForegroundColor Yellow
                Write-Host "   💡 Aguarde alguns segundos e tente novamente" -ForegroundColor Yellow
            } else {
                Start-Process $path
                Write-Host "   ⏳ Aguarde 30-60 segundos para o Docker iniciar completamente..." -ForegroundColor Yellow
                Write-Host "   💡 Execute este script novamente após o Docker iniciar" -ForegroundColor Yellow
            }
            $dockerFound = $true
            break
        }
    }
    
    if (-not $dockerFound) {
        Write-Host "   ❌ Docker Desktop não encontrado" -ForegroundColor Red
        Write-Host ""
        Write-Host "💡 Soluções:" -ForegroundColor Yellow
        Write-Host "   1. Instale o Docker Desktop:" -ForegroundColor White
        Write-Host "      https://www.docker.com/products/docker-desktop" -ForegroundColor Gray
        Write-Host ""
        Write-Host "   2. Após instalar, reinicie o computador" -ForegroundColor White
        Write-Host ""
        Write-Host "   3. Inicie o Docker Desktop manualmente" -ForegroundColor White
        Write-Host ""
        exit 1
    }
    
    Write-Host ""
    Write-Host "⏳ Aguardando Docker iniciar..." -ForegroundColor Cyan
    Write-Host "   (Isso pode levar 1-2 minutos)" -ForegroundColor Gray
    
    # Aguarda até 2 minutos
    $timeout = 120
    $elapsed = 0
    $interval = 5
    
    while ($elapsed -lt $timeout) {
        Start-Sleep -Seconds $interval
        $elapsed += $interval
        
        try {
            $test = docker info 2>&1
            if ($LASTEXITCODE -eq 0) {
                Write-Host ""
                Write-Host "✅ Docker está pronto!" -ForegroundColor Green
                exit 0
            }
        } catch {
            # Continua aguardando
        }
        
        $progress = [math]::Min(($elapsed / $timeout) * 100, 100)
        $progressPercent = [math]::Round($progress)
        Write-Host "   Aguardando... ($progressPercent%)" -ForegroundColor Gray
    }
    
    Write-Host ""
    Write-Host "⚠️  Timeout aguardando Docker iniciar" -ForegroundColor Yellow
    Write-Host "   Verifique manualmente se o Docker Desktop está rodando" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "✅ Docker está funcionando corretamente!" -ForegroundColor Green
Write-Host ""
Write-Host "Proximo passo: Execute o script de inicializacao:" -ForegroundColor Cyan
Write-Host "   .\scripts\start-local.ps1" -ForegroundColor White

