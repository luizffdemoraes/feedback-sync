# Script para testar endpoints da Function App no Azure
# Uso: .\scripts\testar-endpoints.ps1 -FunctionAppName "feedback-function-<seu-nome>" -ResourceGroup "feedback-rg"

param(
    [Parameter(Mandatory=$true)]
    [string]$FunctionAppName,
    
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroup = "feedback-rg"
)

# Obter URL da Function App
Write-Host "🔍 Obtendo URL da Function App..." -ForegroundColor Yellow

try {
    $hostName = az functionapp show `
        --name $FunctionAppName `
        --resource-group $ResourceGroup `
        --query defaultHostName `
        --output tsv
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Erro ao obter URL da Function App" -ForegroundColor Red
        Write-Host "Verifique se o nome da Function App está correto" -ForegroundColor Yellow
        exit 1
    }
    
    $baseUrl = "https://$hostName"
    Write-Host "✅ URL obtida: $baseUrl" -ForegroundColor Green
} catch {
    Write-Host "❌ Erro: $_" -ForegroundColor Red
    Write-Host "Certifique-se de que o Azure CLI está instalado e logado" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "🧪 Testando Endpoints..." -ForegroundColor Green
Write-Host ""

# 1. Health Check
Write-Host "1️⃣ Health Check..." -ForegroundColor Cyan
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/health" -Method GET -ErrorAction Stop
    Write-Host "   ✅ Status: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Falhou: $_" -ForegroundColor Red
}
Write-Host ""

# 2. Enviar Feedback Normal
Write-Host "2️⃣ Enviando Feedback Normal..." -ForegroundColor Cyan
$feedbackBody = @{
    descricao = "Produto excelente, recomendo!"
    nota = 8
    urgencia = "LOW"
} | ConvertTo-Json

try {
    $feedback = Invoke-RestMethod -Uri "$baseUrl/api/avaliacao" `
        -Method POST `
        -Body $feedbackBody `
        -ContentType "application/json" `
        -ErrorAction Stop
    Write-Host "   ✅ Feedback enviado: ID=$($feedback.id)" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Falhou: $_" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "   Resposta: $responseBody" -ForegroundColor Yellow
    }
}
Write-Host ""

# 3. Enviar Feedback Crítico
Write-Host "3️⃣ Enviando Feedback Crítico (nota ≤ 3)..." -ForegroundColor Cyan
$criticalBody = @{
    descricao = "Problema crítico detectado no sistema!"
    nota = 2
    urgencia = "HIGH"
} | ConvertTo-Json

try {
    $critical = Invoke-RestMethod -Uri "$baseUrl/api/avaliacao" `
        -Method POST `
        -Body $criticalBody `
        -ContentType "application/json" `
        -ErrorAction Stop
    Write-Host "   ✅ Feedback crítico enviado: ID=$($critical.id)" -ForegroundColor Green
    Write-Host "   ℹ️  Este feedback será processado pela Function notifyAdmin" -ForegroundColor Yellow
} catch {
    Write-Host "   ❌ Falhou: $_" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "   Resposta: $responseBody" -ForegroundColor Yellow
    }
}
Write-Host ""

# 4. Gerar Relatório Semanal
Write-Host "4️⃣ Gerando Relatório Semanal..." -ForegroundColor Cyan
try {
    $report = Invoke-RestMethod -Uri "$baseUrl/api/relatorio" `
        -Method POST `
        -ContentType "application/json" `
        -ErrorAction Stop
    
    Write-Host "   ✅ Relatório gerado com sucesso!" -ForegroundColor Green
    Write-Host "   📊 Total de avaliações: $($report.total_avaliacoes)" -ForegroundColor Cyan
    Write-Host "   📊 Média: $($report.media_avaliacoes)" -ForegroundColor Cyan
    
    if ($report.avaliacoes_por_dia) {
        Write-Host "   📅 Avaliações por dia:" -ForegroundColor Cyan
        $report.avaliacoes_por_dia.PSObject.Properties | ForEach-Object {
            Write-Host "      $($_.Name): $($_.Value)" -ForegroundColor White
        }
    }
    
    if ($report.avaliacoes_por_urgencia) {
        Write-Host "   ⚠️  Avaliações por urgência:" -ForegroundColor Cyan
        $report.avaliacoes_por_urgencia.PSObject.Properties | ForEach-Object {
            Write-Host "      $($_.Name): $($_.Value)" -ForegroundColor White
        }
    }
    
    if ($report.report_url) {
        Write-Host "   🔗 URL do relatório: $($report.report_url)" -ForegroundColor Cyan
    } else {
        Write-Host "   ⚠️  Nenhum feedback encontrado no período" -ForegroundColor Yellow
    }
} catch {
    Write-Host "   ❌ Falhou: $_" -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $responseBody = $reader.ReadToEnd()
        Write-Host "   Resposta: $responseBody" -ForegroundColor Yellow
    }
}
Write-Host ""

Write-Host "✅ Testes concluídos!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 URLs dos Endpoints:" -ForegroundColor Yellow
Write-Host "   Health Check: $baseUrl/health" -ForegroundColor White
Write-Host "   Enviar Feedback: $baseUrl/api/avaliacao" -ForegroundColor White
Write-Host "   Gerar Relatório: $baseUrl/api/relatorio" -ForegroundColor White
Write-Host ""
Write-Host "📖 Para mais detalhes, consulte: GUIA_CHAMADAS_ENDPOINTS.md" -ForegroundColor Cyan

