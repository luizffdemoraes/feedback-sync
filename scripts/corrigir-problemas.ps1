# Script para corrigir problemas comuns da aplicacao
# Este script:
# 1. Para todos os processos Java relacionados
# 2. Limpa cache do Maven
# 3. Recompila o projeto
# 4. Reinicia a aplicacao corretamente

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  CORRECAO DE PROBLEMAS" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan

# 1. PARAR TODOS OS PROCESSOS JAVA
Write-Host "1. Parando processos Java..." -ForegroundColor Yellow
$javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($javaProcesses) {
    Write-Host "   Encontrados $($javaProcesses.Count) processos Java" -ForegroundColor White
    $javaProcesses | ForEach-Object {
        try {
            Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
            Write-Host "   Processo $($_.Id) parado" -ForegroundColor Gray
        } catch {
            Write-Host "   Erro ao parar processo $($_.Id)" -ForegroundColor Red
        }
    }
    Start-Sleep -Seconds 2
    Write-Host "   Processos Java parados" -ForegroundColor Green
} else {
    Write-Host "   Nenhum processo Java encontrado" -ForegroundColor Green
}

# 2. VERIFICAR PORTA 7071
Write-Host "`n2. Verificando porta 7071..." -ForegroundColor Yellow
$port7071 = Get-NetTCPConnection -LocalPort 7071 -ErrorAction SilentlyContinue
if ($port7071) {
    Write-Host "   Porta 7071 ainda em uso" -ForegroundColor Yellow
    Write-Host "   Aguardando liberacao..." -ForegroundColor White
    Start-Sleep -Seconds 3
} else {
    Write-Host "   Porta 7071 livre" -ForegroundColor Green
}

# 3. LIMPAR CACHE DO MAVEN
Write-Host "`n3. Limpando cache do Maven..." -ForegroundColor Yellow
if (Test-Path "target") {
    Remove-Item -Path "target" -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "   Diretorio target removido" -ForegroundColor Green
} else {
    Write-Host "   Diretorio target nao existe" -ForegroundColor Gray
}

# 4. VERIFICAR DOCKER
Write-Host "`n4. Verificando containers Docker..." -ForegroundColor Yellow
try {
    $containers = docker ps --format "{{.Names}}" 2>$null
    $cosmos = $containers | Where-Object { $_ -match "cosmos" }
    $azurite = $containers | Where-Object { $_ -match "azurite" }
    $servicebus = $containers | Where-Object { $_ -match "servicebus" }
    
    if ($cosmos -and $azurite -and $servicebus) {
        Write-Host "   Todos os containers estao rodando" -ForegroundColor Green
    } else {
        Write-Host "   ATENCAO: Alguns containers nao estao rodando!" -ForegroundColor Yellow
        Write-Host "   Execute: docker-compose up -d" -ForegroundColor White
    }
} catch {
    Write-Host "   Nao foi possivel verificar containers Docker" -ForegroundColor Yellow
}

# 5. COMPILAR PROJETO
Write-Host "`n5. Compilando projeto..." -ForegroundColor Yellow
Write-Host "   Isso pode levar alguns minutos..." -ForegroundColor Gray
$env:QUARKUS_PROFILE = "local"
$compileResult = & .\mvnw.cmd clean compile -q 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "   Compilacao concluida com sucesso" -ForegroundColor Green
} else {
    Write-Host "   ERRO na compilacao!" -ForegroundColor Red
    Write-Host "   Verifique os erros acima" -ForegroundColor Yellow
    exit 1
}

# 6. INFORMACOES FINAIS
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  CORRECAO CONCLUIDA!" -ForegroundColor Green
Write-Host "========================================`n" -ForegroundColor Cyan

Write-Host "PROXIMOS PASSOS:" -ForegroundColor Yellow
Write-Host "  1. Execute: .\executar-app.ps1" -ForegroundColor White
Write-Host "  2. Aguarde ver: 'Listening on: http://localhost:7071'" -ForegroundColor White
Write-Host "  3. Teste o endpoint: http://localhost:7071/api/avaliacao" -ForegroundColor White
Write-Host "`n" -ForegroundColor White

Write-Host "NOTA: O endpoint correto e:" -ForegroundColor Cyan
Write-Host "  POST http://localhost:7071/api/avaliacao" -ForegroundColor Green
Write-Host "  (nao apenas /avaliacao)" -ForegroundColor Gray
Write-Host "`n" -ForegroundColor White

