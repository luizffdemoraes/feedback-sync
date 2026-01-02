# Script para verificar se as variaveis de ambiente estao configuradas corretamente

Write-Host "Verificando Variaveis de Ambiente" -ForegroundColor Cyan
Write-Host ""

# Verificar MAILTRAP_API_TOKEN
Write-Host "1. MAILTRAP_API_TOKEN:" -ForegroundColor Yellow
$mailtrapToken = $env:MAILTRAP_API_TOKEN
if ([string]::IsNullOrWhiteSpace($mailtrapToken)) {
    Write-Host "   [X] NAO configurado" -ForegroundColor Red
    Write-Host '   Configure com: $env:MAILTRAP_API_TOKEN = "seu-token"' -ForegroundColor Gray
} else {
    $tokenPreview = if ($mailtrapToken.Length -gt 8) { 
        $mailtrapToken.Substring(0, 8) + "..." 
    } else { 
        $mailtrapToken 
    }
    Write-Host "   [OK] Configurado: $tokenPreview" -ForegroundColor Green
    Write-Host "   Tamanho: $($mailtrapToken.Length) caracteres" -ForegroundColor Gray
}

Write-Host ""

# Verificar ADMIN_EMAIL
Write-Host "2. ADMIN_EMAIL:" -ForegroundColor Yellow
$adminEmail = $env:ADMIN_EMAIL
if ([string]::IsNullOrWhiteSpace($adminEmail)) {
    Write-Host "   [X] NAO configurado" -ForegroundColor Red
    Write-Host '   Configure com: $env:ADMIN_EMAIL = "seu-email@exemplo.com"' -ForegroundColor Gray
} else {
    Write-Host "   [OK] Configurado: $adminEmail" -ForegroundColor Green
}

Write-Host ""

# Verificar se o arquivo local.settings.json existe
Write-Host "3. Arquivo local.settings.json:" -ForegroundColor Yellow
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptPath
$localSettingsPath = Join-Path $projectRoot "src\main\resources\local.settings.json"

if (Test-Path $localSettingsPath) {
    Write-Host "   [OK] Arquivo encontrado: $localSettingsPath" -ForegroundColor Green
    
    # Ler e mostrar valores atuais no arquivo fonte
    $localSettings = Get-Content -Path $localSettingsPath -Raw | ConvertFrom-Json
    $fileToken = $localSettings.Values."mailtrap.api-token"
    $fileEmail = $localSettings.Values."admin.email"
    
    Write-Host "   Valores no arquivo fonte (template):" -ForegroundColor Gray
    $tokenStatus = if ([string]::IsNullOrWhiteSpace($fileToken)) { 
        "(vazio - correto para template)" 
    } else { 
        "TEM VALOR" 
    }
    $tokenColor = if ([string]::IsNullOrWhiteSpace($fileToken)) { 
        "Gray" 
    } else { 
        "Yellow" 
    }
    Write-Host "   - mailtrap.api-token: $tokenStatus" -ForegroundColor $tokenColor
    
    $emailStatus = if ([string]::IsNullOrWhiteSpace($fileEmail)) { 
        "(vazio - correto para template)" 
    } else { 
        $fileEmail 
    }
    $emailColor = if ([string]::IsNullOrWhiteSpace($fileEmail)) { 
        "Gray" 
    } else { 
        "Yellow" 
    }
    Write-Host "   - admin.email: $emailStatus" -ForegroundColor $emailColor
    
    Write-Host ""
    Write-Host "   NOTA: O arquivo fonte deve ter valores vazios (e um template)." -ForegroundColor Cyan
    Write-Host "      O script executar-azure-functions-local.ps1 atualiza automaticamente" -ForegroundColor Cyan
    Write-Host "      a copia no diretorio target\azure-functions\feedback-service-app\local.settings.json" -ForegroundColor Cyan
} else {
    Write-Host "   [X] Arquivo nao encontrado: $localSettingsPath" -ForegroundColor Red
}

Write-Host ""

# Verificar se existe o arquivo de destino (apos compilacao)
Write-Host "4. Arquivo local.settings.json de destino (apos compilacao):" -ForegroundColor Yellow
$functionsDir = Join-Path $projectRoot "target\azure-functions\feedback-service-app"
$localSettingsTarget = Join-Path $functionsDir "local.settings.json"

if (Test-Path $localSettingsTarget) {
    Write-Host "   [OK] Arquivo encontrado: $localSettingsTarget" -ForegroundColor Green
    
    # Ler e mostrar valores no arquivo de destino
    $targetSettings = Get-Content -Path $localSettingsTarget -Raw | ConvertFrom-Json
    $targetToken = $targetSettings.Values."mailtrap.api-token"
    $targetEmail = $targetSettings.Values."admin.email"
    
    Write-Host "   Valores no arquivo de destino:" -ForegroundColor Gray
    if ([string]::IsNullOrWhiteSpace($targetToken)) {
        Write-Host "   - mailtrap.api-token: [VAZIO] Precisa ser atualizado pelo script" -ForegroundColor Yellow
    } else {
        $tokenPreview = if ($targetToken.Length -gt 8) { 
            $targetToken.Substring(0, 8) + "..." 
        } else { 
            $targetToken 
        }
        Write-Host "   - mailtrap.api-token: $tokenPreview [OK]" -ForegroundColor Green
    }
    
    if ([string]::IsNullOrWhiteSpace($targetEmail)) {
        Write-Host "   - admin.email: [VAZIO] Precisa ser atualizado pelo script" -ForegroundColor Yellow
    } else {
        Write-Host "   - admin.email: $targetEmail [OK]" -ForegroundColor Green
    }
    
    # Comparar com variaveis de ambiente
    Write-Host ""
    Write-Host "   Comparacao com variaveis de ambiente:" -ForegroundColor Gray
    if ($targetToken -eq $mailtrapToken -and $targetEmail -eq $adminEmail) {
        Write-Host "   [OK] Valores sincronizados corretamente!" -ForegroundColor Green
    } else {
        Write-Host "   [AVISO] Valores NAO sincronizados" -ForegroundColor Yellow
        Write-Host "      Execute: .\scripts\executar-azure-functions-local.ps1" -ForegroundColor Yellow
        Write-Host "      para atualizar o arquivo de destino" -ForegroundColor Yellow
    }
} else {
    Write-Host "   [AVISO] Arquivo nao encontrado (normal se ainda nao compilou)" -ForegroundColor Yellow
    Write-Host "      Execute: .\scripts\executar-azure-functions-local.ps1" -ForegroundColor Yellow
    Write-Host "      para compilar e criar o arquivo" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Gray
Write-Host ""

# Resumo final
if (-not [string]::IsNullOrWhiteSpace($mailtrapToken) -and -not [string]::IsNullOrWhiteSpace($adminEmail)) {
    Write-Host "[OK] Todas as variaveis de ambiente estao configuradas!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Proximo passo:" -ForegroundColor Cyan
    Write-Host "   Execute: .\scripts\executar-azure-functions-local.ps1" -ForegroundColor White
} else {
    Write-Host "[ERRO] Configure as variaveis de ambiente antes de continuar:" -ForegroundColor Red
    Write-Host ""
    Write-Host '   $env:MAILTRAP_API_TOKEN = "seu-token"' -ForegroundColor White
    Write-Host '   $env:ADMIN_EMAIL = "seu-email@exemplo.com"' -ForegroundColor White
}
