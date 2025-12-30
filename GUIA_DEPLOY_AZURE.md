# 🚀 Guia Completo de Deploy no Azure

Este guia detalha todos os passos necessários para instalar, configurar e fazer deploy da aplicação no Azure.

**Repositório**: [https://github.com/luizffdemoraes/feedback-sync.git](https://github.com/luizffdemoraes/feedback-sync.git)

## 📋 Índice

1. [Pré-requisitos](#pré-requisitos)
2. [Recursos Azure Necessários](#recursos-azure-necessários)
3. [Instalação e Configuração](#instalação-e-configuração)
4. [Criação dos Recursos Azure](#criação-dos-recursos-azure)
5. [Configuração da Aplicação](#configuração-da-aplicação)
6. [Deploy da Aplicação](#deploy-da-aplicação)
7. [Validação e Testes](#validação-e-testes)
8. [Troubleshooting](#troubleshooting)

---

## 📦 Pré-requisitos

### Software Necessário

1. **Java 21** (JDK)
   - Download: https://adoptium.net/
   - Verificar instalação: `java -version`

2. **Maven 3.8+** (ou use o `mvnw` incluído no projeto)
   - Download: https://maven.apache.org/download.cgi
   - Verificar instalação: `mvn -version`

3. **Azure CLI**
   - Windows: https://aka.ms/installazurecliwindows
   - Linux/Mac: `curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash`
   - Verificar instalação: `az --version`

4. **Git** (opcional, para clonar repositório)
   - Download: https://git-scm.com/downloads

### Conta Azure

- Conta Azure ativa com permissões para criar recursos
- Subscription ativa no Azure

---

## 🏗️ Recursos Azure Necessários

A aplicação requer os seguintes recursos no Azure:

| Recurso | Tipo | Finalidade |
|---------|------|------------|
| **Storage Account** | Standard LRS | Table Storage (feedbacks) + Blob Storage (relatórios) |
| **Service Bus** | Standard | Tópico para notificações críticas |
| **Function App** | Consumption Plan (Linux) | Host da aplicação serverless |
| **Resource Group** | - | Agrupa todos os recursos |

### Detalhamento dos Recursos

#### 1. Storage Account
- **Tipo**: StorageV2 (General Purpose v2)
- **Performance**: Standard
- **Redundância**: LRS (Local Redundant Storage)
- **Recursos habilitados**:
  - Table Storage (para feedbacks)
  - Blob Storage (para relatórios semanais)

#### 2. Service Bus
- **Tier**: Standard
- **Tópico**: `critical-feedbacks`
- **Subscription**: `admin-notifications`

#### 3. Function App
- **Runtime**: Java 21
- **OS**: Linux
- **Plan**: Consumption (Serverless)
- **Functions Extension**: ~4

---

## ⚙️ Instalação e Configuração

### 1. Clonar/Obter o Projeto

```bash
git clone https://github.com/luizffdemoraes/feedback-sync.git
cd feedback-sync
```

### 2. Verificar Pré-requisitos

```powershell
# Verificar Java
java -version

# Verificar Maven (ou usar mvnw)
.\mvnw.cmd -version

# Verificar Azure CLI
az --version
```

### 3. Login no Azure

```powershell
# Login interativo
az login

# Verificar subscription ativa
az account show

# Listar subscriptions disponíveis
az account list --output table

# Definir subscription (se necessário)
az account set --subscription "<subscription-id>"
```

---

## 🏭 Criação dos Recursos Azure

### Opção 1: Script Automatizado (Recomendado)

Crie um script PowerShell para criar todos os recursos:

```powershell
# criar-recursos-azure.ps1
param(
    [Parameter(Mandatory=$true)]
    [string]$ResourceGroupName = "feedback-rg",
    
    [Parameter(Mandatory=$true)]
    [string]$Location = "brazilsouth",
    
    [Parameter(Mandatory=$true)]
    [string]$Suffix  # Sufixo único para nomes (ex: "dev", "prod", seu nome)
)

$ErrorActionPreference = "Stop"

Write-Host "🚀 Criando recursos Azure..." -ForegroundColor Green

# 1. Criar Resource Group
Write-Host "`n📦 Criando Resource Group..." -ForegroundColor Yellow
az group create --name $ResourceGroupName --location $Location

# 2. Criar Storage Account
$storageAccountName = "feedbackstorage$Suffix".ToLower()
Write-Host "`n💾 Criando Storage Account: $storageAccountName" -ForegroundColor Yellow
az storage account create `
    --name $storageAccountName `
    --resource-group $ResourceGroupName `
    --location $Location `
    --sku Standard_LRS `
    --kind StorageV2 `
    --allow-blob-public-access false

# Obter connection string do Storage
$storageConnectionString = az storage account show-connection-string `
    --name $storageAccountName `
    --resource-group $ResourceGroupName `
    --query connectionString -o tsv

# Criar container para relatórios
az storage container create `
    --name "weekly-reports" `
    --account-name $storageAccountName `
    --connection-string $storageConnectionString `
    --public-access off

# 3. Criar Service Bus Namespace
$serviceBusNamespace = "feedback-sb-$Suffix".ToLower()
Write-Host "`n🚌 Criando Service Bus: $serviceBusNamespace" -ForegroundColor Yellow
az servicebus namespace create `
    --resource-group $ResourceGroupName `
    --name $serviceBusNamespace `
    --location $Location `
    --sku Standard

# Criar Tópico
az servicebus topic create `
    --resource-group $ResourceGroupName `
    --namespace-name $serviceBusNamespace `
    --name "critical-feedbacks"

# Criar Subscription
az servicebus topic subscription create `
    --resource-group $ResourceGroupName `
    --namespace-name $serviceBusNamespace `
    --topic-name "critical-feedbacks" `
    --name "admin-notifications"

# Obter connection string do Service Bus
$serviceBusConnectionString = az servicebus namespace authorization-rule keys list `
    --resource-group $ResourceGroupName `
    --namespace-name $serviceBusNamespace `
    --name "RootManageSharedAccessKey" `
    --query primaryConnectionString -o tsv

# 4. Criar Function App
$functionAppName = "feedback-function-$Suffix".ToLower()
Write-Host "`n⚡ Criando Function App: $functionAppName" -ForegroundColor Yellow
az functionapp create `
    --resource-group $ResourceGroupName `
    --consumption-plan-location $Location `
    --runtime java `
    --runtime-version 21 `
    --functions-version 4 `
    --name $functionAppName `
    --storage-account $storageAccountName `
    --os-type Linux

Write-Host "`n✅ Recursos criados com sucesso!" -ForegroundColor Green
Write-Host "`n📋 Informações importantes:" -ForegroundColor Cyan
Write-Host "  Storage Account: $storageAccountName" -ForegroundColor White
Write-Host "  Service Bus: $serviceBusNamespace" -ForegroundColor White
Write-Host "  Function App: $functionAppName" -ForegroundColor White
Write-Host "`n💡 Guarde estas informações para configurar as variáveis de ambiente!" -ForegroundColor Yellow
```

**Uso:**
```powershell
.\criar-recursos-azure.ps1 -ResourceGroupName "feedback-rg" -Location "brazilsouth" -Suffix "dev"
```

### Opção 2: Criar Manualmente via Portal Azure

1. **Acesse**: https://portal.azure.com
2. **Crie Resource Group**: `feedback-rg`
3. **Crie Storage Account**:
   - Nome: `feedbackstorage<seu-sufixo>`
   - Tipo: StorageV2
   - SKU: Standard LRS
   - Criar container `weekly-reports`
4. **Crie Service Bus**:
   - Namespace: `feedback-sb-<seu-sufixo>`
   - Tier: Standard
   - Criar tópico: `critical-feedbacks`
   - Criar subscription: `admin-notifications`
5. **Crie Function App**:
   - Nome: `feedback-function-<seu-sufixo>`
   - Runtime: Java 21
   - OS: Linux
   - Plan: Consumption

---

## 🔧 Configuração da Aplicação

### 1. Obter Connection Strings

Após criar os recursos, obtenha as connection strings:

```powershell
# Storage Account Connection String
$storageAccountName = "feedbackstorage<seu-sufixo>"
$storageConnectionString = az storage account show-connection-string `
    --name $storageAccountName `
    --resource-group "feedback-rg" `
    --query connectionString -o tsv

# Service Bus Connection String
$serviceBusNamespace = "feedback-sb-<seu-sufixo>"
$serviceBusConnectionString = az servicebus namespace authorization-rule keys list `
    --resource-group "feedback-rg" `
    --namespace-name $serviceBusNamespace `
    --name "RootManageSharedAccessKey" `
    --query primaryConnectionString -o tsv

Write-Host "Storage Connection String:" -ForegroundColor Cyan
Write-Host $storageConnectionString -ForegroundColor White
Write-Host "`nService Bus Connection String:" -ForegroundColor Cyan
Write-Host $serviceBusConnectionString -ForegroundColor White
```

### 2. Configurar Application Settings na Function App

```powershell
$functionAppName = "feedback-function-<seu-sufixo>"
$resourceGroup = "feedback-rg"

# Configurar variáveis de ambiente
az functionapp config appsettings set `
    --name $functionAppName `
    --resource-group $resourceGroup `
    --settings `
        "AZURE_STORAGE_CONNECTION_STRING=$storageConnectionString" `
        "AzureWebJobsStorage=$storageConnectionString" `
        "AZURE_SERVICEBUS_CONNECTION_STRING=$serviceBusConnectionString" `
        "AzureServiceBusConnection=$serviceBusConnectionString" `
        "FUNCTIONS_WORKER_RUNTIME=java" `
        "FUNCTIONS_EXTENSION_VERSION=~4" `
        "quarkus.log.level=INFO" `
        "app.environment=production" `
        "azure.servicebus.topic-name=critical-feedbacks" `
        "azure.storage.container-name=weekly-reports" `
        "azure.table.table-name=feedbacks"
```

### 3. Verificar Configurações

```powershell
az functionapp config appsettings list `
    --name $functionAppName `
    --resource-group $resourceGroup `
    --output table
```

---

## 🚀 Deploy da Aplicação

### Opção 1: Usando o Script de Deploy

```powershell
.\scripts\implantar-azure.ps1 `
    -FunctionAppName "feedback-function-<seu-sufixo>" `
    -ResourceGroup "feedback-rg" `
    -Location "brazilsouth"
```

### Opção 2: Deploy Manual via Maven

```powershell
# 1. Compilar o projeto
.\mvnw.cmd clean package -DskipTests

# 2. Fazer deploy
.\mvnw.cmd azure-functions:deploy `
    -DfunctionAppName="feedback-function-<seu-sufixo>"
```

### Opção 3: Deploy via Azure CLI

```powershell
# 1. Compilar
.\mvnw.cmd clean package -DskipTests

# 2. Criar pacote de deploy
$functionAppName = "feedback-function-<seu-sufixo>"
$resourceGroup = "feedback-rg"

# O pacote será criado em target/azure-functions/
# 3. Fazer deploy do pacote
az functionapp deployment source config-zip `
    --resource-group $resourceGroup `
    --name $functionAppName `
    --src target/azure-functions/$functionAppName.zip
```

### Opção 4: Deploy via VS Code (Recomendado para desenvolvimento)

1. Instalar extensão: **Azure Functions**
2. Fazer login no Azure
3. Clicar em "Deploy to Function App"
4. Selecionar Function App criada

---

## ✅ Validação e Testes

### 1. Verificar Deploy

```powershell
# Verificar se Function App está rodando
az functionapp show `
    --name $functionAppName `
    --resource-group $resourceGroup `
    --query state

# Ver logs
az functionapp log tail `
    --name $functionAppName `
    --resource-group $resourceGroup
```

### 2. Obter URL da Function App

```powershell
$functionUrl = az functionapp show `
    --name $functionAppName `
    --resource-group $resourceGroup `
    --query defaultHostName -o tsv

Write-Host "URL da Function App: https://$functionUrl" -ForegroundColor Green
```

### 3. Testar Endpoint

```powershell
$functionUrl = "https://feedback-function-<seu-sufixo>.azurewebsites.net"

# Testar endpoint de feedback
Invoke-RestMethod -Uri "$functionUrl/api/avaliacao" `
    -Method Post `
    -Body '{"descricao":"Teste de deploy","nota":8,"urgencia":"MEDIUM"}' `
    -ContentType "application/json"

# Testar health check
Invoke-RestMethod -Uri "$functionUrl/health" -Method Get
```

### 4. Verificar Logs

```powershell
# Logs em tempo real
az functionapp log tail `
    --name $functionAppName `
    --resource-group $resourceGroup

# Logs de streaming
az webapp log tail `
    --name $functionAppName `
    --resource-group $resourceGroup
```

---

## 🔍 Troubleshooting

### Problema: Function App não inicia

**Solução:**
1. Verificar logs: `az functionapp log tail`
2. Verificar Application Settings estão corretas
3. Verificar se Java 21 está configurado
4. Verificar se connection strings estão corretas

### Problema: Erro de conexão com Storage

**Solução:**
1. Verificar `AZURE_STORAGE_CONNECTION_STRING` está configurada
2. Verificar Storage Account está ativo
3. Verificar container `weekly-reports` foi criado

### Problema: Erro de conexão com Service Bus

**Solução:**
1. Verificar `AZURE_SERVICEBUS_CONNECTION_STRING` está configurada
2. Verificar tópico `critical-feedbacks` existe
3. Verificar subscription `admin-notifications` existe

### Problema: Functions não aparecem

**Solução:**
1. Verificar se classes estão anotadas com `@FunctionName`
2. Verificar se `@ApplicationScoped` está nas classes corretas
3. Verificar logs de inicialização
4. Recompilar e fazer deploy novamente

### Problema: Timeout ou erro 503

**Solução:**
1. Verificar se Function App está no Consumption Plan (cold start)
2. Aguardar alguns segundos após primeira requisição
3. Verificar se recursos Azure estão na mesma região

---

## 📊 Checklist de Deploy

- [ ] Azure CLI instalado e logado
- [ ] Resource Group criado
- [ ] Storage Account criado e container `weekly-reports` criado
- [ ] Service Bus criado com tópico e subscription
- [ ] Function App criada (Java 21, Linux, Consumption)
- [ ] Application Settings configuradas:
  - [ ] `AZURE_STORAGE_CONNECTION_STRING`
  - [ ] `AzureWebJobsStorage`
  - [ ] `AZURE_SERVICEBUS_CONNECTION_STRING`
  - [ ] `AzureServiceBusConnection`
  - [ ] `FUNCTIONS_WORKER_RUNTIME=java`
  - [ ] `FUNCTIONS_EXTENSION_VERSION=~4`
- [ ] Projeto compilado com sucesso
- [ ] Deploy realizado
- [ ] Function App está rodando
- [ ] Endpoint `/api/avaliacao` responde
- [ ] Health check `/health` responde
- [ ] Logs estão sendo gerados

---

## 📚 Recursos Adicionais

- **Repositório do Projeto**: [https://github.com/luizffdemoraes/feedback-sync.git](https://github.com/luizffdemoraes/feedback-sync.git)
- [Azure Functions Java Guide](https://docs.microsoft.com/azure/azure-functions/functions-reference-java)
- [Quarkus Azure Functions](https://quarkus.io/guides/azure-functions-http)
- [Azure Storage Documentation](https://docs.microsoft.com/azure/storage/)
- [Azure Service Bus Documentation](https://docs.microsoft.com/azure/service-bus-messaging/)

---

## 💰 Estimativa de Custos (Consumption Plan)

| Recurso | Custo Estimado (mensal) |
|---------|------------------------|
| Function App (Consumption) | ~$0.20 por 1M execuções |
| Storage Account (LRS) | ~$0.018/GB |
| Service Bus (Standard) | ~$10/mês base + $0.05 por 1M mensagens |

**Total estimado**: ~$15-30/mês para uso moderado

---

## 🎯 Próximos Passos

Após o deploy bem-sucedido:

1. Configurar monitoramento no Azure Portal
2. Configurar alertas para erros
3. Configurar Application Insights (opcional)
4. Configurar CI/CD (GitHub Actions, Azure DevOps)
5. Configurar domínio customizado (opcional)

---

**Última atualização**: $(Get-Date -Format "dd/MM/yyyy")

