# ============================================
# Script para Destruir Recursos Azure
# ============================================
# Este script remove todos os recursos criados no Azure:
# - Function App
# - Storage Account (e containers)
# - Resource Group (remove tudo dentro dele)
# 
# ATENÇÃO: Esta operação é IRREVERSÍVEL!
# Todos os dados serão perdidos permanentemente.
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroupName = "feedback-rg",
    
    [Parameter(Mandatory=$false)]
    [string]$Suffix = "prod",  # Sufixo usado na criação (padrão: "prod")
    
    [Parameter(Mandatory=$false)]
    [switch]$Force = $false,  # Pula confirmação se $true
    
    [Parameter(Mandatory=$false)]
    [switch]$DeleteResourceGroupOnly = $false  # Se $true, deleta apenas o Resource Group (mais rápido)
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Red
Write-Host "  DESTRUIÇÃO de Recursos Azure - Feedback Sync" -ForegroundColor Red
Write-Host "============================================================" -ForegroundColor Red
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

# Normalizar sufixo (apenas letras minúsculas e números)
$Suffix = $Suffix.ToLower() -replace '[^a-z0-9]', ''

# Construir nomes dos recursos
$storageAccountName = "feedbackstorage$Suffix"
if ($storageAccountName.Length -gt 24) {
    $storageAccountName = $storageAccountName.Substring(0, 24)
}

$functionAppName = "feedback-function-$Suffix"
if ($functionAppName.Length -gt 60) {
    $functionAppName = $functionAppName.Substring(0, 60)
}

Write-Host "📋 Recursos que serão destruídos:" -ForegroundColor Yellow
Write-Host ""
Write-Host "Resource Group:" -ForegroundColor White
Write-Host "  Nome: $ResourceGroupName" -ForegroundColor Gray
Write-Host ""
Write-Host "Function App:" -ForegroundColor White
Write-Host "  Nome: $functionAppName" -ForegroundColor Gray
Write-Host ""
Write-Host "Storage Account:" -ForegroundColor White
Write-Host "  Nome: $storageAccountName" -ForegroundColor Gray
Write-Host ""

# Verificar se Resource Group existe
Write-Host "🔍 Verificando recursos..." -ForegroundColor Yellow
$rgExists = az group exists --name $ResourceGroupName 2>&1
if ($rgExists -eq "false") {
    Write-Host "⚠️  Resource Group '$ResourceGroupName' não encontrado." -ForegroundColor Yellow
    Write-Host "   Nenhum recurso para destruir." -ForegroundColor Gray
    exit 0
}

Write-Host "   ✅ Resource Group encontrado" -ForegroundColor Green

# Listar recursos no Resource Group
Write-Host ""
Write-Host "📦 Recursos encontrados no Resource Group:" -ForegroundColor Cyan
az resource list --resource-group $ResourceGroupName --output table --query '[].{Nome:name, Tipo:type, Localizacao:location}' 2>&1 | Out-Null
Write-Host ""

# Confirmação
if (-not $Force) {
    Write-Host "⚠️  ATENÇÃO: Esta operação é IRREVERSÍVEL!" -ForegroundColor Red
    Write-Host "   Todos os dados serão perdidos permanentemente." -ForegroundColor Red
    Write-Host ""
    $confirmation = Read-Host "Digite 'SIM' para confirmar a destruição"
    
    if ($confirmation -ne "SIM") {
        Write-Host ""
        Write-Host "❌ Operação cancelada pelo usuário." -ForegroundColor Yellow
        exit 0
    }
    Write-Host ""
}

# Opção 1: Deletar apenas o Resource Group (mais rápido - deleta tudo automaticamente)
if ($DeleteResourceGroupOnly) {
    Write-Host "🗑️  Deletando Resource Group (isso deletará todos os recursos dentro dele)..." -ForegroundColor Yellow
    Write-Host "   Isso pode levar alguns minutos..." -ForegroundColor Gray
    
    az group delete --name $ResourceGroupName --yes --no-wait 2>&1 | Out-Null
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   ✅ Comando de exclusão iniciado" -ForegroundColor Green
        Write-Host ""
        Write-Host "   ⏳ O Resource Group está sendo deletado em background." -ForegroundColor Yellow
        Write-Host "   Você pode verificar o status no Azure Portal ou com:" -ForegroundColor Gray
        Write-Host "   az group show --name $ResourceGroupName" -ForegroundColor Gray
    } else {
        Write-Host "   ❌ Erro ao iniciar exclusão do Resource Group" -ForegroundColor Red
        exit 1
    }
} else {
    # Opção 2: Deletar recursos individualmente (mais controle)
    
    # 1. Deletar Function App
    Write-Host "🗑️  Deletando Function App: $functionAppName" -ForegroundColor Yellow
    $functionExists = az functionapp show --name $functionAppName --resource-group $ResourceGroupName --query "name" -o tsv 2>&1
    if ($LASTEXITCODE -eq 0 -and $functionExists) {
        az functionapp delete --name $functionAppName --resource-group $ResourceGroupName --yes 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "   ✅ Function App deletada" -ForegroundColor Green
        } else {
            Write-Host "   ⚠️  Aviso: Erro ao deletar Function App (pode não existir)" -ForegroundColor Yellow
        }
    } else {
        Write-Host "   ⚠️  Function App não encontrada (pode já ter sido deletada)" -ForegroundColor Yellow
    }
    
    # 2. Deletar Storage Account
    Write-Host ""
    Write-Host "🗑️  Deletando Storage Account: $storageAccountName" -ForegroundColor Yellow
    $storageExists = az storage account show --name $storageAccountName --resource-group $ResourceGroupName --query "name" -o tsv 2>&1
    if ($LASTEXITCODE -eq 0 -and $storageExists) {
        # Tentar obter connection string para deletar containers (opcional)
        Write-Host "   Preparando exclusão..." -ForegroundColor Gray
        $storageConnectionString = az storage account show-connection-string --name $storageAccountName --resource-group $ResourceGroupName --query connectionString -o tsv 2>&1
        
        if ($LASTEXITCODE -eq 0 -and $storageConnectionString) {
            # Tentar deletar containers (opcional, mas ajuda na limpeza)
            Write-Host "   Deletando containers..." -ForegroundColor Gray
            $containers = az storage container list --account-name $storageAccountName --connection-string $storageConnectionString --query "[].name" -o tsv 2>&1
            if ($LASTEXITCODE -eq 0 -and $containers) {
                $containers | ForEach-Object {
                    az storage container delete --name $_ --account-name $storageAccountName --connection-string $storageConnectionString --yes 2>&1 | Out-Null
                }
            }
        }
        
        # Deletar Storage Account
        az storage account delete --name $storageAccountName --resource-group $ResourceGroupName --yes 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "   ✅ Storage Account deletado" -ForegroundColor Green
        } else {
            Write-Host "   ⚠️  Aviso: Erro ao deletar Storage Account" -ForegroundColor Yellow
        }
    } else {
        Write-Host "   ⚠️  Storage Account não encontrado (pode já ter sido deletado)" -ForegroundColor Yellow
    }
    
    # 3. Deletar Resource Group (remove qualquer recurso restante)
    Write-Host ""
    Write-Host "🗑️  Deletando Resource Group: $ResourceGroupName" -ForegroundColor Yellow
    Write-Host "   Isso removerá qualquer recurso restante..." -ForegroundColor Gray
    
    az group delete --name $ResourceGroupName --yes --no-wait 2>&1 | Out-Null
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   ✅ Comando de exclusão do Resource Group iniciado" -ForegroundColor Green
    } else {
        Write-Host "   ⚠️  Aviso: Erro ao iniciar exclusão do Resource Group" -ForegroundColor Yellow
    }
}

# Resumo final
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "✅ PROCESSO DE DESTRUIÇÃO INICIADO!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Resumo:" -ForegroundColor Cyan
Write-Host ""
Write-Host "Resource Group:" -ForegroundColor White
Write-Host "  Nome: $ResourceGroupName" -ForegroundColor Gray
Write-Host "  Status: Exclusão iniciada" -ForegroundColor Gray
Write-Host ""
Write-Host "Function App:" -ForegroundColor White
Write-Host "  Nome: $functionAppName" -ForegroundColor Gray
if (-not $DeleteResourceGroupOnly) {
    Write-Host "  Status: Deletada" -ForegroundColor Gray
}
Write-Host ""
Write-Host "Storage Account:" -ForegroundColor White
Write-Host "  Nome: $storageAccountName" -ForegroundColor Gray
if (-not $DeleteResourceGroupOnly) {
    Write-Host "  Status: Deletado" -ForegroundColor Gray
}
Write-Host ""
Write-Host "💡 Notas:" -ForegroundColor Yellow
Write-Host "  - A exclusão do Resource Group pode levar alguns minutos" -ForegroundColor White
Write-Host "  - Você pode verificar o status no Azure Portal" -ForegroundColor White
Write-Host "  - Para verificar via CLI:" -ForegroundColor White
Write-Host "    az group show --name $ResourceGroupName" -ForegroundColor Gray
Write-Host ""
Write-Host "📖 Consulte GUIA_DEPLOY_AZURE.md para recriar recursos" -ForegroundColor Cyan
Write-Host ""
