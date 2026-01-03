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
$mailtrapInboxId = $env:MAILTRAP_INBOX_ID

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

if ([string]::IsNullOrWhiteSpace($mailtrapInboxId)) {
    Write-Host "   [X] MAILTRAP_INBOX_ID não configurado" -ForegroundColor Red
    Write-Host "   Configure: `$env:MAILTRAP_INBOX_ID = 'seu-inbox-id'" -ForegroundColor Gray
    Write-Host "   Obtenha o ID da sua inbox no painel do Mailtrap (Settings > Inboxes)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "❌ É necessário configurar MAILTRAP_INBOX_ID antes de executar" -ForegroundColor Red
    exit 1
} else {
    Write-Host "   [OK] MAILTRAP_INBOX_ID configurado: $mailtrapInboxId" -ForegroundColor Green
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
    
    # Remover function.json do QuarkusHttp (não usado na arquitetura híbrida)
    Write-Host ""
    Write-Host "Removendo QuarkusHttp function.json (não usado na arquitetura híbrida)..." -ForegroundColor Yellow
    $quarkusHttpFunctionJson = Join-Path $functionsDir "QuarkusHttp\function.json"
    if (Test-Path $quarkusHttpFunctionJson) {
        Remove-Item -Path $quarkusHttpFunctionJson -Force
        Write-Host "   ✅ QuarkusHttp function.json removido" -ForegroundColor Green
    } else {
        Write-Host "   [OK] QuarkusHttp function.json não encontrado (já removido ou não gerado)" -ForegroundColor Gray
    }
} else {
    Write-Host ""
    Write-Host "✅ Usando compilação existente" -ForegroundColor Green
    
    # Verificar e remover QuarkusHttp mesmo se não recompilou
    $quarkusHttpFunctionJson = Join-Path $functionsDir "QuarkusHttp\function.json"
    if (Test-Path $quarkusHttpFunctionJson) {
        Write-Host "Removendo QuarkusHttp function.json (não usado na arquitetura híbrida)..." -ForegroundColor Yellow
        Remove-Item -Path $quarkusHttpFunctionJson -Force
        Write-Host "   ✅ QuarkusHttp function.json removido" -ForegroundColor Green
    }
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
Write-Host "   - MAILTRAP_INBOX_ID: $(if ($mailtrapInboxId) { $mailtrapInboxId } else { 'NÃO configurado' })" -ForegroundColor Gray

if (Test-Path $localSettingsSource) {
    # Ler o JSON atual
    Write-Host "   Lendo arquivo fonte: $localSettingsSource" -ForegroundColor Gray
    $localSettings = Get-Content -Path $localSettingsSource -Raw | ConvertFrom-Json
    
    # Garantir que a propriedade Values existe
    if (-not $localSettings.Values) {
        $localSettings | Add-Member -MemberType NoteProperty -Name "Values" -Value @{}
    }
    
    # IMPORTANTE: Preservar todas as propriedades existentes (especialmente REPORT_SCHEDULE_CRON)
    Write-Host "   Preservando propriedades existentes do arquivo fonte..." -ForegroundColor Gray
    
    # Atualizar valores com variáveis de ambiente
    Write-Host "   Aplicando variáveis de ambiente ao local.settings.json..." -ForegroundColor Gray
    
    # Garantir que todas as propriedades existam antes de atribuir valores
    # Verificar e criar propriedades se não existirem
    $propertiesToCheck = @("mailtrap.api-token", "admin.email", "mailtrap.inbox-id", "MAILTRAP_API_TOKEN", "ADMIN_EMAIL", "MAILTRAP_INBOX_ID", "REPORT_SCHEDULE_CRON")
    foreach ($propName in $propertiesToCheck) {
        if (-not $localSettings.Values.PSObject.Properties[$propName]) {
            Write-Host "   Criando propriedade: $propName" -ForegroundColor Gray
            $localSettings.Values | Add-Member -MemberType NoteProperty -Name $propName -Value "" -Force
        }
    }
    
    # Preservar REPORT_SCHEDULE_CRON se já existir no arquivo fonte
    if ($localSettings.Values.PSObject.Properties["REPORT_SCHEDULE_CRON"] -and $localSettings.Values."REPORT_SCHEDULE_CRON") {
        Write-Host "   ✅ Preservando REPORT_SCHEDULE_CRON: $($localSettings.Values.'REPORT_SCHEDULE_CRON')" -ForegroundColor Green
    } else {
        # Se não existir, usar o padrão de 5 minutos
        $localSettings.Values."REPORT_SCHEDULE_CRON" = "0 */5 * * * *"
        Write-Host "   ✅ Configurando REPORT_SCHEDULE_CRON padrão: 0 */5 * * * *" -ForegroundColor Green
    }
    
    # Atualizar valores diretamente (garantir que sejam strings)
    Write-Host "   Atribuindo valores das variáveis de ambiente..." -ForegroundColor Gray
    $localSettings.Values."mailtrap.api-token" = [string]$mailtrapToken
    $localSettings.Values."admin.email" = [string]$adminEmail
    $localSettings.Values."mailtrap.inbox-id" = [string]$mailtrapInboxId
    
    # Adicionar também no formato de variáveis de ambiente (para garantir compatibilidade)
    # O Azure Functions Core Tools converte propriedades do local.settings.json para variáveis de ambiente
    # Mas o Quarkus espera variáveis no formato MAILTRAP_API_TOKEN (com underscores)
    # Adicionar ambas as formas para garantir compatibilidade
    $localSettings.Values."MAILTRAP_API_TOKEN" = [string]$mailtrapToken
    $localSettings.Values."ADMIN_EMAIL" = [string]$adminEmail
    $localSettings.Values."MAILTRAP_INBOX_ID" = [string]$mailtrapInboxId
    
    # Configurar perfil do Quarkus para usar application-local.properties
    $localSettings.Values."QUARKUS_PROFILE" = "local"
    
    # Verificar imediatamente após atribuir
    Write-Host "   Verificação imediata - mailtrap.inbox-id: '$($localSettings.Values.'mailtrap.inbox-id')'" -ForegroundColor Cyan
    
    Write-Host "   Debug - mailtrap.inbox-id será salvo como: '$mailtrapInboxId' (tipo: $($mailtrapInboxId.GetType().Name))" -ForegroundColor Cyan
    Write-Host "   Debug - Verificando antes de salvar: '$($localSettings.Values.'mailtrap.inbox-id')'" -ForegroundColor Cyan
    
    # Salvar no diretório de output
    Write-Host "   Salvando em: $localSettingsTarget" -ForegroundColor Gray
    $jsonContent = $localSettings | ConvertTo-Json -Depth 10
    Set-Content -Path $localSettingsTarget -Value $jsonContent -Force -NoNewline
    
    Write-Host ""
    Write-Host "   ✅ Configurações atualizadas com sucesso!" -ForegroundColor Green
    Write-Host "   ✅ mailtrap.api-token / MAILTRAP_API_TOKEN: aplicado (primeiros 8 caracteres: $($mailtrapToken.Substring(0, [Math]::Min(8, $mailtrapToken.Length)))...)" -ForegroundColor Green
    Write-Host "   ✅ admin.email / ADMIN_EMAIL: $adminEmail" -ForegroundColor Green
    Write-Host "   ✅ mailtrap.inbox-id / MAILTRAP_INBOX_ID: $mailtrapInboxId" -ForegroundColor Green
    Write-Host "   ✅ REPORT_SCHEDULE_CRON: $($localSettings.Values.'REPORT_SCHEDULE_CRON')" -ForegroundColor Green
    
    # Verificar se os valores foram realmente salvos
    Write-Host ""
    Write-Host "   Verificando valores salvos..." -ForegroundColor Gray

    # Aguardar um momento para garantir que o arquivo foi escrito
    Start-Sleep -Milliseconds 100
    $savedSettings = Get-Content -Path $localSettingsTarget -Raw | ConvertFrom-Json
    $savedToken = $savedSettings.Values."mailtrap.api-token"
    $savedEmail = $savedSettings.Values."admin.email"
    $savedInboxId = $savedSettings.Values."mailtrap.inbox-id"
    
    Write-Host "   Debug - Valores lidos do arquivo salvo:" -ForegroundColor Cyan
    Write-Host "      mailtrap.api-token: '$savedToken'" -ForegroundColor Gray
    Write-Host "      admin.email: '$savedEmail'" -ForegroundColor Gray
    Write-Host "      mailtrap.inbox-id: '$savedInboxId' (tipo: $(if ($savedInboxId) { $savedInboxId.GetType().Name } else { 'null' }))" -ForegroundColor Gray
    
    # Comparação com conversão para string para garantir compatibilidade
    $tokenMatch = [string]$savedToken -eq [string]$mailtrapToken
    $emailMatch = [string]$savedEmail -eq [string]$adminEmail
    $inboxIdMatch = [string]$savedInboxId -eq [string]$mailtrapInboxId
    
    if ($tokenMatch -and $emailMatch -and $inboxIdMatch) {
        Write-Host "   ✅ Validação: Valores confirmados no arquivo" -ForegroundColor Green
    } else {
        Write-Host "   ⚠️  Validação: Possível inconsistência detectada" -ForegroundColor Yellow
        if (-not $tokenMatch) {
            Write-Host "      Problema detectado: mailtrap.api-token" -ForegroundColor Yellow
            Write-Host "      Esperado: '$mailtrapToken' | Encontrado: '$savedToken'" -ForegroundColor Yellow
        }
        if (-not $emailMatch) {
            Write-Host "      Problema detectado: admin.email" -ForegroundColor Yellow
            Write-Host "      Esperado: '$adminEmail' | Encontrado: '$savedEmail'" -ForegroundColor Yellow
        }
        if (-not $inboxIdMatch) {
            Write-Host "      Problema detectado: mailtrap.inbox-id" -ForegroundColor Yellow
            Write-Host "      Esperado: '$mailtrapInboxId' | Encontrado: '$savedInboxId'" -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "⚠️  Arquivo local.settings.json não encontrado" -ForegroundColor Yellow
    Write-Host "   Criando arquivo básico..." -ForegroundColor Yellow
    
    # Criar arquivo básico se não existir
    # Converter valores para string antes de criar o hashtable
    $mailtrapTokenStr = [string]$mailtrapToken
    $adminEmailStr = [string]$adminEmail
    $mailtrapInboxIdStr = [string]$mailtrapInboxId
    
    # Criar hashtables separadamente para evitar problemas de sintaxe
    $valuesHash = @{
        AzureWebJobsStorage = "UseDevelopmentStorage=true"
        FUNCTIONS_WORKER_RUNTIME = "java"
        FUNCTIONS_EXTENSION_VERSION = "~4"
        "mailtrap.api-token" = $mailtrapTokenStr
        "admin.email" = $adminEmailStr
        "mailtrap.inbox-id" = $mailtrapInboxIdStr
        # Adicionar também no formato de variáveis de ambiente (para garantir compatibilidade)
        "MAILTRAP_API_TOKEN" = $mailtrapTokenStr
        "ADMIN_EMAIL" = $adminEmailStr
        "MAILTRAP_INBOX_ID" = $mailtrapInboxIdStr
        "QUARKUS_PROFILE" = "local"
        "azure.storage.connection-string" = "UseDevelopmentStorage=true"
        "azure.storage.container-name" = "weekly-reports"
        "azure.table.table-name" = "feedbacks"
        APP_ENVIRONMENT = "local"
        APP_DEBUG = "true"
        "REPORT_SCHEDULE_CRON" = "0 */5 * * * *"
    }
    
    $basicSettings = @{
        IsEncrypted = $false
        Values = $valuesHash
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
