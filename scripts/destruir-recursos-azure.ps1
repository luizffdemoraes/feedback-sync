# ============================================
# Script para Destruir Recursos Azure
# ============================================
# Este script remove todos os recursos criados pelo criar-recursos-azure.ps1:
# - Function App (e Application Settings)
# - Storage Account (containers e tabelas)
#   - Container: weekly-reports
#   - Tabela: feedbacks
# - Resource Group (remove qualquer recurso restante)
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
Write-Host "  DESTRUICAO de Recursos Azure - Feedback Sync" -ForegroundColor Red
Write-Host "============================================================" -ForegroundColor Red
Write-Host ""

# Verificar se Azure CLI está instalado
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Host "[ERRO] Azure CLI nao encontrado. Instale em: https://aka.ms/installazurecliwindows" -ForegroundColor Red
    exit 1
}

# Verificar se está logado
$azAccount = az account show 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Nao esta logado no Azure. Execute: az login" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Azure CLI verificado" -ForegroundColor Green
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

Write-Host "Recursos que serao destruidos:" -ForegroundColor Yellow
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
Write-Host "- Verificando recursos..." -ForegroundColor Yellow
$rgExists = az group exists --name $ResourceGroupName 2>&1
if ($rgExists -eq "false") {
    Write-Host "[AVISO] Resource Group '$ResourceGroupName' nao encontrado." -ForegroundColor Yellow
    Write-Host "   Nenhum recurso para destruir." -ForegroundColor Gray
    exit 0
}

Write-Host "   [OK] Resource Group encontrado" -ForegroundColor Green

# Verificar recursos específicos antes de deletar
Write-Host ""
Write-Host "- Verificando recursos especificos..." -ForegroundColor Yellow

# Verificar Function App
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$functionExists = az functionapp show --name $functionAppName --resource-group $ResourceGroupName --query "name" -o tsv 2>&1
$ErrorActionPreference = $oldErrorAction
if ($LASTEXITCODE -eq 0 -and $functionExists) {
    Write-Host "   [OK] Function App encontrada: $functionAppName" -ForegroundColor Green
} else {
    Write-Host "   [AVISO] Function App nao encontrada: $functionAppName" -ForegroundColor Yellow
}

# Verificar Storage Account
$ErrorActionPreference = "Continue"
$storageExists = az storage account show --name $storageAccountName --resource-group $ResourceGroupName --query "name" -o tsv 2>&1
$ErrorActionPreference = $oldErrorAction
if ($LASTEXITCODE -eq 0 -and $storageExists) {
    Write-Host "   [OK] Storage Account encontrado: $storageAccountName" -ForegroundColor Green
} else {
    Write-Host "   [AVISO] Storage Account nao encontrado no Resource Group: $storageAccountName" -ForegroundColor Yellow
    # Verificar se existe em outro Resource Group (Storage Account names são únicos globalmente)
    $ErrorActionPreference = "Continue"
    $storageGlobalCheck = az storage account show --name $storageAccountName --query "resourceGroup" -o tsv 2>&1
    $ErrorActionPreference = $oldErrorAction
    if ($LASTEXITCODE -eq 0 -and $storageGlobalCheck -and $storageGlobalCheck -ne $ResourceGroupName) {
        Write-Host "   [AVISO] Storage Account existe em outro Resource Group: $storageGlobalCheck" -ForegroundColor Yellow
        Write-Host "      Voce precisara deleta-lo manualmente ou usar o Resource Group correto" -ForegroundColor Gray
    }
}

# Listar recursos no Resource Group
Write-Host ""
Write-Host "- Recursos encontrados no Resource Group:" -ForegroundColor Cyan
az resource list --resource-group $ResourceGroupName --output table --query '[].{Nome:name, Tipo:type, Localizacao:location}' 2>&1 | Out-Null
Write-Host ""

# Confirmação
if (-not $Force) {
    Write-Host "[AVISO] ATENCAO: Esta operacao e IRREVERSIVEL!" -ForegroundColor Red
    Write-Host "   Todos os dados serao perdidos permanentemente." -ForegroundColor Red
    Write-Host ""
    $confirmation = Read-Host "Digite 'SIM' para confirmar a destruição"
    
    if ($confirmation -ne "SIM") {
        Write-Host ""
        Write-Host "[CANCELADO] Operacao cancelada pelo usuario." -ForegroundColor Yellow
        exit 0
    }
    Write-Host ""
}

# Opção 1: Deletar apenas o Resource Group (mais rápido - deleta tudo automaticamente)
if ($DeleteResourceGroupOnly) {
    Write-Host "- Deletando Resource Group (isso deletara todos os recursos dentro dele)..." -ForegroundColor Yellow
    Write-Host "   Isso pode levar alguns minutos..." -ForegroundColor Gray
    
    az group delete --name $ResourceGroupName --yes --no-wait 2>&1 | Out-Null
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   [OK] Comando de exclusao iniciado" -ForegroundColor Green
        Write-Host ""
        Write-Host "   - O Resource Group esta sendo deletado em background." -ForegroundColor Yellow
        Write-Host "   Voce pode verificar o status no Azure Portal ou com:" -ForegroundColor Gray
        Write-Host "   az group show --name $ResourceGroupName" -ForegroundColor Gray
    } else {
        Write-Host "   [ERRO] Erro ao iniciar exclusao do Resource Group" -ForegroundColor Red
        exit 1
    }
} else {
    # Opção 2: Deletar recursos individualmente (mais controle)
    
    # 1. Deletar Function App (isso também remove Application Settings automaticamente)
    Write-Host "- Deletando Function App: $functionAppName" -ForegroundColor Yellow
    Write-Host "   (Application Settings serao removidas automaticamente)" -ForegroundColor Gray
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $functionExists = az functionapp show --name $functionAppName --resource-group $ResourceGroupName --query "name" -o tsv 2>&1
    $ErrorActionPreference = $oldErrorAction
    if ($LASTEXITCODE -eq 0 -and $functionExists) {
        az functionapp delete --name $functionAppName --resource-group $ResourceGroupName 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "   [OK] Function App deletada (incluindo Application Settings)" -ForegroundColor Green
        } else {
            Write-Host "   [AVISO] Aviso: Erro ao deletar Function App" -ForegroundColor Yellow
        }
    } else {
        Write-Host "   [AVISO] Function App nao encontrada (pode ja ter sido deletada)" -ForegroundColor Yellow
    }
    
    # 2. Deletar Storage Account (incluindo containers e tabelas)
    Write-Host ""
    Write-Host "- Deletando Storage Account: $storageAccountName" -ForegroundColor Yellow
    Write-Host "   (Containers, tabelas e dados serao removidos)" -ForegroundColor Gray
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $storageExists = az storage account show --name $storageAccountName --resource-group $ResourceGroupName --query "name" -o tsv 2>&1
    $ErrorActionPreference = $oldErrorAction
    if ($LASTEXITCODE -eq 0 -and $storageExists) {
        # Tentar obter connection string para deletar containers antes de deletar o Storage Account
        Write-Host "   Preparando exclusao..." -ForegroundColor Gray
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $storageConnectionString = az storage account show-connection-string --name $storageAccountName --resource-group $ResourceGroupName --query connectionString -o tsv 2>&1
        $ErrorActionPreference = $oldErrorAction
        
        if ($LASTEXITCODE -eq 0 -and $storageConnectionString) {
            # Deletar containers (incluindo o container "weekly-reports" criado pelo script)
            Write-Host "   Deletando containers (incluindo 'weekly-reports')..." -ForegroundColor Gray
            $oldErrorAction = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            $containers = az storage container list --account-name $storageAccountName --connection-string $storageConnectionString --query "[].name" -o tsv 2>&1
            $ErrorActionPreference = $oldErrorAction
            if ($LASTEXITCODE -eq 0 -and $containers) {
                $containerCount = ($containers | Measure-Object).Count
                Write-Host "      Encontrados $containerCount container(s)..." -ForegroundColor Gray
                $containers | ForEach-Object {
                    Write-Host "      Deletando container: $_" -ForegroundColor Gray
                    $oldErrorAction = $ErrorActionPreference
                    $ErrorActionPreference = "Continue"
                    az storage container delete --name $_ --account-name $storageAccountName --connection-string $storageConnectionString --yes 2>&1 | Out-Null
                    $ErrorActionPreference = $oldErrorAction
                    if ($LASTEXITCODE -eq 0) {
                        Write-Host "         [OK] Container '$_' deletado" -ForegroundColor Green
                    }
                }
            } else {
                Write-Host "      Nenhum container encontrado" -ForegroundColor Gray
            }
        }
        
        # Deletar Storage Account (isso também remove tabelas automaticamente)
        Write-Host "   Deletando Storage Account (incluindo tabela 'feedbacks')..." -ForegroundColor Gray
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        az storage account delete --name $storageAccountName --resource-group $ResourceGroupName --yes 2>&1 | Out-Null
        $ErrorActionPreference = $oldErrorAction
        if ($LASTEXITCODE -eq 0) {
            Write-Host "   [OK] Storage Account deletado (containers e tabelas removidos)" -ForegroundColor Green
        } else {
            Write-Host "   [AVISO] Aviso: Erro ao deletar Storage Account" -ForegroundColor Yellow
        }
    } else {
        Write-Host "   [AVISO] Storage Account nao encontrado no Resource Group (pode ja ter sido deletado)" -ForegroundColor Yellow
    }
    
    # 3. Deletar Resource Group (remove qualquer recurso restante)
    Write-Host ""
    Write-Host "- Deletando Resource Group: $ResourceGroupName" -ForegroundColor Yellow
    Write-Host "   Isso removera qualquer recurso restante..." -ForegroundColor Gray
    
    az group delete --name $ResourceGroupName --yes --no-wait 2>&1 | Out-Null
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   [OK] Comando de exclusao do Resource Group iniciado" -ForegroundColor Green
    } else {
        Write-Host "   [AVISO] Aviso: Erro ao iniciar exclusao do Resource Group" -ForegroundColor Yellow
    }
}

# Resumo final
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "[OK] PROCESSO DE DESTRUICAO INICIADO!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "- Resumo:" -ForegroundColor Cyan
Write-Host ""
Write-Host "Resource Group:" -ForegroundColor White
Write-Host "  Nome: $ResourceGroupName" -ForegroundColor Gray
Write-Host "  Status: Exclusao iniciada" -ForegroundColor Gray
Write-Host ""
Write-Host "Function App:" -ForegroundColor White
Write-Host "  Nome: $functionAppName" -ForegroundColor Gray
if (-not $DeleteResourceGroupOnly) {
    Write-Host "  Status: Deletada (Application Settings removidas)" -ForegroundColor Gray
}
Write-Host ""
Write-Host "Storage Account:" -ForegroundColor White
Write-Host "  Nome: $storageAccountName" -ForegroundColor Gray
if (-not $DeleteResourceGroupOnly) {
    Write-Host "  Status: Deletado (containers e tabelas removidos)" -ForegroundColor Gray
    Write-Host "  Container removido: weekly-reports" -ForegroundColor Gray
    Write-Host "  Tabela removida: feedbacks" -ForegroundColor Gray
}
Write-Host ""
Write-Host "- Notas:" -ForegroundColor Yellow
    Write-Host "  - A exclusao do Resource Group pode levar alguns minutos" -ForegroundColor White
    Write-Host "  - Voce pode verificar o status no Azure Portal" -ForegroundColor White
Write-Host "  - Para verificar via CLI:" -ForegroundColor White
Write-Host "    az group show --name $ResourceGroupName" -ForegroundColor Gray
Write-Host ""
Write-Host "[DOC] Consulte GUIA_DEPLOY_AZURE.md para recriar recursos" -ForegroundColor Cyan
Write-Host ""
