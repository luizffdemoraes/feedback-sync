# Script de diagnóstico rápido
$functionAppName = "feedback-function-prod"
$resourceGroup = "feedback-rg"

Write-Host "`n=== DIAGNÓSTICO RÁPIDO ===" -ForegroundColor Cyan

# Verificar se Function App está rodando
Write-Host "`n1. Status da Function App:" -ForegroundColor Yellow
$state = az functionapp show --name $functionAppName --resource-group $resourceGroup --query "state" -o tsv
Write-Host "   Estado: $state" -ForegroundColor $(if ($state -eq "Running") { "Green" } else { "Red" })

# Verificar se QuarkusHttp está habilitada
Write-Host "`n2. Funções registradas:" -ForegroundColor Yellow
az functionapp function list --name $functionAppName --resource-group $resourceGroup --query "[].{Name:name, IsDisabled:isDisabled}" --output table

# Verificar variáveis críticas
Write-Host "`n3. Variáveis críticas:" -ForegroundColor Yellow
$criticalVars = @("AZURE_STORAGE_CONNECTION_STRING", "AzureWebJobsStorage", "FUNCTIONS_WORKER_RUNTIME")
foreach ($var in $criticalVars) {
    $value = az functionapp config appsettings list --name $functionAppName --resource-group $resourceGroup --query "[?name=='$var'].value" -o tsv
    if ($value) {
        Write-Host "   [OK] $var" -ForegroundColor Green
    } else {
        Write-Host "   [X] $var - FALTANDO" -ForegroundColor Red
    }
}

# Testar endpoint
Write-Host "`n4. Teste de conectividade:" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "https://$functionAppName.azurewebsites.net" -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
    Write-Host "   [OK] Function App responde (Status: $($response.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "   [X] Function App não responde: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n=== FIM DO DIAGNÓSTICO ===" -ForegroundColor Cyan
