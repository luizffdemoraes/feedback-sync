# Script para limpar filas do Azurite (útil para desenvolvimento local)
# Este script limpa as filas critical-feedbacks e critical-feedbacks-poison

Write-Host "🧹 Limpando filas do Azurite..." -ForegroundColor Cyan
Write-Host ""

# Verificar se Docker está rodando
Write-Host "Verificando Docker (Azurite)..." -ForegroundColor Yellow
try {
    $containerNames = docker ps --format "{{.Names}}" 2>$null
    $azurite = $containerNames | Where-Object { $_ -match 'azurite' }
    
    if (-not $azurite) {
        Write-Host "   [X] Azurite não está rodando" -ForegroundColor Red
        Write-Host "   Execute: docker compose up -d" -ForegroundColor Yellow
        exit 1
    } else {
        Write-Host "   [OK] Azurite: rodando" -ForegroundColor Green
    }
} catch {
    Write-Host "   [X] Erro ao verificar Docker" -ForegroundColor Red
    Write-Host "   Certifique-se de que o Docker está rodando" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "⚠️  ATENÇÃO: Isso vai limpar TODAS as mensagens das filas!" -ForegroundColor Yellow
Write-Host "   - critical-feedbacks" -ForegroundColor Gray
Write-Host "   - critical-feedbacks-poison" -ForegroundColor Gray
Write-Host ""

$confirmation = Read-Host "Deseja continuar? (S/N)"
if ($confirmation -ne 'S' -and $confirmation -ne 's') {
    Write-Host "Operação cancelada." -ForegroundColor Yellow
    exit 0
}

Write-Host ""
Write-Host "Limpando filas..." -ForegroundColor Yellow

# Connection string do Azurite
# Use variável de ambiente ou a connection string padrão do Azurite
# Para Azurite local, UseDevelopmentStorage=true funciona automaticamente
# Ou use: DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;TableEndpoint=http://localhost:10002/devstoreaccount1;BlobEndpoint=http://localhost:10000/devstoreaccount1;QueueEndpoint=http://localhost:10001/devstoreaccount1;
$connectionString = if ($env:AZURE_STORAGE_CONNECTION_STRING) {
    $env:AZURE_STORAGE_CONNECTION_STRING
} else {
    # Connection string padrão do Azurite (apenas para desenvolvimento local)
    "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;TableEndpoint=http://localhost:10002/devstoreaccount1;BlobEndpoint=http://localhost:10000/devstoreaccount1;QueueEndpoint=http://localhost:10001/devstoreaccount1;"
}

# Verificar se az storage está disponível (Azure CLI)
$azAvailable = $false
try {
    $azVersion = az --version 2>&1
    if ($LASTEXITCODE -eq 0) {
        $azAvailable = $true
    }
} catch {
    # Azure CLI não disponível
}

if ($azAvailable) {
    Write-Host "   Usando Azure CLI para limpar filas..." -ForegroundColor Gray
    
    # Limpar fila critical-feedbacks
    Write-Host "   Limpando critical-feedbacks..." -ForegroundColor Gray
    az storage queue clear --name "critical-feedbacks" --connection-string $connectionString 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   [OK] critical-feedbacks limpa" -ForegroundColor Green
    } else {
        Write-Host "   [⚠] Erro ao limpar critical-feedbacks (pode não existir)" -ForegroundColor Yellow
    }
    
    # Limpar fila critical-feedbacks-poison
    Write-Host "   Limpando critical-feedbacks-poison..." -ForegroundColor Gray
    az storage queue clear --name "critical-feedbacks-poison" --connection-string $connectionString 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   [OK] critical-feedbacks-poison limpa" -ForegroundColor Green
    } else {
        Write-Host "   [⚠] Erro ao limpar critical-feedbacks-poison (pode não existir)" -ForegroundColor Yellow
    }
} else {
    Write-Host "   [⚠] Azure CLI não encontrado" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "   Alternativa: Reinicie o Azurite para limpar as filas:" -ForegroundColor Yellow
    Write-Host "   docker compose down -v" -ForegroundColor White
    Write-Host "   docker compose up -d" -ForegroundColor White
    Write-Host ""
    Write-Host "   Ou instale Azure CLI:" -ForegroundColor Yellow
    Write-Host "   https://aka.ms/installazurecliwindows" -ForegroundColor White
}

Write-Host ""
Write-Host "✅ Limpeza concluída" -ForegroundColor Green
Write-Host ""
Write-Host "Próximos passos:" -ForegroundColor Cyan
Write-Host "   1. Configure as variáveis de ambiente:" -ForegroundColor White
Write-Host "      `$env:MAILTRAP_API_TOKEN = 'seu-token'" -ForegroundColor Gray
Write-Host "      `$env:ADMIN_EMAIL = 'seu-email@exemplo.com'" -ForegroundColor Gray
Write-Host ""
Write-Host "   2. Execute o script atualizado:" -ForegroundColor White
Write-Host "      .\scripts\executar-azure-functions-local.ps1" -ForegroundColor Gray
Write-Host ""
Write-Host "   3. Crie um novo feedback crítico para testar" -ForegroundColor White
