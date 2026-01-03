# ============================================
# Script para Criar Recursos Azure
# ============================================
# Este script cria todos os recursos necessários no Azure:
# - Resource Group
# - Storage Account (Table + Blob)
# - Function App
# 
# NOTA: Service Bus foi removido para reduzir custos.
# Emails são enviados diretamente via Mailtrap.
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroupName = "feedback-rg",
    
    [Parameter(Mandatory=$false)]
    [string]$Location = "brazilsouth",
    
    [Parameter(Mandatory=$false)]
    [string]$Suffix = "prod",  # Sufixo único para nomes (padrão: "prod")
    
    [Parameter(Mandatory=$false)]
    [string]$MailtrapApiToken,  # Token da API do Mailtrap (opcional - configure manualmente depois se não fornecido)
    
    [Parameter(Mandatory=$false)]
    [string]$MailtrapInboxId,  # ID da inbox do Mailtrap (opcional - configure manualmente depois se não fornecido)
    
    [Parameter(Mandatory=$false)]
    [string]$AdminEmail  # Email do administrador para receber notificações (opcional - configure manualmente depois se não fornecido)
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Criação de Recursos Azure - Feedback Sync" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Verificar se Azure CLI está instalado
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Host "[ERRO] Azure CLI não encontrado. Instale em: https://aka.ms/installazurecliwindows" -ForegroundColor Red
    exit 1
}

# Verificar se está logado
az account show 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Não está logado no Azure. Execute: az login" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Azure CLI verificado" -ForegroundColor Green
Write-Host "   Subscription: $(az account show --query name -o tsv)" -ForegroundColor Gray
Write-Host ""

# Normalizar sufixo (apenas letras minúsculas e números)
$Suffix = $Suffix.ToLower() -replace '[^a-z0-9]', ''

# 1. Criar Resource Group
Write-Host "Criando Resource Group: $ResourceGroupName" -ForegroundColor Yellow
az group create --name $ResourceGroupName --location $Location --output none --only-show-errors
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Erro ao criar Resource Group" -ForegroundColor Red
    exit 1
}
Write-Host "   [OK] Resource Group criado" -ForegroundColor Green

# 2. Criar Storage Account
$storageAccountName = "feedbackstorage$Suffix"
# Storage account name deve ter entre 3-24 caracteres, apenas letras minúsculas e números
if ($storageAccountName.Length -gt 24) {
    $storageAccountName = $storageAccountName.Substring(0, 24)
}

Write-Host "`nCriando Storage Account: $storageAccountName" -ForegroundColor Yellow
az storage account create `
    --name $storageAccountName `
    --resource-group $ResourceGroupName `
    --location $Location `
    --sku Standard_LRS `
    --kind StorageV2 `
    --allow-blob-public-access false `
    --output none `
    --only-show-errors

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Erro ao criar Storage Account" -ForegroundColor Red
    exit 1
}
Write-Host "   [OK] Storage Account criado" -ForegroundColor Green

# Obter connection string do Storage
Write-Host "   Obtendo connection string..." -ForegroundColor Gray
$storageConnectionString = az storage account show-connection-string `
    --name $storageAccountName `
    --resource-group $ResourceGroupName `
    --query connectionString -o tsv

# Criar container para relatórios
Write-Host "   Criando container 'weekly-reports'..." -ForegroundColor Gray
az storage container create `
    --name "weekly-reports" `
    --account-name $storageAccountName `
    --connection-string $storageConnectionString `
    --public-access off `
    --output none `
    --only-show-errors `
    --fail-on-exist false

Write-Host "   [OK] Container criado" -ForegroundColor Green

# 3. Criar Function App (Service Bus removido - usando Mailtrap para emails)
$functionAppName = "feedback-function-$Suffix"
# Function App name deve ter entre 2-60 caracteres
if ($functionAppName.Length -gt 60) {
    $functionAppName = $functionAppName.Substring(0, 60)
}

Write-Host "`nCriando Function App: $functionAppName" -ForegroundColor Yellow
az functionapp create `
    --resource-group $ResourceGroupName `
    --consumption-plan-location $Location `
    --runtime java `
    --runtime-version 21 `
    --functions-version 4 `
    --name $functionAppName `
    --storage-account $storageAccountName `
    --os-type Linux `
    --output none `
    --only-show-errors

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Erro ao criar Function App" -ForegroundColor Red
    exit 1
}
Write-Host "   [OK] Function App criada" -ForegroundColor Green

# Configurar Application Settings
Write-Host "`nConfigurando Application Settings..." -ForegroundColor Yellow

# Preparar lista de settings básicas
$appSettings = @(
    "AZURE_STORAGE_CONNECTION_STRING=$storageConnectionString",
    "AzureWebJobsStorage=$storageConnectionString",
    "FUNCTIONS_WORKER_RUNTIME=java",
    "FUNCTIONS_EXTENSION_VERSION=~4",
    "quarkus.log.level=INFO",
    "app.environment=production",
    "azure.storage.container-name=weekly-reports",
    "azure.table.table-name=feedbacks",
    "REPORT_SCHEDULE_CRON=0 */5 * * * *"
)

# Adicionar configurações do Mailtrap se fornecidas
$mailtrapConfigured = $false
if ($MailtrapApiToken -and $MailtrapInboxId -and $AdminEmail) {
    Write-Host "   Configurando Mailtrap automaticamente..." -ForegroundColor Cyan
    $appSettings += "MAILTRAP_API_TOKEN=$MailtrapApiToken"
    $appSettings += "MAILTRAP_INBOX_ID=$MailtrapInboxId"
    $appSettings += "ADMIN_EMAIL=$AdminEmail"
    $mailtrapConfigured = $true
    Write-Host "   [OK] Mailtrap configurado automaticamente" -ForegroundColor Green
} else {
    Write-Host "   [AVISO] Mailtrap não configurado (parâmetros não fornecidos)" -ForegroundColor Yellow
    Write-Host "      Configure manualmente após a criação dos recursos" -ForegroundColor Gray
}

# Configurar Application Settings
# Construir array de argumentos para o Azure CLI
$azArgs = @(
    "functionapp", "config", "appsettings", "set",
    "--name", $functionAppName,
    "--resource-group", $ResourceGroupName
)

# Adicionar cada setting como --settings "key=value"
foreach ($setting in $appSettings) {
    $azArgs += "--settings"
    $azArgs += $setting
}

$azArgs += "--output", "none", "--only-show-errors"

# Executar comando
& az $azArgs

Write-Host "   [OK] Application Settings configuradas" -ForegroundColor Green

# Instruções para configurar Mailtrap manualmente (se não foi configurado)
if (-not $mailtrapConfigured) {
    Write-Host ""
    Write-Host "   Para configurar Mailtrap manualmente:" -ForegroundColor Cyan
    Write-Host "      1. Crie conta gratuita em: https://mailtrap.io" -ForegroundColor White
    Write-Host "      2. Gere um API Token e obtenha o Inbox ID" -ForegroundColor White
    Write-Host "      3. Execute o comando abaixo ou use o script novamente com os parâmetros:" -ForegroundColor White
    Write-Host ""
    Write-Host "         az functionapp config appsettings set `" -ForegroundColor Gray
    Write-Host "             --name $functionAppName `" -ForegroundColor Gray
    Write-Host "             --resource-group $ResourceGroupName `" -ForegroundColor Gray
    Write-Host "             --settings `" -ForegroundColor Gray
    Write-Host "                 MAILTRAP_API_TOKEN='seu-token' `" -ForegroundColor Gray
    Write-Host "                 MAILTRAP_INBOX_ID='seu-inbox-id' `" -ForegroundColor Gray
    Write-Host "                 ADMIN_EMAIL='seu-email@exemplo.com'" -ForegroundColor Gray
    Write-Host ""
    Write-Host "      Ou execute o script novamente com os parâmetros:" -ForegroundColor White
    Write-Host "         .\scripts\criar-recursos-azure.ps1 `" -ForegroundColor Gray
    Write-Host "             -ResourceGroupName `"$ResourceGroupName`" `" -ForegroundColor Gray
    Write-Host "             -Location `"$Location`" `" -ForegroundColor Gray
    Write-Host "             -Suffix `"$Suffix`" `" -ForegroundColor Gray
    Write-Host "             -MailtrapApiToken 'seu-token' `" -ForegroundColor Gray
    Write-Host "             -MailtrapInboxId 'seu-inbox-id' `" -ForegroundColor Gray
    Write-Host "             -AdminEmail 'admin@exemplo.com'" -ForegroundColor Gray
}

# Resumo
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "[OK] RECURSOS CRIADOS COM SUCESSO!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Informações dos Recursos:" -ForegroundColor Cyan
Write-Host ""
Write-Host "Resource Group:" -ForegroundColor White
Write-Host "  Nome: $ResourceGroupName" -ForegroundColor Gray
Write-Host "  Região: $Location" -ForegroundColor Gray
Write-Host ""
Write-Host "Storage Account:" -ForegroundColor White
Write-Host "  Nome: $storageAccountName" -ForegroundColor Gray
Write-Host "  Container: weekly-reports" -ForegroundColor Gray
Write-Host ""
Write-Host "Notificações:" -ForegroundColor White
Write-Host "  Método: Mailtrap" -ForegroundColor Gray
Write-Host "  Service Bus: REMOVIDO para reduzir custos" -ForegroundColor Gray
Write-Host ""
Write-Host "Function App:" -ForegroundColor White
Write-Host "  Nome: $functionAppName" -ForegroundColor Gray
Write-Host "  URL: https://$functionAppName.azurewebsites.net" -ForegroundColor Gray
Write-Host ""
Write-Host "Próximos Passos:" -ForegroundColor Yellow
if (-not $mailtrapConfigured) {
    Write-Host "  0. [AVISO] Configure Mailtrap (veja instruções acima)" -ForegroundColor Yellow
}
Write-Host "  1. Fazer deploy da aplicação:" -ForegroundColor White
Write-Host "     .\scripts\implantar-azure.ps1 -FunctionAppName `"$functionAppName`" -ResourceGroup `"$ResourceGroupName`"" -ForegroundColor Gray
Write-Host ""
Write-Host "  2. Testar o endpoint:" -ForegroundColor White
Write-Host "     https://$functionAppName.azurewebsites.net/api/avaliacao" -ForegroundColor Gray
Write-Host ""
Write-Host "  3. Ver logs:" -ForegroundColor White
Write-Host "     az functionapp log tail --name $functionAppName --resource-group $ResourceGroupName" -ForegroundColor Gray
Write-Host ""
Write-Host "Consulte GUIA_DEPLOY_AZURE.md para mais detalhes" -ForegroundColor Cyan
Write-Host ""

