# ============================================
# Script para Corrigir Configuração no Azure
# ============================================
# Este script verifica e corrige automaticamente as configurações necessárias
# para o funcionamento da aplicação no Azure
# ============================================

param(
    [string]$FunctionAppName = "feedback-function-prod",
    [string]$ResourceGroup = "feedback-rg",
    [string]$StorageAccountName = "",  # Será detectado automaticamente se vazio
    [switch]$FixMissingVars,
    [switch]$CreateContainer,
    [switch]$RestartFunctionApp
)

$ErrorActionPreference = "Continue"

Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "  CORREÇÃO DE CONFIGURAÇÃO AZURE" -ForegroundColor Cyan
Write-Host "============================================`n" -ForegroundColor Cyan

# Detectar Storage Account se não fornecido
if ([string]::IsNullOrWhiteSpace($StorageAccountName)) {
    Write-Host "Detectando Storage Account..." -ForegroundColor Yellow
    $storageAccounts = az storage account list --resource-group $ResourceGroup --query "[].name" -o tsv 2>$null
    if ($storageAccounts) {
        $StorageAccountName = ($storageAccounts | Select-Object -First 1).Trim()
        Write-Host "  [OK] Storage Account detectado: $StorageAccountName" -ForegroundColor Green
    } else {
        Write-Host "  [ERRO] Não foi possível detectar Storage Account" -ForegroundColor Red
        Write-Host "  Forneça o nome: -StorageAccountName 'feedbackstorageprod'" -ForegroundColor Yellow
        exit 1
    }
}

Write-Host "`nFunction App: $FunctionAppName" -ForegroundColor Cyan
Write-Host "Resource Group: $ResourceGroup" -ForegroundColor Cyan
Write-Host "Storage Account: $StorageAccountName`n" -ForegroundColor Cyan

# Verificar se Function App existe
Write-Host "[1/5] Verificando Function App..." -ForegroundColor Yellow
$functionAppExists = az functionapp show --name $FunctionAppName --resource-group $ResourceGroup --query "name" -o tsv 2>$null
if ([string]::IsNullOrWhiteSpace($functionAppExists)) {
    Write-Host "  [ERRO] Function App não encontrada: $FunctionAppName" -ForegroundColor Red
    exit 1
}
Write-Host "  [OK] Function App encontrada" -ForegroundColor Green

# Verificar status
$functionAppState = az functionapp show --name $FunctionAppName --resource-group $ResourceGroup --query "state" -o tsv
Write-Host "  Status: $functionAppState" -ForegroundColor $(if ($functionAppState -eq "Running") { "Green" } else { "Yellow" })

# Verificar variáveis de ambiente
Write-Host "`n[2/5] Verificando variáveis de ambiente..." -ForegroundColor Yellow

$requiredVars = @{
    "AZURE_STORAGE_CONNECTION_STRING" = $null
    "AzureWebJobsStorage" = $null
    "FUNCTIONS_WORKER_RUNTIME" = "java"
    "FUNCTIONS_EXTENSION_VERSION" = "~4"
    "MAILTRAP_API_TOKEN" = $null
    "MAILTRAP_INBOX_ID" = $null
    "ADMIN_EMAIL" = $null
}

$missingVars = @()
$varsToFix = @{}

# Obter todas as variáveis configuradas
$currentSettings = az functionapp config appsettings list `
    --name $FunctionAppName `
    --resource-group $ResourceGroup `
    --query "[].{Name:name, Value:value}" -o json 2>$null | ConvertFrom-Json

$settingsDict = @{}
foreach ($setting in $currentSettings) {
    $settingsDict[$setting.Name] = $setting.Value
}

# Verificar cada variável obrigatória
foreach ($varName in $requiredVars.Keys) {
    $expectedValue = $requiredVars[$varName]
    $currentValue = if ($settingsDict.ContainsKey($varName)) { $settingsDict[$varName] } else { $null }
    
    if ([string]::IsNullOrWhiteSpace($currentValue)) {
        Write-Host "  [X] $varName - NÃO CONFIGURADA" -ForegroundColor Red
        $missingVars += $varName
        
        # Se tem valor esperado, usar ele
        if ($expectedValue) {
            $varsToFix[$varName] = $expectedValue
        } else {
            $varsToFix[$varName] = ""
        }
    } else {
        Write-Host "  [OK] $varName - Configurada" -ForegroundColor Green
    }
}

# Configurar variáveis faltantes
if ($missingVars.Count -gt 0) {
    Write-Host "`n  Variáveis faltantes: $($missingVars.Count)" -ForegroundColor Yellow
    
    if ($FixMissingVars) {
        Write-Host "  Configurando variáveis faltantes..." -ForegroundColor Yellow
        
        # Obter connection string do Storage Account
        if ($missingVars -contains "AZURE_STORAGE_CONNECTION_STRING" -or $missingVars -contains "AzureWebJobsStorage") {
            Write-Host "    Obtendo connection string do Storage Account..." -ForegroundColor Gray
            $storageConnectionString = az storage account show-connection-string `
                --name $StorageAccountName `
                --resource-group $ResourceGroup `
                --query connectionString -o tsv 2>$null
            
            if ($storageConnectionString) {
                $varsToFix["AZURE_STORAGE_CONNECTION_STRING"] = $storageConnectionString
                $varsToFix["AzureWebJobsStorage"] = $storageConnectionString
                Write-Host "    [OK] Connection string obtida" -ForegroundColor Green
            } else {
                Write-Host "    [ERRO] Não foi possível obter connection string" -ForegroundColor Red
            }
        }
        
        # Configurar variáveis com valores padrão
        if ($varsToFix.ContainsKey("FUNCTIONS_WORKER_RUNTIME") -and [string]::IsNullOrWhiteSpace($varsToFix["FUNCTIONS_WORKER_RUNTIME"])) {
            $varsToFix["FUNCTIONS_WORKER_RUNTIME"] = "java"
        }
        if ($varsToFix.ContainsKey("FUNCTIONS_EXTENSION_VERSION") -and [string]::IsNullOrWhiteSpace($varsToFix["FUNCTIONS_EXTENSION_VERSION"])) {
            $varsToFix["FUNCTIONS_EXTENSION_VERSION"] = "~4"
        }
        
        # Configurar no Azure
        $settingsToSet = @()
        foreach ($varName in $varsToFix.Keys) {
            $varValue = $varsToFix[$varName]
            if (-not [string]::IsNullOrWhiteSpace($varValue)) {
                $settingsToSet += "$varName=$varValue"
            }
        }
        
        if ($settingsToSet.Count -gt 0) {
            Write-Host "    Configurando $($settingsToSet.Count) variável(is)..." -ForegroundColor Gray
            az functionapp config appsettings set `
                --name $FunctionAppName `
                --resource-group $ResourceGroup `
                --settings $settingsToSet `
                --output none 2>$null
            
            if ($LASTEXITCODE -eq 0) {
                Write-Host "    [OK] Variáveis configuradas" -ForegroundColor Green
            } else {
                Write-Host "    [ERRO] Falha ao configurar variáveis" -ForegroundColor Red
            }
        } else {
            Write-Host "    [AVISO] Nenhuma variável pode ser configurada automaticamente" -ForegroundColor Yellow
            Write-Host "    Configure manualmente:" -ForegroundColor Yellow
            foreach ($varName in $missingVars) {
                if ($varName -notin @("AZURE_STORAGE_CONNECTION_STRING", "AzureWebJobsStorage", "FUNCTIONS_WORKER_RUNTIME", "FUNCTIONS_EXTENSION_VERSION")) {
                    Write-Host "      - $varName" -ForegroundColor Gray
                }
            }
        }
    } else {
        Write-Host "`n  Execute com -FixMissingVars para configurar automaticamente" -ForegroundColor Yellow
        Write-Host "  Ou configure manualmente no Portal Azure" -ForegroundColor Yellow
    }
} else {
    Write-Host "  [OK] Todas as variáveis estão configuradas" -ForegroundColor Green
}

# Verificar e criar container
Write-Host "`n[3/5] Verificando container no Storage..." -ForegroundColor Yellow
if ($CreateContainer) {
    $storageConnectionString = az storage account show-connection-string `
        --name $StorageAccountName `
        --resource-group $ResourceGroup `
        --query connectionString -o tsv 2>$null
    
    if ($storageConnectionString) {
        $containerExists = az storage container exists `
            --name "weekly-reports" `
            --account-name $StorageAccountName `
            --connection-string $storageConnectionString `
            --query exists -o tsv 2>$null
        
        if ($containerExists -eq "true") {
            Write-Host "  [OK] Container 'weekly-reports' existe" -ForegroundColor Green
        } else {
            Write-Host "  [X] Container 'weekly-reports' não existe" -ForegroundColor Red
            Write-Host "  Criando container..." -ForegroundColor Yellow
            az storage container create `
                --name "weekly-reports" `
                --account-name $StorageAccountName `
                --connection-string $storageConnectionString `
                --output none 2>$null
            
            if ($LASTEXITCODE -eq 0) {
                Write-Host "  [OK] Container criado" -ForegroundColor Green
            } else {
                Write-Host "  [ERRO] Falha ao criar container" -ForegroundColor Red
            }
        }
    } else {
        Write-Host "  [AVISO] Não foi possível verificar container (connection string não disponível)" -ForegroundColor Yellow
    }
} else {
    Write-Host "  Execute com -CreateContainer para verificar/criar container" -ForegroundColor Gray
}

# Verificar CORS
Write-Host "`n[4/5] Verificando CORS..." -ForegroundColor Yellow
$corsOrigins = az functionapp cors show `
    --name $FunctionAppName `
    --resource-group $ResourceGroup `
    --query "allowedOrigins" -o tsv 2>$null

if ($corsOrigins) {
    Write-Host "  [OK] CORS configurado: $corsOrigins" -ForegroundColor Green
} else {
    Write-Host "  [AVISO] CORS não configurado (pode ser necessário para requisições do navegador)" -ForegroundColor Yellow
    Write-Host "  Configure com: az functionapp cors add --name $FunctionAppName --resource-group $ResourceGroup --allowed-origins '*'" -ForegroundColor Gray
}

# Reiniciar Function App se necessário
if ($RestartFunctionApp -or ($missingVars.Count -gt 0 -and $FixMissingVars)) {
    Write-Host "`n[5/5] Reiniciando Function App..." -ForegroundColor Yellow
    az functionapp restart `
        --name $FunctionAppName `
        --resource-group $ResourceGroup `
        --output none 2>$null
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Function App reiniciada" -ForegroundColor Green
        Write-Host "  Aguardando inicialização..." -ForegroundColor Gray
        Start-Sleep -Seconds 10
    } else {
        Write-Host "  [ERRO] Falha ao reiniciar Function App" -ForegroundColor Red
    }
} else {
    Write-Host "`n[5/5] Reiniciar Function App..." -ForegroundColor Yellow
    Write-Host "  Execute com -RestartFunctionApp para reiniciar" -ForegroundColor Gray
}

# Resumo final
Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "  RESUMO" -ForegroundColor Cyan
Write-Host "============================================`n" -ForegroundColor Cyan

if ($missingVars.Count -eq 0) {
    Write-Host "[OK] Todas as configurações estão corretas!" -ForegroundColor Green
    Write-Host "`nTeste os endpoints:" -ForegroundColor Cyan
    Write-Host "  curl https://$FunctionAppName.azurewebsites.net/api/health" -ForegroundColor Gray
    Write-Host "  curl -X POST https://$FunctionAppName.azurewebsites.net/api/avaliacao -H 'Content-Type: application/json' -d '{\"descricao\":\"Teste\",\"nota\":8,\"urgencia\":\"MEDIUM\"}'" -ForegroundColor Gray
} else {
    Write-Host "[AVISO] $($missingVars.Count) variável(is) faltando" -ForegroundColor Yellow
    Write-Host "`nPara corrigir automaticamente, execute:" -ForegroundColor Cyan
    Write-Host "  .\scripts\corrigir-configuracao-azure.ps1 -FixMissingVars -CreateContainer -RestartFunctionApp" -ForegroundColor White
    Write-Host "`nOu configure manualmente no Portal Azure:" -ForegroundColor Cyan
    Write-Host "  https://portal.azure.com > Function App > $FunctionAppName > Configuration" -ForegroundColor Gray
}

Write-Host ""
