# Script para ver os logs da aplicacao em execucao
# Uso: .\scripts\ver-logs.ps1

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  VERIFICANDO LOGS DA APLICACAO" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Verifica se a aplicacao esta rodando
$porta = netstat -ano | findstr :7071 | Select-String "LISTENING"
if ($porta) {
    $processId = ($porta -split '\s+')[-1]
    Write-Host "Aplicacao esta rodando na porta 7071" -ForegroundColor Green
    Write-Host "Processo ID: $pid" -ForegroundColor White
    Write-Host ""
    
    $processo = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($processo) {
        Write-Host "Processo encontrado:" -ForegroundColor Cyan
        Write-Host "  Nome: $($processo.ProcessName)" -ForegroundColor White
        Write-Host "  Iniciado em: $($processo.StartTime)" -ForegroundColor White
        Write-Host ""
    }
    
    Write-Host "IMPORTANTE: Os logs da aplicacao aparecem no terminal onde ela foi iniciada!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Para ver os logs:" -ForegroundColor Cyan
    Write-Host "  1. Encontre o terminal onde executou: .\mvnw.cmd quarkus:dev" -ForegroundColor White
    Write-Host "  2. Ou reinicie a aplicacao neste terminal para ver os logs" -ForegroundColor White
    Write-Host ""
    Write-Host "Para reiniciar a aplicacao:" -ForegroundColor Cyan
    Write-Host "  1. Pare o processo atual (Ctrl+C no terminal onde esta rodando)" -ForegroundColor White
    Write-Host "  2. Ou execute: Stop-Process -Id $processId -Force" -ForegroundColor White
    Write-Host "  3. Depois execute: .\executar-app.ps1" -ForegroundColor White
} else {
    Write-Host "Aplicacao nao esta rodando na porta 7071" -ForegroundColor Red
    Write-Host ""
    Write-Host "Para iniciar a aplicacao:" -ForegroundColor Cyan
    Write-Host "  .\executar-app.ps1" -ForegroundColor White
    Write-Host ""
    Write-Host "Ou manualmente:" -ForegroundColor Cyan
    Write-Host "  `$env:QUARKUS_PROFILE=`"local`"" -ForegroundColor White
    Write-Host "  .\mvnw.cmd quarkus:dev" -ForegroundColor White
}

Write-Host ""

