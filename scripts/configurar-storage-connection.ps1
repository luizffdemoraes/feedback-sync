# ============================================
# Script para Configurar Storage Connection String
# ============================================
# Este script verifica e configura a AZURE_STORAGE_CONNECTION_STRING
# na Function App se não estiver configurada
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$FunctionAppName,  # Nome da Function App (padrão: descobre automaticamente)
    
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroup,  # Nome do Resource Group (padrão: "feedback-rg")
    
    [Parameter(Mandatory=$false)]
    [string]$StorageAccountName  # Nome do Storage Account (padrão: descobre automaticamente)
)

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Configurar Storage Connection String" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Mudar para o diretório raiz do projeto
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
Push-Location $projectRoot

# Função helper para sair e voltar ao diretório original
function Exit-Script {
    param([int]$ExitCode = 0)
    Pop-Location
    exit $ExitCode
}

# Verificar se Azure CLI está instalado
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Host "[ERRO] Azure CLI nao encontrado. Instale em: https://aka.ms/installazurecliwindows" -ForegroundColor Red
    Exit-Script 1
}

# Verificar se está logado
$azAccount = az account show 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Nao esta logado no Azure. Execute: az login" -ForegroundColor Red
    Exit-Script 1
}

Write-Host "[OK] Azure CLI verificado" -ForegroundColor Green
$subscriptionName = az account show --query name -o tsv
Write-Host "   Subscription: $subscriptionName" -ForegroundColor Gray
Write-Host ""

# Descobrir Resource Group automaticamente se não fornecido
if ([string]::IsNullOrWhiteSpace($ResourceGroup)) {
    Write-Host "Descobrindo Resource Group automaticamente..." -ForegroundColor Yellow
    
    $defaultRg = "feedback-rg"
    $rgExists = az group exists --name $defaultRg 2>&1
    if ($rgExists -eq "true") {
        $ResourceGroup = $defaultRg
        Write-Host "   [OK] Usando Resource Group padrao: $ResourceGroup" -ForegroundColor Green
    } else {
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $rgs = az group list --query "[?starts_with(name, 'feedback')].name" -o tsv 2>&1
        $ErrorActionPreference = $oldErrorAction
        
        if ($LASTEXITCODE -eq 0 -and $rgs -and -not ($rgs -match "ERROR")) {
            $rgsArray = $rgs -split "`n" | Where-Object { $_.Trim() -ne "" }
            if ($rgsArray.Count -gt 0) {
                $ResourceGroup = $rgsArray[0].Trim()
                Write-Host "   [OK] Encontrado Resource Group: $ResourceGroup" -ForegroundColor Green
            } else {
                Write-Host "[ERRO] Nenhum Resource Group encontrado." -ForegroundColor Red
                Exit-Script 1
            }
        } else {
            Write-Host "[ERRO] Nao foi possivel descobrir Resource Group automaticamente." -ForegroundColor Red
            Exit-Script 1
        }
    }
}

# Descobrir Function App automaticamente se não fornecido
if ([string]::IsNullOrWhiteSpace($FunctionAppName)) {
    Write-Host "Descobrindo Function App automaticamente..." -ForegroundColor Yellow
    
    $defaultFunctionApp = "feedback-function-prod"
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $functionExists = az functionapp show --name $defaultFunctionApp --resource-group $ResourceGroup --query "name" -o tsv 2>&1
    $ErrorActionPreference = $oldErrorAction
    
    if ($LASTEXITCODE -eq 0 -and $functionExists) {
        $FunctionAppName = $defaultFunctionApp
        Write-Host "   [OK] Usando Function App padrao: $FunctionAppName" -ForegroundColor Green
    } else {
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $functions = az functionapp list --resource-group $ResourceGroup --query "[?starts_with(name, 'feedback-function-')].name" -o tsv 2>&1
        $ErrorActionPreference = $oldErrorAction
        
        if ($LASTEXITCODE -eq 0 -and $functions -and -not ($functions -match "ERROR")) {
            $functionsArray = $functions -split "`n" | Where-Object { $_.Trim() -ne "" }
            if ($functionsArray.Count -gt 0) {
                $FunctionAppName = $functionsArray[0].Trim()
                Write-Host "   [OK] Encontrada Function App: $FunctionAppName" -ForegroundColor Green
            } else {
                Write-Host "[ERRO] Nenhuma Function App encontrada." -ForegroundColor Red
                Exit-Script 1
            }
        } else {
            Write-Host "[ERRO] Nao foi possivel descobrir Function App automaticamente." -ForegroundColor Red
            Exit-Script 1
        }
    }
}

# Descobrir Storage Account automaticamente se não fornecido
if ([string]::IsNullOrWhiteSpace($StorageAccountName)) {
    Write-Host "Descobrindo Storage Account automaticamente..." -ForegroundColor Yellow
    
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $storageAccounts = az storage account list --resource-group $ResourceGroup --query "[?starts_with(name, 'feedbackstorage')].name" -o tsv 2>&1
    $ErrorActionPreference = $oldErrorAction
    
    if ($LASTEXITCODE -eq 0 -and $storageAccounts) {
        $storageAccountsArray = ($storageAccounts -split "`n" | Where-Object { $_ -and $_.ToString().Trim() -ne "" }) | ForEach-Object { $_.ToString().Trim() }
        if ($storageAccountsArray.Count -gt 0) {
            $StorageAccountName = $storageAccountsArray[0]
            Write-Host "   [OK] Encontrado Storage Account: $StorageAccountName" -ForegroundColor Green
        } else {
            Write-Host "[ERRO] Nenhum Storage Account encontrado." -ForegroundColor Red
            Exit-Script 1
        }
    } else {
        Write-Host "[ERRO] Nao foi possivel descobrir Storage Account automaticamente." -ForegroundColor Red
        Exit-Script 1
    }
}

Write-Host ""
Write-Host "Verificando configuracoes atuais..." -ForegroundColor Yellow

# Verificar se AZURE_STORAGE_CONNECTION_STRING está configurada
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$currentConnectionString = az functionapp config appsettings list `
    --name $FunctionAppName `
    --resource-group $ResourceGroup `
    --query "[?name=='AZURE_STORAGE_CONNECTION_STRING'].value" -o tsv 2>&1
$ErrorActionPreference = $oldErrorAction

if ($LASTEXITCODE -eq 0 -and $currentConnectionString -and -not ($currentConnectionString -match "ERROR")) {
    Write-Host "   [OK] AZURE_STORAGE_CONNECTION_STRING ja esta configurada" -ForegroundColor Green
    Write-Host "   Tamanho: $($currentConnectionString.Length) caracteres" -ForegroundColor Gray
    Write-Host "   Primeiros 50 chars: $($currentConnectionString.Substring(0, [Math]::Min(50, $currentConnectionString.Length)))..." -ForegroundColor Gray
    Write-Host ""
    Write-Host "Se deseja atualizar, execute:" -ForegroundColor Yellow
    Write-Host "   .\scripts\configurar-storage-connection.ps1 -Force" -ForegroundColor Gray
    Exit-Script 0
}

Write-Host "   [AVISO] AZURE_STORAGE_CONNECTION_STRING nao encontrada" -ForegroundColor Yellow
Write-Host ""

# Tentar obter connection string do AzureWebJobsStorage primeiro (mais rápido)
Write-Host "Tentando obter connection string de AzureWebJobsStorage..." -ForegroundColor Yellow
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$azureWebJobsStorage = az functionapp config appsettings list `
    --name $FunctionAppName `
    --resource-group $ResourceGroup `
    --query "[?name=='AzureWebJobsStorage'].value" -o tsv 2>&1
$ErrorActionPreference = $oldErrorAction

$storageConnectionString = $null

if ($LASTEXITCODE -eq 0 -and $azureWebJobsStorage -and -not ($azureWebJobsStorage -match "ERROR") -and -not [string]::IsNullOrWhiteSpace($azureWebJobsStorage)) {
    Write-Host "   [OK] AzureWebJobsStorage encontrado - usando como connection string" -ForegroundColor Green
    $storageConnectionString = $azureWebJobsStorage
} else {
    Write-Host "   [AVISO] AzureWebJobsStorage nao encontrado - obtendo do Storage Account..." -ForegroundColor Yellow
    
    # Obter connection string do Storage Account
    Write-Host "Obtendo connection string do Storage Account..." -ForegroundColor Yellow
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $storageConnectionString = az storage account show-connection-string `
        --name $StorageAccountName `
        --resource-group $ResourceGroup `
        --query connectionString -o tsv 2>&1
    $ErrorActionPreference = $oldErrorAction
    
    if ($LASTEXITCODE -ne 0 -or -not $storageConnectionString -or ($storageConnectionString -match "ERROR")) {
        Write-Host "[ERRO] Nao foi possivel obter connection string do Storage Account" -ForegroundColor Red
        Write-Host "   Storage Account: $StorageAccountName" -ForegroundColor Gray
        Write-Host "   Resource Group: $ResourceGroup" -ForegroundColor Gray
        Exit-Script 1
    }
}

Write-Host "   [OK] Connection string obtida (tamanho: $($storageConnectionString.Length) caracteres)" -ForegroundColor Green
Write-Host ""

# Configurar na Function App
Write-Host "Configurando AZURE_STORAGE_CONNECTION_STRING na Function App..." -ForegroundColor Yellow
Write-Host "   Function App: $FunctionAppName" -ForegroundColor Gray
Write-Host "   Resource Group: $ResourceGroup" -ForegroundColor Gray
Write-Host ""

# Verificar se AzureWebJobsStorage precisa ser atualizado
$updateAzureWebJobsStorage = $false
if ($azureWebJobsStorage) {
    if ($azureWebJobsStorage -ne $storageConnectionString) {
        Write-Host "   [INFO] AzureWebJobsStorage sera atualizado para corresponder" -ForegroundColor Gray
        $updateAzureWebJobsStorage = $true
    }
} else {
    # Se não tinha AzureWebJobsStorage, vamos configurar também
    $updateAzureWebJobsStorage = $true
}

$settingsArray = @("AZURE_STORAGE_CONNECTION_STRING=$storageConnectionString")
if ($updateAzureWebJobsStorage) {
    $settingsArray += "AzureWebJobsStorage=$storageConnectionString"
}

$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
az functionapp config appsettings set `
    --name $FunctionAppName `
    --resource-group $ResourceGroup `
    --settings $settingsArray `
    --output table 2>&1 | Out-Null
$ErrorActionPreference = $oldErrorAction

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "[OK] CONFIGURACAO CONCLUIDA COM SUCESSO!" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host ""
Write-Host "Configuracoes aplicadas:" -ForegroundColor Cyan
Write-Host "  - AZURE_STORAGE_CONNECTION_STRING: Configurada" -ForegroundColor Gray
if ($updateAzureWebJobsStorage) {
    Write-Host "  - AzureWebJobsStorage: Configurada/Atualizada" -ForegroundColor Gray
} else {
    Write-Host "  - AzureWebJobsStorage: Ja estava configurada corretamente" -ForegroundColor Gray
}
Write-Host ""
Write-Host "Proximos passos:" -ForegroundColor Yellow
Write-Host "  1. Aguarde alguns segundos para as configuracoes serem aplicadas" -ForegroundColor White
Write-Host "  2. Verifique as configuracoes:" -ForegroundColor White
Write-Host "     .\scripts\verificar-variaveis-cloud.ps1" -ForegroundColor Gray
Write-Host "  3. Teste o endpoint novamente:" -ForegroundColor White
Write-Host "     curl --location 'https://$FunctionAppName.azurewebsites.net/api/avaliacao' --header 'Content-Type: application/json' --data '{\"descricao\":\"Teste\",\"nota\":2,\"urgencia\":\"HIGH\"}'" -ForegroundColor Gray
Write-Host ""
} else {
    Write-Host ""
    Write-Host "[ERRO] Erro ao configurar connection string" -ForegroundColor Red
    Exit-Script 1
}

Exit-Script 0
