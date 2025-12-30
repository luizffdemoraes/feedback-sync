# Script de Deploy para Azure Functions
# Requisitos: Azure CLI instalado e logado (az login)

param(
    [Parameter(Mandatory=$true)]
    [string]$FunctionAppName,
    
    [Parameter(Mandatory=$true)]
    [string]$ResourceGroup,
    
    [Parameter(Mandatory=$false)]
    [string]$Location = "brazilsouth"
)

Write-Host "🚀 Iniciando deploy para Azure Functions..." -ForegroundColor Green
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

# Compilar projeto
Write-Host ""
Write-Host "📦 Compilando projeto..." -ForegroundColor Yellow
mvn clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Erro ao compilar projeto" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Projeto compilado com sucesso" -ForegroundColor Green

# Fazer deploy
Write-Host ""
Write-Host "🚀 Fazendo deploy para Azure Functions..." -ForegroundColor Yellow
Write-Host "Function App: $FunctionAppName" -ForegroundColor Cyan
Write-Host "Resource Group: $ResourceGroup" -ForegroundColor Cyan

mvn azure-functions:deploy -DfunctionAppName=$FunctionAppName

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Erro ao fazer deploy" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✅ Deploy concluído com sucesso!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Próximos passos:" -ForegroundColor Yellow
Write-Host "1. Verificar configurações no Azure Portal" -ForegroundColor White
Write-Host "2. Configurar variáveis de ambiente (Application Settings)" -ForegroundColor White
Write-Host "3. Testar endpoints: https://$FunctionAppName.azurewebsites.net/api/avaliacao" -ForegroundColor White
Write-Host ""
Write-Host "📖 Consulte GUIA_DEPLOY_AZURE_COMPLETO.md para mais detalhes" -ForegroundColor Cyan

