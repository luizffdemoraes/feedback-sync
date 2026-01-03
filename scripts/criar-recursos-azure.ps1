# ============================================
# Script para Criar Recursos Azure
# ============================================
# Este script cria todos os recursos necessários no Azure:
# - Resource Group
# - Storage Account (Table + Blob)
# - Function App
# 
# NOTA: Service Bus foi removido para reduzir custos.
# Emails são enviados diretamente via Mailtrap.
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroupName = "feedback-rg",
    
    [Parameter(Mandatory=$false)]
    [string]$Location = "northcentralus",  # Região padrão que funciona com Azure for Students
    
    [Parameter(Mandatory=$false)]
    [string]$Suffix = "prod",  # Sufixo único para nomes (padrão: "prod")
    
    [Parameter(Mandatory=$false)]
    [string]$MailtrapApiToken,  # Token da API do Mailtrap (opcional - configure manualmente depois se não fornecido)
    
    [Parameter(Mandatory=$false)]
    [string]$MailtrapInboxId,  # ID da inbox do Mailtrap (opcional - configure manualmente depois se não fornecido)
    
    [Parameter(Mandatory=$false)]
    [string]$AdminEmail  # Email do administrador para receber notificações (opcional - configure manualmente depois se não fornecido)
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Criação de Recursos Azure - Feedback Sync" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Verificar se Azure CLI está instalado
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Host "[ERRO] Azure CLI não encontrado. Instale em: https://aka.ms/installazurecliwindows" -ForegroundColor Red
    exit 1
}

# Verificar se está logado
az account show 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Não está logado no Azure. Execute: az login" -ForegroundColor Red
    exit 1
}

# Obter subscription atual e definir explicitamente
$currentSubscription = az account show --query id -o tsv
if ($LASTEXITCODE -ne 0 -or -not $currentSubscription) {
    Write-Host "[ERRO] Não foi possível obter a subscription atual" -ForegroundColor Red
    exit 1
}

# Definir subscription explicitamente para evitar erros
az account set --subscription $currentSubscription 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Não foi possível definir a subscription" -ForegroundColor Red
    exit 1
}

# Verificar permissões da subscription
Write-Host "[OK] Azure CLI verificado" -ForegroundColor Green
$subscriptionName = az account show --query name -o tsv
$subscriptionState = az account show --query state -o tsv
Write-Host "   Subscription: $subscriptionName" -ForegroundColor Gray
Write-Host "   Subscription ID: $currentSubscription" -ForegroundColor Gray
Write-Host "   Estado: $subscriptionState" -ForegroundColor Gray

if ($subscriptionState -ne "Enabled") {
    Write-Host "[ERRO] A subscription não está habilitada. Estado: $subscriptionState" -ForegroundColor Red
    exit 1
}

# Verificar e registrar Resource Providers necessários
Write-Host "   Verificando Resource Providers..." -ForegroundColor Gray
$requiredProviders = @("Microsoft.Storage", "Microsoft.Web", "Microsoft.Insights")

foreach ($provider in $requiredProviders) {
    $providerStatus = az provider show --namespace $provider --query "registrationState" -o tsv 2>&1
    if ($LASTEXITCODE -eq 0) {
        if ($providerStatus -ne "Registered") {
            Write-Host "   Registrando provider: $provider..." -ForegroundColor Yellow
            az provider register --namespace $provider --wait --output none 2>&1 | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Write-Host "   [OK] Provider $provider registrado" -ForegroundColor Green
            } else {
                Write-Host "   [AVISO] Falha ao registrar provider $provider" -ForegroundColor Yellow
            }
        } else {
            Write-Host "   [OK] Provider $provider já registrado" -ForegroundColor Gray
        }
    } else {
        Write-Host "   [AVISO] Não foi possível verificar provider $provider" -ForegroundColor Yellow
    }
}

Write-Host ""

# Fazer refresh do token de acesso para garantir autenticação válida
Write-Host "   Atualizando token de acesso..." -ForegroundColor Gray
az account get-access-token --output none 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "   [OK] Token atualizado" -ForegroundColor Green
} else {
    Write-Host "   [AVISO] Não foi possível atualizar o token" -ForegroundColor Yellow
}
Write-Host ""

# Região padrão que funciona com Azure for Students
# Se precisar usar outra região, especifique via parâmetro -Location
Write-Host "   Região configurada: $Location" -ForegroundColor Cyan
Write-Host ""

# Normalizar sufixo (apenas letras minúsculas e números)
$Suffix = $Suffix.ToLower() -replace '[^a-z0-9]', ''

# 1. Criar Resource Group
Write-Host "Criando Resource Group: $ResourceGroupName" -ForegroundColor Yellow

# Verificar se Resource Group já existe
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$existingRgLocation = az group show --name $ResourceGroupName --query location -o tsv 2>&1
$ErrorActionPreference = $oldErrorAction

if ($LASTEXITCODE -eq 0 -and $existingRgLocation -and -not ($existingRgLocation -match "not found" -or $existingRgLocation -match "ERROR")) {
    $existingRgLocation = $existingRgLocation.Trim()
    if ($existingRgLocation -eq $Location) {
        Write-Host "   [OK] Resource Group já existe na região: $Location" -ForegroundColor Green
    } else {
        Write-Host "   [ERRO] Resource Group já existe em região diferente: $existingRgLocation" -ForegroundColor Red
        Write-Host "   Para evitar custos, delete o Resource Group existente ou use outro nome:" -ForegroundColor Yellow
        Write-Host "   az group delete --name $ResourceGroupName --yes" -ForegroundColor Gray
        Write-Host "   Ou use outro nome: .\criar-recursos-azure.ps1 -ResourceGroupName `"feedback-rg-novo`"" -ForegroundColor Gray
        exit 1
    }
} else {
    # Criar Resource Group
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $rgError = az group create --name $ResourceGroupName --location $Location --output none --only-show-errors 2>&1
    $ErrorActionPreference = $oldErrorAction
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERRO] Erro ao criar Resource Group" -ForegroundColor Red
        Write-Host "   Detalhes: $rgError" -ForegroundColor Yellow
        
        if ($rgError -match "RequestDisallowedByAzure" -or $rgError -match "disallowed by Azure") {
            Write-Host ""
            Write-Host "   ⚠️ Região '$Location' não disponível para sua subscription" -ForegroundColor Red
            Write-Host "   Execute com outra região: .\criar-recursos-azure.ps1 -Location `"northcentralus`"" -ForegroundColor Yellow
            Write-Host ""
        }
        exit 1
    }
    Write-Host "   [OK] Resource Group criado" -ForegroundColor Green
}

# 2. Criar Storage Account
$storageAccountName = "feedbackstorage$Suffix"
# Storage account name deve ter entre 3-24 caracteres, apenas letras minúsculas e números
if ($storageAccountName.Length -gt 24) {
    $storageAccountName = $storageAccountName.Substring(0, 24)
}

Write-Host "`nCriando Storage Account: $storageAccountName" -ForegroundColor Yellow

# Verificar se Storage Account já existe (nomes são únicos globalmente)
$storageAccountExists = $false
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$existingStorageLocationOutput = az storage account show --name $storageAccountName --query location -o tsv 2>&1
$ErrorActionPreference = $oldErrorAction

if ($LASTEXITCODE -eq 0 -and $existingStorageLocationOutput -and -not ($existingStorageLocationOutput -match "not found" -or $existingStorageLocationOutput -match "ERROR")) {
    # Storage Account existe, verificar região
    Write-Host "   [AVISO] Storage Account '$storageAccountName' já existe" -ForegroundColor Yellow
    Write-Host "   Verificando região..." -ForegroundColor Gray
    $existingStorageLocation = $existingStorageLocationOutput.Trim()
    if ($existingStorageLocation -eq $Location) {
        Write-Host "   [OK] Storage Account já existe na região correta: $Location" -ForegroundColor Green
        $storageAccountExists = $true
    } else {
        Write-Host "   [AVISO] Storage Account existe em região diferente: $existingStorageLocation" -ForegroundColor Yellow
        Write-Host "   Você precisará deletar manualmente ou usar um nome diferente (sufixo)" -ForegroundColor Yellow
        Write-Host "   Para deletar: az storage account delete --name $storageAccountName --yes" -ForegroundColor Gray
        Write-Host "   Ou use um sufixo diferente: .\criar-recursos-azure.ps1 -Suffix `"novo`"" -ForegroundColor Gray
        exit 1
    }
}

# Tentar criar o Storage Account apenas se não existir
if (-not $storageAccountExists) {
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $storageError = az storage account create `
        --name $storageAccountName `
        --resource-group $ResourceGroupName `
        --location $Location `
        --sku Standard_LRS `
        --kind StorageV2 `
        --allow-blob-public-access false `
        --output json `
        --only-show-errors 2>&1
    $ErrorActionPreference = $oldErrorAction
} else {
    $storageError = ""
    $LASTEXITCODE = 0
}

if ($LASTEXITCODE -ne 0 -and -not $storageAccountExists) {
    Write-Host "[ERRO] Erro ao criar Storage Account" -ForegroundColor Red
    Write-Host "   Detalhes do erro:" -ForegroundColor Yellow
    Write-Host $storageError -ForegroundColor Red
    
    # Verificar se é erro de região não disponível
    if ($storageError -match "RequestDisallowedByAzure" -or $storageError -match "disallowed by Azure") {
        Write-Host ""
        Write-Host "   ⚠️ ERRO: Região '$Location' não disponível para sua subscription" -ForegroundColor Red
        Write-Host ""
        Write-Host "   SOLUÇÕES:" -ForegroundColor Cyan
        Write-Host "   1. Execute o script com uma região que funciona (northcentralus é a padrão):" -ForegroundColor White
        Write-Host "      .\criar-recursos-azure.ps1 -Location `"northcentralus`" -Suffix `"$Suffix`"" -ForegroundColor Gray
        Write-Host ""
        Write-Host "   2. Ou tente outras regiões disponíveis:" -ForegroundColor White
        Write-Host "      .\criar-recursos-azure.ps1 -Location `"westus2`" -Suffix `"$Suffix`"" -ForegroundColor Gray
        Write-Host "      .\criar-recursos-azure.ps1 -Location `"centralus`" -Suffix `"$Suffix`"" -ForegroundColor Gray
        Write-Host ""
        Write-Host "   3. Listar todas as regiões disponíveis:" -ForegroundColor White
        Write-Host "      az account list-locations --query `"[?metadata.regionCategory=='Recommended'].{Name:name, DisplayName:displayName}`" -o table" -ForegroundColor Gray
        Write-Host ""
        Write-Host "   NOTA: O script usa 'northcentralus' como padrão (já testado e funcionando)" -ForegroundColor Yellow
        Write-Host ""
    }
    # Verificar se é erro de subscription
    elseif ($storageError -match "SubscriptionNotFound") {
        Write-Host ""
        Write-Host "   SOLUÇÕES POSSÍVEIS:" -ForegroundColor Cyan
        Write-Host "   1. Verifique se a subscription 'Azure for Students' está ativa no portal Azure" -ForegroundColor White
        Write-Host "   2. Tente fazer logout e login novamente:" -ForegroundColor White
        Write-Host "      az logout" -ForegroundColor Gray
        Write-Host "      az login" -ForegroundColor Gray
        Write-Host "   3. Verifique se há restrições regionais na subscription" -ForegroundColor White
        Write-Host "   4. Certifique-se de que os Resource Providers estão registrados" -ForegroundColor White
        Write-Host ""
    }
    
    exit 1
} elseif (-not $storageAccountExists) {
    Write-Host "   [OK] Storage Account criado" -ForegroundColor Green
}

# Obter connection string do Storage
Write-Host "   Obtendo connection string..." -ForegroundColor Gray
$storageConnectionString = az storage account show-connection-string `
    --name $storageAccountName `
    --resource-group $ResourceGroupName `
    --query connectionString -o tsv

# Criar container para relatórios
Write-Host "   Criando container 'weekly-reports'..." -ForegroundColor Gray
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
az storage container create `
    --name "weekly-reports" `
    --account-name $storageAccountName `
    --connection-string $storageConnectionString `
    --public-access off `
    --output none `
    --only-show-errors 2>&1 | Out-Null
$ErrorActionPreference = $oldErrorAction

Write-Host "   [OK] Container criado" -ForegroundColor Green

# 3. Criar Function App (Service Bus removido - usando Mailtrap para emails)
$functionAppName = "feedback-function-$Suffix"
# Function App name deve ter entre 2-60 caracteres
if ($functionAppName.Length -gt 60) {
    $functionAppName = $functionAppName.Substring(0, 60)
}

Write-Host "`nCriando Function App: $functionAppName" -ForegroundColor Yellow
az functionapp create `
    --resource-group $ResourceGroupName `
    --consumption-plan-location $Location `
    --runtime java `
    --runtime-version 21.0 `
    --functions-version 4 `
    --name $functionAppName `
    --storage-account $storageAccountName `
    --os-type Linux `
    --output none `
    --only-show-errors

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Erro ao criar Function App" -ForegroundColor Red
    exit 1
}
Write-Host "   [OK] Function App criada" -ForegroundColor Green

# Configurar Application Settings
Write-Host "`nConfigurando Application Settings..." -ForegroundColor Yellow

# Preparar lista de settings básicas
$appSettings = @(
    "AZURE_STORAGE_CONNECTION_STRING=$storageConnectionString",
    "AzureWebJobsStorage=$storageConnectionString",
    "FUNCTIONS_WORKER_RUNTIME=java",
    "FUNCTIONS_EXTENSION_VERSION=~4",
    "quarkus.log.level=INFO",
    "app.environment=production",
    "azure.storage.container-name=weekly-reports",
    "azure.table.table-name=feedbacks",
    "REPORT_SCHEDULE_CRON=0 */5 * * * *"
)

# Adicionar configurações do Mailtrap se fornecidas
$mailtrapConfigured = $false
if ($MailtrapApiToken -and $MailtrapInboxId -and $AdminEmail) {
    Write-Host "   Configurando Mailtrap automaticamente..." -ForegroundColor Cyan
    $appSettings += "MAILTRAP_API_TOKEN=$MailtrapApiToken"
    $appSettings += "MAILTRAP_INBOX_ID=$MailtrapInboxId"
    $appSettings += "ADMIN_EMAIL=$AdminEmail"
    $mailtrapConfigured = $true
    Write-Host "   [OK] Mailtrap configurado automaticamente" -ForegroundColor Green
} else {
    Write-Host "   [AVISO] Mailtrap não configurado (parâmetros não fornecidos)" -ForegroundColor Yellow
    Write-Host "      Configure manualmente após a criação dos recursos" -ForegroundColor Gray
}

# Configurar Application Settings
# Construir array de argumentos para o Azure CLI
$azArgs = @(
    "functionapp", "config", "appsettings", "set",
    "--name", $functionAppName,
    "--resource-group", $ResourceGroupName
)

# Adicionar cada setting como --settings "key=value"
foreach ($setting in $appSettings) {
    $azArgs += "--settings"
    $azArgs += $setting
}

$azArgs += "--output", "none", "--only-show-errors"

# Executar comando
& az $azArgs

Write-Host "   [OK] Application Settings configuradas" -ForegroundColor Green

# Instruções para configurar Mailtrap manualmente (se não foi configurado)
if (-not $mailtrapConfigured) {
    Write-Host ""
    Write-Host "   Para configurar Mailtrap manualmente:" -ForegroundColor Cyan
    Write-Host "      1. Crie conta gratuita em: https://mailtrap.io" -ForegroundColor White
    Write-Host "      2. Gere um API Token e obtenha o Inbox ID" -ForegroundColor White
    Write-Host "      3. Execute o comando abaixo ou use o script novamente com os parâmetros:" -ForegroundColor White
    Write-Host ""
    Write-Host "         az functionapp config appsettings set `" -ForegroundColor Gray
    Write-Host "             --name $functionAppName `" -ForegroundColor Gray
    Write-Host "             --resource-group $ResourceGroupName `" -ForegroundColor Gray
    Write-Host "             --settings `" -ForegroundColor Gray
    Write-Host "                 MAILTRAP_API_TOKEN='seu-token' `" -ForegroundColor Gray
    Write-Host "                 MAILTRAP_INBOX_ID='seu-inbox-id' `" -ForegroundColor Gray
    Write-Host "                 ADMIN_EMAIL='seu-email@exemplo.com'" -ForegroundColor Gray
    Write-Host ""
    Write-Host "      Ou execute o script novamente com os parâmetros:" -ForegroundColor White
    Write-Host "         .\scripts\criar-recursos-azure.ps1 `" -ForegroundColor Gray
    Write-Host "             -ResourceGroupName `"$ResourceGroupName`" `" -ForegroundColor Gray
    Write-Host "             -Location `"$Location`" `" -ForegroundColor Gray
    Write-Host "             -Suffix `"$Suffix`" `" -ForegroundColor Gray
    Write-Host "             -MailtrapApiToken 'seu-token' `" -ForegroundColor Gray
    Write-Host "             -MailtrapInboxId 'seu-inbox-id' `" -ForegroundColor Gray
    Write-Host "             -AdminEmail 'admin@exemplo.com'" -ForegroundColor Gray
}

# Resumo
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "[OK] RECURSOS CRIADOS COM SUCESSO!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Informações dos Recursos:" -ForegroundColor Cyan
Write-Host ""
Write-Host "Resource Group:" -ForegroundColor White
Write-Host "  Nome: $ResourceGroupName" -ForegroundColor Gray
Write-Host "  Região: $Location" -ForegroundColor Gray
Write-Host ""
Write-Host "Storage Account:" -ForegroundColor White
Write-Host "  Nome: $storageAccountName" -ForegroundColor Gray
Write-Host "  Container: weekly-reports" -ForegroundColor Gray
Write-Host ""
Write-Host "Notificações:" -ForegroundColor White
Write-Host "  Método: Mailtrap" -ForegroundColor Gray
Write-Host "  Service Bus: REMOVIDO para reduzir custos" -ForegroundColor Gray
Write-Host ""
Write-Host "Function App:" -ForegroundColor White
Write-Host "  Nome: $functionAppName" -ForegroundColor Gray
Write-Host "  URL: https://$functionAppName.azurewebsites.net" -ForegroundColor Gray
Write-Host ""
Write-Host "Próximos Passos:" -ForegroundColor Yellow
if (-not $mailtrapConfigured) {
    Write-Host "  0. [AVISO] Configure Mailtrap (veja instruções acima)" -ForegroundColor Yellow
}
Write-Host "  1. Fazer deploy da aplicação:" -ForegroundColor White
Write-Host "     .\scripts\implantar-azure.ps1 -FunctionAppName `"$functionAppName`" -ResourceGroup `"$ResourceGroupName`"" -ForegroundColor Gray
Write-Host ""
Write-Host "  2. Testar o endpoint:" -ForegroundColor White
Write-Host "     https://$functionAppName.azurewebsites.net/api/avaliacao" -ForegroundColor Gray
Write-Host ""
Write-Host "  3. Ver logs:" -ForegroundColor White
Write-Host "     az functionapp log tail --name $functionAppName --resource-group $ResourceGroupName" -ForegroundColor Gray
Write-Host ""
Write-Host "Consulte GUIA_DEPLOY_AZURE.md para mais detalhes" -ForegroundColor Cyan
Write-Host ""

