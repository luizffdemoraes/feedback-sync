# Script para verificar logs da Function App no Azure
param(
    [string]$FunctionAppName = "feedback-function-prod",
    [string]$ResourceGroup = "feedback-rg",
    [int]$Lines = 50
)

Write-Host "`n=== VERIFICANDO LOGS DA FUNCTION APP ===" -ForegroundColor Cyan
Write-Host "Function App: $FunctionAppName`n" -ForegroundColor Yellow

# Verificar status da Function App
Write-Host "1. Status da Function App:" -ForegroundColor Yellow
$status = az functionapp show --name $FunctionAppName --resource-group $ResourceGroup --query "{state:state, enabled:enabled, defaultHostName:defaultHostName}" -o json | ConvertFrom-Json
Write-Host "   Estado: $($status.state)" -ForegroundColor $(if ($status.state -eq "Running") { "Green" } else { "Red" })
Write-Host "   Habilitada: $($status.enabled)" -ForegroundColor $(if ($status.enabled) { "Green" } else { "Yellow" })
Write-Host "   URL: https://$($status.defaultHostName)" -ForegroundColor Cyan
Write-Host ""

# Verificar funções registradas
Write-Host "2. Funções registradas:" -ForegroundColor Yellow
az functionapp function list --name $FunctionAppName --resource-group $ResourceGroup --query "[].{Name:name, IsDisabled:isDisabled}" --output table
Write-Host ""

# Tentar obter logs via Kudu API
Write-Host "3. Analisando logs via Kudu API..." -ForegroundColor Yellow
$kuduBase = "https://$FunctionAppName.scm.azurewebsites.net"
$errorsFound = $false

try {
    # Tentar acessar logs Docker
    Write-Host "   Buscando logs Docker..." -ForegroundColor Gray
    $dockerLogs = Invoke-RestMethod -Uri "$kuduBase/api/logs/docker" -Method GET -UseBasicParsing -TimeoutSec 15 -ErrorAction SilentlyContinue
    
    if ($dockerLogs) {
        $logText = if ($dockerLogs -is [String]) { $dockerLogs } else { $dockerLogs | ConvertTo-Json -Depth 10 }
        
        # Procurar por erros relacionados ao Quarkus
        $quarkusErrors = $logText -split "`n" | Select-String -Pattern "Quarkus|quarkus" -CaseSensitive:$false | Select-Object -First 20
        $generalErrors = $logText -split "`n" | Select-String -Pattern "error|Error|ERROR|exception|Exception|EXCEPTION|failed|Failed|FAILED|Cannot|cannot|ClassNotFoundException|NoClassDefFoundError|Initialization|startup.*fail" -CaseSensitive:$false | Select-Object -First 30
        
        if ($quarkusErrors) {
            Write-Host "   [ERRO] Erros relacionados ao Quarkus encontrados:" -ForegroundColor Red
            $quarkusErrors | ForEach-Object { Write-Host "     $_" -ForegroundColor Red }
            $errorsFound = $true
        }
        
        # Filtrar HTML de autenticação
        $realErrors = $generalErrors | Where-Object { 
            $_ -notmatch "login\.microsoftonline\.com" -and 
            $_ -notmatch "Sign in" -and 
            $_ -notmatch "DOCTYPE html" -and
            $_ -notmatch "urlMsaSignUp" -and
            $_.Length -lt 500
        }
        
        if ($realErrors -and -not $quarkusErrors) {
            Write-Host "   [AVISO] Erros gerais encontrados:" -ForegroundColor Yellow
            $realErrors | Select-Object -First 15 | ForEach-Object { Write-Host "     $_" -ForegroundColor Yellow }
        }
        
        # Procurar por mensagens de inicialização bem-sucedida
        $successMessages = $logText -split "`n" | Select-String -Pattern "started|ready|initialized|listening|Quarkus.*started" -CaseSensitive:$false | Select-Object -First 10
        
        if ($successMessages) {
            Write-Host "   [OK] Mensagens de inicialização encontradas:" -ForegroundColor Green
            $successMessages | Select-Object -First 5 | ForEach-Object { Write-Host "     $_" -ForegroundColor Gray }
        }
        
        if (-not $quarkusErrors -and -not $generalErrors) {
            Write-Host "   [OK] Nenhum erro óbvio encontrado nos logs Docker" -ForegroundColor Green
            Write-Host "   Últimas linhas dos logs:" -ForegroundColor Gray
            ($logText -split "`n" | Select-Object -Last 10) -join "`n" | Write-Host -ForegroundColor Gray
        }
    } else {
        Write-Host "   [AVISO] Não foi possível acessar logs Docker" -ForegroundColor Yellow
    }
} catch {
    Write-Host "   [AVISO] Erro ao acessar logs via Kudu: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host ""

# Verificar variáveis de ambiente Quarkus
Write-Host "4. Verificando variáveis de ambiente Quarkus:" -ForegroundColor Yellow
$quarkusVars = az functionapp config appsettings list --name $FunctionAppName --resource-group $ResourceGroup --query "[?contains(name, 'QUARKUS')].{Name:name, Value:value}" -o json | ConvertFrom-Json

if ($quarkusVars) {
    Write-Host "   Variáveis configuradas:" -ForegroundColor Green
    $quarkusVars | ForEach-Object { Write-Host "     $($_.Name) = $($_.Value)" -ForegroundColor Gray }
} else {
    Write-Host "   [AVISO] Nenhuma variável QUARKUS encontrada" -ForegroundColor Yellow
}
Write-Host ""

# Testar endpoints
Write-Host "5. Testando endpoints:" -ForegroundColor Yellow
$endpoints = @(
    @{Name="Health Check"; Url="https://$($status.defaultHostName)/api/health"},
    @{Name="Avaliação"; Url="https://$($status.defaultHostName)/api/avaliacao"}
)

foreach ($endpoint in $endpoints) {
    try {
        $response = Invoke-WebRequest -Uri $endpoint.Url -Method GET -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
        Write-Host "   [OK] $($endpoint.Name): Status $($response.StatusCode)" -ForegroundColor Green
    } catch {
        $statusCode = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { "N/A" }
        Write-Host "   [ERRO] $($endpoint.Name): Status $statusCode" -ForegroundColor Red
    }
}
Write-Host ""

# Resumo
Write-Host "=== RESUMO ===" -ForegroundColor Cyan
if ($errorsFound) {
    Write-Host "   [ERRO] Erros encontrados nos logs. Verifique acima." -ForegroundColor Red
} else {
    Write-Host "   [OK] Nenhum erro crítico encontrado nos logs disponíveis" -ForegroundColor Green
}

Write-Host "`n=== ACESSO AOS LOGS ===" -ForegroundColor Cyan
$functionAppUrl = "https://$FunctionAppName.scm.azurewebsites.net"
Write-Host "Portal Azure: https://portal.azure.com > Function App > $FunctionAppName > Log stream" -ForegroundColor Gray
Write-Host "Kudu: $functionAppUrl" -ForegroundColor Gray
Write-Host ""
Write-Host "Comandos úteis:" -ForegroundColor Yellow
Write-Host "   az webapp log tail --name $FunctionAppName --resource-group $ResourceGroup" -ForegroundColor Gray
Write-Host ""
