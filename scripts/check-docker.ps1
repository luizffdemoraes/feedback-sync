# Script para verificar se o Docker está rodando
# Uso: .\scripts\check-docker.ps1

Write-Host "🔍 Verificando status do Docker..." -ForegroundColor Cyan
Write-Host ""

# Verifica se o comando docker existe
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "❌ Docker não está instalado ou não está no PATH" -ForegroundColor Red
    Write-Host ""
    Write-Host "💡 Instale o Docker Desktop em: https://www.docker.com/products/docker-desktop" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ Comando 'docker' encontrado" -ForegroundColor Green

# Verifica se o Docker está rodando
Write-Host "🔍 Verificando se o Docker está rodando..." -ForegroundColor Cyan
try {
    $dockerInfo = docker info 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Docker está rodando e acessível" -ForegroundColor Green
        Write-Host ""
        
        # Mostra informações básicas
        Write-Host "📊 Informações do Docker:" -ForegroundColor Cyan
        docker version --format "   Server Version: {{.Server.Version}}"
        docker version --format "   Client Version: {{.Client.Version}}"
        
        Write-Host ""
        Write-Host "✅ Pronto para usar!" -ForegroundColor Green
        exit 0
    } else {
        throw "Docker não está acessível"
    }
} catch {
    Write-Host "❌ Docker não está rodando ou não está acessível" -ForegroundColor Red
    Write-Host ""
    Write-Host "💡 Soluções:" -ForegroundColor Yellow
    Write-Host "   1. Abra o Docker Desktop" -ForegroundColor White
    Write-Host "   2. Aguarde até que o Docker esteja completamente iniciado" -ForegroundColor White
    Write-Host "      (Procure pelo ícone da baleia na bandeja do sistema)" -ForegroundColor Gray
    Write-Host "   3. Verifique se o serviço Docker está rodando:" -ForegroundColor White
    Write-Host "      Get-Service *docker*" -ForegroundColor Gray
    Write-Host ""
    Write-Host "   Para iniciar o Docker Desktop manualmente:" -ForegroundColor Yellow
    
    # Tenta encontrar o Docker Desktop em locais comuns
    $dockerPaths = @(
        "C:\Program Files\Docker\Docker\Docker Desktop.exe",
        "${env:ProgramFiles}\Docker\Docker\Docker Desktop.exe",
        "${env:ProgramFiles(x86)}\Docker\Docker\Docker Desktop.exe",
        "$env:LOCALAPPDATA\Docker\Docker Desktop.exe"
    )
    
    $dockerFound = $false
    foreach ($path in $dockerPaths) {
        if (Test-Path $path) {
            Write-Host "   Docker Desktop encontrado em: $path" -ForegroundColor Green
            Write-Host ""
            Write-Host "   Deseja iniciar o Docker Desktop agora? (S/N)" -ForegroundColor Yellow
            $response = Read-Host
            if ($response -eq 'S' -or $response -eq 's' -or $response -eq 'Y' -or $response -eq 'y') {
                Write-Host "   Iniciando Docker Desktop..." -ForegroundColor Cyan
                Start-Process $path
                Write-Host "   Aguarde 30-60 segundos para o Docker iniciar completamente..." -ForegroundColor Yellow
                Write-Host "   Execute este script novamente após o Docker iniciar." -ForegroundColor Yellow
            }
            $dockerFound = $true
            break
        }
    }
    
    if (-not $dockerFound) {
        Write-Host "   Docker Desktop não encontrado nos locais padrão." -ForegroundColor Red
        Write-Host "   Baixe e instale em: https://www.docker.com/products/docker-desktop" -ForegroundColor Yellow
    }
    
    Write-Host ""
    exit 1
}

