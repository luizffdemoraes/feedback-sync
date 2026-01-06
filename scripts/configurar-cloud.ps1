# ============================================
# Script ÚNICO para Configurar Tudo na Cloud (Azure)
# ============================================
# Este é o script único e completo para configurar todas as variáveis
# de ambiente na Function App do Azure.
#
# O que este script faz:
# 1. Configura Storage Connection String (AZURE_STORAGE_CONNECTION_STRING e AzureWebJobsStorage)
# 2. Sincroniza variáveis de ambiente do sistema ($env:VARIAVEL) para a cloud
# 3. Configura todas as variáveis necessárias para o funcionamento completo
#
# O script lê as variáveis de ambiente da máquina e configura tudo na cloud.
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$FunctionAppName,  # Nome da Function App (padrão: descobre automaticamente)
    
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroup,  # Nome do Resource Group (padrão: "feedback-rg")
    
    [Parameter(Mandatory=$false)]
    [string]$StorageAccountName,  # Nome do Storage Account (padrão: descobre automaticamente)
    
    [Parameter(Mandatory=$false)]
    [switch]$Force  # Força atualização mesmo se já estiver configurado
)

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Script ÚNICO de Configuração - Cloud (Azure)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Este script configura TODAS as variáveis necessárias:" -ForegroundColor Gray
Write-Host "  - Storage Connection String" -ForegroundColor Gray
Write-Host "  - Variáveis de ambiente do sistema" -ForegroundColor Gray
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

Write-Host ""
Write-Host "Coletando variaveis de ambiente do sistema..." -ForegroundColor Yellow

# Coletar variáveis de ambiente do sistema
$localVars = @{}

# Mailtrap - variáveis de ambiente do sistema
$mailtrapToken = $env:MAILTRAP_API_TOKEN
$adminEmail = $env:ADMIN_EMAIL
$mailtrapInboxId = $env:MAILTRAP_INBOX_ID

if (-not [string]::IsNullOrWhiteSpace($mailtrapToken)) {
    $localVars["MAILTRAP_API_TOKEN"] = $mailtrapToken
    Write-Host "   [OK] MAILTRAP_API_TOKEN encontrado: $($mailtrapToken.Substring(0, [Math]::Min(8, $mailtrapToken.Length)))..." -ForegroundColor Green
} else {
    Write-Host "   [!] MAILTRAP_API_TOKEN nao encontrado nas variaveis de ambiente" -ForegroundColor Yellow
}

if (-not [string]::IsNullOrWhiteSpace($adminEmail)) {
    $localVars["ADMIN_EMAIL"] = $adminEmail
    Write-Host "   [OK] ADMIN_EMAIL encontrado: $adminEmail" -ForegroundColor Green
} else {
    Write-Host "   [!] ADMIN_EMAIL nao encontrado nas variaveis de ambiente" -ForegroundColor Yellow
}

if (-not [string]::IsNullOrWhiteSpace($mailtrapInboxId)) {
    $localVars["MAILTRAP_INBOX_ID"] = $mailtrapInboxId
    Write-Host "   [OK] MAILTRAP_INBOX_ID encontrado: $mailtrapInboxId" -ForegroundColor Green
} else {
    Write-Host "   [!] MAILTRAP_INBOX_ID nao encontrado nas variaveis de ambiente" -ForegroundColor Yellow
}

# Outras variáveis de ambiente opcionais
$tableName = $env:AZURE_TABLE_NAME
if (-not [string]::IsNullOrWhiteSpace($tableName)) {
    $localVars["azure.table.table-name"] = $tableName
    Write-Host "   [OK] AZURE_TABLE_NAME encontrado: $tableName" -ForegroundColor Green
}

$containerName = $env:AZURE_STORAGE_CONTAINER_NAME
if (-not [string]::IsNullOrWhiteSpace($containerName)) {
    $localVars["azure.storage.container-name"] = $containerName
    Write-Host "   [OK] AZURE_STORAGE_CONTAINER_NAME encontrado: $containerName" -ForegroundColor Green
}

$reportSchedule = $env:REPORT_SCHEDULE_CRON
if (-not [string]::IsNullOrWhiteSpace($reportSchedule)) {
    $localVars["REPORT_SCHEDULE_CRON"] = $reportSchedule
    Write-Host "   [OK] REPORT_SCHEDULE_CRON encontrado: $reportSchedule" -ForegroundColor Green
}

Write-Host ""

# Obter Storage Connection String
Write-Host "Configurando Storage Connection String..." -ForegroundColor Yellow

# Verificar se já está configurada
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$currentStorageConnection = az functionapp config appsettings list `
    --name $FunctionAppName `
    --resource-group $ResourceGroup `
    --query "[?name=='AZURE_STORAGE_CONNECTION_STRING'].value" -o tsv 2>&1
$ErrorActionPreference = $oldErrorAction

$needsStorageConfig = $true
if ($LASTEXITCODE -eq 0 -and $currentStorageConnection -and -not ($currentStorageConnection -match "ERROR") -and -not [string]::IsNullOrWhiteSpace($currentStorageConnection)) {
    if (-not $Force) {
        Write-Host "   [OK] AZURE_STORAGE_CONNECTION_STRING ja esta configurada" -ForegroundColor Green
        Write-Host "   (use -Force para atualizar)" -ForegroundColor Gray
        $needsStorageConfig = $false
    } else {
        Write-Host "   [AVISO] AZURE_STORAGE_CONNECTION_STRING ja configurada - sera atualizada (Force)" -ForegroundColor Yellow
    }
}

$storageConnectionString = $null

if ($needsStorageConfig) {
    # Descobrir Storage Account automaticamente se não fornecido
    if ([string]::IsNullOrWhiteSpace($StorageAccountName)) {
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $storageAccounts = az storage account list --resource-group $ResourceGroup --query "[?starts_with(name, 'feedbackstorage')].name" -o tsv 2>&1
        $ErrorActionPreference = $oldErrorAction
        
        if ($LASTEXITCODE -eq 0 -and $storageAccounts) {
            $storageAccountsArray = ($storageAccounts -split "`n" | Where-Object { $_ -and $_.ToString().Trim() -ne "" }) | ForEach-Object { $_.ToString().Trim() }
            if ($storageAccountsArray.Count -gt 0) {
                $StorageAccountName = $storageAccountsArray[0]
                Write-Host "   [OK] Storage Account encontrado: $StorageAccountName" -ForegroundColor Green
            }
        }
    }
    
    # Tentar obter connection string do AzureWebJobsStorage primeiro (mais rápido)
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $azureWebJobsStorage = az functionapp config appsettings list `
        --name $FunctionAppName `
        --resource-group $ResourceGroup `
        --query "[?name=='AzureWebJobsStorage'].value" -o tsv 2>&1
    $ErrorActionPreference = $oldErrorAction
    
    if ($LASTEXITCODE -eq 0 -and $azureWebJobsStorage -and -not ($azureWebJobsStorage -match "ERROR") -and -not [string]::IsNullOrWhiteSpace($azureWebJobsStorage)) {
        Write-Host "   [OK] AzureWebJobsStorage encontrado - usando como connection string" -ForegroundColor Green
        $storageConnectionString = $azureWebJobsStorage
    } elseif ($StorageAccountName) {
        Write-Host "   [AVISO] AzureWebJobsStorage nao encontrado - obtendo do Storage Account..." -ForegroundColor Yellow
        
        # Obter connection string do Storage Account
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $storageConnectionString = az storage account show-connection-string `
            --name $StorageAccountName `
            --resource-group $ResourceGroup `
            --query connectionString -o tsv 2>&1
        $ErrorActionPreference = $oldErrorAction
        
        if ($LASTEXITCODE -ne 0 -or -not $storageConnectionString -or ($storageConnectionString -match "ERROR")) {
            Write-Host "   [ERRO] Nao foi possivel obter connection string do Storage Account" -ForegroundColor Red
            Write-Host "   Continuando sem configurar Storage Connection String..." -ForegroundColor Yellow
            $storageConnectionString = $null
        } else {
            Write-Host "   [OK] Connection string obtida do Storage Account" -ForegroundColor Green
        }
    } else {
        Write-Host "   [AVISO] Storage Account nao encontrado - pulando configuracao de Storage" -ForegroundColor Yellow
    }
}

# Preparar array de configurações para aplicar
$settingsArray = @()

# Storage Connection String (configurar se necessário)
if ($needsStorageConfig -and $storageConnectionString) {
    $settingsArray += "AZURE_STORAGE_CONNECTION_STRING=$storageConnectionString"
    $settingsArray += "AzureWebJobsStorage=$storageConnectionString"
    Write-Host ""
    Write-Host "Variaveis de Storage a configurar:" -ForegroundColor Cyan
    Write-Host "   - AZURE_STORAGE_CONNECTION_STRING" -ForegroundColor Gray
    Write-Host "   - AzureWebJobsStorage" -ForegroundColor Gray
} elseif ($needsStorageConfig -and -not $storageConnectionString) {
    Write-Host ""
    Write-Host "[AVISO] Storage Connection String nao pode ser configurada (Storage Account nao encontrado)" -ForegroundColor Yellow
}

# Variáveis coletadas das variáveis de ambiente do sistema
if ($localVars.Count -gt 0) {
    Write-Host ""
    Write-Host "Variaveis de ambiente encontradas a configurar na cloud:" -ForegroundColor Cyan
    foreach ($key in $localVars.Keys) {
        $value = $localVars[$key]
        $preview = $value
        if ($key -match "TOKEN|KEY|CONNECTION_STRING") {
            if ($value.Length -gt 20) {
                $preview = $value.Substring(0, 10) + "..." + $value.Substring($value.Length - 5)
            } else {
                $preview = "***"
            }
        }
        Write-Host "   - $key = $preview" -ForegroundColor Gray
        $settingsArray += "$key=$value"
    }
}

if ($settingsArray.Count -eq 0) {
    Write-Host ""
    Write-Host "[AVISO] Nenhuma variavel encontrada para configurar." -ForegroundColor Yellow
    Write-Host ""
    
    if (-not $needsStorageConfig) {
        Write-Host "Storage Connection String: [OK] Ja configurada" -ForegroundColor Green
    } else {
        Write-Host "Storage Connection String: [AVISO] Nao configurada (Storage Account nao encontrado)" -ForegroundColor Yellow
    }
    
    if ($localVars.Count -eq 0) {
        Write-Host ""
        Write-Host "Configure as variaveis de ambiente do sistema primeiro:" -ForegroundColor Yellow
        Write-Host '   $env:MAILTRAP_API_TOKEN = "seu-token"' -ForegroundColor Gray
        Write-Host '   $env:ADMIN_EMAIL = "seu-email@exemplo.com"' -ForegroundColor Gray
        Write-Host '   $env:MAILTRAP_INBOX_ID = "seu-inbox-id"' -ForegroundColor Gray
        Write-Host ""
        Write-Host "Para configurar permanentemente no Windows:" -ForegroundColor Yellow
        Write-Host '   [System.Environment]::SetEnvironmentVariable("MAILTRAP_API_TOKEN", "seu-token", "User")' -ForegroundColor Gray
        Write-Host '   [System.Environment]::SetEnvironmentVariable("ADMIN_EMAIL", "seu-email@exemplo.com", "User")' -ForegroundColor Gray
        Write-Host '   [System.Environment]::SetEnvironmentVariable("MAILTRAP_INBOX_ID", "seu-inbox-id", "User")' -ForegroundColor Gray
        Write-Host ""
        Write-Host "Depois, reinicie o terminal e execute este script novamente." -ForegroundColor Yellow
    }
    
    Exit-Script 0
}

Write-Host ""
Write-Host "Verificando configuracoes atuais na Function App..." -ForegroundColor Yellow

# Verificar configurações atuais (apenas informativo)
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$currentSettings = az functionapp config appsettings list `
    --name $FunctionAppName `
    --resource-group $ResourceGroup `
    --query "[].{Name:name, Value:value}" -o json 2>&1
$ErrorActionPreference = $oldErrorAction

if ($LASTEXITCODE -eq 0 -and $currentSettings -and -not ($currentSettings -match "ERROR")) {
    $currentSettingsObj = $currentSettings | ConvertFrom-Json
    $currentHash = @{}
    foreach ($setting in $currentSettingsObj) {
        $currentHash[$setting.Name] = $setting.Value
    }
    
    Write-Host "   Configuracoes existentes:" -ForegroundColor Gray
    foreach ($setting in $settingsArray) {
        $parts = $setting -split "=", 2
        if ($parts.Count -eq 2) {
            $varName = $parts[0]
            $varValue = $parts[1]
            
            if ($currentHash.ContainsKey($varName)) {
                $currentValue = $currentHash[$varName]
                if ($currentValue -eq $varValue -and -not $Force) {
                    Write-Host "   - $varName : [JA CONFIGURADA] (use -Force para atualizar)" -ForegroundColor Green
                } else {
                    Write-Host "   - $varName : [SERA ATUALIZADA]" -ForegroundColor Yellow
                }
            } else {
                Write-Host "   - $varName : [SERA ADICIONADA]" -ForegroundColor Cyan
            }
        }
    }
}

Write-Host ""
Write-Host "Configurando variaveis na Function App..." -ForegroundColor Yellow
Write-Host "   Function App: $FunctionAppName" -ForegroundColor Gray
Write-Host "   Resource Group: $ResourceGroup" -ForegroundColor Gray
Write-Host "   Total de variaveis: $($settingsArray.Count)" -ForegroundColor Gray
Write-Host ""

# Configurar na Function App
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$result = az functionapp config appsettings set `
    --name $FunctionAppName `
    --resource-group $ResourceGroup `
    --settings $settingsArray `
    --output json 2>&1
$ErrorActionPreference = $oldErrorAction

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "[OK] CONFIGURACAO CONCLUIDA COM SUCESSO!" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Configuracoes aplicadas (sincronizadas das variaveis de ambiente do sistema):" -ForegroundColor Cyan
    
    foreach ($setting in $settingsArray) {
        $parts = $setting -split "=", 2
        if ($parts.Count -eq 2) {
            $varName = $parts[0]
            Write-Host "   - $varName : Configurada" -ForegroundColor Gray
        }
    }
    
    Write-Host ""
    Write-Host "Proximos passos:" -ForegroundColor Yellow
    Write-Host "  1. Aguarde alguns segundos para as configuracoes serem aplicadas" -ForegroundColor White
    Write-Host "  2. Verifique as configuracoes:" -ForegroundColor White
    Write-Host "     .\scripts\verificar-variaveis-cloud.ps1" -ForegroundColor Gray
    Write-Host "  3. Teste o endpoint:" -ForegroundColor White
    Write-Host "     curl --location 'https://$FunctionAppName.azurewebsites.net/api/avaliacao' --header 'Content-Type: application/json' --data '{\"descricao\":\"Teste\",\"nota\":8}'" -ForegroundColor Gray
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "[ERRO] Erro ao configurar variaveis" -ForegroundColor Red
    Write-Host "   Detalhes: $result" -ForegroundColor Gray
    Exit-Script 1
}

Exit-Script 0
