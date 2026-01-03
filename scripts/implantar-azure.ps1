# ============================================
# Script de Deploy para Azure Functions
# ============================================
# Este script faz deploy da aplicação para a Function App criada
# pelo criar-recursos-azure.ps1
# 
# Requisitos:
# - Azure CLI instalado e logado (az login)
# - Function App já criada (use criar-recursos-azure.ps1 primeiro)
# - Maven instalado
# ============================================

param(
    [Parameter(Mandatory=$true)]
    [string]$FunctionAppName,  # Nome da Function App (ex: feedback-function-prod)
    
    [Parameter(Mandatory=$true)]
    [string]$ResourceGroup,  # Nome do Resource Group (ex: feedback-rg)
    
    [Parameter(Mandatory=$false)]
    [switch]$SkipBuild = $false  # Se $true, pula a compilação (usa build existente)
)

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Deploy para Azure Functions - Feedback Sync" -ForegroundColor Cyan
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
$subscriptionName = az account show --query name -o tsv
Write-Host "   Subscription: $subscriptionName" -ForegroundColor Gray
Write-Host ""

# Verificar se Resource Group existe
Write-Host "🔍 Verificando recursos..." -ForegroundColor Yellow
$rgExists = az group exists --name $ResourceGroup 2>&1
if ($rgExists -eq "false") {
    Write-Host "❌ Resource Group '$ResourceGroup' não encontrado." -ForegroundColor Red
    Write-Host "   Execute primeiro: .\scripts\criar-recursos-azure.ps1" -ForegroundColor Yellow
    exit 1
}
Write-Host "   ✅ Resource Group encontrado" -ForegroundColor Green

# Verificar se Function App existe
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$functionExists = az functionapp show --name $FunctionAppName --resource-group $ResourceGroup --query "name" -o tsv 2>&1
$ErrorActionPreference = $oldErrorAction

if ($LASTEXITCODE -ne 0 -or -not $functionExists) {
    Write-Host "❌ Function App '$FunctionAppName' não encontrada no Resource Group '$ResourceGroup'." -ForegroundColor Red
    Write-Host "   Execute primeiro: .\scripts\criar-recursos-azure.ps1" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "   Ou use os parâmetros corretos:" -ForegroundColor Yellow
    Write-Host "   .\scripts\implantar-azure.ps1 -FunctionAppName `"feedback-function-prod`" -ResourceGroup `"feedback-rg`"" -ForegroundColor Gray
    exit 1
}
Write-Host "   ✅ Function App encontrada" -ForegroundColor Green

# Verificar se Maven está instalado
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "❌ Maven não encontrado. Instale o Maven para continuar." -ForegroundColor Red
    Write-Host "   Download: https://maven.apache.org/download.cgi" -ForegroundColor Yellow
    exit 1
}
Write-Host "   ✅ Maven encontrado" -ForegroundColor Green
Write-Host ""

# Compilar projeto (se não pular)
if (-not $SkipBuild) {
    Write-Host "📦 Compilando projeto..." -ForegroundColor Yellow
    mvn clean package -DskipTests

    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Erro ao compilar projeto" -ForegroundColor Red
        Write-Host "   Tente executar manualmente: mvn clean package -DskipTests" -ForegroundColor Yellow
        exit 1
    }

    Write-Host "✅ Projeto compilado com sucesso" -ForegroundColor Green
} else {
    Write-Host "⏭️  Pulando compilação (usando build existente)" -ForegroundColor Yellow
}
Write-Host ""

# Fazer deploy
Write-Host "🚀 Fazendo deploy para Azure Functions..." -ForegroundColor Yellow
Write-Host ""
Write-Host "Function App: $FunctionAppName" -ForegroundColor Cyan
Write-Host "Resource Group: $ResourceGroup" -ForegroundColor Cyan
Write-Host ""

mvn azure-functions:deploy -DfunctionAppName=$FunctionAppName

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ Erro ao fazer deploy" -ForegroundColor Red
    Write-Host ""
    Write-Host "💡 Possíveis soluções:" -ForegroundColor Yellow
    Write-Host "   1. Verifique se a Function App existe:" -ForegroundColor White
    Write-Host "      az functionapp show --name $FunctionAppName --resource-group $ResourceGroup" -ForegroundColor Gray
    Write-Host ""
    Write-Host "   2. Verifique se está logado:" -ForegroundColor White
    Write-Host "      az account show" -ForegroundColor Gray
    Write-Host ""
    Write-Host "   3. Verifique as configurações no pom.xml" -ForegroundColor White
    Write-Host ""
    exit 1
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "✅ DEPLOY CONCLUÍDO COM SUCESSO!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Informações da aplicação:" -ForegroundColor Cyan
Write-Host ""
Write-Host "Function App:" -ForegroundColor White
Write-Host "  Nome: $FunctionAppName" -ForegroundColor Gray
Write-Host "  URL: https://$FunctionAppName.azurewebsites.net" -ForegroundColor Gray
Write-Host ""
Write-Host "Endpoints disponíveis:" -ForegroundColor White
Write-Host "  POST https://$FunctionAppName.azurewebsites.net/api/avaliacao" -ForegroundColor Gray
Write-Host "  GET  https://$FunctionAppName.azurewebsites.net/api/relatorio-semanal" -ForegroundColor Gray
Write-Host ""
Write-Host "📋 Próximos passos:" -ForegroundColor Yellow
Write-Host "1. Verificar Application Settings no Azure Portal" -ForegroundColor White
Write-Host "2. Testar o endpoint de avaliação:" -ForegroundColor White
Write-Host "   https://$FunctionAppName.azurewebsites.net/api/avaliacao" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Ver logs em tempo real:" -ForegroundColor White
Write-Host "   az functionapp log tail --name $FunctionAppName --resource-group $ResourceGroup" -ForegroundColor Gray
Write-Host ""
Write-Host "4. Ver logs no Azure Portal:" -ForegroundColor White
$subscriptionId = az account show --query id -o tsv
Write-Host "   https://portal.azure.com/#@/resource/subscriptions/$subscriptionId/resourceGroups/$ResourceGroup/providers/Microsoft.Web/sites/$FunctionAppName/logStream" -ForegroundColor Gray
Write-Host ""
Write-Host "📖 Consulte GUIA_DEPLOY_AZURE.md para mais detalhes" -ForegroundColor Cyan
Write-Host ""

