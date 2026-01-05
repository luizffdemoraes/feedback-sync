# ============================================
# Script para Verificar Dados no Table Storage
# ============================================

param(
    [Parameter(Mandatory=$false)]
    [string]$ResourceGroup = "feedback-rg",
    
    [Parameter(Mandatory=$false)]
    [string]$StorageAccountName,
    
    [Parameter(Mandatory=$false)]
    [string]$TableName = "feedbacks"
)

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Verificacao de Dados no Table Storage" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Verificar Azure CLI
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Host "[ERRO] Azure CLI nao encontrado. Instale em: https://aka.ms/installazurecliwindows" -ForegroundColor Red
    exit 1
}

# Verificar login
$accountCheck = az account show 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Nao esta logado no Azure. Execute: az login" -ForegroundColor Red
    exit 1
}

# Obter Storage Account Name se nao fornecido
if ([string]::IsNullOrWhiteSpace($StorageAccountName)) {
    Write-Host "Obtendo nome do Storage Account..." -ForegroundColor Yellow
    
    $storageAccounts = az storage account list --resource-group $ResourceGroup --query "[].name" -o tsv 2>&1
    if ($LASTEXITCODE -eq 0 -and $storageAccounts) {
        $StorageAccountName = ($storageAccounts | Select-Object -First 1).Trim()
        Write-Host "[OK] Storage Account encontrado: $StorageAccountName" -ForegroundColor Green
    } else {
        Write-Host "[ERRO] Nao foi possivel encontrar o Storage Account." -ForegroundColor Red
        Write-Host "   Forneca o nome manualmente: -StorageAccountName 'nome-do-storage'" -ForegroundColor Yellow
        exit 1
    }
}

Write-Host "Storage Account: $StorageAccountName" -ForegroundColor Gray
Write-Host "Tabela: $TableName" -ForegroundColor Gray
Write-Host ""

# Obter connection string
Write-Host "Obtendo connection string..." -ForegroundColor Yellow
$connStr = az storage account show-connection-string `
    --name $StorageAccountName `
    --resource-group $ResourceGroup `
    --query connectionString `
    --output tsv 2>&1

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($connStr)) {
    Write-Host "[ERRO] Nao foi possivel obter connection string" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Connection string obtida" -ForegroundColor Green
Write-Host ""

# Verificar se a tabela existe
Write-Host "Verificando se a tabela existe..." -ForegroundColor Yellow
$tableExists = az storage table exists `
    --name $TableName `
    --connection-string $connStr `
    --query "exists" `
    --output tsv 2>&1

if ($LASTEXITCODE -ne 0 -or $tableExists -ne "True") {
    Write-Host "[AVISO] Tabela '$TableName' nao encontrada ou nao existe ainda." -ForegroundColor Yellow
    Write-Host "   A tabela sera criada automaticamente quando o primeiro feedback for salvo." -ForegroundColor Gray
    Write-Host ""
    exit 0
}

Write-Host "[OK] Tabela encontrada" -ForegroundColor Green
Write-Host ""

# Consultar entidades
Write-Host "Consultando entidades na tabela..." -ForegroundColor Yellow
$entities = az storage entity query `
    --table-name $TableName `
    --connection-string $connStr `
    --query "[].{PartitionKey:PartitionKey, RowKey:RowKey, id:id, description:description, score:score, urgency:urgency, createdAt:createdAt}" `
    --output json 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Erro ao consultar entidades da tabela" -ForegroundColor Red
    exit 1
}

try {
    $entityList = $entities | ConvertFrom-Json
    
    if ($null -eq $entityList -or $entityList.Count -eq 0) {
        Write-Host "[INFO] Tabela vazia" -ForegroundColor Yellow
        Write-Host ""
    } else {
        $totalMsg = "[OK] Encontrados {0} feedback(s) na tabela" -f $entityList.Count
        Write-Host $totalMsg -ForegroundColor Green
        Write-Host ""
        
        Write-Host "Estatisticas dos Feedbacks:" -ForegroundColor Yellow
        Write-Host ""
        
        # Contar por score
        $scoreGroups = $entityList | Group-Object -Property score
        Write-Host "Distribuicao por Score:" -ForegroundColor White
        $scoreGroups | Sort-Object -Property Name | ForEach-Object {
            $scoreMsg = "  Score {0}: {1} feedback(s)" -f $_.Name, $_.Count
            Write-Host $scoreMsg -ForegroundColor Gray
        }
        Write-Host ""
        
        # Contar por urgencia
        if ($entityList[0].urgency) {
            $urgencyGroups = $entityList | Group-Object -Property urgency
            Write-Host "Distribuicao por Urgencia:" -ForegroundColor White
            $urgencyGroups | Sort-Object -Property Name | ForEach-Object {
                $urgencyMsg = "  {0}: {1} feedback(s)" -f $_.Name, $_.Count
                Write-Host $urgencyMsg -ForegroundColor Gray
            }
            Write-Host ""
        }
        
        # Calcular media de scores
        $scores = $entityList | Where-Object { $_.score -ne $null } | ForEach-Object { [int]$_.score }
        if ($scores.Count -gt 0) {
            $average = ($scores | Measure-Object -Average).Average
            $avgMsg = "Media de Scores: {0}" -f [math]::Round($average, 2)
            Write-Host $avgMsg -ForegroundColor White
            Write-Host ""
        }
        
        # Mostrar primeiros 10 feedbacks
        Write-Host "Primeiros 10 feedbacks:" -ForegroundColor Yellow
        Write-Host ""
        $entityList | Select-Object -First 10 | ForEach-Object {
            Write-Host "  ID: $($_.RowKey)" -ForegroundColor Cyan
            Write-Host "  Score: $($_.score) | Urgencia: $($_.urgency)" -ForegroundColor White
            Write-Host "  Descricao: $($_.description)" -ForegroundColor Gray
            Write-Host "  Criado em: $($_.createdAt)" -ForegroundColor Gray
            Write-Host ""
        }
        
        if ($entityList.Count -gt 10) {
            $restantes = $entityList.Count - 10
            $restantesMsg = "  ... e mais {0} feedback(s)" -f $restantes
            Write-Host $restantesMsg -ForegroundColor Gray
            Write-Host ""
        }
    }
} catch {
    Write-Host "[AVISO] Erro ao processar resposta" -ForegroundColor Yellow
    Write-Host "[INFO] Use o Portal do Azure para ver os dados" -ForegroundColor Gray
    Write-Host ""
}

Write-Host "============================================================" -ForegroundColor Gray
Write-Host ""
