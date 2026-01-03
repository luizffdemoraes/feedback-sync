# Script para enviar uma mensagem de teste diretamente na fila critical-feedbacks
# Util para testar se a funcao notifyAdmin esta funcionando

Write-Host "Enviando mensagem de teste para a fila 'critical-feedbacks'..." -ForegroundColor Cyan
Write-Host ""

# Verificar se Docker esta rodando
Write-Host "Verificando Docker (Azurite)..." -ForegroundColor Yellow
try {
    $containerNames = docker ps --format "{{.Names}}" 2>$null
    $azurite = $containerNames | Where-Object { $_ -match 'azurite' }
    
    if (-not $azurite) {
        Write-Host "   [X] Azurite nao esta rodando" -ForegroundColor Red
        Write-Host "   Execute: docker compose up -d" -ForegroundColor Yellow
        exit 1
    } else {
        Write-Host "   [OK] Azurite: rodando" -ForegroundColor Green
    }
} catch {
    Write-Host "   [X] Erro ao verificar Docker" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Connection string do Azurite
$connectionString = if ($env:AZURE_STORAGE_CONNECTION_STRING) {
    $env:AZURE_STORAGE_CONNECTION_STRING
} else {
    "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;TableEndpoint=http://localhost:10002/devstoreaccount1;BlobEndpoint=http://localhost:10000/devstoreaccount1;QueueEndpoint=http://localhost:10001/devstoreaccount1;"
}

# Verificar se az storage esta disponivel (Azure CLI)
$azAvailable = $false
try {
    $null = az --version 2>&1
    if ($LASTEXITCODE -eq 0) {
        $azAvailable = $true
    }
} catch {
    # Azure CLI nao disponivel
}

if ($azAvailable) {
    # Criar mensagem de teste (formato JSON de Feedback)
    $timestamp = Get-Date -Format 'yyyyMMddHHmmss'
    $timestampReadable = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    $createdAt = Get-Date -Format 'yyyy-MM-ddTHH:mm:ss'
    
    $testId = 'test-' + $timestamp
    $testDescription = 'Mensagem de teste enviada diretamente para a fila - ' + $timestampReadable
    
    $testMessageHash = @{}
    $testMessageHash.id = $testId
    $testMessageHash.description = $testDescription
    $testMessageHash.score = @{}
    $testMessageHash.score.value = 2
    $testMessageHash.urgency = 'HIGH'
    $testMessageHash.createdAt = $createdAt
    
    $testMessage = $testMessageHash | ConvertTo-Json -Depth 10
    
    Write-Host "Mensagem de teste criada:" -ForegroundColor Yellow
    Write-Host $testMessage -ForegroundColor Gray
    Write-Host ""
    
    # Enviar mensagem
    Write-Host "Enviando mensagem para a fila..." -ForegroundColor Yellow
    
    # Salvar connection string atual (se existir)
    $oldConnectionString = $env:AZURE_STORAGE_CONNECTION_STRING
    
    # Definir connection string temporariamente
    $env:AZURE_STORAGE_CONNECTION_STRING = $connectionString
    
    try {
        $result = az storage message put --queue-name "critical-feedbacks" --content $testMessage --output json 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "   [OK] Mensagem enviada com sucesso!" -ForegroundColor Green
            Write-Host ""
            
            # Verificar imediatamente se a mensagem esta na fila
            Write-Host "Verificando se a mensagem esta na fila..." -ForegroundColor Yellow
            Start-Sleep -Milliseconds 500
            $verifyResult = az storage message peek --queue-name "critical-feedbacks" --num-messages 1 --output json 2>&1
            
            if ($LASTEXITCODE -eq 0) {
                # Filtrar warnings
                $verifyJson = ($verifyResult | Where-Object { 
                    $_ -notmatch '^WARNING:' -and 
                    $_ -notmatch '^ERROR:' -and 
                    ($_ -match '^\s*\{' -or $_ -match '^\s*\[')
                }) -join "`n"
                
                if ($verifyJson) {
                    try {
                        $verifyArray = $verifyJson | ConvertFrom-Json
                        if ($verifyArray -and $verifyArray.Count -gt 0) {
                            Write-Host "   [OK] Mensagem confirmada na fila!" -ForegroundColor Green
                        } else {
                            Write-Host "   [AVISO] Mensagem enviada mas nao encontrada na fila (pode ter sido consumida)" -ForegroundColor Yellow
                        }
                    } catch {
                        Write-Host "   [AVISO] Nao foi possivel verificar mensagem na fila" -ForegroundColor Yellow
                    }
                } else {
                    Write-Host "   [AVISO] Mensagem enviada mas nao encontrada na fila (pode ter sido consumida)" -ForegroundColor Yellow
                }
            }
            
            Write-Host ""
            Write-Host "A funcao 'notifyAdmin' deve processar esta mensagem em ate 2 segundos." -ForegroundColor Cyan
            Write-Host "Verifique os logs do Azure Functions para confirmar o processamento." -ForegroundColor Cyan
        } else {
            Write-Host "   [X] Erro ao enviar mensagem: $result" -ForegroundColor Red
            exit 1
        }
    } finally {
        # Restaurar connection string original
        if ($oldConnectionString) {
            $env:AZURE_STORAGE_CONNECTION_STRING = $oldConnectionString
        } else {
            Remove-Item Env:\AZURE_STORAGE_CONNECTION_STRING -ErrorAction SilentlyContinue
        }
    }
    
} else {
    Write-Host "   [X] Azure CLI nao encontrado" -ForegroundColor Red
    Write-Host ""
    Write-Host "   Instale Azure CLI:" -ForegroundColor Yellow
    Write-Host "   https://aka.ms/installazurecliwindows" -ForegroundColor White
    exit 1
}

Write-Host ""
Write-Host "[OK] Teste concluido" -ForegroundColor Green
