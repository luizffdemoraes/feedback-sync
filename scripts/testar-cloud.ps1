# ============================================
# Script de Teste do Ambiente Cloud (Azure)
# ============================================
# Este script testa especificamente o ambiente cloud:
# - GET /api/health - Health check endpoint
# - POST /api/avaliacao - Endpoint de criação de feedback
# ============================================

param(
    [string]$FunctionAppUrl = "https://feedback-function-prod.azurewebsites.net",
    [switch]$Verbose,
    [switch]$SkipHealthCheck
)

$ErrorActionPreference = "Continue"

# Cores para output
function Write-Success { param($msg) Write-Host $msg -ForegroundColor Green }
function Write-Error { param($msg) Write-Host $msg -ForegroundColor Red }
function Write-Warning { param($msg) Write-Host $msg -ForegroundColor Yellow }
function Write-Info { param($msg) Write-Host $msg -ForegroundColor Cyan }
function Write-Detail { param($msg) if ($Verbose) { Write-Host "  -> $msg" -ForegroundColor Gray } }

Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "  TESTE DO AMBIENTE CLOUD (AZURE)" -ForegroundColor Cyan
Write-Host "============================================`n" -ForegroundColor Cyan

Write-Info "URL da Function App: $FunctionAppUrl"
Write-Host ""

$results = @{
    Connectivity = @{ Success = $false; Error = $null }
    HealthCheck = @{ Success = $false; StatusCode = $null; ResponseTime = $null; Error = $null; ResponseBody = $null }
    Avaliacao = @{ Success = $false; StatusCode = $null; ResponseTime = $null; Error = $null; ResponseBody = $null }
}

# ============================================
# Teste 0: Conectividade Básica
# ============================================
Write-Info "[0/3] Verificando conectividade básica..."
Write-Detail "Testando: $FunctionAppUrl"

try {
    $startTime = Get-Date
    $response = Invoke-WebRequest -Uri $FunctionAppUrl -Method GET -TimeoutSec 10 -ErrorAction Stop -UseBasicParsing
    $endTime = Get-Date
    $responseTime = ($endTime - $startTime).TotalMilliseconds
    
    $results.Connectivity.Success = $true
    Write-Success "  [OK] Conectividade OK"
    Write-Detail "  Status Code: $($response.StatusCode)"
    Write-Detail "  Tempo de Resposta: $([math]::Round($responseTime, 2))ms"
} catch {
    $results.Connectivity.Error = $_.Exception.Message
    Write-Error "  ✗ Conectividade FALHOU"
    Write-Detail "  Erro: $($_.Exception.Message)"
    
    if ($_.Exception -is [System.Net.WebException]) {
        $webException = $_.Exception
        if ($webException.Status -eq [System.Net.WebExceptionStatus]::NameResolutionFailure) {
            Write-Warning "  Problema de DNS: O nome do dominio nao foi resolvido"
            Write-Warning "  Verifique se a URL esta correta: $FunctionAppUrl"
        } elseif ($webException.Status -eq [System.Net.WebExceptionStatus]::ConnectFailure) {
            Write-Warning "  Problema de conexao: Nao foi possivel conectar ao servidor"
            Write-Warning "  Verifique se a Function App esta rodando no Azure"
        } elseif ($webException.Status -eq [System.Net.WebExceptionStatus]::Timeout) {
            Write-Warning "  Timeout: O servidor nao respondeu a tempo"
        } elseif ($webException.Response) {
            $statusCode = [int]$webException.Response.StatusCode.value__
            Write-Warning "  Status Code recebido: $statusCode"
            if ($statusCode -eq 404) {
                Write-Warning "  A Function App pode nao estar implantada ou a URL esta incorreta"
            } elseif ($statusCode -eq 503) {
                Write-Warning "  A Function App pode estar em processo de inicializacao (cold start)"
            }
        }
    }
    
    Write-Host ""
    Write-Warning "DIAGNOSTICO:"
    Write-Host "  1. Verifique se a Function App esta implantada:" -ForegroundColor Yellow
    Write-Host "     az functionapp list --query '[].{Name:name, State:state}' --output table" -ForegroundColor Gray
    Write-Host "  2. Verifique o status da Function App:" -ForegroundColor Yellow
    Write-Host "     az functionapp show --name feedback-function-prod --resource-group feedback-rg --query '{State:state, Enabled:enabled}'" -ForegroundColor Gray
    Write-Host "  3. Verifique os logs da Function App:" -ForegroundColor Yellow
    Write-Host "     az functionapp log tail --name feedback-function-prod --resource-group feedback-rg" -ForegroundColor Gray
    Write-Host ""
    
    if (-not $SkipHealthCheck) {
        Write-Warning "Continuando com os testes mesmo com falha de conectividade..."
        Write-Host ""
    } else {
        Write-Error "Abortando testes devido à falha de conectividade"
        exit 1
    }
}

# ============================================
# Teste 1: GET /api/health
# ============================================
if (-not $SkipHealthCheck) {
    Write-Info "`n[1/2] Testando GET /api/health..."
    Write-Detail "URL: $FunctionAppUrl/api/health"
    
    try {
        $startTime = Get-Date
        $response = Invoke-WebRequest -Uri "$FunctionAppUrl/api/health" -Method GET -TimeoutSec 15 -ErrorAction Stop -UseBasicParsing
        $endTime = Get-Date
        $responseTime = ($endTime - $startTime).TotalMilliseconds
        
        $results.HealthCheck.Success = $true
        $results.HealthCheck.StatusCode = $response.StatusCode
        $results.HealthCheck.ResponseTime = [math]::Round($responseTime, 2)
        
        try {
            $jsonResponse = $response.Content | ConvertFrom-Json
            $results.HealthCheck.ResponseBody = $jsonResponse
        } catch {
            $results.HealthCheck.ResponseBody = $response.Content
        }
        
        Write-Success "  [OK] Health check OK"
        Write-Detail "  Status Code: $($response.StatusCode)"
        Write-Detail "  Tempo de Resposta: $($results.HealthCheck.ResponseTime)ms"
        
        if ($Verbose -and $results.HealthCheck.ResponseBody) {
            if ($results.HealthCheck.ResponseBody -is [PSCustomObject]) {
                Write-Detail "  Resposta: $($results.HealthCheck.ResponseBody | ConvertTo-Json -Compress)"
            } else {
                Write-Detail "  Resposta: $($results.HealthCheck.ResponseBody)"
            }
        }
    } catch {
        $results.HealthCheck.Error = $_.Exception.Message
        Write-Error "  [X] Health check FALHOU"
        Write-Detail "  Erro: $($_.Exception.Message)"
        
        if ($_.Exception -is [System.Net.WebException]) {
            $webException = $_.Exception
            if ($webException.Response) {
                $statusCode = [int]$webException.Response.StatusCode.value__
                $results.HealthCheck.StatusCode = $statusCode
                Write-Warning "  Status Code recebido: $statusCode"
                
                try {
                    $errorStream = $webException.Response.GetResponseStream()
                    $reader = New-Object System.IO.StreamReader($errorStream)
                    $errorBody = $reader.ReadToEnd()
                    Write-Detail "  Corpo do erro: $errorBody"
                    
                    if ($errorBody -match "404" -or $statusCode -eq 404) {
                        Write-Warning "  O endpoint /api/health nao foi encontrado"
                        Write-Warning "  Possiveis causas:"
                        Write-Warning "    - O Quarkus SmallRye Health nao esta configurado corretamente"
                        Write-Warning "    - A aplicacao nao foi implantada corretamente"
                        Write-Warning "    - O endpoint esta em outro caminho (verifique /health sem /api)"
                    }
                } catch {
                    Write-Detail "  Nao foi possivel ler o corpo do erro"
                }
            } elseif ($webException.Status -eq [System.Net.WebExceptionStatus]::Timeout) {
                Write-Warning "  Timeout: A Function App pode estar em cold start"
                Write-Warning "  Tente novamente em alguns segundos"
            }
        }
    }
} else {
    Write-Info "`n[1/2] Testando GET /api/health... (PULADO)"
}

# ============================================
# Teste 2: POST /api/avaliacao
# ============================================
Write-Info "`n[2/2] Testando POST /api/avaliacao..."
Write-Detail "URL: $FunctionAppUrl/api/avaliacao"

$testPayload = @{
    descricao = "Teste automatizado do ambiente cloud - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    nota = 8
    urgencia = "LOW"
} | ConvertTo-Json -Compress

Write-Detail "Payload: $testPayload"

try {
    $startTime = Get-Date
    $response = Invoke-WebRequest -Uri "$FunctionAppUrl/api/avaliacao" `
        -Method POST `
        -ContentType "application/json" `
        -Body $testPayload `
        -TimeoutSec 20 `
        -ErrorAction Stop `
        -UseBasicParsing
    $endTime = Get-Date
    $responseTime = ($endTime - $startTime).TotalMilliseconds
    
    $results.Avaliacao.Success = $true
    $results.Avaliacao.StatusCode = $response.StatusCode
    $results.Avaliacao.ResponseTime = [math]::Round($responseTime, 2)
    
    try {
        $jsonResponse = $response.Content | ConvertFrom-Json
        $results.Avaliacao.ResponseBody = $jsonResponse
    } catch {
        $results.Avaliacao.ResponseBody = $response.Content
    }
    
    Write-Success "  [OK] POST /api/avaliacao OK"
    Write-Detail "  Status Code: $($response.StatusCode)"
    Write-Detail "  Tempo de Resposta: $($results.Avaliacao.ResponseTime)ms"
    
    if ($results.Avaliacao.ResponseBody) {
        if ($results.Avaliacao.ResponseBody -is [PSCustomObject]) {
            $responseJson = $results.Avaliacao.ResponseBody | ConvertTo-Json -Compress
            Write-Detail "  Resposta: $responseJson"
            Write-Host ""
            Write-Success "  Feedback criado com sucesso!"
            Write-Host "  ID: $($results.Avaliacao.ResponseBody.id)" -ForegroundColor Gray
            Write-Host "  Status: $($results.Avaliacao.ResponseBody.status)" -ForegroundColor Gray
        } else {
            Write-Detail "  Resposta: $($results.Avaliacao.ResponseBody)"
        }
    }
} catch {
    $results.Avaliacao.Error = $_.Exception.Message
    Write-Error "  [X] POST /api/avaliacao FALHOU"
    Write-Detail "  Erro: $($_.Exception.Message)"
    
    if ($_.Exception -is [System.Net.WebException]) {
        $webException = $_.Exception
        if ($webException.Response) {
            $statusCode = [int]$webException.Response.StatusCode.value__
            $results.Avaliacao.StatusCode = $statusCode
            Write-Warning "  Status Code recebido: $statusCode"
            
            try {
                $errorStream = $webException.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($errorStream)
                $errorBody = $reader.ReadToEnd()
                Write-Detail "  Corpo do erro: $errorBody"
                
                if ($statusCode -eq 404) {
                    Write-Warning "  O endpoint /api/avaliacao nao foi encontrado"
                    Write-Warning "  Possiveis causas:"
                    Write-Warning "    - A FeedbackHttpFunction nao esta sendo exposta corretamente"
                    Write-Warning "    - A aplicacao nao foi implantada corretamente"
                    Write-Warning "    - Verifique se a Azure Function foi deployada corretamente"
                } elseif ($statusCode -eq 500) {
                    Write-Warning "  Erro interno do servidor"
                    Write-Warning "  Verifique os logs da Function App para mais detalhes:"
                    Write-Warning "    az functionapp log tail --name feedback-function-prod --resource-group feedback-rg"
                } elseif ($statusCode -eq 503) {
                    Write-Warning "  Servico temporariamente indisponivel"
                    Write-Warning "  A Function App pode estar em processo de inicializacao (cold start)"
                }
            } catch {
                Write-Detail "  Nao foi possivel ler o corpo do erro"
            }
        } elseif ($webException.Status -eq [System.Net.WebExceptionStatus]::Timeout) {
            Write-Warning "  Timeout: A Function App pode estar em cold start"
            Write-Warning "  Tente novamente em alguns segundos"
        }
    }
}

# ============================================
# Resumo Final
# ============================================
Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "  RESUMO DOS TESTES" -ForegroundColor Cyan
Write-Host "============================================`n" -ForegroundColor Cyan

Write-Host "Conectividade Básica:" -ForegroundColor White
if ($results.Connectivity.Success) {
    Write-Success "  Status: [OK] SUCESSO"
} else {
    Write-Error "  Status: [X] FALHOU"
    if ($results.Connectivity.Error) {
        Write-Host "  Erro: $($results.Connectivity.Error)" -ForegroundColor Red
    }
}

if (-not $SkipHealthCheck) {
    Write-Host "`nGET /api/health:" -ForegroundColor White
    if ($results.HealthCheck.Success) {
        Write-Success "  Status: [OK] SUCESSO"
        Write-Host "  Status Code: $($results.HealthCheck.StatusCode)" -ForegroundColor Gray
        Write-Host "  Tempo de Resposta: $($results.HealthCheck.ResponseTime)ms" -ForegroundColor Gray
    } else {
        Write-Error "  Status: [X] FALHOU"
        if ($results.HealthCheck.StatusCode) {
            Write-Host "  Status Code: $($results.HealthCheck.StatusCode)" -ForegroundColor Yellow
        }
        if ($results.HealthCheck.Error) {
            Write-Host "  Erro: $($results.HealthCheck.Error)" -ForegroundColor Red
        }
    }
}

Write-Host "`nPOST /api/avaliacao:" -ForegroundColor White
if ($results.Avaliacao.Success) {
    Write-Success "  Status: [OK] SUCESSO"
    Write-Host "  Status Code: $($results.Avaliacao.StatusCode)" -ForegroundColor Gray
    Write-Host "  Tempo de Resposta: $($results.Avaliacao.ResponseTime)ms" -ForegroundColor Gray
    if ($results.Avaliacao.ResponseBody -and $results.Avaliacao.ResponseBody.id) {
        Write-Host "  Feedback ID: $($results.Avaliacao.ResponseBody.id)" -ForegroundColor Gray
    }
} else {
    Write-Error "  Status: [X] FALHOU"
    if ($results.Avaliacao.StatusCode) {
        Write-Host "  Status Code: $($results.Avaliacao.StatusCode)" -ForegroundColor Yellow
    }
    if ($results.Avaliacao.Error) {
        Write-Host "  Erro: $($results.Avaliacao.Error)" -ForegroundColor Red
    }
}

Write-Host "`n============================================" -ForegroundColor Cyan

# Contar testes bem-sucedidos
$testsToCount = @($results.Connectivity)
if (-not $SkipHealthCheck) {
    $testsToCount += $results.HealthCheck
}
$testsToCount += $results.Avaliacao

$totalTests = $testsToCount.Count
$successTests = ($testsToCount | Where-Object { $_.Success -eq $true }).Count

if ($successTests -eq $totalTests) {
    Write-Success "RESULTADO FINAL: TODOS OS TESTES PASSARAM ($successTests/$totalTests)"
    Write-Host ""
    Write-Info "Proximos passos:"
    Write-Host "  - Teste criar um feedback critico (nota <= 3) para verificar notificacoes" -ForegroundColor Gray
    Write-Host "  - Verifique os logs da Function App para monitorar o processamento" -ForegroundColor Gray
    exit 0
} elseif ($successTests -gt 0) {
    $msg = "RESULTADO FINAL: TESTES PARCIAIS ($successTests/$totalTests passaram)"
    Write-Warning $msg
    Write-Host ""
    Write-Info "Comandos uteis para diagnostico:"
    Write-Host "  # Verificar status da Function App" -ForegroundColor Gray
    Write-Host "  az functionapp show --name feedback-function-prod --resource-group feedback-rg" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  # Ver logs em tempo real" -ForegroundColor Gray
    Write-Host "  az functionapp log tail --name feedback-function-prod --resource-group feedback-rg" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  # Verificar configurações da aplicação" -ForegroundColor Gray
    Write-Host "  az functionapp config appsettings list --name feedback-function-prod --resource-group feedback-rg" -ForegroundColor Gray
    exit 1
} else {
    Write-Error "RESULTADO FINAL: TODOS OS TESTES FALHARAM (0/$totalTests)"
    Write-Host ""
    Write-Warning "DIAGNOSTICO RECOMENDADO:"
    Write-Host "  1. Verifique se a Function App esta implantada e rodando" -ForegroundColor Yellow
    Write-Host "  2. Verifique se a URL esta correta: $FunctionAppUrl" -ForegroundColor Yellow
    Write-Host "  3. Verifique os logs da Function App para erros" -ForegroundColor Yellow
    Write-Host "  4. Verifique as configuracoes de aplicacao (variaveis de ambiente)" -ForegroundColor Yellow
    Write-Host ""
    Write-Info "Execute os comandos de diagnostico acima para mais informacoes"
    exit 1
}
