# Script para limpar processos antigos e reiniciar aplicacao
# Uso: .\scripts\limpar-e-reiniciar.ps1

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  LIMPEZA E REINICIO DA APLICACAO" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "1. PARANDO PROCESSOS JAVA ANTIGOS..." -ForegroundColor Cyan
Write-Host ""

$javaProcesses = Get-Process | Where-Object {$_.ProcessName -eq "java"}
if ($javaProcesses) {
    Write-Host "  Encontrados $($javaProcesses.Count) processos Java" -ForegroundColor Yellow
    Write-Host "  Parando processos Java recentes (ultimos 30 minutos)..." -ForegroundColor White
    
    $stopped = 0
    foreach ($proc in $javaProcesses) {
        $tempoRodando = (Get-Date) - $proc.StartTime
        if ($tempoRodando.TotalMinutes -lt 30) {
            try {
                Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
                $stopped++
                Write-Host "    Parado: PID $($proc.Id) (iniciado ha $([math]::Round($tempoRodando.TotalMinutes, 1)) minutos)" -ForegroundColor Gray
            } catch {
                # Ignora erros
            }
        }
    }
    
    if ($stopped -gt 0) {
        Write-Host "  $stopped processo(s) parado(s)" -ForegroundColor Green
        Start-Sleep -Seconds 2
    } else {
        Write-Host "  Nenhum processo recente para parar" -ForegroundColor Gray
    }
} else {
    Write-Host "  Nenhum processo Java encontrado" -ForegroundColor Gray
}

Write-Host ""

Write-Host "2. VERIFICANDO PORTA 7071..." -ForegroundColor Cyan
$porta = netstat -ano | findstr :7071 | Select-String "LISTENING"
if ($porta) {
    Write-Host "  Porta 7071 ainda em uso" -ForegroundColor Yellow
    Write-Host "  Aguardando 3 segundos..." -ForegroundColor White
    Start-Sleep -Seconds 3
} else {
    Write-Host "  Porta 7071 livre" -ForegroundColor Green
}

Write-Host ""

Write-Host "3. VERIFICANDO CONTAINERS DOCKER..." -ForegroundColor Cyan
$containers = docker ps --format "{{.Names}}" 2>$null
$cosmos = $containers | Where-Object { $_ -match "cosmos" }
$azurite = $containers | Where-Object { $_ -match "azurite" }

if (-not $cosmos) {
    Write-Host "  Cosmos DB nao esta rodando!" -ForegroundColor Red
    Write-Host "  Execute: docker-compose up -d" -ForegroundColor Yellow
} else {
    Write-Host "  Cosmos DB: OK" -ForegroundColor Green
}

if (-not $azurite) {
    Write-Host "  Azurite nao esta rodando!" -ForegroundColor Red
    Write-Host "  Execute: docker-compose up -d" -ForegroundColor Yellow
} else {
    Write-Host "  Azurite: OK" -ForegroundColor Green
}

Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  PRONTO PARA REINICIAR!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "AGORA EXECUTE:" -ForegroundColor Green
Write-Host "  .\executar-app.ps1" -ForegroundColor White
Write-Host ""

Write-Host "OBSERVE OS LOGS E PROCURE POR:" -ForegroundColor Cyan
Write-Host "  - 'Listening on: http://localhost:7071' (SUCESSO)" -ForegroundColor Green
Write-Host "  - 'Function submitFeedback registered' (SUCESSO)" -ForegroundColor Green
Write-Host "  - Qualquer mensagem de ERRO (PROBLEMA)" -ForegroundColor Red
Write-Host ""

Write-Host "Se aparecer algum erro, copie a mensagem e me envie!" -ForegroundColor Yellow
Write-Host ""

