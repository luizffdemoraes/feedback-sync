# Script para configurar variáveis de ambiente no Azure Functions
# Requisitos: Azure CLI instalado e logado

param(
    [Parameter(Mandatory=$true)]
    [string]$FunctionAppName,
    
    [Parameter(Mandatory=$true)]
    [string]$ResourceGroup,
    
    [Parameter(Mandatory=$true)]
    [string]$StorageConnectionString,
    
    [Parameter(Mandatory=$true)]
    [string]$ServiceBusConnectionString
)

Write-Host "⚙️  Configurando variáveis de ambiente no Azure Functions..." -ForegroundColor Green
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
Write-Host ""

# Configurar variáveis de ambiente
Write-Host "📝 Configurando Application Settings..." -ForegroundColor Yellow

az functionapp config appsettings set `
    --name $FunctionAppName `
    --resource-group $ResourceGroup `
    --settings `
        AZURE_STORAGE_CONNECTION_STRING="$StorageConnectionString" `
        AzureWebJobsStorage="$StorageConnectionString" `
        AZURE_SERVICEBUS_CONNECTION_STRING="$ServiceBusConnectionString" `
        AzureServiceBusConnection="$ServiceBusConnectionString" `
        FUNCTIONS_WORKER_RUNTIME=java `
        FUNCTIONS_EXTENSION_VERSION=~4 `
        quarkus.log.level=INFO `
        app.environment=production

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Erro ao configurar variáveis de ambiente" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✅ Variáveis de ambiente configuradas com sucesso!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Variáveis configuradas:" -ForegroundColor Yellow
Write-Host "  - AZURE_STORAGE_CONNECTION_STRING" -ForegroundColor White
Write-Host "  - AzureWebJobsStorage" -ForegroundColor White
Write-Host "  - AZURE_SERVICEBUS_CONNECTION_STRING" -ForegroundColor White
Write-Host "  - AzureServiceBusConnection" -ForegroundColor White
Write-Host "  - FUNCTIONS_WORKER_RUNTIME=java" -ForegroundColor White
Write-Host "  - FUNCTIONS_EXTENSION_VERSION=~4" -ForegroundColor White
Write-Host ""
Write-Host "⚠️  IMPORTANTE: Reinicie a Function App para aplicar as mudanças:" -ForegroundColor Yellow
Write-Host "   az functionapp restart --name $FunctionAppName --resource-group $ResourceGroup" -ForegroundColor Cyan

