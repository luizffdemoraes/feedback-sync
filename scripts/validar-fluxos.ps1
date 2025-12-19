# Script PowerShell para validar todos os fluxos da aplicacao
# Uso: .\scripts\validar-fluxos.ps1

$API_URL = if ($env:API_URL) { $env:API_URL } else { "http://localhost:7071/api/avaliacao" }

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  VALIDACAO COMPLETA DOS FLUXOS" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Funcao para fazer requisicoes HTTP
function Invoke-ApiRequest {
    param(
        [string]$Url,
        [string]$Method = "POST",
        [object]$Body = $null,
        [string]$ContentType = "application/json"
    )
    
    try {
        if ($Body) {
            $jsonBody = $Body | ConvertTo-Json -Compress
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Body $jsonBody -ContentType $ContentType -ErrorAction Stop
        } else {
            $response = Invoke-RestMethod -Uri $Url -Method $Method -ErrorAction Stop
        }
        return @{ Success = $true; Data = $response }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $errorMessage = $_.ErrorDetails.Message
        return @{ Success = $false; StatusCode = $statusCode; Error = $errorMessage }
    }
}

# ============================================
# 1. VERIFICACAO DE SERVICOS
# ============================================
Write-Host "1. VERIFICANDO SERVICOS..." -ForegroundColor Cyan
Write-Host ""

Write-Host "Verificando Aplicacao..." -ForegroundColor Yellow -NoNewline
try {
    $response = Invoke-WebRequest -Uri "http://localhost:7071" -Method Get -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
    Write-Host " OK" -ForegroundColor Green
} catch {
    Write-Host " Indisponivel" -ForegroundColor Red
    Write-Host "   Erro: A aplicacao pode estar ainda inicializando" -ForegroundColor Yellow
}

# ============================================
# 2. TESTE DE FEEDBACK NORMAL
# ============================================
Write-Host ""
Write-Host "2. TESTANDO FLUXO DE FEEDBACK NORMAL..." -ForegroundColor Cyan
Write-Host ""

$feedbackNormal = @{
    descricao = "Produto muito bom, recomendo!"
    nota = 8
    urgencia = "LOW"
}

Write-Host "Enviando feedback normal (nota 8)..." -ForegroundColor Yellow
$result = Invoke-ApiRequest -Url $API_URL -Body $feedbackNormal

if ($result.Success) {
    Write-Host "Feedback criado com sucesso!" -ForegroundColor Green
    Write-Host "   ID: $($result.Data.id)" -ForegroundColor White
    Write-Host "   Status: $($result.Data.status)" -ForegroundColor White
    $feedbackId1 = $result.Data.id
} else {
    Write-Host "Erro ao criar feedback: $($result.Error)" -ForegroundColor Red
    if ($result.StatusCode) {
        Write-Host "   Status Code: $($result.StatusCode)" -ForegroundColor Red
    }
}

Start-Sleep -Seconds 2

# ============================================
# 3. TESTE DE FEEDBACK CRITICO (NOTIFICACAO)
# ============================================
Write-Host ""
Write-Host "3. TESTANDO FLUXO DE NOTIFICACAO (Feedback Critico)..." -ForegroundColor Cyan
Write-Host ""

$feedbackCritico = @{
    descricao = "Produto com defeito grave, precisa de atencao urgente!"
    nota = 2
    urgencia = "HIGH"
}

Write-Host "Enviando feedback critico (nota 2)..." -ForegroundColor Yellow
Write-Host "   Isso deve disparar uma notificacao no Service Bus..." -ForegroundColor Gray

$result = Invoke-ApiRequest -Url $API_URL -Body $feedbackCritico

if ($result.Success) {
    Write-Host "Feedback critico criado com sucesso!" -ForegroundColor Green
    Write-Host "   ID: $($result.Data.id)" -ForegroundColor White
    Write-Host "   Status: $($result.Data.status)" -ForegroundColor White
    $feedbackId2 = $result.Data.id
    
    Write-Host ""
    Write-Host "Aguardando processamento da notificacao (5 segundos)..." -ForegroundColor Yellow
    Start-Sleep -Seconds 5
    
    Write-Host ""
    Write-Host "Verifique os logs da aplicacao para confirmar:" -ForegroundColor Cyan
    Write-Host "   - Mensagem enviada ao Service Bus" -ForegroundColor White
    Write-Host "   - NotifyAdminFunction processou a mensagem" -ForegroundColor White
} else {
    Write-Host "Erro ao criar feedback critico: $($result.Error)" -ForegroundColor Red
}

Start-Sleep -Seconds 2

# ============================================
# 4. TESTE DE VALIDACAO
# ============================================
Write-Host ""
Write-Host "4. TESTANDO VALIDACOES..." -ForegroundColor Cyan
Write-Host ""

# Teste: Nota invalida
Write-Host "Teste 1: Nota invalida (15)..." -ForegroundColor Yellow
$feedbackInvalido = @{
    descricao = "Teste de validacao"
    nota = 15
}
$result = Invoke-ApiRequest -Url $API_URL -Body $feedbackInvalido
if (-not $result.Success) {
    Write-Host "Validacao funcionando (nota deve estar entre 0-10)" -ForegroundColor Green
} else {
    Write-Host "Validacao nao funcionou como esperado" -ForegroundColor Yellow
}

Start-Sleep -Seconds 1

# Teste: Campo obrigatorio faltando
Write-Host ""
Write-Host "Teste 2: Campo obrigatorio faltando (sem descricao)..." -ForegroundColor Yellow
$feedbackSemDescricao = @{
    nota = 5
}
$result = Invoke-ApiRequest -Url $API_URL -Body $feedbackSemDescricao
if (-not $result.Success) {
    Write-Host "Validacao funcionando (descricao e obrigatoria)" -ForegroundColor Green
} else {
    Write-Host "Validacao nao funcionou como esperado" -ForegroundColor Yellow
}

# ============================================
# 5. VERIFICACAO NO COSMOS DB
# ============================================
Write-Host ""
Write-Host "5. VERIFICANDO DADOS NO COSMOS DB..." -ForegroundColor Cyan
Write-Host ""

Write-Host "Para verificar os dados salvos:" -ForegroundColor Cyan
Write-Host "   1. Acesse: https://localhost:8081/_explorer/index.html" -ForegroundColor White
Write-Host "   2. Database: feedback-db" -ForegroundColor White
Write-Host "   3. Container: feedbacks" -ForegroundColor White
Write-Host "   4. Verifique se os feedbacks foram salvos" -ForegroundColor White

# ============================================
# 6. VERIFICACAO NO SERVICE BUS
# ============================================
Write-Host ""
Write-Host "6. VERIFICANDO NOTIFICACOES NO SERVICE BUS..." -ForegroundColor Cyan
Write-Host ""

Write-Host "Para verificar as mensagens no Service Bus:" -ForegroundColor Cyan
Write-Host "   1. Verifique os logs da aplicacao" -ForegroundColor White
Write-Host "   2. Procure por: 'Notificacao enviada' ou 'NotifyAdminFunction'" -ForegroundColor White
Write-Host "   3. O feedback critico (nota <= 3) deve ter disparado notificacao" -ForegroundColor White

# ============================================
# 7. TESTE DE RELATORIO SEMANAL
# ============================================
Write-Host ""
Write-Host "7. TESTANDO GERACAO DE RELATORIO SEMANAL..." -ForegroundColor Cyan
Write-Host ""

Write-Host "O relatorio semanal e gerado automaticamente via Timer Trigger" -ForegroundColor Cyan
Write-Host "   Configurado para executar a cada 5 minutos (modo local)" -ForegroundColor White
Write-Host ""
Write-Host "   Para testar manualmente:" -ForegroundColor Yellow
Write-Host "   1. Aguarde o proximo trigger (maximo 5 minutos)" -ForegroundColor White
Write-Host "   2. Ou verifique os logs para ver quando foi executado" -ForegroundColor White
Write-Host "   3. Procure por: 'WeeklyReportFunction' ou 'Relatorio gerado'" -ForegroundColor White

Write-Host ""
Write-Host "Para verificar o relatorio no Blob Storage:" -ForegroundColor Cyan
Write-Host "   1. Use Azure Storage Explorer conectado ao Azurite" -ForegroundColor White
Write-Host "   2. Ou verifique os logs da aplicacao" -ForegroundColor White

# ============================================
# RESUMO
# ============================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RESUMO DA VALIDACAO" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Fluxos testados:" -ForegroundColor Green
Write-Host "   - Criacao de feedback normal" -ForegroundColor White
Write-Host "   - Criacao de feedback critico (notificacao)" -ForegroundColor White
Write-Host "   - Validacoes de entrada" -ForegroundColor White

Write-Host ""
Write-Host "Proximos passos:" -ForegroundColor Yellow
Write-Host "   1. Verifique os logs da aplicacao" -ForegroundColor White
Write-Host "   2. Confirme que a notificacao foi processada" -ForegroundColor White
Write-Host "   3. Verifique os dados no Cosmos DB" -ForegroundColor White
Write-Host "   4. Aguarde o relatorio semanal ou verifique logs" -ForegroundColor White
Write-Host "   5. Verifique o relatorio no Blob Storage" -ForegroundColor White

Write-Host ""
Write-Host "Dica: Os logs da aplicacao mostrarao todos os eventos!" -ForegroundColor Cyan
Write-Host ""
