# Script definitivo para verificar mensagens na fila critical-feedbacks
# Tenta multiplas abordagens para garantir funcionamento

Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "VERIFICADOR DEFINITIVO DE FILA AZURITE" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
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
$connectionString = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;TableEndpoint=http://localhost:10002/devstoreaccount1;BlobEndpoint=http://localhost:10000/devstoreaccount1;QueueEndpoint=http://localhost:10001/devstoreaccount1;"
$queueName = "critical-feedbacks"

$success = $false

# METODO 1: Tentar usar modulo Az.Storage do PowerShell
Write-Host "METODO 1: Tentando usar modulo Az.Storage..." -ForegroundColor Yellow
try {
    if (Get-Module -ListAvailable -Name Az.Storage) {
        Import-Module Az.Storage -ErrorAction Stop
        
        $storageContext = New-AzStorageContext -ConnectionString $connectionString -ErrorAction Stop
        $queue = Get-AzStorageQueue -Name $queueName -Context $storageContext -ErrorAction Stop
        
        Write-Host "   [OK] Modulo Az.Storage carregado com sucesso" -ForegroundColor Green
        
        # Verificar mensagens
        $messages = $queue.CloudQueue.PeekMessages(32)
        
        if ($messages.Count -gt 0) {
            Write-Host "   [OK] Encontradas $($messages.Count) mensagem(ns) na fila" -ForegroundColor Green
            Write-Host ""
            
            foreach ($msg in $messages) {
                Write-Host "   Mensagem ID: $($msg.Id)" -ForegroundColor Cyan
                Write-Host "   Tamanho: $($msg.AsString.Length) caracteres" -ForegroundColor Gray
                
                $messageText = $msg.AsString
                
                # Tentar decodificar Base64
                try {
                    $decodedBytes = [Convert]::FromBase64String($messageText)
                    $decodedText = [System.Text.Encoding]::UTF8.GetString($decodedBytes)
                    if ($decodedText.Trim().StartsWith("{") -or $decodedText.Trim().StartsWith("[")) {
                        $messageText = $decodedText
                        Write-Host "   (Decodificado de Base64)" -ForegroundColor Gray
                    }
                } catch {
                    # Nao e Base64
                }
                
                if ($messageText.Length -gt 0) {
                    $contentPreview = if ($messageText.Length -gt 200) {
                        $messageText.Substring(0, 200)
                    } else {
                        $messageText
                    }
                    Write-Host "   Conteudo: $contentPreview" -ForegroundColor White
                }
                Write-Host ""
            }
            
            $success = $true
        } else {
            Write-Host "   [AVISO] Nenhuma mensagem encontrada na fila" -ForegroundColor Yellow
            $success = $true
        }
    } else {
        Write-Host "   [AVISO] Modulo Az.Storage nao instalado" -ForegroundColor Yellow
        Write-Host "   Instale com: Install-Module -Name Az.Storage" -ForegroundColor Gray
    }
} catch {
    Write-Host "   [X] Erro ao usar Az.Storage: $_" -ForegroundColor Red
}

Write-Host ""

# METODO 2: Tentar usar Azure CLI (mais confiavel)
if (-not $success) {
    Write-Host "METODO 2: Tentando usar Azure CLI..." -ForegroundColor Yellow
    
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
        # Salvar connection string atual
        $oldConnectionString = $env:AZURE_STORAGE_CONNECTION_STRING
        
        # Definir connection string temporariamente
        $env:AZURE_STORAGE_CONNECTION_STRING = $connectionString
        
        try {
            # Verificar se a fila existe
            Write-Host "   Verificando se a fila existe..." -ForegroundColor Gray
            $queueExists = az storage queue exists --name $queueName --output tsv 2>&1
            
            if ($LASTEXITCODE -eq 0 -and $queueExists -eq "True") {
                Write-Host "   [OK] Fila existe" -ForegroundColor Green
                
                # Funcao auxiliar para filtrar warnings e extrair JSON limpo
                function Get-JsonFromAzOutput {
                    param([array]$output)
                    
                    # Converter array para string
                    $outputString = if ($output -is [Array]) {
                        $output -join "`n"
                    } else {
                        $output.ToString()
                    }
                    
                    # Remover linhas de WARNING e ERROR usando regex multiline
                    $outputString = $outputString -replace '(?m)^WARNING:.*$', ''
                    $outputString = $outputString -replace '(?m)^ERROR:.*$', ''
                    $outputString = $outputString -replace '(?m)^Command group.*$', ''
                    $outputString = $outputString -replace '(?m)^Reference and support.*$', ''
                    
                    # Limpar linhas vazias
                    $outputString = $outputString -replace '(?m)^\s*$', ''
                    
                    # Tentar encontrar JSON array (mais comum)
                    if ($outputString -match '(\[[\s\S]*?\])') {
                        $jsonMatch = $matches[1]
                        # Validar que e JSON valido tentando parsear
                        try {
                            $null = $jsonMatch | ConvertFrom-Json
                            return $jsonMatch.Trim()
                        } catch {
                            # Tentar encontrar JSON mais completo
                        }
                    }
                    
                    # Tentar encontrar JSON objeto
                    if ($outputString -match '(\{[\s\S]*?\})') {
                        $jsonMatch = $matches[1]
                        try {
                            $null = $jsonMatch | ConvertFrom-Json
                            return $jsonMatch.Trim()
                        } catch {
                            # Continuar
                        }
                    }
                    
                    # Se nao encontrou padrao valido, retornar limpo e tentar parsear mesmo assim
                    return $outputString.Trim()
                }
                
                # Tentar pegar mensagens
                Write-Host "   Buscando mensagens..." -ForegroundColor Gray
                $messagesOutput = az storage message peek --queue-name $queueName --num-messages 32 --output json 2>&1
                
                if ($LASTEXITCODE -eq 0) {
                    # Converter para array se for string
                    if ($messagesOutput -is [String]) {
                        $messagesOutput = $messagesOutput -split "`n"
                    }
                    
                    $messagesJson = Get-JsonFromAzOutput -output $messagesOutput
                    
                    if ($messagesJson) {
                        try {
                            # Limpar JSON antes de converter
                            $messagesJson = $messagesJson.Trim()
                            
                            $msgArray = $messagesJson | ConvertFrom-Json
                            
                            # Garantir que e um array
                            if (-not ($msgArray -is [Array])) {
                                $msgArray = @($msgArray)
                            }
                            
                            if ($msgArray.Count -gt 0) {
                                Write-Host "   [OK] Encontradas $($msgArray.Count) mensagem(ns) na fila" -ForegroundColor Green
                                Write-Host ""
                                
                                foreach ($msg in $msgArray) {
                                    Write-Host "   Mensagem ID: $($msg.id)" -ForegroundColor Cyan
                                    Write-Host "   Insertion Time: $($msg.insertionTime)" -ForegroundColor Gray
                                    Write-Host "   Dequeue Count: $($msg.dequeueCount)" -ForegroundColor Gray
                                    
                                    # O Azure CLI retorna 'content' ao inves de 'messageText'
                                    $msgText = if ($msg.content) { 
                                        $msg.content 
                                    } elseif ($msg.messageText) { 
                                        $msg.messageText 
                                    } else { 
                                        "" 
                                    }
                                    
                                    # O Azure CLI pode estar truncando o conteudo no peek
                                    # Tentar usar 'get' para a primeira mensagem para ver o conteudo completo
                                    if ($msgText -eq "{" -or ($msgText.Length -lt 10 -and $msgText.StartsWith("{"))) {
                                        Write-Host "   [AVISO] Conteudo truncado no peek" -ForegroundColor Yellow
                                        
                                        # Usar 'get' para pegar conteudo completo (remove mensagem da fila)
                                        # So fazer isso para a primeira mensagem como exemplo
                                        if ($msg -eq $msgArray[0]) {
                                            Write-Host "   Tentando obter conteudo completo da primeira mensagem (sera removida)..." -ForegroundColor Yellow
                                            try {
                                                $fullMessage = az storage message get --queue-name $queueName --num-messages 1 --output json 2>&1
                                                
                                                if ($LASTEXITCODE -eq 0) {
                                                    $fullJson = Get-JsonFromAzOutput -output $fullMessage
                                                    if ($fullJson) {
                                                        $fullMsgArray = $fullJson | ConvertFrom-Json
                                                        if (-not ($fullMsgArray -is [Array])) {
                                                            $fullMsgArray = @($fullMsgArray)
                                                        }
                                                        
                                                        if ($fullMsgArray.Count -gt 0) {
                                                            $fullContent = if ($fullMsgArray[0].content) { 
                                                                $fullMsgArray[0].content 
                                                            } elseif ($fullMsgArray[0].messageText) { 
                                                                $fullMsgArray[0].messageText 
                                                            } else { 
                                                                "" 
                                                            }
                                                            
                                                            Write-Host "   [OK] Conteudo completo obtido:" -ForegroundColor Green
                                                            
                                                            # Tentar decodificar Base64
                                                            if ($fullContent -and $fullContent.Length -gt 0) {
                                                                try {
                                                                    if ($fullContent -match '^[A-Za-z0-9+/=]+$' -and $fullContent.Length % 4 -eq 0) {
                                                                        $decodedBytes = [Convert]::FromBase64String($fullContent)
                                                                        $decodedText = [System.Text.Encoding]::UTF8.GetString($decodedBytes)
                                                                        if ($decodedText.Trim().StartsWith("{") -or $decodedText.Trim().StartsWith("[")) {
                                                                            $fullContent = $decodedText
                                                                            Write-Host "   (Decodificado de Base64)" -ForegroundColor Gray
                                                                        }
                                                                    }
                                                                } catch {
                                                                    # Nao e Base64
                                                                }
                                                                
                                                                Write-Host "   Tamanho: $($fullContent.Length) caracteres" -ForegroundColor Gray
                                                                $contentPreview = if ($fullContent.Length -gt 500) {
                                                                    $fullContent.Substring(0, 500) + "..."
                                                                } else {
                                                                    $fullContent
                                                                }
                                                                Write-Host "   Conteudo:" -ForegroundColor White
                                                                Write-Host "   $contentPreview" -ForegroundColor White
                                                            } else {
                                                                Write-Host "   [AVISO] Conteudo vazio mesmo apos get" -ForegroundColor Yellow
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Write-Host "   [AVISO] Erro ao obter mensagem completa: $fullMessage" -ForegroundColor Yellow
                                                }
                                            } catch {
                                                Write-Host "   [AVISO] Excecao ao obter conteudo completo: $_" -ForegroundColor Yellow
                                            }
                                        } else {
                                            Write-Host "   [INFO] Conteudo truncado (similar a primeira mensagem)" -ForegroundColor Gray
                                        }
                                    } else {
                                        # Conteudo nao esta truncado
                                        Write-Host "   Tamanho: $($msgText.Length) caracteres" -ForegroundColor Gray
                                        
                                        # Tentar decodificar Base64
                                        try {
                                            if ($msgText -match '^[A-Za-z0-9+/=]+$' -and $msgText.Length % 4 -eq 0) {
                                                $decodedBytes = [Convert]::FromBase64String($msgText)
                                                $decodedText = [System.Text.Encoding]::UTF8.GetString($decodedBytes)
                                                if ($decodedText.Trim().StartsWith("{") -or $decodedText.Trim().StartsWith("[")) {
                                                    $msgText = $decodedText
                                                    Write-Host "   (Decodificado de Base64)" -ForegroundColor Gray
                                                }
                                            }
                                        } catch {
                                            # Nao e Base64
                                        }
                                        
                                        $contentPreview = if ($msgText.Length -gt 300) {
                                            $msgText.Substring(0, 300) + "..."
                                        } else {
                                            $msgText
                                        }
                                        Write-Host "   Conteudo:" -ForegroundColor White
                                        Write-Host "   $contentPreview" -ForegroundColor White
                                    }
                                    Write-Host ""
                                }
                                
                                $success = $true
                            } else {
                                Write-Host "   [AVISO] Nenhuma mensagem encontrada na fila" -ForegroundColor Yellow
                                $success = $true
                            }
                        } catch {
                            Write-Host "   [X] Erro ao processar JSON: $_" -ForegroundColor Red
                            Write-Host "   Saida bruta:" -ForegroundColor Gray
                            Write-Host "   $messagesOutput" -ForegroundColor DarkGray
                        }
                    } else {
                        Write-Host "   [AVISO] Nenhuma mensagem encontrada (JSON vazio)" -ForegroundColor Yellow
                        Write-Host "   Saida bruta:" -ForegroundColor Gray
                        Write-Host "   $messagesOutput" -ForegroundColor DarkGray
                        $success = $true
                    }
                } else {
                    Write-Host "   [X] Erro ao buscar mensagens: $messagesOutput" -ForegroundColor Red
                }
            } else {
                Write-Host "   [X] Fila nao existe ou erro ao verificar" -ForegroundColor Red
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
    }
}

Write-Host ""

# METODO 3: Verificar poison queue
Write-Host "METODO 3: Verificando poison queue..." -ForegroundColor Yellow
$poisonQueueName = "$queueName-poison"

try {
    $oldConnectionString = $env:AZURE_STORAGE_CONNECTION_STRING
    $env:AZURE_STORAGE_CONNECTION_STRING = $connectionString
    
    try {
        $poisonExists = az storage queue exists --name $poisonQueueName --output tsv 2>&1
        
        if ($LASTEXITCODE -eq 0 -and $poisonExists -eq "True") {
            $poisonMessages = az storage message peek --queue-name $poisonQueueName --num-messages 32 --output json 2>&1
            
            if ($LASTEXITCODE -eq 0) {
                $poisonJson = ($poisonMessages | Where-Object { 
                    $_ -notmatch '^WARNING:' -and 
                    $_ -notmatch '^ERROR:' -and 
                    ($_ -match '^\s*\{' -or $_ -match '^\s*\[')
                }) -join "`n"
                
                if ($poisonJson) {
                    try {
                        $poisonArray = $poisonJson | ConvertFrom-Json
                        if (-not ($poisonArray -is [Array])) {
                            $poisonArray = @($poisonArray)
                        }
                        
                        if ($poisonArray.Count -gt 0) {
                            Write-Host "   [ATENCAO] Encontradas $($poisonArray.Count) mensagem(ns) na poison queue!" -ForegroundColor Red
                            Write-Host "   Isso indica que a funcao notifyAdmin esta falhando" -ForegroundColor Red
                            Write-Host ""
                        } else {
                            Write-Host "   [OK] Nenhuma mensagem na poison queue" -ForegroundColor Green
                        }
                    } catch {
                        Write-Host "   [AVISO] Erro ao processar poison queue" -ForegroundColor Yellow
                    }
                } else {
                    Write-Host "   [OK] Nenhuma mensagem na poison queue" -ForegroundColor Green
                }
            }
        } else {
            Write-Host "   [OK] Poison queue nao existe (normal se nao houve falhas)" -ForegroundColor Green
        }
    } finally {
        if ($oldConnectionString) {
            $env:AZURE_STORAGE_CONNECTION_STRING = $oldConnectionString
        } else {
            Remove-Item Env:\AZURE_STORAGE_CONNECTION_STRING -ErrorAction SilentlyContinue
        }
    }
} catch {
    Write-Host "   [AVISO] Erro ao verificar poison queue: $_" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "RESUMO:" -ForegroundColor Yellow
Write-Host ""

if ($success) {
    Write-Host "   [OK] Verificacao concluida com sucesso" -ForegroundColor Green
} else {
    Write-Host "   [X] Nenhum metodo funcionou corretamente" -ForegroundColor Red
    Write-Host ""
    Write-Host "   SOLUCOES:" -ForegroundColor Yellow
    Write-Host "   1. Instale o modulo Az.Storage:" -ForegroundColor White
    Write-Host "      Install-Module -Name Az.Storage -Force" -ForegroundColor Gray
    Write-Host ""
    Write-Host "   2. Ou instale Azure CLI:" -ForegroundColor White
    Write-Host "      https://aka.ms/installazurecliwindows" -ForegroundColor Gray
    Write-Host ""
    Write-Host "   3. Ou verifique manualmente usando:" -ForegroundColor White
    Write-Host "      docker exec -it <container-azurite> azurite-queue" -ForegroundColor Gray
}

Write-Host ""
Write-Host "===========================================================" -ForegroundColor Cyan
