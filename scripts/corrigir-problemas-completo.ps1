# Script COMPLETO para corrigir problemas da aplicacao
# Este script faz uma limpeza profunda e reinicia tudo

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  CORRECAO COMPLETA DE PROBLEMAS" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan

# 1. PARAR TODOS OS PROCESSOS JAVA (FORCE)
Write-Host "1. Parando TODOS os processos Java..." -ForegroundColor Yellow
$javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($javaProcesses) {
    Write-Host "   Encontrados $($javaProcesses.Count) processos Java" -ForegroundColor White
    foreach ($proc in $javaProcesses) {
        try {
            Write-Host "   Parando processo $($proc.Id)..." -ForegroundColor Gray
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        } catch {
            Write-Host "   Erro ao parar processo $($proc.Id)" -ForegroundColor Red
        }
    }
    Start-Sleep -Seconds 3
    Write-Host "   Processos Java parados" -ForegroundColor Green
} else {
    Write-Host "   Nenhum processo Java encontrado" -ForegroundColor Green
}

# 2. LIBERAR PORTA 7071
Write-Host "`n2. Liberando porta 7071..." -ForegroundColor Yellow
$port7071 = Get-NetTCPConnection -LocalPort 7071 -ErrorAction SilentlyContinue
if ($port7071) {
    foreach ($conn in $port7071) {
        try {
            $pid = $conn.OwningProcess
            Write-Host "   Processo $pid usando porta 7071, parando..." -ForegroundColor Yellow
            Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
        } catch {
            Write-Host "   Erro ao parar processo na porta 7071" -ForegroundColor Red
        }
    }
    Start-Sleep -Seconds 2
}
Write-Host "   Porta 7071 liberada" -ForegroundColor Green

# 3. LIMPAR CACHE COMPLETO
Write-Host "`n3. Limpando cache completo..." -ForegroundColor Yellow
$dirsToClean = @("target", ".quarkus", ".mvn")
foreach ($dir in $dirsToClean) {
    if (Test-Path $dir) {
        Write-Host "   Removendo $dir..." -ForegroundColor Gray
        Remove-Item -Path $dir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
Write-Host "   Cache limpo" -ForegroundColor Green

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

# 5. COMPILAR PROJETO (SEM CACHE)
Write-Host "`n5. Compilando projeto (limpo)..." -ForegroundColor Yellow
Write-Host "   Isso pode levar alguns minutos..." -ForegroundColor Gray
$env:QUARKUS_PROFILE = "local"
$compileResult = & .\mvnw.cmd clean compile -U 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "   Compilacao concluida com sucesso" -ForegroundColor Green
} else {
    Write-Host "   ERRO na compilacao!" -ForegroundColor Red
    Write-Host "   Verifique os erros acima" -ForegroundColor Yellow
    exit 1
}

# 6. VERIFICAR SE AS CLASSES ESTAO CORRETAS
Write-Host "`n6. Verificando classes das funcoes..." -ForegroundColor Yellow
$functionFiles = @(
    "src\main\java\br\com\fiap\postech\feedback\infrastructure\handlers\SubmitFeedbackFunction.java",
    "src\main\java\br\com\fiap\postech\feedback\infrastructure\handlers\NotifyAdminFunction.java",
    "src\main\java\br\com\fiap\postech\feedback\infrastructure\handlers\WeeklyReportFunction.java"
)

foreach ($file in $functionFiles) {
    if (Test-Path $file) {
        $content = Get-Content $file -Raw
        if ($content -match "@FunctionName") {
            Write-Host "   OK: $($file.Split('\')[-1])" -ForegroundColor Green
        } else {
            Write-Host "   ERRO: $($file.Split('\')[-1]) sem @FunctionName" -ForegroundColor Red
        }
    } else {
        Write-Host "   ERRO: Arquivo nao encontrado: $file" -ForegroundColor Red
    }
}

# 7. INFORMACOES FINAIS
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  CORRECAO CONCLUIDA!" -ForegroundColor Green
Write-Host "========================================`n" -ForegroundColor Cyan

Write-Host "PROXIMOS PASSOS:" -ForegroundColor Yellow
Write-Host "  1. Execute: .\executar-app.ps1" -ForegroundColor White
Write-Host "  2. Aguarde COMPLETAMENTE a inicializacao" -ForegroundColor White
Write-Host "  3. Procure nos logs por:" -ForegroundColor White
Write-Host "     - 'Listening on: http://localhost:7071'" -ForegroundColor Cyan
Write-Host "     - 'Function submitFeedback registered'" -ForegroundColor Cyan
Write-Host "  4. Se as funcoes NAO aparecerem registradas:" -ForegroundColor Yellow
Write-Host "     - Verifique os logs para erros" -ForegroundColor White
Write-Host "     - Verifique se ha problemas de injeção de dependencia" -ForegroundColor White
Write-Host "`n" -ForegroundColor White

Write-Host "DIAGNOSTICO ADICIONAL:" -ForegroundColor Yellow
Write-Host "  Se o problema persistir, execute:" -ForegroundColor White
Write-Host "    .\scripts\verificar-logs.ps1" -ForegroundColor Cyan
Write-Host "`n" -ForegroundColor White

