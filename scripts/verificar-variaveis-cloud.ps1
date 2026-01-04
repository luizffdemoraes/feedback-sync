# ============================================
# Script para Verificar Variáveis de Ambiente na Cloud (Azure)
# ============================================
# Verifica se todas as variáveis necessárias estão configuradas
# na Function App do Azure para o fluxo completo funcionar:
# - Recebimento de feedback
# - Postagem na fila (para feedbacks críticos)
# - Envio de email via Mailtrap
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$FunctionAppName = "feedback-function-prod",
    
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroup = "feedback-rg"
)

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Verificando Variáveis de Ambiente na Cloud (Azure)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Function App: $FunctionAppName" -ForegroundColor Gray
Write-Host "Resource Group: $ResourceGroup" -ForegroundColor Gray
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

$subscriptionName = az account show --query name -o tsv
Write-Host "[OK] Azure CLI verificado - Subscription: $subscriptionName" -ForegroundColor Green
Write-Host ""

# Obter todas as configurações da Function App
Write-Host "Obtendo configuracoes da Function App..." -ForegroundColor Yellow
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$appSettings = az functionapp config appsettings list `
    --name $FunctionAppName `
    --resource-group $ResourceGroup `
    --query "[].{Name:name, Value:value}" -o json 2>&1
$ErrorActionPreference = $oldErrorAction

if ($LASTEXITCODE -ne 0 -or -not $appSettings -or ($appSettings -match "ERROR")) {
    Write-Host "[ERRO] Nao foi possivel obter configuracoes da Function App" -ForegroundColor Red
    Write-Host "   Verifique se a Function App existe e se voce esta logado:" -ForegroundColor Yellow
    Write-Host "   az login" -ForegroundColor Gray
    exit 1
}

$settings = $appSettings | ConvertFrom-Json

# Criar hashtable para busca rápida
$settingsHash = @{}
foreach ($setting in $settings) {
    $settingsHash[$setting.Name] = $setting.Value
}

Write-Host "[OK] Configuracoes obtidas" -ForegroundColor Green
Write-Host ""

# Variáveis para verificar (organizadas por categoria)
$requiredVars = @(
    # Storage (OBRIGATÓRIAS)
    @{Name="AZURE_STORAGE_CONNECTION_STRING"; Required=$true; Category="Storage"; Description="Connection string do Storage Account (Table Storage, Queue Storage)"},
    @{Name="AzureWebJobsStorage"; Required=$true; Category="Storage"; Description="Connection string do Storage Account (Azure Functions)"},
    
    # Azure Functions Runtime (OBRIGATÓRIAS)
    @{Name="FUNCTIONS_WORKER_RUNTIME"; Required=$true; Category="Runtime"; Description="Runtime do Azure Functions (deve ser 'java')"},
    @{Name="FUNCTIONS_EXTENSION_VERSION"; Required=$true; Category="Runtime"; Description="Versao do Azure Functions (deve ser '~4')"},
    
    # Mailtrap (OPCIONAIS mas necessárias para envio de email)
    @{Name="MAILTRAP_API_TOKEN"; Required=$false; Category="Mailtrap"; Description="Token da API do Mailtrap (necessario para envio de email)"},
    @{Name="ADMIN_EMAIL"; Required=$false; Category="Mailtrap"; Description="Email do administrador (necessario para receber notificacoes)"},
    @{Name="MAILTRAP_INBOX_ID"; Required=$false; Category="Mailtrap"; Description="ID da inbox do Mailtrap (necessario para envio de email)"},
    
    # Configurações da aplicação (OPCIONAIS)
    @{Name="azure.table.table-name"; Required=$false; Category="App Config"; Description="Nome da tabela (padrao: 'feedbacks')"},
    @{Name="azure.storage.container-name"; Required=$false; Category="App Config"; Description="Nome do container (padrao: 'weekly-reports')"},
    @{Name="REPORT_SCHEDULE_CRON"; Required=$false; Category="App Config"; Description="Agendamento do relatorio semanal"}
)

# Agrupar por categoria
$categories = @{}
foreach ($var in $requiredVars) {
    $category = $var.Category
    if (-not $categories.ContainsKey($category)) {
        $categories[$category] = @()
    }
    $categories[$category] += $var
}

$allOk = $true
$missingRequired = @()
$missingOptional = @()

# Verificar cada categoria
foreach ($category in $categories.Keys | Sort-Object) {
    Write-Host "=== $category ===" -ForegroundColor Yellow
    Write-Host ""
    
    foreach ($var in $categories[$category]) {
        $varName = $var.Name
        $isRequired = $var.Required
        $description = $var.Description
        
        if ($settingsHash.ContainsKey($varName)) {
            $value = $settingsHash[$varName]
            if ([string]::IsNullOrWhiteSpace($value)) {
                if ($isRequired) {
                    Write-Host "   [$varName]: [X] CONFIGURADA MAS VAZIA (OBRIGATORIA)" -ForegroundColor Red
                    $allOk = $false
                    $missingRequired += $varName
                } else {
                    Write-Host "   [$varName]: [!] Configurada mas vazia (opcional)" -ForegroundColor Yellow
                    if ($category -eq "Mailtrap") {
                        $missingOptional += $varName
                    }
                }
            } else {
                # Mostrar preview do valor (ocultar dados sensíveis)
                $preview = $value
                if ($varName -match "CONNECTION_STRING|TOKEN|KEY") {
                    if ($value.Length -gt 50) {
                        $preview = $value.Substring(0, 20) + "..." + $value.Substring($value.Length - 10)
                    } else {
                        $preview = "***" + $value.Substring([Math]::Max(0, $value.Length - 5))
                    }
                } elseif ($varName -eq "MAILTRAP_API_TOKEN" -and $value.Length -gt 8) {
                    $preview = $value.Substring(0, 8) + "..."
                } elseif ($varName -eq "ADMIN_EMAIL") {
                    $preview = $value
                } else {
                    if ($value.Length -gt 50) {
                        $preview = $value.Substring(0, 50) + "..."
                    }
                }
                
                $status = "[OK]"
                $color = "Green"
                Write-Host "   [$varName]: $status $preview" -ForegroundColor $color
                Write-Host "      Descricao: $description" -ForegroundColor Gray
                Write-Host "      Tamanho: $($value.Length) caracteres" -ForegroundColor Gray
            }
        } else {
            if ($isRequired) {
                Write-Host "   [$varName]: [X] NAO CONFIGURADA (OBRIGATORIA)" -ForegroundColor Red
                $allOk = $false
                $missingRequired += $varName
            } else {
                Write-Host "   [$varName]: [-] Nao configurada (opcional)" -ForegroundColor Gray
                if ($category -eq "Mailtrap") {
                    $missingOptional += $varName
                }
            }
        }
        Write-Host ""
    }
}

Write-Host "============================================================" -ForegroundColor Gray
Write-Host ""

# Resumo e diagnóstico
Write-Host "=== RESUMO ===" -ForegroundColor Cyan
Write-Host ""

if ($allOk) {
    Write-Host "[OK] Todas as variaveis obrigatorias estao configuradas!" -ForegroundColor Green
} else {
    Write-Host "[ERRO] Variaveis obrigatorias faltando:" -ForegroundColor Red
    foreach ($var in $missingRequired) {
        Write-Host "   - $var" -ForegroundColor Yellow
    }
}

# Verificar se Mailtrap está configurado (necessário para envio de email)
$mailtrapConfigured = $true
$mailtrapVars = @("MAILTRAP_API_TOKEN", "ADMIN_EMAIL", "MAILTRAP_INBOX_ID")
foreach ($var in $mailtrapVars) {
    if (-not $settingsHash.ContainsKey($var) -or [string]::IsNullOrWhiteSpace($settingsHash[$var])) {
        $mailtrapConfigured = $false
        break
    }
}

Write-Host ""
if ($mailtrapConfigured) {
    Write-Host "[OK] Mailtrap configurado - Emails serao enviados corretamente" -ForegroundColor Green
} else {
    Write-Host "[AVISO] Mailtrap NAO configurado completamente:" -ForegroundColor Yellow
    foreach ($var in $mailtrapVars) {
        if (-not $settingsHash.ContainsKey($var) -or [string]::IsNullOrWhiteSpace($settingsHash[$var])) {
            Write-Host "   - $var esta faltando ou vazia" -ForegroundColor Yellow
        }
    }
    Write-Host ""
    Write-Host "   CONSEQUENCIA: Feedbacks criticos serao salvos e publicados na fila," -ForegroundColor Yellow
    Write-Host "   mas o email NAO sera enviado via Mailtrap." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== DIAGNOSTICO DO FLUXO ===" -ForegroundColor Cyan
Write-Host ""

# Verificar fluxo completo
$storageOk = $settingsHash.ContainsKey("AZURE_STORAGE_CONNECTION_STRING") -and `
             -not [string]::IsNullOrWhiteSpace($settingsHash["AZURE_STORAGE_CONNECTION_STRING"])

$queueOk = $storageOk  # Queue usa a mesma connection string

$emailOk = $mailtrapConfigured

Write-Host "Fluxo de Feedback:" -ForegroundColor White
Write-Host "   1. Recebimento de feedback (POST /api/avaliacao): " -NoNewline
if ($allOk) {
    Write-Host "[OK]" -ForegroundColor Green
} else {
    Write-Host "[ERRO] - Configure variaveis obrigatorias" -ForegroundColor Red
}

Write-Host "   2. Salvamento no Table Storage: " -NoNewline
if ($storageOk) {
    Write-Host "[OK]" -ForegroundColor Green
} else {
    Write-Host "[ERRO] - AZURE_STORAGE_CONNECTION_STRING nao configurada" -ForegroundColor Red
}

Write-Host "   3. Publicacao na fila (feedbacks criticos): " -NoNewline
if ($queueOk) {
    Write-Host "[OK]" -ForegroundColor Green
} else {
    Write-Host "[ERRO] - AZURE_STORAGE_CONNECTION_STRING nao configurada" -ForegroundColor Red
}

Write-Host "   4. Envio de email via Mailtrap: " -NoNewline
if ($emailOk) {
    Write-Host "[OK]" -ForegroundColor Green
} else {
    Write-Host "[AVISO] - Mailtrap nao configurado completamente" -ForegroundColor Yellow
}

Write-Host ""

# Instruções para configurar
if (-not $allOk -or -not $mailtrapConfigured) {
    Write-Host "=== INSTRUCOES PARA CONFIGURAR ===" -ForegroundColor Cyan
    Write-Host ""
    
    if (-not $allOk) {
        Write-Host "1. Configurar variaveis obrigatorias faltantes:" -ForegroundColor Yellow
        Write-Host ""
        
        if ($missingRequired -contains "AZURE_STORAGE_CONNECTION_STRING") {
            Write-Host "   Obter connection string do Storage Account:" -ForegroundColor White
            Write-Host "   `$storageAccountName = `"feedbackstorage<seu-sufixo>`"" -ForegroundColor Gray
            Write-Host "   `$storageConnectionString = az storage account show-connection-string --name `$storageAccountName --resource-group $ResourceGroup --query connectionString -o tsv" -ForegroundColor Gray
            Write-Host ""
            Write-Host "   Configurar na Function App:" -ForegroundColor White
            Write-Host "   az functionapp config appsettings set --name $FunctionAppName --resource-group $ResourceGroup --settings `"AZURE_STORAGE_CONNECTION_STRING=`$storageConnectionString`" `"AzureWebJobsStorage=`$storageConnectionString`"" -ForegroundColor Gray
            Write-Host ""
        }
    }
    
    if (-not $mailtrapConfigured) {
        Write-Host "2. Configurar Mailtrap (para envio de email):" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "   az functionapp config appsettings set --name $FunctionAppName --resource-group $ResourceGroup --settings `"MAILTRAP_API_TOKEN=seu-token`" `"ADMIN_EMAIL=seu-email@exemplo.com`" `"MAILTRAP_INBOX_ID=seu-inbox-id`"" -ForegroundColor Gray
        Write-Host ""
        Write-Host "   Obtenha as credenciais em: https://mailtrap.io" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Gray
Write-Host ""
