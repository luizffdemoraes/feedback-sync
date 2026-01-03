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
| **Storage Account** | Standard LRS | Table Storage (feedbacks) + Blob Storage (relatórios) + Queue Storage (notificações) |
| **Function App** | Consumption Plan (Linux) | Host da aplicação serverless |
| **Resource Group** | - | Agrupa todos os recursos |
| **Mailtrap** | Free Tier | Envio de emails para notificações críticas |

### Detalhamento dos Recursos

#### 1. Storage Account
- **Tipo**: StorageV2 (General Purpose v2)
- **Performance**: Standard
- **Redundância**: LRS (Local Redundant Storage)
- **Recursos habilitados**:
  - Table Storage (para feedbacks)
  - Blob Storage (para relatórios semanais)
  - Queue Storage (para fila de notificações críticas - fila: `critical-feedbacks`)

#### 2. Mailtrap
- **Tier**: Free Tier (suficiente para desenvolvimento e testes)
- **Finalidade**: Envio de emails para notificações críticas
- **Configuração**: Requer API Token e Inbox ID

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

O script `criar-recursos-azure.ps1` cria todos os recursos necessários e configura as variáveis de ambiente automaticamente.

#### Uso Básico (sem Mailtrap)

```powershell
.\scripts\criar-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Location "brazilsouth" `
    -Suffix "prod"
```

**Nota**: Com este uso básico, você precisará configurar o Mailtrap manualmente depois (veja instruções abaixo).

#### Uso Completo (com Mailtrap - Recomendado)

Para configurar tudo automaticamente, incluindo as variáveis do Mailtrap:

```powershell
.\scripts\criar-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Location "brazilsouth" `
    -Suffix "prod" `
    -MailtrapApiToken "seu-token-mailtrap" `
    -MailtrapInboxId "seu-inbox-id" `
    -AdminEmail "admin@exemplo.com"
```

**Parâmetros do Script:**

| Parâmetro | Obrigatório | Descrição | Padrão |
|-----------|-------------|-----------|--------|
| `ResourceGroupName` | Não | Nome do Resource Group | `feedback-rg` |
| `Location` | Não | Região do Azure | `brazilsouth` |
| `Suffix` | Não | Sufixo único para nomes dos recursos | `prod` |
| `MailtrapApiToken` | Não | Token da API do Mailtrap | - |
| `MailtrapInboxId` | Não | ID da inbox do Mailtrap | - |
| `AdminEmail` | Não | Email do administrador | - |

**⚠️ IMPORTANTE - Variáveis de Ambiente:**

O script configura automaticamente:
- ✅ `AZURE_STORAGE_CONNECTION_STRING` - Configurada automaticamente
- ✅ `AzureWebJobsStorage` - Configurada automaticamente
- ✅ `MAILTRAP_API_TOKEN` - Configurada apenas se `-MailtrapApiToken` for fornecido
- ✅ `MAILTRAP_INBOX_ID` - Configurada apenas se `-MailtrapInboxId` for fornecido
- ✅ `ADMIN_EMAIL` - Configurada apenas se `-AdminEmail` for fornecido

**Se você não fornecer os parâmetros do Mailtrap**, o script criará os recursos mas mostrará instruções de como configurar manualmente depois.

### Opção 2: Criar Manualmente via Portal Azure

1. **Acesse**: https://portal.azure.com
2. **Crie Resource Group**: `feedback-rg`
3. **Crie Storage Account**:
   - Nome: `feedbackstorage<seu-sufixo>`
   - Tipo: StorageV2
   - SKU: Standard LRS
   - Criar container `weekly-reports`
4. **Configure Mailtrap** (opcional para testes locais, necessário para produção):
   - Crie conta gratuita em: https://mailtrap.io
   - Gere API Token
   - Obtenha Inbox ID
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

Write-Host "Storage Connection String:" -ForegroundColor Cyan
Write-Host $storageConnectionString -ForegroundColor White
Write-Host "`n📧 Mailtrap Configuration:" -ForegroundColor Cyan
Write-Host "  Configure manualmente no Azure Portal:" -ForegroundColor White
Write-Host "  - MAILTRAP_API_TOKEN: <seu-token>" -ForegroundColor Gray
Write-Host "  - MAILTRAP_INBOX_ID: <seu-inbox-id>" -ForegroundColor Gray
Write-Host "  - ADMIN_EMAIL: <admin@exemplo.com>" -ForegroundColor Gray
```

### 2. Configurar Application Settings na Function App

**Se você usou o script com os parâmetros do Mailtrap**, as variáveis já estarão configuradas automaticamente. Pule para a seção de Deploy.

**Se você não forneceu os parâmetros do Mailtrap**, configure manualmente:

#### Opção A: Via Azure CLI (Recomendado)

```powershell
$functionAppName = "feedback-function-<seu-sufixo>"
$resourceGroup = "feedback-rg"

# Configurar variáveis de ambiente do Mailtrap
az functionapp config appsettings set `
    --name $functionAppName `
    --resource-group $resourceGroup `
    --settings `
        "MAILTRAP_API_TOKEN=<seu-mailtrap-api-token>" `
        "MAILTRAP_INBOX_ID=<seu-mailtrap-inbox-id>" `
        "ADMIN_EMAIL=<admin@exemplo.com>"
```

#### Opção B: Re-executar o Script com Parâmetros do Mailtrap

Você pode executar o script novamente apenas para atualizar as configurações do Mailtrap (os recursos já existentes não serão recriados):

```powershell
.\scripts\criar-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Location "brazilsouth" `
    -Suffix "prod" `
    -MailtrapApiToken "seu-token-mailtrap" `
    -MailtrapInboxId "seu-inbox-id" `
    -AdminEmail "admin@exemplo.com"
```

**Nota**: O script detecta recursos existentes e apenas atualiza as configurações necessárias.

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

### Problema: Erro de conexão com Queue Storage

**Solução:**
1. Verificar `AZURE_STORAGE_CONNECTION_STRING` está configurada
2. Verificar se a fila `critical-feedbacks` existe (é criada automaticamente)
3. Verificar se o Azure Functions está processando a fila

### Problema: Email não está sendo enviado

**Solução:**
1. Verificar `MAILTRAP_API_TOKEN` está configurada
2. Verificar `MAILTRAP_INBOX_ID` está configurada
3. Verificar `ADMIN_EMAIL` está configurada
4. Verificar logs da NotifyAdminFunction para erros

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
- [ ] Resource Group criado (via script ou manualmente)
- [ ] Storage Account criado e container `weekly-reports` criado (via script ou manualmente)
- [ ] Function App criada (Java 21, Linux, Consumption) (via script ou manualmente)
- [ ] Application Settings configuradas:
  - [ ] `AZURE_STORAGE_CONNECTION_STRING` ✅ Configurada automaticamente pelo script
  - [ ] `AzureWebJobsStorage` ✅ Configurada automaticamente pelo script
  - [ ] `MAILTRAP_API_TOKEN` ⚠️ Configure via parâmetro `-MailtrapApiToken` ou manualmente
  - [ ] `MAILTRAP_INBOX_ID` ⚠️ Configure via parâmetro `-MailtrapInboxId` ou manualmente
  - [ ] `ADMIN_EMAIL` ⚠️ Configure via parâmetro `-AdminEmail` ou manualmente
  - [ ] `FUNCTIONS_WORKER_RUNTIME=java` ✅ Configurada automaticamente pelo script
  - [ ] `FUNCTIONS_EXTENSION_VERSION=~4` ✅ Configurada automaticamente pelo script
- [ ] Projeto compilado com sucesso
- [ ] Deploy realizado
- [ ] Function App está rodando
- [ ] Endpoint `/api/avaliacao` responde
- [ ] Health check `/health` responde
- [ ] Logs estão sendo gerados

**Legenda:**
- ✅ Configurado automaticamente pelo script `criar-recursos-azure.ps1`
- ⚠️ Requer configuração manual ou via parâmetros do script

---

## 📚 Recursos Adicionais

- **Repositório do Projeto**: [https://github.com/luizffdemoraes/feedback-sync.git](https://github.com/luizffdemoraes/feedback-sync.git)
- [Azure Functions Java Guide](https://docs.microsoft.com/azure/azure-functions/functions-reference-java)
- [Quarkus Azure Functions](https://quarkus.io/guides/azure-functions-http)
- [Azure Storage Documentation](https://docs.microsoft.com/azure/storage/)
- [Azure Queue Storage Documentation](https://docs.microsoft.com/azure/storage/queues/)
- [Mailtrap Documentation](https://mailtrap.io/docs/)

---

## 💰 Estimativa de Custos (Consumption Plan)

| Recurso | Custo Estimado (mensal) |
|---------|------------------------|
| Function App (Consumption) | ~$0.20 por 1M execuções |
| Storage Account (LRS) | ~$0.018/GB (inclui Table, Blob e Queue Storage) |
| Mailtrap (Free Tier) | $0 (até 500 emails/mês) |

**Total estimado**: ~$5-10/mês para uso moderado (sem Service Bus, reduzindo custos significativamente)

---

## 🗑️ Destruição de Recursos Azure

### ⚠️ ATENÇÃO

A destruição de recursos é uma operação **IRREVERSÍVEL**. Todos os dados serão perdidos permanentemente, incluindo:
- Todos os feedbacks armazenados no Table Storage
- Todos os relatórios semanais no Blob Storage
- Todas as configurações da Function App
- Application Settings e secrets

### Opção 1: Script Automatizado (Recomendado)

Use o script PowerShell para destruir todos os recursos:

```powershell
.\scripts\destruir-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Suffix "prod"
```

**Parâmetros:**
- `-ResourceGroupName`: Nome do Resource Group (padrão: "feedback-rg")
- `-Suffix`: Sufixo usado na criação dos recursos (padrão: "prod")
- `-Force`: Pula confirmação (use com cuidado!)
- `-DeleteResourceGroupOnly`: Deleta apenas o Resource Group (mais rápido)

**Exemplos de uso:**

```powershell
# Destruição com confirmação interativa
.\scripts\destruir-recursos-azure.ps1 -ResourceGroupName "feedback-rg" -Suffix "prod"

# Destruição rápida (deleta apenas o Resource Group)
.\scripts\destruir-recursos-azure.ps1 -ResourceGroupName "feedback-rg" -Suffix "prod" -DeleteResourceGroupOnly

# Destruição sem confirmação (cuidado!)
.\scripts\destruir-recursos-azure.ps1 -ResourceGroupName "feedback-rg" -Suffix "prod" -Force
```

### Opção 2: Destruição Manual via Azure CLI

#### Deletar recursos individualmente:

```powershell
$resourceGroup = "feedback-rg"
$suffix = "prod"
$functionAppName = "feedback-function-$suffix"
$storageAccountName = "feedbackstorage$suffix"

# 1. Deletar Function App
az functionapp delete --name $functionAppName --resource-group $resourceGroup --yes

# 2. Deletar Storage Account
az storage account delete --name $storageAccountName --resource-group $resourceGroup --yes

# 3. Deletar Resource Group (remove tudo que restou)
az group delete --name $resourceGroup --yes --no-wait
```

#### Deletar apenas o Resource Group (mais rápido):

```powershell
# Isso deleta TODOS os recursos dentro do Resource Group automaticamente
az group delete --name "feedback-rg" --yes --no-wait
```

### Opção 3: Destruição via Azure Portal

1. Acesse: https://portal.azure.com
2. Navegue até **Resource Groups**
3. Selecione o Resource Group `feedback-rg`
4. Clique em **Delete resource group**
5. Digite o nome do Resource Group para confirmar
6. Clique em **Delete**

### Verificar Status da Exclusão

```powershell
# Verificar se Resource Group ainda existe
az group show --name "feedback-rg"

# Listar todos os Resource Groups
az group list --output table

# Verificar logs de exclusão (via Portal)
# Portal Azure > Resource Groups > Deleted resources
```

### ⏱️ Tempo de Exclusão

- **Function App**: ~2-5 minutos
- **Storage Account**: ~5-10 minutos (depende do tamanho)
- **Resource Group**: ~10-15 minutos (processo completo)

A exclusão do Resource Group é assíncrona. Use `--no-wait` para não bloquear o terminal.

### 🔄 Recriar Recursos Após Destruição

Após destruir os recursos, você pode recriá-los usando o script de criação:

```powershell
.\scripts\criar-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Location "brazilsouth" `
    -Suffix "prod"
```

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

