# ============================================
# Script para Criar Recursos Azure
# ============================================
# Este script cria todos os recursos necessários no Azure:
# - Resource Group
# - Storage Account (Table + Blob)
# - Service Bus (Namespace, Tópico, Subscription)
# - Function App
# ============================================

param(
    [Parameter(Mandatory=$true)]
    [string]$ResourceGroupName = "feedback-rg",
    
    [Parameter(Mandatory=$true)]
    [string]$Location = "brazilsouth",
    
    [Parameter(Mandatory=$true)]
    [string]$Suffix  # Sufixo único para nomes (ex: "dev", "prod", seu nome)
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Criação de Recursos Azure - Feedback Sync" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Verificar se Azure CLI está instalado
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Host "❌ Azure CLI não encontrado. Instale em: https://aka.ms/installazurecliwindows" -ForegroundColor Red
    exit 1
}

# Verificar se está logado
$azAccount = az account show 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Não está logado no Azure. Execute: az login" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Azure CLI verificado" -ForegroundColor Green
Write-Host "   Subscription: $(az account show --query name -o tsv)" -ForegroundColor Gray
Write-Host ""

# Normalizar sufixo (apenas letras minúsculas e números)
$Suffix = $Suffix.ToLower() -replace '[^a-z0-9]', ''

# 1. Criar Resource Group
Write-Host "📦 Criando Resource Group: $ResourceGroupName" -ForegroundColor Yellow
az group create --name $ResourceGroupName --location $Location --output none
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Erro ao criar Resource Group" -ForegroundColor Red
    exit 1
}
Write-Host "   ✅ Resource Group criado" -ForegroundColor Green

# 2. Criar Storage Account
$storageAccountName = "feedbackstorage$Suffix"
# Storage account name deve ter entre 3-24 caracteres, apenas letras minúsculas e números
if ($storageAccountName.Length -gt 24) {
    $storageAccountName = $storageAccountName.Substring(0, 24)
}

Write-Host "`n💾 Criando Storage Account: $storageAccountName" -ForegroundColor Yellow
az storage account create `
    --name $storageAccountName `
    --resource-group $ResourceGroupName `
    --location $Location `
    --sku Standard_LRS `
    --kind StorageV2 `
    --allow-blob-public-access false `
    --output none

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Erro ao criar Storage Account" -ForegroundColor Red
    exit 1
}
Write-Host "   ✅ Storage Account criado" -ForegroundColor Green

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
    --output none

Write-Host "   ✅ Container criado" -ForegroundColor Green

# 3. Criar Service Bus Namespace
$serviceBusNamespace = "feedback-sb-$Suffix"
# Service Bus namespace deve ter entre 6-50 caracteres
if ($serviceBusNamespace.Length -gt 50) {
    $serviceBusNamespace = $serviceBusNamespace.Substring(0, 50)
}

Write-Host "`n🚌 Criando Service Bus Namespace: $serviceBusNamespace" -ForegroundColor Yellow
az servicebus namespace create `
    --resource-group $ResourceGroupName `
    --name $serviceBusNamespace `
    --location $Location `
    --sku Standard `
    --output none

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Erro ao criar Service Bus Namespace" -ForegroundColor Red
    exit 1
}
Write-Host "   ✅ Service Bus Namespace criado" -ForegroundColor Green

# Aguardar alguns segundos para o namespace estar pronto
Start-Sleep -Seconds 5

# Criar Tópico
Write-Host "   Criando tópico 'critical-feedbacks'..." -ForegroundColor Gray
az servicebus topic create `
    --resource-group $ResourceGroupName `
    --namespace-name $serviceBusNamespace `
    --name "critical-feedbacks" `
    --output none

Write-Host "   ✅ Tópico criado" -ForegroundColor Green

# Criar Subscription
Write-Host "   Criando subscription 'admin-notifications'..." -ForegroundColor Gray
az servicebus topic subscription create `
    --resource-group $ResourceGroupName `
    --namespace-name $serviceBusNamespace `
    --topic-name "critical-feedbacks" `
    --name "admin-notifications" `
    --output none

Write-Host "   ✅ Subscription criada" -ForegroundColor Green

# Obter connection string do Service Bus
Write-Host "   Obtendo connection string..." -ForegroundColor Gray
$serviceBusConnectionString = az servicebus namespace authorization-rule keys list `
    --resource-group $ResourceGroupName `
    --namespace-name $serviceBusNamespace `
    --name "RootManageSharedAccessKey" `
    --query primaryConnectionString -o tsv

# 4. Criar Function App
$functionAppName = "feedback-function-$Suffix"
# Function App name deve ter entre 2-60 caracteres
if ($functionAppName.Length -gt 60) {
    $functionAppName = $functionAppName.Substring(0, 60)
}

Write-Host "`n⚡ Criando Function App: $functionAppName" -ForegroundColor Yellow
az functionapp create `
    --resource-group $ResourceGroupName `
    --consumption-plan-location $Location `
    --runtime java `
    --runtime-version 21 `
    --functions-version 4 `
    --name $functionAppName `
    --storage-account $storageAccountName `
    --os-type Linux `
    --output none

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Erro ao criar Function App" -ForegroundColor Red
    exit 1
}
Write-Host "   ✅ Function App criada" -ForegroundColor Green

# Configurar Application Settings
Write-Host "`n⚙️ Configurando Application Settings..." -ForegroundColor Yellow
az functionapp config appsettings set `
    --name $functionAppName `
    --resource-group $ResourceGroupName `
    --settings `
        "AZURE_STORAGE_CONNECTION_STRING=$storageConnectionString" `
        "AzureWebJobsStorage=$storageConnectionString" `
        "AZURE_SERVICEBUS_CONNECTION_STRING=$serviceBusConnectionString" `
        "AzureServiceBusConnection=$serviceBusConnectionString" `
        "FUNCTIONS_WORKER_RUNTIME=java" `
        "FUNCTIONS_EXTENSION_VERSION=~4" `
        "quarkus.log.level=INFO" `
        "app.environment=production" `
        "azure.servicebus.topic-name=critical-feedbacks" `
        "azure.storage.container-name=weekly-reports" `
        "azure.table.table-name=feedbacks" `
    --output none

Write-Host "   ✅ Application Settings configuradas" -ForegroundColor Green

# Resumo
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "✅ RECURSOS CRIADOS COM SUCESSO!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Informações dos Recursos:" -ForegroundColor Cyan
Write-Host ""
Write-Host "Resource Group:" -ForegroundColor White
Write-Host "  Nome: $ResourceGroupName" -ForegroundColor Gray
Write-Host "  Região: $Location" -ForegroundColor Gray
Write-Host ""
Write-Host "Storage Account:" -ForegroundColor White
Write-Host "  Nome: $storageAccountName" -ForegroundColor Gray
Write-Host "  Container: weekly-reports" -ForegroundColor Gray
Write-Host ""
Write-Host "Service Bus:" -ForegroundColor White
Write-Host "  Namespace: $serviceBusNamespace" -ForegroundColor Gray
Write-Host "  Tópico: critical-feedbacks" -ForegroundColor Gray
Write-Host "  Subscription: admin-notifications" -ForegroundColor Gray
Write-Host ""
Write-Host "Function App:" -ForegroundColor White
Write-Host "  Nome: $functionAppName" -ForegroundColor Gray
Write-Host "  URL: https://$functionAppName.azurewebsites.net" -ForegroundColor Gray
Write-Host ""
Write-Host "💡 Próximos Passos:" -ForegroundColor Yellow
Write-Host "  1. Fazer deploy da aplicação:" -ForegroundColor White
Write-Host "     .\scripts\implantar-azure.ps1 -FunctionAppName `"$functionAppName`" -ResourceGroup `"$ResourceGroupName`"" -ForegroundColor Gray
Write-Host ""
Write-Host "  2. Testar o endpoint:" -ForegroundColor White
Write-Host "     https://$functionAppName.azurewebsites.net/api/avaliacao" -ForegroundColor Gray
Write-Host ""
Write-Host "  3. Ver logs:" -ForegroundColor White
Write-Host "     az functionapp log tail --name $functionAppName --resource-group $ResourceGroupName" -ForegroundColor Gray
Write-Host ""
Write-Host "📖 Consulte GUIA_DEPLOY_AZURE.md para mais detalhes" -ForegroundColor Cyan
Write-Host ""

