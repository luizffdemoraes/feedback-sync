# ============================================
# Script de Deploy para Azure Functions
# ============================================
# Este script faz deploy da aplicação para a Function App criada
# pelo criar-recursos-azure.ps1
# 
# Requisitos:
# - Azure CLI instalado e logado (az login)
# - Function App já criada (use criar-recursos-azure.ps1 primeiro)
# - Maven instalado
# 
# Uso:
#   .\scripts\implantar-azure.ps1
#   .\scripts\implantar-azure.ps1 -SkipBuild
#   .\scripts\implantar-azure.ps1 -FunctionAppName "feedback-function-prod" -ResourceGroup "feedback-rg"
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$FunctionAppName,  # Nome da Function App (padrão: descobre automaticamente)
    
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroup,  # Nome do Resource Group (padrão: "feedback-rg")
    
    [Parameter(Mandatory=$false)]
    [switch]$SkipBuild  # Se presente, pula a compilação (usa build existente)
)

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Deploy para Azure Functions - Feedback Sync" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Mudar para o diretório raiz do projeto (onde está o pom.xml)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
Push-Location $projectRoot
Write-Host "Diretorio do projeto: $projectRoot" -ForegroundColor Gray
Write-Host ""

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
    
    # Tentar o padrão primeiro
    $defaultRg = "feedback-rg"
    $rgExists = az group exists --name $defaultRg 2>&1
    if ($rgExists -eq "true") {
        $ResourceGroup = $defaultRg
        Write-Host "   [OK] Usando Resource Group padrao: $ResourceGroup" -ForegroundColor Green
    } else {
        # Procurar por Resource Groups que começam com "feedback"
        Write-Host "   Procurando Resource Groups com padrao 'feedback'..." -ForegroundColor Gray
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
                Write-Host "   Execute primeiro: .\scripts\criar-recursos-azure.ps1" -ForegroundColor Yellow
                Exit-Script 1
            }
        } else {
            Write-Host "[ERRO] Nao foi possivel descobrir Resource Group automaticamente." -ForegroundColor Red
            Write-Host "   Execute primeiro: .\scripts\criar-recursos-azure.ps1" -ForegroundColor Yellow
            Write-Host "   Ou informe manualmente: .\scripts\implantar-azure.ps1 -ResourceGroup `"feedback-rg`"" -ForegroundColor Gray
            Exit-Script 1
        }
    }
}

# Verificar se Resource Group existe
Write-Host "Verificando recursos..." -ForegroundColor Yellow
$rgExists = az group exists --name $ResourceGroup 2>&1
if ($rgExists -eq "false") {
    Write-Host "[ERRO] Resource Group '$ResourceGroup' nao encontrado." -ForegroundColor Red
    Write-Host "   Execute primeiro: .\scripts\criar-recursos-azure.ps1" -ForegroundColor Yellow
    Exit-Script 1
}
Write-Host "   [OK] Resource Group encontrado: $ResourceGroup" -ForegroundColor Green

# Descobrir Function App automaticamente se não fornecido
if ([string]::IsNullOrWhiteSpace($FunctionAppName)) {
    Write-Host "Descobrindo Function App automaticamente..." -ForegroundColor Yellow
    
    # Tentar o padrão primeiro
    $defaultFunctionApp = "feedback-function-prod"
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $functionExists = az functionapp show --name $defaultFunctionApp --resource-group $ResourceGroup --query "name" -o tsv 2>&1
    $ErrorActionPreference = $oldErrorAction
    
    if ($LASTEXITCODE -eq 0 -and $functionExists) {
        $FunctionAppName = $defaultFunctionApp
        Write-Host "   [OK] Usando Function App padrao: $FunctionAppName" -ForegroundColor Green
    } else {
        # Procurar por Function Apps no Resource Group que seguem o padrão "feedback-function-*"
        Write-Host "   Procurando Function Apps no Resource Group '$ResourceGroup'..." -ForegroundColor Gray
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
                # Se não encontrou com o padrão, tentar qualquer Function App no Resource Group
                Write-Host "   Nenhuma Function App com padrao 'feedback-function-*' encontrada, procurando qualquer Function App..." -ForegroundColor Gray
                $oldErrorAction = $ErrorActionPreference
                $ErrorActionPreference = "Continue"
                $allFunctions = az functionapp list --resource-group $ResourceGroup --query "[].name" -o tsv 2>&1
                $ErrorActionPreference = $oldErrorAction
                
                if ($LASTEXITCODE -eq 0 -and $allFunctions -and -not ($allFunctions -match "ERROR")) {
                    $allFunctionsArray = $allFunctions -split "`n" | Where-Object { $_.Trim() -ne "" }
                    if ($allFunctionsArray.Count -gt 0) {
                        $FunctionAppName = $allFunctionsArray[0].Trim()
                        Write-Host "   [OK] Encontrada Function App: $FunctionAppName" -ForegroundColor Green
                    } else {
                        Write-Host "[ERRO] Nenhuma Function App encontrada no Resource Group '$ResourceGroup'." -ForegroundColor Red
                        Write-Host "   Execute primeiro: .\scripts\criar-recursos-azure.ps1" -ForegroundColor Yellow
                        Exit-Script 1
                    }
                } else {
                    Write-Host "[ERRO] Nenhuma Function App encontrada no Resource Group '$ResourceGroup'." -ForegroundColor Red
                    Write-Host "   Execute primeiro: .\scripts\criar-recursos-azure.ps1" -ForegroundColor Yellow
                    Exit-Script 1
                }
            }
        } else {
            Write-Host "[ERRO] Nao foi possivel descobrir Function App automaticamente." -ForegroundColor Red
            Write-Host "   Execute primeiro: .\scripts\criar-recursos-azure.ps1" -ForegroundColor Yellow
            Write-Host "   Ou informe manualmente: .\scripts\implantar-azure.ps1 -FunctionAppName `"feedback-function-prod`"" -ForegroundColor Gray
            Exit-Script 1
        }
    }
}

# Verificar se Function App existe
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$functionExists = az functionapp show --name $FunctionAppName --resource-group $ResourceGroup --query "name" -o tsv 2>&1
$ErrorActionPreference = $oldErrorAction

if ($LASTEXITCODE -ne 0 -or -not $functionExists) {
    Write-Host "[ERRO] Function App '$FunctionAppName' nao encontrada no Resource Group '$ResourceGroup'." -ForegroundColor Red
    Write-Host "   Execute primeiro: .\scripts\criar-recursos-azure.ps1" -ForegroundColor Yellow
    Exit-Script 1
}
Write-Host "   [OK] Function App encontrada: $FunctionAppName" -ForegroundColor Green

# Descobrir informações da Function App (região, App Service Plan e Storage Account)
Write-Host "Obtendo informacoes da Function App..." -ForegroundColor Gray
$oldErrorAction = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$functionAppInfo = az functionapp show --name $FunctionAppName --resource-group $ResourceGroup --query "{location:location, appServicePlanId:appServicePlanId}" -o json 2>&1
$ErrorActionPreference = $oldErrorAction

$functionAppLocation = "northcentralus"  # Valor padrão
$appServicePlanName = $null
$storageAccountName = $null
$isConsumptionPlan = $false  # Flag para identificar Consumption Plans

if ($LASTEXITCODE -eq 0 -and $functionAppInfo) {
    try {
        $functionAppJson = $functionAppInfo | ConvertFrom-Json
        $functionAppLocation = $functionAppJson.location
        $appServicePlanId = $functionAppJson.appServicePlanId
        
        # Extrair nome do App Service Plan do ID
        if ($appServicePlanId) {
            $appServicePlanName = $appServicePlanId.Split('/')[-1]
            # Verificar se é Consumption Plan (nomes contêm "Dynamic", "Consumption" ou "Y1")
            # Consumption Plans NÃO devem ter appServicePlanName passado no deploy
            if ($appServicePlanName -match "Dynamic|Consumption|Y1") {
                $isConsumptionPlan = $true
                Write-Host "   [INFO] Consumption Plan detectado - appServicePlanName nao sera usado no deploy" -ForegroundColor Gray
            }
        }
        
        # Obter Storage Account do Resource Group (procurar por storage account que começa com "feedbackstorage")
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $storageAccounts = az storage account list --resource-group $ResourceGroup --query "[?starts_with(name, 'feedbackstorage')].name" -o tsv 2>&1
        $ErrorActionPreference = $oldErrorAction
        if ($LASTEXITCODE -eq 0 -and $storageAccounts) {
            $storageAccountsArray = ($storageAccounts -split "`n" | Where-Object { $_ -and $_.ToString().Trim() -ne "" }) | ForEach-Object { $_.ToString().Trim() }
            if ($storageAccountsArray.Count -gt 0) {
                $storageAccountName = $storageAccountsArray[0]
            }
        }
        
        Write-Host "   Regiao: $functionAppLocation" -ForegroundColor Gray
        if ($appServicePlanName) {
            Write-Host "   App Service Plan: $appServicePlanName" -ForegroundColor Gray
        }
        if ($storageAccountName) {
            Write-Host "   Storage Account: $storageAccountName" -ForegroundColor Gray
        }
    } catch {
        Write-Host "   [AVISO] Erro ao processar informacoes da Function App: $_" -ForegroundColor Yellow
    }
} else {
    Write-Host "   [AVISO] Nao foi possivel obter informacoes completas da Function App" -ForegroundColor Yellow
}

# Verificar se Maven está instalado
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "[ERRO] Maven nao encontrado. Instale o Maven para continuar." -ForegroundColor Red
    Write-Host "   Download: https://maven.apache.org/download.cgi" -ForegroundColor Yellow
    Exit-Script 1
}
Write-Host "   [OK] Maven encontrado" -ForegroundColor Green
Write-Host ""

# Compilar projeto (se não pular)
if (-not $SkipBuild) {
    Write-Host "Compilando projeto..." -ForegroundColor Yellow
    mvn clean package -DskipTests

    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERRO] Erro ao compilar projeto" -ForegroundColor Red
        Write-Host "   Tente executar manualmente: mvn clean package -DskipTests" -ForegroundColor Yellow
        Exit-Script 1
    }

    Write-Host "[OK] Projeto compilado com sucesso" -ForegroundColor Green
}
else {
    Write-Host "Pulando compilacao (usando build existente)" -ForegroundColor Yellow
}
Write-Host ""

# Fazer deploy
Write-Host "Fazendo deploy para Azure Functions..." -ForegroundColor Yellow
Write-Host ""
Write-Host "Function App: $FunctionAppName" -ForegroundColor Cyan
Write-Host "Resource Group: $ResourceGroup" -ForegroundColor Cyan
if ($functionAppLocation) {
    # Normalizar região para exibição
    $normalizedRegionDisplay = $functionAppLocation -replace '\s+', '' | ForEach-Object { $_.ToLower() }
    Write-Host "Regiao (normalizada): $normalizedRegionDisplay" -ForegroundColor Cyan
}
Write-Host ""

# Deploy via Azure CLI (mais confiável que plugin Maven para Consumption Plans)
# O plugin Maven 1.30.0 tem bugs conhecidos com Consumption Plans
$deployPath = "target\azure-functions\$FunctionAppName"
$zipPath = "$deployPath.zip"

# Verificar se o diretório de deploy existe
if (-not (Test-Path $deployPath)) {
    Write-Host "[ERRO] Diretorio de deploy nao encontrado: $deployPath" -ForegroundColor Red
    Write-Host "   Certifique-se de que o projeto foi compilado com sucesso" -ForegroundColor Yellow
    Exit-Script 1
}

Write-Host "Criando pacote ZIP para deploy..." -ForegroundColor Yellow

# Aguardar um pouco para garantir que processos Java/Maven terminem e liberem arquivos
Write-Host "   Aguardando liberacao de arquivos..." -ForegroundColor Gray
Start-Sleep -Seconds 2

# Remover ZIP anterior se existir
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
}

# Criar ZIP do pacote de deploy com retry em caso de arquivo em uso
$maxRetries = 3
$retryDelay = 2
$zipCreated = $false

for ($attempt = 1; $attempt -le $maxRetries; $attempt++) {
    try {
        if ($attempt -gt 1) {
            Write-Host "   Tentativa $attempt de $maxRetries..." -ForegroundColor Yellow
            Start-Sleep -Seconds $retryDelay
        }
        
        # Usar .NET ZipFile para melhor controle e evitar problemas de arquivo em uso
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::CreateFromDirectory($deployPath, $zipPath, [System.IO.Compression.CompressionLevel]::Optimal, $false)
        
        Write-Host "   [OK] Pacote ZIP criado: $zipPath" -ForegroundColor Green
        $zipCreated = $true
        break
    } catch {
        if ($attempt -eq $maxRetries) {
            Write-Host "[ERRO] Falha ao criar pacote ZIP apos $maxRetries tentativas" -ForegroundColor Red
            Write-Host "   Erro: $_" -ForegroundColor Red
            Write-Host ""
            Write-Host "   Possiveis solucoes:" -ForegroundColor Yellow
            Write-Host "   1. Feche qualquer processo Java/Maven que possa estar usando os arquivos" -ForegroundColor White
            Write-Host "   2. Aguarde alguns segundos e tente novamente" -ForegroundColor White
            Write-Host "   3. Tente criar o ZIP manualmente:" -ForegroundColor White
            Write-Host "      Compress-Archive -Path `"$deployPath\*`" -DestinationPath `"$zipPath`" -Force" -ForegroundColor Gray
            Exit-Script 1
        }
        Write-Host "   [AVISO] Tentativa $attempt falhou, aguardando..." -ForegroundColor Yellow
    }
}

if (-not $zipCreated) {
    Write-Host "[ERRO] Nao foi possivel criar o pacote ZIP" -ForegroundColor Red
    Exit-Script 1
}

Write-Host ""
Write-Host "Fazendo deploy via Azure CLI..." -ForegroundColor Yellow
Write-Host "   Function App: $FunctionAppName" -ForegroundColor Gray
Write-Host "   Resource Group: $ResourceGroup" -ForegroundColor Gray
Write-Host "   Pacote: $zipPath" -ForegroundColor Gray
Write-Host ""

# Fazer deploy usando Azure CLI
az functionapp deployment source config-zip `
    --resource-group $ResourceGroup `
    --name $FunctionAppName `
    --src $zipPath

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "[ERRO] Erro ao fazer deploy via Azure CLI" -ForegroundColor Red
    Write-Host ""
    Write-Host "Possiveis solucoes:" -ForegroundColor Yellow
    Write-Host "   1. Verifique se a Function App existe:" -ForegroundColor White
    Write-Host "      az functionapp show --name $FunctionAppName --resource-group $ResourceGroup" -ForegroundColor Gray
    Write-Host ""
    Write-Host "   2. Verifique se esta logado:" -ForegroundColor White
    Write-Host "      az account show" -ForegroundColor Gray
    Write-Host ""
    Write-Host "   3. Verifique se o pacote ZIP foi criado corretamente:" -ForegroundColor White
    Write-Host "      Test-Path $zipPath" -ForegroundColor Gray
    Write-Host ""
    Write-Host "   4. Tente fazer deploy manualmente:" -ForegroundColor White
    Write-Host "      az functionapp deployment source config-zip --resource-group $ResourceGroup --name $FunctionAppName --src $zipPath" -ForegroundColor Gray
    Write-Host ""
    
    # Limpar ZIP em caso de erro
    if (Test-Path $zipPath) {
        Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
    }
    
    Exit-Script 1
}

# Limpar ZIP após deploy bem-sucedido
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
    Write-Host "   [OK] Arquivo ZIP temporario removido" -ForegroundColor Gray
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "[OK] DEPLOY CONCLUIDO COM SUCESSO!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Informacoes da aplicacao:" -ForegroundColor Cyan
Write-Host ""
Write-Host "Function App:" -ForegroundColor White
Write-Host "  Nome: $FunctionAppName" -ForegroundColor Gray
Write-Host "  URL: https://$FunctionAppName.azurewebsites.net" -ForegroundColor Gray
Write-Host ""
Write-Host "Endpoints disponiveis:" -ForegroundColor White
Write-Host "  POST https://$FunctionAppName.azurewebsites.net/api/avaliacao" -ForegroundColor Gray
Write-Host "  GET  https://$FunctionAppName.azurewebsites.net/api/relatorio-semanal" -ForegroundColor Gray
Write-Host ""
Write-Host "Proximos passos:" -ForegroundColor Yellow
Write-Host "1. Verificar Application Settings no Azure Portal" -ForegroundColor White
Write-Host "2. Testar o endpoint de avaliacao:" -ForegroundColor White
Write-Host "   https://$FunctionAppName.azurewebsites.net/api/avaliacao" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Ver logs em tempo real:" -ForegroundColor White
Write-Host "   az functionapp log tail --name $FunctionAppName --resource-group $ResourceGroup" -ForegroundColor Gray
Write-Host ""
Write-Host "4. Ver logs no Azure Portal:" -ForegroundColor White
$subscriptionId = az account show --query id -o tsv
Write-Host "   https://portal.azure.com/#@/resource/subscriptions/$subscriptionId/resourceGroups/$ResourceGroup/providers/Microsoft.Web/sites/$FunctionAppName/logStream" -ForegroundColor Gray
Write-Host ""
Write-Host "Consulte GUIA_DEPLOY_AZURE.md para mais detalhes" -ForegroundColor Cyan
Write-Host ""

# Voltar para o diretório original e sair com sucesso
Exit-Script 0
