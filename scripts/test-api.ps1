# Script PowerShell para testar a API local
# Uso: .\scripts\test-api.ps1

$API_URL = if ($env:API_URL) { $env:API_URL } else { "http://localhost:7071/api/avaliacao" }

Write-Host "🧪 Testando API de Feedback..." -ForegroundColor Cyan
Write-Host "URL: $API_URL"
Write-Host ""

# Teste 1: Feedback normal
Write-Host "📝 Teste 1: Criando feedback normal (nota 7)..." -ForegroundColor Yellow
$body1 = @{
    descricao = "Produto muito bom, recomendo!"
    nota = 7
    urgencia = "LOW"
} | ConvertTo-Json

try {
    $response1 = Invoke-RestMethod -Uri $API_URL -Method Post -Body $body1 -ContentType "application/json"
    Write-Host "Resposta: $($response1 | ConvertTo-Json)" -ForegroundColor Green
} catch {
    Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Resposta: $($_.ErrorDetails.Message)" -ForegroundColor Yellow
}
Write-Host ""

# Teste 2: Feedback crítico (deve disparar notificação)
Write-Host "🚨 Teste 2: Criando feedback crítico (nota 2)..." -ForegroundColor Yellow
$body2 = @{
    descricao = "Produto com defeito grave, precisa de atenção urgente!"
    nota = 2
    urgencia = "HIGH"
} | ConvertTo-Json

try {
    $response2 = Invoke-RestMethod -Uri $API_URL -Method Post -Body $body2 -ContentType "application/json"
    Write-Host "Resposta: $($response2 | ConvertTo-Json)" -ForegroundColor Green
} catch {
    Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Resposta: $($_.ErrorDetails.Message)" -ForegroundColor Yellow
}
Write-Host ""

# Teste 3: Validação - nota inválida
Write-Host "❌ Teste 3: Tentando criar feedback com nota inválida (15)..." -ForegroundColor Yellow
$body3 = @{
    descricao = "Teste de validação"
    nota = 15
} | ConvertTo-Json

try {
    $response3 = Invoke-RestMethod -Uri $API_URL -Method Post -Body $body3 -ContentType "application/json"
    Write-Host "Resposta: $($response3 | ConvertTo-Json)" -ForegroundColor Green
} catch {
    Write-Host "Erro esperado: $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "Resposta: $($_.ErrorDetails.Message)" -ForegroundColor Yellow
}
Write-Host ""

# Teste 4: Validação - campo obrigatório faltando
Write-Host "❌ Teste 4: Tentando criar feedback sem descrição..." -ForegroundColor Yellow
$body4 = @{
    nota = 5
} | ConvertTo-Json

try {
    $response4 = Invoke-RestMethod -Uri $API_URL -Method Post -Body $body4 -ContentType "application/json"
    Write-Host "Resposta: $($response4 | ConvertTo-Json)" -ForegroundColor Green
} catch {
    Write-Host "Erro esperado: $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "Resposta: $($_.ErrorDetails.Message)" -ForegroundColor Yellow
}
Write-Host ""

Write-Host "✅ Testes concluídos!" -ForegroundColor Green
Write-Host ""
Write-Host "💡 Verifique os logs da aplicação para ver as notificações e persistências." -ForegroundColor Cyan

