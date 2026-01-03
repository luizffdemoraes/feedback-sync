# ============================================
# Script Simplificado para Configurar Alertas de Custo Azure
# ============================================
# Configura alertas de custo simples:
# - Budget mensal (padrão: $10)
# - Alertas em 50%, 90% e 100% do budget
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$BudgetAmount = "10",  # Valor do budget mensal em USD
    
    [Parameter(Mandatory=$false)]
    [string]$EmailNotification  # Email para receber alertas
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Configuração de Alertas de Custo Azure" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Verificações básicas
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Host "[ERRO] Azure CLI não encontrado. Instale em: https://aka.ms/installazurecliwindows" -ForegroundColor Red
    exit 1
}

az account show 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Não está logado no Azure. Execute: az login" -ForegroundColor Red
    exit 1
}

# Obter subscription atual
$SubscriptionId = az account show --query id -o tsv
$subscriptionName = az account show --query name -o tsv

Write-Host "[OK] Subscription: $subscriptionName" -ForegroundColor Green
Write-Host "   Budget: `$$BudgetAmount USD/mês" -ForegroundColor Gray
Write-Host ""

# Solicitar email se não fornecido
if ([string]::IsNullOrWhiteSpace($EmailNotification)) {
    $EmailNotification = Read-Host "Digite o email para receber alertas"
    if ([string]::IsNullOrWhiteSpace($EmailNotification)) {
        Write-Host "[ERRO] Email é obrigatório" -ForegroundColor Red
        exit 1
    }
}

# Instalar extensão se necessário
$extensionInstalled = az extension list --query "[?name=='consumption'].name" -o tsv 2>&1
if ($extensionInstalled -ne "consumption") {
    Write-Host "Instalando extensão Azure Consumption..." -ForegroundColor Yellow
    az extension add --name consumption 2>&1 | Out-Null
}

# Nome do budget
$budgetName = "feedback-sync-budget"

# Deletar budget existente se houver
Write-Host "Verificando budget existente..." -ForegroundColor Gray
az consumption budget delete --budget-name $budgetName --subscription $SubscriptionId 2>&1 | Out-Null
Start-Sleep -Seconds 2

# Criar budget com alertas simplificados (50%, 90%, 100%)
Write-Host "Criando budget e alertas..." -ForegroundColor Yellow
$startDate = Get-Date -Format "yyyy-MM-01"
$endDate = (Get-Date).AddYears(1).ToString("yyyy-MM-01")

az consumption budget create `
    --budget-name $budgetName `
    --subscription $SubscriptionId `
    --amount $BudgetAmount `
    --time-grain Monthly `
    --start-date $startDate `
    --end-date $endDate `
    --category Cost `
    --notifications threshold=50 enabled=true operator=GreaterThan contact-emails=$EmailNotification `
    --notifications threshold=90 enabled=true operator=GreaterThan contact-emails=$EmailNotification `
    --notifications threshold=100 enabled=true operator=GreaterThan contact-emails=$EmailNotification `
    2>&1 | Out-Null

if ($LASTEXITCODE -eq 0) {
    Write-Host "   [OK] Budget criado com sucesso" -ForegroundColor Green
} else {
    Write-Host "[ERRO] Não foi possível criar o budget" -ForegroundColor Red
    Write-Host "   Configure manualmente no Portal:" -ForegroundColor Yellow
    Write-Host "   https://portal.azure.com/#view/Microsoft_Azure_CostManagement/Menu/~/Budgets" -ForegroundColor Gray
    exit 1
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "[OK] ALERTAS DE CUSTO CONFIGURADOS!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Budget: `$$BudgetAmount USD/mês" -ForegroundColor Cyan
Write-Host "Alertas: 50%, 90% e 100%" -ForegroundColor Cyan
Write-Host "Email: $EmailNotification" -ForegroundColor Cyan
Write-Host ""
Write-Host "Ver no Portal:" -ForegroundColor Yellow
Write-Host "  https://portal.azure.com/#view/Microsoft_Azure_CostManagement/Menu/~/Budgets" -ForegroundColor Gray
Write-Host ""
