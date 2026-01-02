# Script para executar Azure Functions localmente
# Este script compila e executa as Azure Functions usando Azure Functions Core Tools

Write-Host "🚀 Executando Azure Functions Localmente" -ForegroundColor Cyan
Write-Host ""

# Verificar e encontrar Azure Functions Core Tools
Write-Host "Verificando Azure Functions Core Tools..." -ForegroundColor Yellow

$funcPath = $null
$possiblePaths = @(
    "C:\Program Files\Microsoft\Azure Functions Core Tools\func.exe",
    "$env:ProgramFiles\Microsoft\Azure Functions Core Tools\func.exe",
    "$env:LOCALAPPDATA\Programs\Azure Functions Core Tools\func.exe"
)

foreach ($path in $possiblePaths) {
    if (Test-Path $path) {
        $funcPath = $path
        break
    }
}

# Tentar encontrar no PATH
if (-not $funcPath) {
    try {
        $funcVersion = func --version 2>&1
        if ($LASTEXITCODE -eq 0) {
            $funcPath = "func"
        }
    } catch {
        # func não está no PATH
    }
}

if (-not $funcPath) {
    Write-Host "   [X] Azure Functions Core Tools não encontrado" -ForegroundColor Red
    Write-Host ""
    Write-Host "Por favor, adicione ao PATH ou reinicie o terminal:" -ForegroundColor Yellow
    Write-Host "   C:\Program Files\Microsoft\Azure Functions Core Tools" -ForegroundColor White
    Write-Host ""
    Write-Host "Ou adicione temporariamente ao PATH neste terminal:" -ForegroundColor Yellow
    Write-Host "   `$env:Path += ';C:\Program Files\Microsoft\Azure Functions Core Tools'" -ForegroundColor White
    Write-Host ""
    Write-Host "Depois reinicie o terminal ou execute o comando acima." -ForegroundColor Yellow
    exit 1
}

Write-Host "   [OK] Azure Functions Core Tools encontrado" -ForegroundColor Green

# Verificar versão
try {
    if ($funcPath -eq "func") {
        $funcVersion = func --version 2>&1
    } else {
        $funcVersion = & $funcPath --version 2>&1
    }
    Write-Host "   Versão: $funcVersion" -ForegroundColor Gray
} catch {
    Write-Host "   [⚠] Não foi possível verificar a versão" -ForegroundColor Yellow
}

Write-Host ""

# Verificar se Docker está rodando
Write-Host "Verificando Docker (Azurite)..." -ForegroundColor Yellow
try {
    $containerNames = docker ps --format "{{.Names}}" 2>$null
    $azurite = $containerNames | Where-Object { $_ -match 'azurite' }
    
    if (-not $azurite) {
        Write-Host "   [X] Azurite não está rodando" -ForegroundColor Red
        Write-Host ""
        Write-Host "Iniciando Azurite..." -ForegroundColor Yellow
        docker compose up -d
        Start-Sleep -Seconds 5
        Write-Host "   [OK] Azurite iniciado" -ForegroundColor Green
    } else {
        Write-Host "   [OK] Azurite: rodando" -ForegroundColor Green
    }
} catch {
    Write-Host "   [X] Erro ao verificar Docker" -ForegroundColor Red
    Write-Host "   Certifique-se de que o Docker está rodando" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# Verificar configurações do Mailtrap
Write-Host "Verificando configurações do Mailtrap..." -ForegroundColor Yellow
$mailtrapToken = $env:MAILTRAP_API_TOKEN
$adminEmail = $env:ADMIN_EMAIL

if ([string]::IsNullOrWhiteSpace($mailtrapToken)) {
    Write-Host "   [⚠] MAILTRAP_API_TOKEN não configurado" -ForegroundColor Yellow
    Write-Host "   Configure: `$env:MAILTRAP_API_TOKEN = 'seu-token'" -ForegroundColor Gray
} else {
    Write-Host "   [OK] MAILTRAP_API_TOKEN configurado" -ForegroundColor Green
}

if ([string]::IsNullOrWhiteSpace($adminEmail)) {
    Write-Host "   [⚠] ADMIN_EMAIL não configurado" -ForegroundColor Yellow
    Write-Host "   Configure: `$env:ADMIN_EMAIL = 'seu-email@exemplo.com'" -ForegroundColor Gray
} else {
    Write-Host "   [OK] ADMIN_EMAIL configurado: $adminEmail" -ForegroundColor Green
}

Write-Host ""

# Navegar para o diretório raiz do projeto
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptPath
Set-Location $projectRoot

# Compilar o projeto
Write-Host "Compilando projeto..." -ForegroundColor Yellow
Write-Host "   (Isso pode levar alguns minutos na primeira vez)" -ForegroundColor Gray
Write-Host ""

.\mvnw.cmd clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ Erro ao compilar o projeto" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✅ Compilação concluída" -ForegroundColor Green
Write-Host ""

# Verificar se o diretório de output existe
$functionsDir = Join-Path $projectRoot "target\azure-functions\feedback-service-app"
if (-not (Test-Path $functionsDir)) {
    Write-Host "❌ Diretório de Azure Functions não encontrado: $functionsDir" -ForegroundColor Red
    Write-Host "   Verifique se a compilação foi bem-sucedida" -ForegroundColor Yellow
    exit 1
}

# Copiar local.settings.json para o diretório de output
$localSettingsSource = Join-Path $projectRoot "src\main\resources\local.settings.json"
$localSettingsTarget = Join-Path $functionsDir "local.settings.json"

if (Test-Path $localSettingsSource) {
    Copy-Item -Path $localSettingsSource -Destination $localSettingsTarget -Force
    Write-Host "✅ Configurações locais copiadas" -ForegroundColor Green
} else {
    Write-Host "⚠️  Arquivo local.settings.json não encontrado" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "🚀 Iniciando Azure Functions..." -ForegroundColor Cyan
Write-Host ""
Write-Host "DICA: Quando ver 'Host started', as funções estarão prontas!" -ForegroundColor Green
Write-Host "      Você verá os logs quando mensagens chegarem na fila." -ForegroundColor Green
Write-Host ""
Write-Host "Para testar, crie um feedback crítico em outro terminal:" -ForegroundColor Yellow
Write-Host "   curl --location 'http://localhost:7071/avaliacao' `" -ForegroundColor Gray
Write-Host "   --header 'Content-Type: application/json' `" -ForegroundColor Gray
Write-Host "   --data '{`"descricao`":`"Teste`",`"nota`":2,`"urgencia`":`"HIGH`"}'" -ForegroundColor Gray
Write-Host ""

# Navegar para o diretório de Azure Functions e executar
Set-Location $functionsDir

# Executar func usando o caminho completo se necessário
if ($funcPath -eq "func") {
    func start
} else {
    & $funcPath start
}
