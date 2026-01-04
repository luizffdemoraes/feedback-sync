# ============================================
# Script para Deletar Function App
# ============================================
# Este script deleta a Function App existente
# mantendo Storage Account e Resource Group intactos
# 
# Use antes de recriar a Function App manualmente ou via criar-recursos-azure.ps1
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroupName = "feedback-rg",
    
    [Parameter(Mandatory=$false)]
    [string]$Suffix = "prod"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  DELECAO DE FUNCTION APP" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Verificar se Azure CLI está instalado
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Host "[ERRO] Azure CLI nao encontrado. Instale em: https://aka.ms/installazurecliwindows" -ForegroundColor Red
    exit 1
}

# Verificar se está logado
az account show 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Nao esta logado no Azure. Execute: az login" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Azure CLI verificado" -ForegroundColor Green
$subscriptionName = az account show --query name -o tsv
Write-Host "   Subscription: $subscriptionName" -ForegroundColor Gray
Write-Host ""

# Normalizar sufixo
$Suffix = $Suffix.ToLower() -replace '[^a-z0-9]', ''

# Construir nome da Function App
$functionAppName = "feedback-function-$Suffix"
# Remover hífens duplicados e do início/fim
$functionAppName = $functionAppName -replace '-+', '-' -replace '^-', '' -replace '-$', ''
# Garantir apenas alfanuméricos e hífens
$functionAppName = $functionAppName -replace '[^a-z0-9-]', ''
# Limitar tamanho
if ($functionAppName.Length -gt 60) {
    $functionAppName = $functionAppName.Substring(0, 60)
}
# Garantir que não termine com hífen após truncamento
$functionAppName = $functionAppName -replace '-$', ''

Write-Host "Configuracao:" -ForegroundColor Yellow
Write-Host "   Resource Group: $ResourceGroupName" -ForegroundColor Gray
Write-Host "   Function App: $functionAppName" -ForegroundColor Gray
Write-Host ""

# Confirmação
Write-Host "ATENCAO: A Function App sera deletada!" -ForegroundColor Yellow
Write-Host "   Os dados do Storage Account e Resource Group serao preservados." -ForegroundColor Gray
Write-Host ""
$confirmation = Read-Host "Digite SIM para continuar"

if ($confirmation -ne "SIM") {
    Write-Host ""
    Write-Host "[CANCELADO] Operacao cancelada pelo usuario." -ForegroundColor Yellow
    exit 0
}
Write-Host ""

# Verificar se Function App existe
Write-Host "Verificando Function App..." -ForegroundColor Gray
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$functionExists = az functionapp show --name $functionAppName --resource-group $ResourceGroupName --query "name" -o tsv 2>&1
$ErrorActionPreference = $oldErrorAction

if ($LASTEXITCODE -eq 0 -and $functionExists) {
    Write-Host "[DELETANDO] Deletando Function App: $functionAppName" -ForegroundColor Yellow
    az functionapp delete --name $functionAppName --resource-group $ResourceGroupName 2>&1 | Out-Null
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   [OK] Function App deletada com sucesso" -ForegroundColor Green
        Write-Host "   Aguardando 10 segundos para limpeza completa..." -ForegroundColor Gray
        Start-Sleep -Seconds 10
    } else {
        Write-Host "   [ERRO] Erro ao deletar Function App" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "   [AVISO] Function App nao encontrada: $functionAppName" -ForegroundColor Yellow
    Write-Host "   Pode ja ter sido deletada anteriormente." -ForegroundColor Gray
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "[OK] DELECAO CONCLUIDA!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Proximos passos:" -ForegroundColor Yellow
Write-Host "   1. Recrie a Function App usando criar-recursos-azure.ps1" -ForegroundColor White
Write-Host "   2. Faca o deploy usando implantar-azure.ps1" -ForegroundColor White
Write-Host ""

exit 0
