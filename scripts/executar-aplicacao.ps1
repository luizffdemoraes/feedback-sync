# Script para executar a aplicacao Quarkus
# Este script mostra todos os logs em tempo real

Write-Host "Iniciando aplicacao Quarkus..." -ForegroundColor Cyan
Write-Host ""
Write-Host "Verificando pre-requisitos..." -ForegroundColor Yellow

# Verifica se os containers estao rodando
Write-Host "Verificando containers Docker..." -ForegroundColor Yellow
try {
    $containerNames = docker ps --format "{{.Names}}" 2>$null
    $azurite = $containerNames | Where-Object { $_ -match 'azurite' }
    
    if (-not $azurite) {
        Write-Host "   [X] Azurite nao esta rodando (Table Storage, Blob Storage)" -ForegroundColor Red
        Write-Host ""
        Write-Host "ATENCAO: Container Docker nao esta rodando!" -ForegroundColor Yellow
        Write-Host "   Execute: docker compose up -d" -ForegroundColor White
        Write-Host ""
        Write-Host "Deseja continuar mesmo assim? (S/N)" -ForegroundColor Yellow
        $continuar = Read-Host
        if ($continuar -ne "S" -and $continuar -ne "s") {
            Write-Host "Execucao cancelada" -ForegroundColor Yellow
            exit 0
        }
        Write-Host ""
    } else {
        Write-Host "   [OK] Azurite: rodando (Table Storage, Blob Storage)" -ForegroundColor Green
        Write-Host ""
        Write-Host "OK: Container Docker esta rodando" -ForegroundColor Green
        Write-Host ""
    }
} catch {
    Write-Host "ATENCAO: Nao foi possivel verificar containers Docker" -ForegroundColor Yellow
    Write-Host "   Certifique-se de que o Docker esta rodando" -ForegroundColor White
    Write-Host ""
}

# Verificar configurações do Mailtrap
Write-Host "Verificando configurações do Mailtrap..." -ForegroundColor Yellow
$mailtrapToken = $env:MAILTRAP_API_TOKEN
$adminEmail = $env:ADMIN_EMAIL
$mailtrapInboxId = $env:MAILTRAP_INBOX_ID

if ([string]::IsNullOrWhiteSpace($mailtrapToken)) {
    Write-Host "   [X] MAILTRAP_API_TOKEN não configurado" -ForegroundColor Red
    Write-Host "   Configure: `$env:MAILTRAP_API_TOKEN = 'seu-token'" -ForegroundColor Gray
    Write-Host ""
    Write-Host "⚠️  AVISO: Emails não serão enviados sem MAILTRAP_API_TOKEN" -ForegroundColor Yellow
    Write-Host "   Deseja continuar mesmo assim? (S/N)" -ForegroundColor Yellow
    $continuar = Read-Host
    if ($continuar -ne "S" -and $continuar -ne "s") {
        Write-Host "Execução cancelada" -ForegroundColor Yellow
        exit 0
    }
} else {
    Write-Host "   [OK] MAILTRAP_API_TOKEN configurado" -ForegroundColor Green
}

if ([string]::IsNullOrWhiteSpace($adminEmail)) {
    Write-Host "   [X] ADMIN_EMAIL não configurado" -ForegroundColor Red
    Write-Host "   Configure: `$env:ADMIN_EMAIL = 'seu-email@exemplo.com'" -ForegroundColor Gray
    Write-Host ""
    Write-Host "⚠️  AVISO: Emails não serão enviados sem ADMIN_EMAIL" -ForegroundColor Yellow
} else {
    Write-Host "   [OK] ADMIN_EMAIL configurado: $adminEmail" -ForegroundColor Green
}

if ([string]::IsNullOrWhiteSpace($mailtrapInboxId)) {
    Write-Host "   [X] MAILTRAP_INBOX_ID não configurado" -ForegroundColor Red
    Write-Host "   Configure: `$env:MAILTRAP_INBOX_ID = 'seu-inbox-id'" -ForegroundColor Gray
    Write-Host ""
    Write-Host "⚠️  AVISO: Emails não serão enviados sem MAILTRAP_INBOX_ID" -ForegroundColor Yellow
} else {
    Write-Host "   [OK] MAILTRAP_INBOX_ID configurado: $mailtrapInboxId" -ForegroundColor Green
}

Write-Host ""

Write-Host "Compilando e iniciando aplicacao..." -ForegroundColor Cyan
Write-Host "   (Isso pode levar 1-2 minutos na primeira vez)" -ForegroundColor Gray
Write-Host ""
Write-Host "DICA: Quando ver 'Listening on: http://localhost:7071', a aplicacao estara pronta!" -ForegroundColor Green
Write-Host ""

# Mudar para o diretório raiz do projeto (onde está o mvnw.cmd)
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptPath
Set-Location $projectRoot

# Configurar variáveis de ambiente para o Quarkus
# O Quarkus lê do application-local.properties que usa ${MAILTRAP_API_TOKEN:}
# Garantir que as variáveis estejam disponíveis
if (-not [string]::IsNullOrWhiteSpace($mailtrapToken)) {
    $env:MAILTRAP_API_TOKEN = $mailtrapToken
}
if (-not [string]::IsNullOrWhiteSpace($adminEmail)) {
    $env:ADMIN_EMAIL = $adminEmail
}
if (-not [string]::IsNullOrWhiteSpace($mailtrapInboxId)) {
    $env:MAILTRAP_INBOX_ID = $mailtrapInboxId
}

# Executa a aplicacao (nao em background para ver os logs)
# No PowerShell, o -D precisa estar entre aspas
$env:QUARKUS_PROFILE = 'local'
.\mvnw.cmd quarkus:dev
