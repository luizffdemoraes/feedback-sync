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
    Write-Host "   [X] MAILTRAP_API_TOKEN não configurado" -ForegroundColor Red
    Write-Host "   Configure: `$env:MAILTRAP_API_TOKEN = 'seu-token'" -ForegroundColor Gray
    Write-Host ""
    Write-Host "❌ É necessário configurar MAILTRAP_API_TOKEN antes de executar" -ForegroundColor Red
    exit 1
} else {
    Write-Host "   [OK] MAILTRAP_API_TOKEN configurado" -ForegroundColor Green
}

if ([string]::IsNullOrWhiteSpace($adminEmail)) {
    Write-Host "   [X] ADMIN_EMAIL não configurado" -ForegroundColor Red
    Write-Host "   Configure: `$env:ADMIN_EMAIL = 'seu-email@exemplo.com'" -ForegroundColor Gray
    Write-Host ""
    Write-Host "❌ É necessário configurar ADMIN_EMAIL antes de executar" -ForegroundColor Red
    exit 1
} else {
    Write-Host "   [OK] ADMIN_EMAIL configurado: $adminEmail" -ForegroundColor Green
}

Write-Host ""

# Navegar para o diretório raiz do projeto
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptPath
Set-Location $projectRoot

# Verificar se já existe compilação do Azure Functions
$functionsDir = Join-Path $projectRoot "target\azure-functions\feedback-service-app"
$needsCompile = $true

if (Test-Path $functionsDir) {
    Write-Host "Verificando se precisa recompilar..." -ForegroundColor Yellow
    $lastCompile = (Get-ChildItem $functionsDir -Recurse | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
    $sourceFiles = Get-ChildItem -Path (Join-Path $projectRoot "src") -Recurse -Include *.java | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    
    if ($sourceFiles -and $lastCompile -gt $sourceFiles.LastWriteTime) {
        Write-Host "   [OK] Compilação existente está atualizada" -ForegroundColor Green
        $needsCompile = $false
    } else {
        Write-Host "   [⚠] Código-fonte foi modificado, precisa recompilar" -ForegroundColor Yellow
    }
}

# Compilar o projeto (apenas se necessário)
if ($needsCompile) {
    Write-Host ""
    Write-Host "Compilando projeto..." -ForegroundColor Yellow
    Write-Host "   (Isso pode levar alguns minutos na primeira vez)" -ForegroundColor Gray
    Write-Host "   NOTA: Usando 'package' sem 'clean' para evitar conflito com aplicação rodando" -ForegroundColor Gray
    Write-Host ""
    
    # Usa apenas 'package' sem 'clean' para evitar conflito com aplicação Quarkus rodando
    .\mvnw.cmd package -DskipTests
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "❌ Erro ao compilar o projeto" -ForegroundColor Red
        Write-Host ""
        Write-Host "DICA: Se o erro for relacionado a arquivos em uso, pare a aplicação Quarkus primeiro (Ctrl+C)" -ForegroundColor Yellow
        exit 1
    }
    
    Write-Host ""
    Write-Host "✅ Compilação concluída" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "✅ Usando compilação existente" -ForegroundColor Green
}

# Verificar se o diretório de output existe
if (-not $functionsDir) {
    $functionsDir = Join-Path $projectRoot "target\azure-functions\feedback-service-app"
}
if (-not (Test-Path $functionsDir)) {
    Write-Host "❌ Diretório de Azure Functions não encontrado: $functionsDir" -ForegroundColor Red
    Write-Host "   Verifique se a compilação foi bem-sucedida" -ForegroundColor Yellow
    exit 1
}

# Atualizar local.settings.json com variáveis de ambiente
Write-Host "Atualizando configurações do Azure Functions..." -ForegroundColor Yellow
$localSettingsSource = Join-Path $projectRoot "src\main\resources\local.settings.json"
$localSettingsTarget = Join-Path $functionsDir "local.settings.json"

Write-Host "   Lendo variáveis de ambiente..." -ForegroundColor Gray
Write-Host "   - MAILTRAP_API_TOKEN: $(if ($mailtrapToken) { 'configurado (' + $mailtrapToken.Substring(0, [Math]::Min(8, $mailtrapToken.Length)) + '...)' } else { 'NÃO configurado' })" -ForegroundColor Gray
Write-Host "   - ADMIN_EMAIL: $(if ($adminEmail) { $adminEmail } else { 'NÃO configurado' })" -ForegroundColor Gray

if (Test-Path $localSettingsSource) {
    # Ler o JSON atual
    Write-Host "   Lendo arquivo fonte: $localSettingsSource" -ForegroundColor Gray
    $localSettings = Get-Content -Path $localSettingsSource -Raw | ConvertFrom-Json
    
    # Atualizar valores com variáveis de ambiente
    Write-Host "   Aplicando variáveis de ambiente ao local.settings.json..." -ForegroundColor Gray
    $localSettings.Values."mailtrap.api-token" = $mailtrapToken
    $localSettings.Values."admin.email" = $adminEmail
    
    # Salvar no diretório de output
    Write-Host "   Salvando em: $localSettingsTarget" -ForegroundColor Gray
    $localSettings | ConvertTo-Json -Depth 10 | Set-Content -Path $localSettingsTarget -Force
    
    Write-Host ""
    Write-Host "   ✅ Configurações atualizadas com sucesso!" -ForegroundColor Green
    Write-Host "   ✅ mailtrap.api-token: aplicado (primeiros 8 caracteres: $($mailtrapToken.Substring(0, [Math]::Min(8, $mailtrapToken.Length)))...)" -ForegroundColor Green
    Write-Host "   ✅ admin.email: $adminEmail" -ForegroundColor Green
    
    # Verificar se os valores foram realmente salvos
    Write-Host ""
    Write-Host "   Verificando valores salvos..." -ForegroundColor Gray
    $savedSettings = Get-Content -Path $localSettingsTarget -Raw | ConvertFrom-Json
    $savedToken = $savedSettings.Values."mailtrap.api-token"
    $savedEmail = $savedSettings.Values."admin.email"
    
    if ($savedToken -eq $mailtrapToken -and $savedEmail -eq $adminEmail) {
        Write-Host "   ✅ Validação: Valores confirmados no arquivo" -ForegroundColor Green
    } else {
        Write-Host "   ⚠️  Validação: Possível inconsistência detectada" -ForegroundColor Yellow
    }
} else {
    Write-Host "⚠️  Arquivo local.settings.json não encontrado" -ForegroundColor Yellow
    Write-Host "   Criando arquivo básico..." -ForegroundColor Yellow
    
    # Criar arquivo básico se não existir
    $basicSettings = @{
        IsEncrypted = $false
        Values = @{
            AzureWebJobsStorage = "UseDevelopmentStorage=true"
            FUNCTIONS_WORKER_RUNTIME = "java"
            FUNCTIONS_EXTENSION_VERSION = "~4"
            "mailtrap.api-token" = $mailtrapToken
            "admin.email" = $adminEmail
            "azure.storage.connection-string" = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;TableEndpoint=http://localhost:10002/devstoreaccount1;BlobEndpoint=http://localhost:10000/devstoreaccount1;QueueEndpoint=http://localhost:10001/devstoreaccount1;"
            "azure.storage.container-name" = "weekly-reports"
            "azure.table.table-name" = "feedbacks"
            APP_ENVIRONMENT = "local"
            APP_DEBUG = "true"
        }
        Host = @{
            LocalHttpPort = 7071
            CORS = "*"
            CORSCredentials = $false
        }
    }
    
    $basicSettings | ConvertTo-Json -Depth 10 | Set-Content -Path $localSettingsTarget -Force
    Write-Host "   [OK] Arquivo criado com configurações básicas" -ForegroundColor Green
}

# Copiar host.json se existir no diretório de recursos
Write-Host ""
Write-Host "Verificando host.json..." -ForegroundColor Yellow
$hostJsonSource = Join-Path $projectRoot "src\main\resources\host.json"
$hostJsonTarget = Join-Path $functionsDir "host.json"

if (Test-Path $hostJsonSource) {
    Write-Host "   Copiando host.json de $hostJsonSource para $hostJsonTarget" -ForegroundColor Gray
    Copy-Item -Path $hostJsonSource -Destination $hostJsonTarget -Force
    Write-Host "   ✅ host.json copiado com sucesso" -ForegroundColor Green
} else {
    Write-Host "   [⚠] host.json não encontrado em $hostJsonSource (usando padrão)" -ForegroundColor Yellow
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
