# ============================================
# Script para Diagnosticar Endpoints
# ============================================
# Este script testa os endpoints da Function App
# e verifica se estão funcionando corretamente
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$FunctionAppName = "feedback-function-prod",
    
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroup = "feedback-rg"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  DIAGNOSTICO DE ENDPOINTS" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "https://$FunctionAppName.azurewebsites.net"

Write-Host "Function App: $FunctionAppName" -ForegroundColor Yellow
Write-Host "Base URL: $baseUrl" -ForegroundColor Yellow
Write-Host ""

# Verificar se Function App existe
Write-Host "Verificando Function App..." -ForegroundColor Gray
$ErrorActionPreference = "Continue"
$faExists = az functionapp show --name $FunctionAppName --resource-group $ResourceGroup --query "name" -o tsv 2>&1
$ErrorActionPreference = "Stop"

if ($LASTEXITCODE -ne 0 -or -not $faExists) {
    Write-Host "[ERRO] Function App nao encontrada: $FunctionAppName" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Function App encontrada" -ForegroundColor Green
Write-Host ""

# Verificar estado
Write-Host "Verificando estado da Function App..." -ForegroundColor Gray
$faState = az functionapp show --name $FunctionAppName --resource-group $ResourceGroup --query "state" -o tsv
Write-Host "Estado: $faState" -ForegroundColor $(if ($faState -eq "Running") { "Green" } else { "Yellow" })
Write-Host ""

# Testar endpoints
Write-Host "============================================================" -ForegroundColor Yellow
Write-Host "Testando Endpoints" -ForegroundColor Yellow
Write-Host "============================================================" -ForegroundColor Yellow
Write-Host ""

# 1. Testar /api/health
Write-Host "1. Testando GET $baseUrl/api/health" -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/health" -Method GET -TimeoutSec 30 -ErrorAction Stop
    Write-Host "   Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response: $($response.Content)" -ForegroundColor Gray
} catch {
    Write-Host "   [ERRO] $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        Write-Host "   Status Code: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
    }
}
Write-Host ""

# 2. Testar /api/avaliacao (POST)
Write-Host "2. Testando POST $baseUrl/api/avaliacao" -ForegroundColor Cyan
try {
    $body = @{
        descricao = "Teste de diagnostico"
        nota = 5
        urgencia = "LOW"
    } | ConvertTo-Json
    
    $response = Invoke-WebRequest -Uri "$baseUrl/api/avaliacao" -Method POST -Body $body -ContentType "application/json" -TimeoutSec 30 -ErrorAction Stop
    Write-Host "   Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response: $($response.Content)" -ForegroundColor Gray
} catch {
    Write-Host "   [ERRO] $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        Write-Host "   Status Code: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $responseBody = $reader.ReadToEnd()
            Write-Host "   Response Body: $responseBody" -ForegroundColor Red
        } catch {
            # Ignorar erro ao ler response
        }
    }
}
Write-Host ""

# 3. Verificar logs recentes
Write-Host "============================================================" -ForegroundColor Yellow
Write-Host "Ultimos Logs (ultimas 50 linhas)" -ForegroundColor Yellow
Write-Host "============================================================" -ForegroundColor Yellow
Write-Host ""

Write-Host "Para ver logs completos, execute:" -ForegroundColor Gray
Write-Host "   az webapp log download --name $FunctionAppName --resource-group $ResourceGroup --log-file logs.zip" -ForegroundColor Gray
Write-Host ""

# Tentar obter logs via API
Write-Host "Verificando Application Insights..." -ForegroundColor Gray
$subscriptionId = az account show --query id -o tsv
Write-Host "   Portal: https://portal.azure.com/#@/resource/subscriptions/$subscriptionId/resourceGroups/$ResourceGroup/providers/Microsoft.Web/sites/$FunctionAppName/logStream" -ForegroundColor Gray
Write-Host ""

Write-Host "============================================================" -ForegroundColor Green
Write-Host "Diagnostico Concluido" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
