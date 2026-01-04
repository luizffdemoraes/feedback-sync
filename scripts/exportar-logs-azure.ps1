# ============================================
# Script para Exportar Logs do Azure Functions
# ============================================
# Este script exporta logs do Azure Functions para arquivos locais
# para análise e troubleshooting
# ============================================

param(
    [string]$FunctionAppName = "feedback-function-prod",
    [string]$ResourceGroup = "feedback-rg",
    [string]$OutputDir = "logs-export",
    [switch]$IncludeApplicationLogs,
    [switch]$IncludeDockerLogs,
    [switch]$IncludeKuduLogs
)

$ErrorActionPreference = "Continue"

Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "  EXPORTAÇÃO DE LOGS DO AZURE" -ForegroundColor Cyan
Write-Host "============================================`n" -ForegroundColor Cyan

# Obter diretório do projeto
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$outputPath = Join-Path $projectRoot $OutputDir

# Criar diretório de saída
if (-not (Test-Path $outputPath)) {
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
    Write-Host "Diretório criado: $outputPath" -ForegroundColor Green
}

Write-Host "Function App: $FunctionAppName" -ForegroundColor Cyan
Write-Host "Resource Group: $ResourceGroup" -ForegroundColor Cyan
Write-Host "Diretório de saída: $outputPath`n" -ForegroundColor Cyan

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$exportedFiles = @()

# 1. Exportar logs via Azure CLI (Application Logs)
Write-Host "[1/5] Exportando Application Logs via Azure CLI..." -ForegroundColor Yellow
try {
    $appLogsFile = Join-Path $outputPath "application-logs-$timestamp.txt"
    
    # Tentar baixar logs
    Write-Host "   Tentando baixar logs..." -ForegroundColor Gray
    az webapp log download `
        --name $FunctionAppName `
        --resource-group $ResourceGroup `
        --log-file $appLogsFile `
        2>&1 | Out-Null
    
    if ((Test-Path $appLogsFile) -and ((Get-Item $appLogsFile).Length -gt 0)) {
        Write-Host "   [OK] Logs salvos em: $appLogsFile" -ForegroundColor Green
        $exportedFiles += $appLogsFile
    } else {
        Write-Host "   [AVISO] Não foi possível baixar logs via CLI (pode estar vazio ou não disponível)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "   [AVISO] Erro ao exportar logs via CLI: $($_.Exception.Message)" -ForegroundColor Yellow
}

# 2. Exportar logs via Kudu API (Docker Logs)
Write-Host "`n[2/5] Exportando Docker Logs via Kudu API..." -ForegroundColor Yellow
try {
    $kuduBase = "https://$FunctionAppName.scm.azurewebsites.net"
    $dockerLogsFile = Join-Path $outputPath "docker-logs-$timestamp.txt"
    
    Write-Host "   Acessando Kudu API..." -ForegroundColor Gray
    $headers = @{
        "Accept" = "application/json"
    }
    
    # Tentar obter logs Docker
    try {
        $dockerLogs = Invoke-RestMethod `
            -Uri "$kuduBase/api/logs/docker" `
            -Method GET `
            -Headers $headers `
            -UseBasicParsing `
            -TimeoutSec 30 `
            -ErrorAction Stop
        
        if ($dockerLogs) {
            $logContent = if ($dockerLogs -is [String]) { 
                $dockerLogs 
            } else { 
                $dockerLogs | ConvertTo-Json -Depth 10 
            }
            
            # Filtrar HTML de autenticação
            $cleanLogs = $logContent -split "`n" | Where-Object {
                $_ -notmatch "login\.microsoftonline\.com" -and
                $_ -notmatch "Sign in" -and
                $_ -notmatch "DOCTYPE html" -and
                $_ -notmatch "urlMsaSignUp" -and
                $_.Length -lt 1000
            }
            
            if ($cleanLogs.Count -gt 0) {
                $cleanLogs | Out-File -FilePath $dockerLogsFile -Encoding UTF8
                Write-Host "   [OK] Logs Docker salvos em: $dockerLogsFile" -ForegroundColor Green
                $exportedFiles += $dockerLogsFile
            } else {
                Write-Host "   [AVISO] Logs Docker vazios ou apenas HTML de autenticação" -ForegroundColor Yellow
            }
        }
    } catch {
        Write-Host "   [AVISO] Não foi possível acessar logs Docker via Kudu: $($_.Exception.Message)" -ForegroundColor Yellow
        Write-Host "   (Isso é normal se não houver autenticação configurada)" -ForegroundColor Gray
    }
} catch {
    Write-Host "   [AVISO] Erro ao exportar logs Docker: $($_.Exception.Message)" -ForegroundColor Yellow
}

# 3. Capturar Log Stream em tempo real (últimos logs)
Write-Host "`n[3/5] Capturando Log Stream..." -ForegroundColor Yellow
try {
    $logStreamFile = Join-Path $outputPath "log-stream-$timestamp.txt"
    
    Write-Host "   Capturando últimos logs do stream..." -ForegroundColor Gray
    
    # Nota: az webapp log tail não suporta --timeout e roda indefinidamente
    # Usamos Start-Job com timeout manual para capturar alguns logs
    $job = Start-Job -ScriptBlock {
        param($FunctionAppName, $ResourceGroup)
        az webapp log tail --name $FunctionAppName --resource-group $ResourceGroup 2>&1
    } -ArgumentList $FunctionAppName, $ResourceGroup
    
    # Aguardar alguns segundos para capturar logs
    Start-Sleep -Seconds 5
    
    # Parar o job e coletar resultados
    Stop-Job -Job $job -ErrorAction SilentlyContinue
    $logStream = Receive-Job -Job $job -ErrorAction SilentlyContinue
    Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
    
    if ($logStream -and $logStream.Count -gt 0) {
        $logStream | Out-File -FilePath $logStreamFile -Encoding UTF8
        Write-Host "   [OK] Log stream salvo em: $logStreamFile" -ForegroundColor Green
        $exportedFiles += $logStreamFile
    } else {
        Write-Host "   [AVISO] Log stream vazio ou não disponível (normal se não houver logs recentes)" -ForegroundColor Yellow
        # Criar arquivo vazio com nota
        "Log stream vazio ou não disponível no momento da exportação.`nUse 'az webapp log tail' para ver logs em tempo real." | Out-File -FilePath $logStreamFile -Encoding UTF8
    }
} catch {
    Write-Host "   [AVISO] Erro ao capturar log stream: $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "   (Isso é normal - az webapp log tail requer conexão ativa)" -ForegroundColor Gray
}

# 4. Exportar informações da Function App e configurações
Write-Host "`n[4/5] Exportando informações da Function App..." -ForegroundColor Yellow
try {
    $infoFile = Join-Path $outputPath "function-app-info-$timestamp.json"
    
    # Obter informações da Function App
    $functionAppInfo = az functionapp show `
        --name $FunctionAppName `
        --resource-group $ResourceGroup `
        --output json 2>&1
    
    # Obter configurações
    $appSettings = az functionapp config appsettings list `
        --name $FunctionAppName `
        --resource-group $ResourceGroup `
        --output json 2>&1
    
    # Obter funções registradas
    $functions = az functionapp function list `
        --name $FunctionAppName `
        --resource-group $ResourceGroup `
        --output json 2>&1
    
    $exportInfo = @{
        Timestamp = $timestamp
        FunctionAppName = $FunctionAppName
        ResourceGroup = $ResourceGroup
        FunctionAppInfo = $functionAppInfo | ConvertFrom-Json -ErrorAction SilentlyContinue
        AppSettings = $appSettings | ConvertFrom-Json -ErrorAction SilentlyContinue
        Functions = $functions | ConvertFrom-Json -ErrorAction SilentlyContinue
    }
    
    $exportInfo | ConvertTo-Json -Depth 10 | Out-File -FilePath $infoFile -Encoding UTF8
    Write-Host "   [OK] Informações salvas em: $infoFile" -ForegroundColor Green
    $exportedFiles += $infoFile
} catch {
    Write-Host "   [AVISO] Erro ao exportar informações: $($_.Exception.Message)" -ForegroundColor Yellow
}

# 5. Criar arquivo de resumo
Write-Host "`n[5/5] Criando resumo da exportação..." -ForegroundColor Yellow
try {
    $summaryFile = Join-Path $outputPath "RESUMO-$timestamp.txt"
    
    $summary = @"
============================================
RESUMO DA EXPORTAÇÃO DE LOGS
============================================
Data/Hora: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
Function App: $FunctionAppName
Resource Group: $ResourceGroup

Arquivos Exportados:
$(if ($exportedFiles.Count -gt 0) {
    $exportedFiles | ForEach-Object { "  - $(Split-Path -Leaf $_)" }
} else {
    "  Nenhum arquivo exportado"
})

============================================
PRÓXIMOS PASSOS
============================================
1. Revise os arquivos exportados no diretório: $outputPath
2. Procure por erros relacionados a:
   - Quarkus initialization
   - ClassNotFoundException
   - NoClassDefFoundError
   - Application startup failed
   - HTTP 404 errors
3. Compartilhe os arquivos relevantes para análise

============================================
COMANDOS ÚTEIS
============================================
# Ver logs em tempo real no Azure Portal:
https://portal.azure.com > Function App > $FunctionAppName > Log stream

# Ver logs via CLI:
az webapp log tail --name $FunctionAppName --resource-group $ResourceGroup

# Acessar Kudu (console de debug):
https://$FunctionAppName.scm.azurewebsites.net

============================================
"@
    
    $summary | Out-File -FilePath $summaryFile -Encoding UTF8
    Write-Host "   [OK] Resumo salvo em: $summaryFile" -ForegroundColor Green
    $exportedFiles += $summaryFile
} catch {
    Write-Host "   [AVISO] Erro ao criar resumo: $($_.Exception.Message)" -ForegroundColor Yellow
}

# Resumo final
Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "  EXPORTAÇÃO CONCLUÍDA" -ForegroundColor Cyan
Write-Host "============================================`n" -ForegroundColor Cyan

if ($exportedFiles.Count -gt 0) {
    Write-Host "[OK] $($exportedFiles.Count) arquivo(s) exportado(s):" -ForegroundColor Green
    Write-Host ""
    foreach ($file in $exportedFiles) {
        $fileInfo = Get-Item $file
        Write-Host "  $(Split-Path -Leaf $file)" -ForegroundColor White
        Write-Host "    Tamanho: $([math]::Round($fileInfo.Length / 1KB, 2)) KB" -ForegroundColor Gray
        Write-Host "    Caminho: $file" -ForegroundColor Gray
        Write-Host ""
    }
    
    Write-Host "Diretório completo: $outputPath" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Para compartilhar os logs:" -ForegroundColor Yellow
    Write-Host "1. Compacte o diretório: Compress-Archive -Path '$outputPath' -DestinationPath 'logs-$timestamp.zip'" -ForegroundColor Gray
    Write-Host "2. Ou envie os arquivos individuais mais relevantes" -ForegroundColor Gray
} else {
    Write-Host "[AVISO] Nenhum arquivo foi exportado com sucesso" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Possíveis causas:" -ForegroundColor Yellow
    Write-Host "  - Logs não disponíveis via CLI/Kudu" -ForegroundColor Gray
    Write-Host "  - Problemas de autenticação" -ForegroundColor Gray
    Write-Host "  - Function App não está gerando logs" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Recomendação: Acesse o Azure Portal para ver os logs em tempo real" -ForegroundColor Cyan
    Write-Host "  https://portal.azure.com > Function App > $FunctionAppName > Log stream" -ForegroundColor Gray
}

Write-Host ""
