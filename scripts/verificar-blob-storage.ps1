# ============================================
# Script para Verificar Relatórios no Blob Storage
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroup = "feedback-rg",
    
    [Parameter(Mandatory=$false)]
    [string]$StorageAccountName,
    
    [Parameter(Mandatory=$false)]
    [string]$ContainerName = "weekly-reports"
)

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Verificacao de Relatorios no Blob Storage" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Verificar Azure CLI
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Host "[ERRO] Azure CLI não encontrado. Instale em: https://aka.ms/installazurecliwindows" -ForegroundColor Red
    exit 1
}

# Verificar login
$accountCheck = az account show 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Não está logado no Azure. Execute: az login" -ForegroundColor Red
    exit 1
}

# Obter Storage Account Name se não fornecido
if ([string]::IsNullOrWhiteSpace($StorageAccountName)) {
    Write-Host "Obtendo nome do Storage Account..." -ForegroundColor Yellow
    
    $storageAccounts = az storage account list --resource-group $ResourceGroup --query "[].name" -o tsv 2>&1
    if ($LASTEXITCODE -eq 0 -and $storageAccounts) {
        $StorageAccountName = ($storageAccounts | Select-Object -First 1).Trim()
        Write-Host "[OK] Storage Account encontrado: $StorageAccountName" -ForegroundColor Green
    } else {
        Write-Host "[ERRO] Não foi possível encontrar o Storage Account." -ForegroundColor Red
        Write-Host "   Forneça o nome manualmente: -StorageAccountName 'nome-do-storage'" -ForegroundColor Yellow
        exit 1
    }
}

Write-Host "Storage Account: $StorageAccountName" -ForegroundColor Gray
Write-Host "Container: $ContainerName" -ForegroundColor Gray
Write-Host ""

# Obter connection string
Write-Host "Obtendo connection string..." -ForegroundColor Yellow
$connStr = az storage account show-connection-string `
    --name $StorageAccountName `
    --resource-group $ResourceGroup `
    --query connectionString `
    --output tsv 2>&1

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($connStr)) {
    Write-Host "[ERRO] Não foi possível obter connection string" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Connection string obtida" -ForegroundColor Green
Write-Host ""

# Listar blobs
Write-Host "Listando relatórios disponíveis..." -ForegroundColor Yellow
$blobs = az storage blob list `
    --container-name $ContainerName `
    --connection-string $connStr `
    --query "[].{Name:name, Size:properties.contentLength, LastModified:properties.lastModified}" `
    --output json 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "[AVISO] Erro ao listar blobs. Tentando criar container..." -ForegroundColor Yellow
    az storage container create `
        --name $ContainerName `
        --connection-string $connStr `
        --public-access off 2>&1 | Out-Null
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] Container criado: $ContainerName" -ForegroundColor Green
        Write-Host "[INFO] Nenhum relatorio encontrado ainda." -ForegroundColor Yellow
    } else {
        Write-Host "[ERRO] Não foi possível criar ou acessar o container" -ForegroundColor Red
    }
    exit 0
}

$blobList = $blobs | ConvertFrom-Json

if ($blobList.Count -eq 0) {
        Write-Host "[INFO] Nenhum relatorio encontrado no container" -ForegroundColor Yellow
    Write-Host ""
} else {
    $totalMsg = "[OK] Encontrados {0} relatorio(s)" -f $blobList.Count
    Write-Host $totalMsg -ForegroundColor Green
    Write-Host ""
    
    $sortedBlobs = $blobList | Sort-Object -Property LastModified -Descending
    
    Write-Host "Relatorios disponiveis:" -ForegroundColor White
    Write-Host ""
    
    $index = 1
    foreach ($blob in $sortedBlobs) {
        $sizeKB = [math]::Round($blob.Size / 1KB, 2)
        $lastModified = [DateTime]::Parse($blob.LastModified)
        $formattedDate = $lastModified.ToString("dd/MM/yyyy HH:mm:ss")
        
        Write-Host "  [$index] $($blob.Name)" -ForegroundColor Cyan
        Write-Host "      Tamanho: $sizeKB KB" -ForegroundColor Gray
        Write-Host "      Ultima modificacao: $formattedDate" -ForegroundColor Gray
        Write-Host ""
        
        $index++
    }
    
    # Mostrar conteudo do relatorio mais recente
    $latestBlob = $sortedBlobs[0]
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "  CONTEUDO DO RELATORIO MAIS RECENTE" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Arquivo: $($latestBlob.Name)" -ForegroundColor White
    Write-Host ""
    
    $tempFile = [System.IO.Path]::GetTempFileName()
    az storage blob download `
        --container-name $ContainerName `
        --name $latestBlob.Name `
        --file $tempFile `
        --connection-string $connStr 2>&1 | Out-Null
    
    if ($LASTEXITCODE -eq 0) {
        try {
            $content = Get-Content $tempFile -Raw -Encoding UTF8
            $jsonContent = $content | ConvertFrom-Json
            
            Write-Host "Periodo do Relatorio:" -ForegroundColor Yellow
            Write-Host "  Inicio: $($jsonContent.periodo_inicio)" -ForegroundColor White
            Write-Host "  Fim: $($jsonContent.periodo_fim)" -ForegroundColor White
            Write-Host ""
            
            Write-Host "Estatisticas:" -ForegroundColor Yellow
            Write-Host "  Total de avaliacoes: $($jsonContent.total_avaliacoes)" -ForegroundColor White
            Write-Host "  Media de avaliacoes: $($jsonContent.media_avaliacoes)" -ForegroundColor White
            Write-Host "  Data de geracao: $($jsonContent.data_geracao)" -ForegroundColor White
            Write-Host ""
        } catch {
            $erroMsg = "[AVISO] Erro ao processar conteudo do relatorio"
            Write-Host $erroMsg -ForegroundColor Yellow
        }
    }
    
    if (Test-Path $tempFile) {
        Remove-Item $tempFile -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "============================================================" -ForegroundColor Gray
Write-Host ""
