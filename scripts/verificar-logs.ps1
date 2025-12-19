# Script para verificar logs e diagnosticar problemas da aplicacao
# Uso: .\scripts\verificar-logs.ps1

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  DIAGNOSTICO DE LOGS E FUNCOES" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Verifica se a aplicacao esta rodando
Write-Host "1. VERIFICANDO SE A APLICACAO ESTA RODANDO..." -ForegroundColor Cyan
Write-Host ""

$porta = netstat -ano | findstr :7071 | Select-String "LISTENING"
if ($porta) {
    $processId = ($porta -split '\s+')[-1]
    Write-Host "  Aplicacao esta rodando na porta 7071" -ForegroundColor Green
    Write-Host "  Processo ID: $processId" -ForegroundColor White
    
    $processo = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($processo) {
        Write-Host "  Processo: $($processo.ProcessName)" -ForegroundColor White
        Write-Host "  Iniciado em: $($processo.StartTime)" -ForegroundColor White
    }
} else {
    Write-Host "  Aplicacao NAO esta rodando na porta 7071" -ForegroundColor Red
    Write-Host ""
    Write-Host "  SOLUCAO: Execute a aplicacao primeiro:" -ForegroundColor Yellow
    Write-Host "    .\executar-app.ps1" -ForegroundColor White
    Write-Host ""
    exit 1
}

Write-Host ""

# Verifica se o endpoint esta respondendo
Write-Host "2. TESTANDO ENDPOINT /api/avaliacao..." -ForegroundColor Cyan
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri "http://localhost:7071/api/avaliacao" -Method Get -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
    Write-Host "  Endpoint esta respondendo!" -ForegroundColor Green
    Write-Host "  Status: $($response.StatusCode)" -ForegroundColor White
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 404) {
        Write-Host "  ERRO: Endpoint retornou 404 (Nao encontrado)" -ForegroundColor Red
        Write-Host "  Isso indica que a funcao Azure Functions nao foi registrada!" -ForegroundColor Yellow
    } elseif ($statusCode -eq 405) {
        Write-Host "  Endpoint existe mas metodo GET nao permitido (esperado para POST)" -ForegroundColor Yellow
        Write-Host "  Isso e normal - o endpoint so aceita POST" -ForegroundColor Green
    } else {
        Write-Host "  Erro ao acessar endpoint: $statusCode" -ForegroundColor Red
    }
}

Write-Host ""

# Verifica se os containers Docker estao rodando
Write-Host "3. VERIFICANDO CONTAINERS DOCKER..." -ForegroundColor Cyan
Write-Host ""

$containers = docker ps --format "{{.Names}}" 2>$null
if ($containers) {
    $cosmos = $containers | Where-Object { $_ -match "cosmos" }
    $azurite = $containers | Where-Object { $_ -match "azurite" }
    $servicebus = $containers | Where-Object { $_ -match "servicebus" }
    
    if ($cosmos) {
        Write-Host "  Cosmos DB: Rodando ($cosmos)" -ForegroundColor Green
    } else {
        Write-Host "  Cosmos DB: NAO esta rodando" -ForegroundColor Red
    }
    
    if ($azurite) {
        Write-Host "  Azurite: Rodando ($azurite)" -ForegroundColor Green
    } else {
        Write-Host "  Azurite: NAO esta rodando" -ForegroundColor Red
    }
    
    if ($servicebus) {
        Write-Host "  Service Bus: Rodando ($servicebus)" -ForegroundColor Green
    } else {
        Write-Host "  Service Bus: NAO esta rodando" -ForegroundColor Red
    }
} else {
    Write-Host "  Nenhum container Docker encontrado" -ForegroundColor Red
    Write-Host "  Execute: docker-compose up -d" -ForegroundColor Yellow
}

Write-Host ""

# Instrucoes para verificar logs
Write-Host "4. COMO VERIFICAR OS LOGS DA APLICACAO..." -ForegroundColor Cyan
Write-Host ""

Write-Host "  IMPORTANTE: Os logs aparecem no terminal onde a aplicacao foi iniciada!" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Procure por:" -ForegroundColor Cyan
Write-Host "    - 'Listening on: http://localhost:7071'" -ForegroundColor White
Write-Host "    - 'Function submitFeedback registered'" -ForegroundColor White
Write-Host "    - 'Function notifyAdmin registered'" -ForegroundColor White
Write-Host "    - 'Function weeklyReport registered'" -ForegroundColor White
Write-Host "    - Qualquer mensagem de ERRO em vermelho" -ForegroundColor Red
Write-Host ""

Write-Host "  Se voce nao ve os logs:" -ForegroundColor Yellow
Write-Host "    1. Encontre o terminal onde executou: .\executar-app.ps1" -ForegroundColor White
Write-Host "    2. Ou reinicie a aplicacao neste terminal para ver os logs" -ForegroundColor White
Write-Host ""

# Verifica arquivos de log (se existirem)
Write-Host "5. VERIFICANDO ARQUIVOS DE LOG..." -ForegroundColor Cyan
Write-Host ""

$logFiles = Get-ChildItem -Path "." -Filter "*.log" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 5
if ($logFiles) {
    Write-Host "  Arquivos de log encontrados:" -ForegroundColor Green
    foreach ($log in $logFiles) {
        Write-Host "    - $($log.FullName)" -ForegroundColor White
    }
} else {
    Write-Host "  Nenhum arquivo de log encontrado" -ForegroundColor Gray
    Write-Host "  (Logs aparecem apenas no terminal da aplicacao)" -ForegroundColor Gray
}

Write-Host ""

# Resumo e recomendacoes
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RESUMO E RECOMENDACOES" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "SE O ENDPOINT RETORNA 404:" -ForegroundColor Yellow
Write-Host "  1. Verifique os logs da aplicacao para erros" -ForegroundColor White
Write-Host "  2. Verifique se as funcoes foram registradas" -ForegroundColor White
Write-Host "  3. Reinicie a aplicacao se necessario" -ForegroundColor White
Write-Host "  4. Aguarde a inicializacao completa" -ForegroundColor White
Write-Host ""

Write-Host "COMANDOS UTEIS:" -ForegroundColor Cyan
Write-Host "  - Ver logs da aplicacao: Terminal onde executou .\executar-app.ps1" -ForegroundColor White
Write-Host "  - Reiniciar aplicacao: .\executar-app.ps1" -ForegroundColor White
Write-Host "  - Ver logs Docker: docker-compose logs -f" -ForegroundColor White
Write-Host "  - Testar endpoint: Invoke-RestMethod -Uri 'http://localhost:7071/api/avaliacao' -Method Post -Body '{\"descricao\":\"Teste\",\"nota\":8}' -ContentType 'application/json'" -ForegroundColor White
Write-Host ""

