# ============================================
# Script de Validação do Projeto Feedback
# ============================================
# Este script testa:
# - Endpoint REST (FeedbackController)
# - Azure Functions (NotifyAdminFunction, WeeklyReportFunction)
# e gera um relatório de validação completo
# ============================================

param(
    [switch]$SkipPreChecks,
    [switch]$Verbose,
    [int]$TimeoutSeconds = 10,
    [int]$MaxRetries = 2
)

$ErrorActionPreference = "Stop"

# Cores para output
function Write-Success { param($msg) Write-Host $msg -ForegroundColor Green }
function Write-Error { param($msg) Write-Host $msg -ForegroundColor Red }
function Write-Warning { param($msg) Write-Host $msg -ForegroundColor Yellow }
function Write-Info { param($msg) Write-Host $msg -ForegroundColor Cyan }
function Write-Detail { param($msg) if ($Verbose) { Write-Host "  -> $msg" -ForegroundColor Gray } }

# Variáveis globais
$baseUrl = "http://localhost:7071"
# Endpoint REST (FeedbackController - Clean Architecture)
$endpointSubmit = "$baseUrl/avaliacao"  # Quarkus REST endpoint
$testResults = @()
$startTime = Get-Date

# Limpar resultados de execuções anteriores (se houver)
$testResults = @()

# ============================================
# Funções auxiliares
# ============================================

function Test-Prerequisites {
    Write-Info "`n=== VERIFICANDO PRÉ-REQUISITOS ==="
    
    $allOk = $true
    
    # Verificar containers Docker
    Write-Info "Verificando containers Docker..."
    try {
        $containers = docker ps --format "{{.Names}}" 2>$null
        if (-not $containers) {
            Write-Error "  [X] Docker nao esta rodando ou nao ha containers"
            $allOk = $false
        } else {
            # Containers necessários: Azurite (Table Storage + Blob), SQL Server, Service Bus
            $required = @("azurite", "servicebus", "sqlserver")
            foreach ($req in $required) {
                $found = $containers | Where-Object { $_ -match $req }
                if ($found) {
                    $displayName = switch ($req) {
                        "azurite" { "Azurite (Table Storage + Blob)" }
                        "servicebus" { "Service Bus" }
                        "sqlserver" { "SQL Server" }
                        default { $req }
                    }
                    Write-Success "  [OK] $displayName : rodando"
                } else {
                    Write-Error "  [X] $req : nao encontrado"
                    $allOk = $false
                }
            }
        }
    } catch {
        Write-Error "  [X] Erro ao verificar containers: $_"
        $allOk = $false
    }
    
    # Verificar se aplicação está rodando
    Write-Info "`nVerificando se aplicacao esta respondendo..."
    try {
        # Usa o endpoint de health check do Quarkus
        $healthCheckUrl = "$baseUrl/health"
        $response = Invoke-WebRequest -Uri $healthCheckUrl -Method GET -TimeoutSec 10 -ErrorAction Stop
        Write-Success "  [OK] Aplicacao esta respondendo na porta 7071 (Health Check: $($response.StatusCode))"
    } catch {
        $errorDetails = $_.Exception.Message
        # Verificar se é erro de conexão ou timeout
        if ($_.Exception -is [System.Net.WebException]) {
            $webException = $_.Exception
            if ($webException.Response) {
                # Aplicação respondeu mas com erro HTTP (isso significa que está rodando!)
                $statusCode = [int]$webException.Response.StatusCode.value__
                Write-Success "  [OK] Aplicacao esta respondendo na porta 7071 (Status: $statusCode)"
                Write-Detail "  Detalhes: $errorDetails"
            } elseif ($webException.Status -eq [System.Net.WebExceptionStatus]::ConnectFailure) {
                # Erro de conexão (aplicação não está rodando)
                Write-Error "  [X] Aplicacao nao esta respondendo em $baseUrl"
                Write-Warning "  Erro de conexao: Nao foi possivel conectar"
                Write-Warning "  Certifique-se de que executar-app.ps1 esta rodando"
                Write-Info "  Dica: Procure por 'Listening on: http://localhost:7071' nos logs da aplicacao"
                $allOk = $false
            } elseif ($webException.Status -eq [System.Net.WebExceptionStatus]::Timeout) {
                # Timeout
                Write-Warning "  [!] Timeout ao conectar (aplicacao pode estar iniciando)"
                Write-Info "  Tente novamente em alguns segundos"
                $allOk = $false
            } else {
                # Outro erro de WebException
                Write-Warning "  [!] Erro ao verificar: $($webException.Status)"
                Write-Detail "  Detalhes: $errorDetails"
                # Considerar como OK se não for erro de conexão
            }
        } else {
            # Outro tipo de erro
            Write-Warning "  [!] Erro ao verificar aplicacao: $errorDetails"
            Write-Info "  Tentando continuar mesmo assim..."
            # Não marcar como erro fatal, apenas avisar
        }
    }
    
    if (-not $allOk -and -not $SkipPreChecks) {
        Write-Warning "`nATENCAO: Alguns pre-requisitos nao foram atendidos!"
        Write-Warning "Use -SkipPreChecks para continuar mesmo assim"
        return $false
    }
    
    Write-Success "`n[OK] Todos os pre-requisitos estao OK`n"
    return $true
}

function Test-ApplicationHealth {
    param([int]$MaxAttempts = 3, [int]$TimeoutSec = 10)
    
    $healthCheckUrl = "$baseUrl/health"
    
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        try {
            # Usa o endpoint de health check do Quarkus
            $response = Invoke-WebRequest -Uri $healthCheckUrl -Method GET -TimeoutSec $TimeoutSec -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Detail "  Health check OK (Status: 200)"
                return $true
            }
        } catch {
            # Se recebeu resposta HTTP (mesmo erro), a aplicação está rodando
            if ($_.Exception.Response) {
                $statusCode = [int]$_.Exception.Response.StatusCode.value__
                Write-Detail "  Aplicação respondendo no health check (Status: $statusCode)"
                # Aceita qualquer resposta HTTP como indicativo de que está rodando
                return $true
            }
            
            # Se for erro de conexão/timeout, tenta novamente
            if ($i -lt $MaxAttempts) {
                Write-Detail "  Tentativa $i/$MaxAttempts falhou, aguardando 3s..."
                Start-Sleep -Seconds 3
            } else {
                Write-Warning "  Aplicação não está respondendo após $MaxAttempts tentativas (timeout: ${TimeoutSec}s)"
                Write-Warning "  Verifique se a aplicação está rodando e se não há erros nos logs"
                return $false
            }
        }
    }
    return $false
}

function Invoke-FunctionTest {
    param(
        [string]$TestName,
        [string]$Description,
        [string]$Method = "POST",
        [string]$Uri,
        [object]$Body = $null,
        [int]$ExpectedStatus = 200,
        [scriptblock]$Validation = $null
    )
    
    Write-Info "`n--- Teste: $TestName ---"
    Write-Detail $Description
    
    # Verificar saúde da aplicação antes de cada teste crítico
    if ($TestName -match "Critico|Critica") {
        Write-Detail "Verificando saúde da aplicação antes do teste crítico..."
        if (-not (Test-ApplicationHealth -TimeoutSec 10)) {
            $testResult = @{
                Name = $TestName
                Description = $Description
                Status = "ERROR"
                Message = "Aplicação não está respondendo. Verifique se está rodando e se não há erros nos logs."
                Duration = 0
                Response = $null
                EndpointUsed = $Uri
            }
            $script:testResults += $testResult
            Write-Error "  [X] ERRO: Aplicação não está respondendo"
            Write-Warning "  Dica: Execute 'executar-app.ps1' para reiniciar a aplicação"
            return $testResult
        }
    }
    
    # Criar novo objeto hashtable para cada teste
    $testResult = [ordered]@{
        Name = $TestName
        Description = $Description
        Status = "PENDING"
        Message = ""
        Duration = 0
        Response = $null
        EndpointUsed = $Uri
    }
    
    $testStart = Get-Date
    
    try {
        # Endpoint único (REST Controller)
        $endpointsToTry = @($Uri)
        
        $lastError = $null
        $success = $false
        
        foreach ($endpointUri in $endpointsToTry) {
            $retryCount = 0
            while ($retryCount -le $MaxRetries -and -not $success) {
                try {
                    $params = @{
                        Uri = $endpointUri
                        Method = $Method
                        ContentType = "application/json"
                        TimeoutSec = $TimeoutSeconds
                        ErrorAction = "Stop"
                    }
                    
                    if ($Body) {
                        if ($Body -is [string]) {
                            $params.Body = $Body
                        } else {
                            $params.Body = ($Body | ConvertTo-Json -Compress)
                        }
                    }
                    
                    $response = Invoke-RestMethod @params
                    $testResult.Response = $response
                    $testResult.EndpointUsed = $endpointUri
                    $success = $true
                    break
                    
                } catch {
                    $lastError = $_
                    
                    # Se for erro de conexão e ainda houver tentativas, fazer retry
                    if ($_.Exception -is [System.Net.WebException] -and 
                        $_.Exception.Status -eq [System.Net.WebExceptionStatus]::ConnectFailure -and
                        $retryCount -lt $MaxRetries) {
                        $retryCount++
                        Write-Detail "  Retry $retryCount/$MaxRetries após erro de conexão..."
                        Start-Sleep -Seconds 1
                        continue
                    }
                    
                    # Se for 404 e houver mais endpoints para tentar, continuar
                    if ($_.Exception.Response) {
                        $statusCode = [int]$_.Exception.Response.StatusCode.value__
                        if ($statusCode -eq 404 -and $endpointsToTry.IndexOf($endpointUri) -lt ($endpointsToTry.Count - 1)) {
                            Write-Detail "  Tentando endpoint alternativo..."
                            break
                        }
                    }
                    
                    # Se não for retry, sair do loop
                    break
                }
            }
            
            if ($success) {
                break
            }
        }
        
        if (-not $success) {
            # Se nenhum endpoint funcionou, tratar como erro
            $errorResponse = $lastError.Exception.Response
            if ($errorResponse) {
                $statusCode = [int]$errorResponse.StatusCode.value__
                $reader = New-Object System.IO.StreamReader($errorResponse.GetResponseStream())
                $responseBody = $reader.ReadToEnd()
                
                if ($statusCode -eq $ExpectedStatus) {
                    $testResult.Status = "PASS"
                    $testResult.Message = "Erro esperado recebido corretamente"
                    Write-Success "  [OK] PASSOU (erro esperado)"
                } elseif ($statusCode -eq 503 -and $ExpectedStatus -eq 400) {
                    # 503 pode indicar que a aplicação está sobrecarregada ou caiu
                    $testResult.Status = "ERROR"
                    $testResult.Message = "Serviço indisponível (503). A aplicação pode ter caído ou estar sobrecarregada."
                    Write-Error "  [X] ERRO: $($testResult.Message)"
                    Write-Warning "  Verifique os logs da aplicação e se ela está rodando corretamente"
                } else {
                    $testResult.Status = "FAIL"
                    $testResult.Message = "Erro inesperado: Status $statusCode (esperado $ExpectedStatus) - $responseBody"
                    Write-Error "  [X] FALHOU: $($testResult.Message)"
                }
                $testResult.Response = $responseBody
            } else {
                $testResult.Status = "ERROR"
                $errorMsg = $lastError.Exception.Message
                
                # Verificar se é erro de conexão (aplicação pode ter caído)
                if ($lastError.Exception -is [System.Net.WebException]) {
                    $webEx = $lastError.Exception
                    if ($webEx.Status -eq [System.Net.WebExceptionStatus]::ConnectFailure) {
                        $testResult.Message = "Erro de conexão: Aplicação não está respondendo. Verifique se a aplicação está rodando."
                        Write-Error "  [X] ERRO: $($testResult.Message)"
                        Write-Warning "  Dica: Execute 'executar-app.ps1' para iniciar a aplicação"
                    } elseif ($webEx.Status -eq [System.Net.WebExceptionStatus]::Timeout) {
                        $testResult.Message = "Timeout: A requisição excedeu $TimeoutSeconds segundos"
                        Write-Error "  [X] ERRO: $($testResult.Message)"
                    } else {
                        $testResult.Message = "Erro de conexão: $errorMsg"
                        Write-Error "  [X] ERRO: $($testResult.Message)"
                    }
                } else {
                    $testResult.Message = "Erro: $errorMsg"
                    Write-Error "  [X] ERRO: $($testResult.Message)"
                }
            }
        } else {
            # Verificar status code (se disponível)
            if ($testResult.Response -is [System.Net.HttpWebResponse]) {
                $statusCode = $testResult.Response.StatusCode
            } else {
                # Se retornou objeto, assumir sucesso
                $statusCode = 200
            }
            
            if ($statusCode -eq $ExpectedStatus -or ($ExpectedStatus -eq 201 -and $statusCode -eq 200)) {
                if ($Validation) {
                    $validationResult = & $Validation $testResult.Response
                    if ($validationResult) {
                        $testResult.Status = "PASS"
                        $testResult.Message = "Teste passou com validacao"
                        Write-Success "  [OK] PASSOU"
                    } else {
                        $testResult.Status = "FAIL"
                        $testResult.Message = "Validacao falhou"
                        Write-Error "  [X] FALHOU: Validacao nao passou"
                    }
                } else {
                    $testResult.Status = "PASS"
                    $testResult.Message = "Resposta recebida com sucesso"
                    Write-Success "  [OK] PASSOU"
                }
            } else {
                $testResult.Status = "FAIL"
                $testResult.Message = "Status code esperado: $ExpectedStatus, recebido: $statusCode"
                Write-Error "  [X] FALHOU: Status code incorreto"
            }
        }
    } finally {
        $testResult.Duration = ((Get-Date) - $testStart).TotalMilliseconds
        if ($Verbose -and $testResult.Response) {
            Write-Detail "Resposta: $($testResult.Response | ConvertTo-Json -Compress)"
        }
    }
    
    # Criar cópia do resultado para evitar problemas de referência
    $resultCopy = @{
        Name = $testResult.Name
        Description = $testResult.Description
        Status = $testResult.Status
        Message = $testResult.Message
        Duration = $testResult.Duration
        Response = $testResult.Response
        EndpointUsed = $testResult.EndpointUsed
    }
    
    $script:testResults += $resultCopy
    return $resultCopy
}

# ============================================
# TESTES DAS FUNCTIONS
# ============================================

function Test-FeedbackController {
    Write-Info "`n========================================"
    Write-Info "TESTANDO: FeedbackController (REST)"
    Write-Info "========================================"
    Write-Info "Endpoint: POST /avaliacao"
    Write-Info "Arquitetura: Clean Architecture (infrastructure/controllers)"
    Write-Info "========================================`n"
    
    # Teste 1: Feedback normal (sucesso)
    Invoke-FunctionTest `
        -TestName "SubmitFeedback - Feedback Normal" `
        -Description "Envia feedback com nota 8 (nao critico)" `
        -Uri $endpointSubmit `
        -Body @{
            descricao = "Aula muito boa, professor explicou bem os conceitos"
            nota = 8
            urgencia = "LOW"
        } `
        -ExpectedStatus 201 `
        -Validation {
            param($response)
            return ($response.id -and $response.status -eq "recebido")
        }
    
    Start-Sleep -Seconds 2
    
    # Verificar saúde da aplicação antes do teste crítico
    Write-Detail "Verificando saúde da aplicação antes do teste crítico..."
    if (-not (Test-ApplicationHealth -TimeoutSec 10)) {
        Write-Warning "`n[AVISO] Aplicação não está respondendo. Pulando testes restantes da função Test-FeedbackController."
        Write-Warning "  A aplicação pode ter caído ou estar sobrecarregada."
        Write-Warning "  Verifique os logs da aplicação e tente reiniciá-la."
        return
    }
    
    # Teste 2: Feedback crítico (nota <= 3) - deve funcionar e disparar notificação
    Invoke-FunctionTest `
        -TestName "SubmitFeedback - Feedback Critico" `
        -Description "Envia feedback com nota 2 (critico - deve disparar NotifyAdminFunction)" `
        -Uri $endpointSubmit `
        -Body @{
            descricao = "Aula muito ruim, nao entendi nada"
            nota = 2
            urgencia = "HIGH"
        } `
        -ExpectedStatus 201 `
        -Validation {
            param($response)
            return ($response.id -and $response.status -eq "recebido")
        }
    
    Start-Sleep -Seconds 3
    
    # Teste 3: Feedback com nota alta
    Invoke-FunctionTest `
        -TestName "SubmitFeedback - Feedback Nota Alta" `
        -Description "Envia feedback com nota 9 (excelente)" `
        -Uri $endpointSubmit `
        -Body @{
            descricao = "Aula excelente, muito didatica"
            nota = 9
            urgencia = "LOW"
        } `
        -ExpectedStatus 201 `
        -Validation {
            param($response)
            return ($response.id -and $response.status -eq "recebido")
        }
    
    Start-Sleep -Seconds 1
    
    # Teste 4: Validação - Corpo vazio
    Invoke-FunctionTest `
        -TestName "SubmitFeedback - Validacao Corpo Vazio" `
        -Description "Tenta enviar requisicao sem corpo (deve retornar erro 400)" `
        -Uri $endpointSubmit `
        -Body "" `
        -ExpectedStatus 400
    
    Start-Sleep -Seconds 1
    
    # Teste 5: Validação - Nota inválida (maior que 10)
    Invoke-FunctionTest `
        -TestName "SubmitFeedback - Nota Invalida (Maior que 10)" `
        -Description "Tenta enviar feedback com nota 15 (deve retornar erro 400)" `
        -Uri $endpointSubmit `
        -Body @{
            descricao = "Teste"
            nota = 15
        } `
        -ExpectedStatus 400
    
    Start-Sleep -Seconds 1
    
    # Teste 6: Validação - Nota inválida (menor que 0)
    Invoke-FunctionTest `
        -TestName "SubmitFeedback - Nota Invalida (Menor que 0)" `
        -Description "Tenta enviar feedback com nota -1 (deve retornar erro 400)" `
        -Uri $endpointSubmit `
        -Body @{
            descricao = "Teste"
            nota = -1
        } `
        -ExpectedStatus 400
    
    Start-Sleep -Seconds 1
    
    # Teste 7: Validação - Campo obrigatório faltando (descrição)
    Invoke-FunctionTest `
        -TestName "SubmitFeedback - Descricao Faltando" `
        -Description "Tenta enviar feedback sem descricao (deve retornar erro 400)" `
        -Uri $endpointSubmit `
        -Body @{
            nota = 7
        } `
        -ExpectedStatus 400
    
    Start-Sleep -Seconds 1
    
    # Teste 8: Validação - Campo obrigatório faltando (nota)
    Invoke-FunctionTest `
        -TestName "SubmitFeedback - Nota Faltando" `
        -Description "Tenta enviar feedback sem nota (deve retornar erro 400)" `
        -Uri $endpointSubmit `
        -Body @{
            descricao = "Teste sem nota"
        } `
        -ExpectedStatus 400
    
    Start-Sleep -Seconds 1
    
    # Teste 9: Feedback com urgência em diferentes formatos
    Invoke-FunctionTest `
        -TestName "SubmitFeedback - Urgencia LOW" `
        -Description "Envia feedback com urgencia LOW" `
        -Uri $endpointSubmit `
        -Body @{
            descricao = "Feedback de teste"
            nota = 7
            urgencia = "LOW"
        } `
        -ExpectedStatus 201
    
    Start-Sleep -Seconds 1
    
    Invoke-FunctionTest `
        -TestName "SubmitFeedback - Urgencia MEDIUM" `
        -Description "Envia feedback com urgencia MEDIUM" `
        -Uri $endpointSubmit `
        -Body @{
            descricao = "Feedback de teste"
            nota = 6
            urgencia = "MEDIUM"
        } `
        -ExpectedStatus 201
    
    Start-Sleep -Seconds 1
    
    Invoke-FunctionTest `
        -TestName "SubmitFeedback - Urgencia HIGH" `
        -Description "Envia feedback com urgencia HIGH" `
        -Uri $endpointSubmit `
        -Body @{
            descricao = "Feedback de teste"
            nota = 5
            urgencia = "HIGH"
        } `
        -ExpectedStatus 201
}

function Test-NotifyAdminFunction {
    Write-Info "`n========================================"
    Write-Info "TESTANDO: NotifyAdminFunction"
    Write-Info "========================================"
    
    Write-Info "`nA NotifyAdminFunction e disparada automaticamente quando um feedback critico (nota <= 3) e criado."
    Write-Info "Para validar, verifique os logs da aplicacao apos enviar um feedback critico."
    
    # Criar feedback crítico para disparar a função
    Write-Info "`nEnviando feedback critico para disparar NotifyAdminFunction..."
    
    try {
        $criticalBody = @{
            descricao = "ALERTA: Feedback critico para teste de notificacao"
            nota = 1
            urgencia = "HIGH"
        } | ConvertTo-Json
        
        $response = Invoke-RestMethod -Uri $endpointSubmit `
            -Method POST `
            -ContentType "application/json" `
            -Body $criticalBody `
            -TimeoutSec $TimeoutSeconds `
            -ErrorAction Stop
        
        Write-Success "  [OK] Feedback critico criado (ID: $($response.id))"
        Write-Info "`nAgora verifique os logs da aplicacao para confirmar:"
        Write-Info "  - 'Processando mensagem critica do Service Bus'"
        Write-Info "  - 'Feedback critico recebido'"
        Write-Info "  - 'Notificacao enviada ao administrador com sucesso'"
        
        $script:testResults += @{
            Name = "NotifyAdminFunction - Disparo Automatico"
            Description = "Verificar logs apos feedback critico"
            Status = "INFO"
            Message = "Feedback critico criado. Verifique logs manualmente."
            Duration = 0
            Response = $response
        }
        
    } catch {
        Write-Error "  [X] Erro ao criar feedback critico: $_"
        $script:testResults += @{
            Name = "NotifyAdminFunction - Disparo Automatico"
            Description = "Verificar logs apos feedback critico"
            Status = "ERROR"
            Message = "Erro ao criar feedback critico: $_"
            Duration = 0
            Response = $null
        }
    }
    
    Start-Sleep -Seconds 2
}

function Test-WeeklyReportFunction {
    Write-Info "`n========================================"
    Write-Info "TESTANDO: WeeklyReportFunction"
    Write-Info "========================================"
    
    Write-Info "`nA WeeklyReportFunction e um Timer Trigger que executa automaticamente"
    Write-Info "toda segunda-feira as 08:00 (cron: 0 0 8 * * MON)"
    Write-Info "`nPara testes, voce pode:"
    Write-Info "  1. Aguardar o horario agendado"
    Write-Info "  2. Modificar temporariamente o cron em WeeklyReportFunction.java"
    Write-Info "  3. Verificar se a configuracao esta correta"
    
    # Verificar configuração do timer
    Write-Info "`nVerificando configuracao do timer..."
    
    try {
        $functionFile = "src\main\java\br\com\fiap\postech\feedback\infrastructure\handlers\WeeklyReportFunction.java"
        if (Test-Path $functionFile) {
            $content = Get-Content $functionFile -Raw
            $regexPattern = 'schedule\s*=\s*"([^"]+)"'
            if ($content -match $regexPattern) {
                $cron = $matches[1]
                Write-Success "  [OK] Cron encontrado: $cron"
                Write-Info "  Cron significa: Toda segunda-feira as 08:00"
            } else {
                Write-Warning "  [AVISO] Nao foi possivel encontrar o cron no arquivo"
            }
        } else {
            Write-Warning "  [AVISO] Arquivo da funcao nao encontrado"
        }
        
        $script:testResults += @{
            Name = "WeeklyReportFunction - Configuracao Timer"
            Description = "Verificar configuracao do cron"
            Status = "INFO"
            Message = "Timer configurado para executar toda segunda-feira as 08:00"
            Duration = 0
            Response = $null
        }
        
    } catch {
        Write-Error "  [X] Erro ao verificar configuracao: $_"
    }
}

function Show-Report {
    Write-Info "`n`n========================================"
    Write-Info "RELATORIO DE VALIDACAO"
    Write-Info "========================================"
    
    $totalTests = $testResults.Count
    $passedTests = ($testResults | Where-Object { $_.Status -eq "PASS" }).Count
    $failedTests = ($testResults | Where-Object { $_.Status -eq "FAIL" }).Count
    $errorTests = ($testResults | Where-Object { $_.Status -eq "ERROR" }).Count
    $infoTests = ($testResults | Where-Object { $_.Status -eq "INFO" }).Count
    $totalDuration = ((Get-Date) - $startTime).TotalSeconds
    
    Write-Info "`nResumo Geral:"
    Write-Host "  Total de testes: $totalTests" -ForegroundColor White
    Write-Success "  [OK] Passou: $passedTests"
    Write-Error "  [X] Falhou: $failedTests"
    Write-Warning "  [!] Erro: $errorTests"
    Write-Info "  [i] Info: $infoTests"
    Write-Host "  Tempo total: $([math]::Round($totalDuration, 2))s" -ForegroundColor Gray
    
    Write-Info "`nDetalhes dos Testes:"
    Write-Host "`n"
    
    foreach ($test in $testResults) {
        $statusIcon = switch ($test.Status) {
            "PASS" { "[OK]" }
            "FAIL" { "[X]" }
            "ERROR" { "[!]" }
            "INFO" { "[i]" }
            default { "[?]" }
        }
        
        $color = switch ($test.Status) {
            "PASS" { "Green" }
            "FAIL" { "Red" }
            "ERROR" { "Yellow" }
            "INFO" { "Cyan" }
            default { "White" }
        }
        
        Write-Host "  $statusIcon [$($test.Status)] $($test.Name)" -ForegroundColor $color
        Write-Host "      $($test.Description)" -ForegroundColor Gray
        if ($test.Message) {
            Write-Host "      -> $($test.Message)" -ForegroundColor DarkGray
        }
        if ($test.Duration -gt 0) {
            Write-Host "      Tempo: $([math]::Round($test.Duration, 0))ms" -ForegroundColor DarkGray
        }
        Write-Host ""
    }
    
    # Salvar relatório em arquivo
    $reportFile = "relatorio-validacao-$(Get-Date -Format 'yyyyMMdd-HHmmss').json"
    $testResults | ConvertTo-Json -Depth 10 | Out-File $reportFile -Encoding UTF8
    Write-Info "`nRelatorio detalhado salvo em: $reportFile"
    
    # Resultado final
    Write-Info "`n========================================"
    if ($failedTests -eq 0 -and $errorTests -eq 0) {
        Write-Success "[SUCESSO] TODOS OS TESTES PASSARAM!"
    } else {
        Write-Warning "[ATENCAO] ALGUNS TESTES FALHARAM. Revise os detalhes acima."
    }
    Write-Info "========================================`n"
}

# ============================================
# EXECUÇÃO PRINCIPAL
# ============================================

Write-Host "`n"
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Script de Validacao do Projeto Feedback" -ForegroundColor Cyan
Write-Host "  feedback-sync - FIAP Postech" -ForegroundColor Cyan
Write-Host "  Clean Architecture + Azure Functions" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Info "Parametros configurados:"
Write-Host "  Timeout: $TimeoutSeconds segundos" -ForegroundColor Gray
Write-Host "  Max Retries: $MaxRetries" -ForegroundColor Gray
Write-Host ""

if (-not $SkipPreChecks) {
    $preChecksOk = Test-Prerequisites
    if (-not $preChecksOk) {
        Write-Error "`nPre-requisitos nao atendidos. Execute novamente com -SkipPreChecks para continuar."
        exit 1
    }
} else {
    Write-Warning "Pulando verificacao de pre-requisitos..."
}

Write-Info "`nIniciando testes do projeto...`n"
Write-Info "  - FeedbackController (REST)"
Write-Info "  - NotifyAdminFunction (Azure Function)"
Write-Info "  - WeeklyReportFunction (Azure Function)`n"

try {
    # Executar testes
    Test-FeedbackController
    Test-NotifyAdminFunction
    Test-WeeklyReportFunction
    
    # Mostrar relatório
    Show-Report
    
} catch {
    Write-Error "`nErro durante execucao dos testes: $_"
    Write-Error $_.ScriptStackTrace
    exit 1
}

Write-Info "`nValidacao concluida!`n"

