# Script para analisar problemas e verificar logs
# Uso: .\scripts\analisar-problema.ps1

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  ANALISE DE PROBLEMAS E LOGS" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Verifica processo Java
Write-Host "1. PROCESSOS JAVA RODANDO:" -ForegroundColor Cyan
$javaProcesses = Get-Process | Where-Object {$_.ProcessName -eq "java"} | Select-Object Id, StartTime, @{Name="TempoRodando";Expression={(Get-Date) - $_.StartTime}}
if ($javaProcesses) {
    $javaProcesses | Format-Table -AutoSize
    Write-Host "  Total de processos Java: $($javaProcesses.Count)" -ForegroundColor White
} else {
    Write-Host "  Nenhum processo Java encontrado!" -ForegroundColor Red
    Write-Host "  Execute a aplicacao primeiro: .\executar-app.ps1" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# 2. Verifica porta 7071
Write-Host "2. PORTA 7071:" -ForegroundColor Cyan
$porta = netstat -ano | findstr :7071 | Select-String "LISTENING"
if ($porta) {
    Write-Host "  Porta 7071 esta em uso (aplicacao rodando)" -ForegroundColor Green
} else {
    Write-Host "  Porta 7071 NAO esta em uso!" -ForegroundColor Red
    Write-Host "  A aplicacao nao esta rodando corretamente" -ForegroundColor Yellow
}

Write-Host ""

# 3. Testa endpoint
Write-Host "3. TESTE DO ENDPOINT:" -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -Uri "http://localhost:7071/api" -Method Get -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
    Write-Host "  Endpoint /api esta acessivel" -ForegroundColor Green
} catch {
    Write-Host "  Endpoint /api nao esta acessivel" -ForegroundColor Yellow
}

try {
    $response = Invoke-WebRequest -Uri "http://localhost:7071/api/avaliacao" -Method Get -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
    Write-Host "  Endpoint /api/avaliacao esta acessivel" -ForegroundColor Green
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 404) {
        Write-Host "  ERRO 404: Funcao nao registrada!" -ForegroundColor Red
        Write-Host "  Isso significa que a Azure Function nao foi registrada corretamente" -ForegroundColor Yellow
    } elseif ($statusCode -eq 405) {
        Write-Host "  Endpoint existe (405 = metodo GET nao permitido, mas POST deve funcionar)" -ForegroundColor Green
    } else {
        Write-Host "  Erro: $statusCode" -ForegroundColor Red
    }
}

Write-Host ""

# 4. Verifica containers
Write-Host "4. CONTAINERS DOCKER:" -ForegroundColor Cyan
$containers = docker ps --format "{{.Names}}\t{{.Status}}" 2>$null
if ($containers) {
    $containers | ForEach-Object {
        if ($_ -match "cosmos") {
            Write-Host "  Cosmos DB: $_" -ForegroundColor Green
        } elseif ($_ -match "azurite") {
            Write-Host "  Azurite: $_" -ForegroundColor Green
        } elseif ($_ -match "servicebus") {
            Write-Host "  Service Bus: $_" -ForegroundColor Green
        }
    }
} else {
    Write-Host "  Nenhum container encontrado" -ForegroundColor Red
}

Write-Host ""

# 5. Instrucoes para ver logs
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  COMO VER OS LOGS DA APLICACAO" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "OPCAO 1: Ver logs no terminal da aplicacao" -ForegroundColor Green
Write-Host "  Os logs aparecem no terminal onde voce executou:" -ForegroundColor White
Write-Host "    .\executar-app.ps1" -ForegroundColor Gray
Write-Host "  OU" -ForegroundColor White
Write-Host "    `$env:QUARKUS_PROFILE='local'; .\mvnw.cmd quarkus:dev" -ForegroundColor Gray
Write-Host ""

Write-Host "OPCAO 2: Reiniciar aplicacao para ver logs" -ForegroundColor Green
Write-Host "  1. Pare a aplicacao atual (Ctrl+C no terminal)" -ForegroundColor White
Write-Host "  2. Execute: .\executar-app.ps1" -ForegroundColor White
Write-Host "  3. Observe os logs que aparecem" -ForegroundColor White
Write-Host ""

Write-Host "O QUE PROCURAR NOS LOGS:" -ForegroundColor Cyan
Write-Host "  SUCESSO (verde):" -ForegroundColor Green
Write-Host "    - 'Listening on: http://localhost:7071'" -ForegroundColor White
Write-Host "    - 'Function submitFeedback registered'" -ForegroundColor White
Write-Host "    - 'Function notifyAdmin registered'" -ForegroundColor White
Write-Host "    - 'Function weeklyReport registered'" -ForegroundColor White
Write-Host ""
Write-Host "  ERROS (vermelho):" -ForegroundColor Red
Write-Host "    - Qualquer mensagem com 'ERROR' ou 'Exception'" -ForegroundColor White
Write-Host "    - 'Failed to register function'" -ForegroundColor White
Write-Host "    - 'Connection refused' ou 'Connection timeout'" -ForegroundColor White
Write-Host "    - Erros relacionados a Cosmos DB, Service Bus ou Blob Storage" -ForegroundColor White
Write-Host ""

Write-Host "PROBLEMAS COMUNS E SOLUCOES:" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Problema: Endpoint 404" -ForegroundColor Yellow
Write-Host "  Solucao:" -ForegroundColor White
Write-Host "    1. Verifique se a aplicacao terminou de inicializar" -ForegroundColor Gray
Write-Host "    2. Verifique se ha erros nos logs" -ForegroundColor Gray
Write-Host "    3. Reinicie a aplicacao" -ForegroundColor Gray
Write-Host ""
Write-Host "  Problema: Funcoes nao registradas" -ForegroundColor Yellow
Write-Host "  Solucao:" -ForegroundColor White
Write-Host "    1. Verifique se todas as dependencias estao corretas no pom.xml" -ForegroundColor Gray
Write-Host "    2. Verifique se as classes estao anotadas corretamente com @FunctionName" -ForegroundColor Gray
Write-Host "    3. Limpe e recompile: .\mvnw.cmd clean compile" -ForegroundColor Gray
Write-Host ""
Write-Host "  Problema: Erro de conexao com Cosmos DB" -ForegroundColor Yellow
Write-Host "  Solucao:" -ForegroundColor White
Write-Host "    1. Verifique se o container Cosmos DB esta rodando" -ForegroundColor Gray
Write-Host "    2. Aguarde 30 segundos apos iniciar o container" -ForegroundColor Gray
Write-Host "    3. Verifique a connection string em application-local.properties" -ForegroundColor Gray
Write-Host ""

Write-Host "COMANDO PARA REINICIAR TUDO:" -ForegroundColor Cyan
Write-Host "  1. Pare a aplicacao (Ctrl+C)" -ForegroundColor White
Write-Host "  2. Execute: .\executar-app.ps1" -ForegroundColor Green
Write-Host "  3. Aguarde ver 'Listening on: http://localhost:7071'" -ForegroundColor White
Write-Host "  4. Execute: .\scripts\validar-fluxos.ps1" -ForegroundColor Green
Write-Host ""

