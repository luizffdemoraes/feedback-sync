# 🚀 Guia Completo de Deploy no Azure

Este guia detalha todos os passos necessários para instalar, configurar e fazer deploy da aplicação no Azure.

**Repositório**: [https://github.com/luizffdemoraes/feedback-sync.git](https://github.com/luizffdemoraes/feedback-sync.git)

## 📋 Índice

1. [🚀 Passo a Passo Rápido (Ordem de Execução)](#-passo-a-passo-rápido-ordem-de-execução)
2. [Pré-requisitos](#pré-requisitos)
3. [Recursos Azure Necessários](#recursos-azure-necessários)
4. [Instalação e Configuração](#instalação-e-configuração)
5. [Criação dos Recursos Azure](#criação-dos-recursos-azure)
6. [Configuração da Aplicação](#configuração-da-aplicação)
7. [Deploy da Aplicação](#deploy-da-aplicação)
8. [Validação e Testes](#validação-e-testes)
9. [Troubleshooting](#troubleshooting)

---

## 🚀 Passo a Passo Rápido (Ordem de Execução)

Siga estes passos **na ordem** para configurar e fazer deploy da aplicação no Azure:

### **Passo 1: Criar Recursos Azure**

Execute o script para criar todos os recursos necessários (Resource Group, Storage Account, Function App):

```powershell
.\scripts\criar-recursos-azure.ps1
```

**Opção com Mailtrap (recomendado):**
```powershell
.\scripts\criar-recursos-azure.ps1 `
    -MailtrapApiToken "seu-token" `
    -MailtrapInboxId "seu-inbox-id" `
    -AdminEmail "seu-email@exemplo.com"
```

**O que este script faz:**
- ✅ Cria Resource Group (`feedback-rg`)
- ✅ Cria Storage Account (`feedbackstorage<sufixo>`)
- ✅ Cria Function App (`feedback-function-prod`)
- ✅ Configura variáveis de ambiente básicas
- ✅ Configura Mailtrap (se parâmetros fornecidos)

**⏱️ Tempo estimado:** 3-5 minutos

---

### **Passo 2: Configurar Storage Connection String**

Se `AZURE_STORAGE_CONNECTION_STRING` não foi configurada automaticamente, execute:

```powershell
.\scripts\configurar-storage-connection.ps1
```

**O que este script faz:**
- ✅ Verifica se `AZURE_STORAGE_CONNECTION_STRING` está configurada
- ✅ Se não estiver, usa `AzureWebJobsStorage` como fallback
- ✅ Se não encontrar, obtém connection string do Storage Account
- ✅ Configura ambas as variáveis na Function App

**⏱️ Tempo estimado:** 30 segundos

---

### **Passo 3: Configurar Mailtrap (se não foi feito no Passo 1)**

Se você não forneceu as credenciais do Mailtrap no Passo 1, configure agora:

```powershell
az functionapp config appsettings set `
    --name feedback-function-prod `
    --resource-group feedback-rg `
    --settings `
        "MAILTRAP_API_TOKEN=seu-token" `
        "ADMIN_EMAIL=seu-email@exemplo.com" `
        "MAILTRAP_INBOX_ID=seu-inbox-id"
```

**Como obter credenciais do Mailtrap:**
1. Acesse: https://mailtrap.io
2. Crie uma conta gratuita
3. Vá em **Settings > API Tokens** e gere um token
4. Vá em **Settings > Inboxes** e copie o **Inbox ID**

**⏱️ Tempo estimado:** 2 minutos

---

### **Passo 4: Verificar Variáveis de Ambiente**

Verifique se todas as variáveis estão configuradas corretamente:

```powershell
.\scripts\verificar-variaveis-cloud.ps1
```

**O que este script verifica:**
- ✅ Variáveis obrigatórias (Storage, Runtime)
- ✅ Variáveis do Mailtrap (para envio de email)
- ✅ Diagnóstico do fluxo completo

**Resultado esperado:**
```
[OK] Todas as variáveis obrigatórias estão configuradas!
[OK] Mailtrap configurado - Emails serão enviados corretamente

Fluxo de Feedback:
   1. Recebimento de feedback (POST /api/avaliacao): [OK]
   2. Salvamento no Table Storage: [OK]
   3. Envio de email via Mailtrap (se crítico): [OK]
```

**⏱️ Tempo estimado:** 10 segundos

---

### **Passo 5: Fazer Deploy da Aplicação**

Compile e faça deploy da aplicação para a Function App:

```powershell
.\scripts\implantar-azure.ps1
```

**O que este script faz:**
- ✅ Compila o projeto (`mvn clean package`)
- ✅ Faz deploy via Azure Functions Core Tools
- ✅ Registra todas as funções na Function App

**⏱️ Tempo estimado:** 2-3 minutos

---

### **Passo 6: Testar a Aplicação**

Teste o endpoint de avaliação:

```bash
curl --location 'https://feedback-function-prod.azurewebsites.net/api/avaliacao' \
--header 'Content-Type: application/json' \
--data '{
    "descricao": "Aula muito confusa, não consegui entender o conteúdo. Preciso de ajuda urgente!",
    "nota": 2,
    "urgencia": "HIGH"
}'
```

**Resposta esperada (sucesso):**
```json
{
    "id": "uuid-do-feedback",
    "status": "recebido"
}
```

**Teste também o health check:**
```bash
curl --location 'https://feedback-function-prod.azurewebsites.net/api/health'
```

**⏱️ Tempo estimado:** 1 minuto

---

### **Passo 7: Verificar Logs e Email**

**Verificar logs em tempo real:**
```powershell
az functionapp log tail --name feedback-function-prod --resource-group feedback-rg
```

**Verificar email no Mailtrap:**
1. Acesse: https://mailtrap.io
2. Vá em **Inboxes** e selecione sua inbox
3. Você deve ver o email de notificação para feedbacks críticos (nota ≤ 3)

**⏱️ Tempo estimado:** 2 minutos

---

### **Passo 8: Verificar Logs (se email não for enviado)**

Se o email não estiver sendo enviado, verifique os logs da Function App:

```powershell
az functionapp log tail --name feedback-function-prod --resource-group feedback-rg
```

**Ou acesse o portal Azure:**
1. Acesse: https://portal.azure.com
2. Vá para: Function App → `feedback-function-prod` → Log stream
3. Procure por logs relacionados a:
   - `"Feedback crítico detectado"`
   - `"Enviando notificação por email"`
   - `"Email enviado com sucesso"`
   - `"ERRO"` (se houver falha)

**Verificar variáveis do Mailtrap:**
```powershell
.\scripts\verificar-variaveis-cloud.ps1
```
3. Verifique logs: `az functionapp log tail --name feedback-function-prod --resource-group feedback-rg`

**⏱️ Tempo estimado:** 10 segundos

---

## ✅ Checklist de Validação

Após seguir todos os passos, verifique:

- [ ] ✅ Recursos Azure criados (Resource Group, Storage Account, Function App)
- [ ] ✅ `AZURE_STORAGE_CONNECTION_STRING` configurada
- [ ] ✅ `AzureWebJobsStorage` configurada
- [ ] ✅ `MAILTRAP_API_TOKEN` configurado
- [ ] ✅ `ADMIN_EMAIL` configurado
- [ ] ✅ `MAILTRAP_INBOX_ID` configurado
- [ ] ✅ Deploy realizado com sucesso
- [ ] ✅ Endpoint `/api/health` retorna 200 OK
- [ ] ✅ Endpoint `/api/avaliacao` retorna 201 Created
- [ ] ✅ Email recebido no Mailtrap para feedbacks críticos

---

## 🔄 Resumo da Ordem de Execução

```
1. .\scripts\criar-recursos-azure.ps1
   ↓
2. .\scripts\configurar-storage-connection.ps1
   ↓
3. Configurar Mailtrap (se não foi feito no passo 1)
   ↓
4. .\scripts\verificar-variaveis-cloud.ps1
   ↓
5. .\scripts\implantar-azure.ps1
   ↓
6. Testar endpoints
   ↓
7. Verificar logs e emails
   ↓
8. Verificar logs e variáveis de ambiente (se necessário)
```

**⏱️ Tempo total estimado:** 10-15 minutos

---

## 🔍 Diagnosticar Problemas de Email

Se os emails não estão sendo enviados para feedbacks críticos, siga estes passos:

### **Verificar Variáveis de Ambiente**

```powershell
.\scripts\verificar-variaveis-cloud.ps1
```

**Verifique se estão configuradas:**
- ✅ `MAILTRAP_API_TOKEN`
- ✅ `MAILTRAP_INBOX_ID`
- ✅ `ADMIN_EMAIL`

### **Verificar Logs**

```powershell
az functionapp log tail --name feedback-function-prod --resource-group feedback-rg
```

**Ou acesse o portal Azure:**
- Function App → `feedback-function-prod` → Log stream

**Procure por:**
- `"Feedback crítico detectado"` → Feedback identificado como crítico
- `"Enviando notificação por email"` → Tentando enviar email
- `"Email enviado com sucesso"` → Email enviado
- `"ERRO"` → Erro no envio (ver detalhes)

### **Problemas Comuns e Soluções:**

1. **Variáveis do Mailtrap não configuradas:**
   - Configure usando: `az functionapp config appsettings set --name feedback-function-prod --resource-group feedback-rg --settings "MAILTRAP_API_TOKEN=..." "MAILTRAP_INBOX_ID=..." "ADMIN_EMAIL=..."`

2. **Feedback não é crítico:**
   - Apenas feedbacks com nota ≤ 3 disparam email
   - Teste com: `{"descricao":"Teste","nota":2,"urgencia":"HIGH"}`

3. **Erro no envio de email:**
   - Verifique logs para erro específico do Mailtrap
   - Verifique se o token e inbox ID estão corretos
   - Verifique se o email do admin está correto
   - Verifique se há erros de compilação

4. **Email não recebido:**
   - Verifique se as variáveis do Mailtrap estão configuradas: `.\scripts\verificar-variaveis-cloud.ps1`
   - Verifique os logs da função para erros
   - Confirme que o feedback tem nota ≤ 3 (crítico)

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
| **Function App** | Consumption Plan (Linux) | Host da aplicação serverless |
| **Resource Group** | - | Agrupa todos os recursos |
| **Mailtrap** | Free Tier | Envio de emails para notificações críticas |

### 🌍 Região Azure

**Região Padrão**: `northcentralus` (North Central US)

Todos os recursos são criados na mesma região para:
- ✅ Reduzir latência entre recursos
- ✅ Minimizar custos de transferência de dados
- ✅ Garantir compliance com requisitos regionais
- ✅ Otimizar performance da aplicação

**⚠️ Nota sobre Azure for Students**: A região `northcentralus` foi escolhida como padrão porque é compatível com assinaturas Azure for Students. Se sua subscription tiver restrições regionais, você pode especificar outra região usando o parâmetro `-Location` no script de criação.

**Regiões alternativas recomendadas** (se `northcentralus` não estiver disponível):
- `westus2` (West US 2)
- `centralus` (Central US)
- `eastus` (East US)

Para listar todas as regiões disponíveis na sua subscription:
```powershell
az account list-locations --query "[?metadata.regionCategory=='Recommended'].{Name:name, DisplayName:displayName}" -o table
```

### Detalhamento dos Recursos

#### 1. Storage Account
- **Tipo**: StorageV2 (General Purpose v2)
- **Performance**: Standard
- **Redundância**: LRS (Local Redundant Storage)
- **Recursos habilitados**:
  - Table Storage (para feedbacks)
  - Blob Storage (para relatórios semanais)

#### 2. Mailtrap
- **Tier**: Free Tier (suficiente para desenvolvimento e testes)
- **Finalidade**: Envio de emails para notificações críticas
- **Configuração**: Requer API Token e Inbox ID

#### 3. Function App
- **Runtime**: Java 21
- **OS**: Linux
- **Plan**: Consumption (Serverless)
- **Functions Extension**: ~4
- **Região**: `northcentralus` (padrão) ou conforme especificado

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
# Usando região padrão (northcentralus)
.\scripts\criar-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Suffix "prod"
```

**Nota**: Com este uso básico, você precisará configurar o Mailtrap manualmente depois (veja instruções abaixo).

#### Uso Completo (com Mailtrap - Recomendado)

Para configurar tudo automaticamente, incluindo as variáveis do Mailtrap:

```powershell
# Usando região padrão (northcentralus)
.\scripts\criar-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Suffix "prod" `
    -MailtrapApiToken "seu-token-mailtrap" `
    -MailtrapInboxId "seu-inbox-id" `
    -AdminEmail "admin@exemplo.com"
```

#### Especificando Região Personalizada

Se precisar usar uma região diferente (por exemplo, se `northcentralus` não estiver disponível na sua subscription):

```powershell
.\scripts\criar-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Location "westus2" `
    -Suffix "prod" `
    -MailtrapApiToken "seu-token-mailtrap" `
    -MailtrapInboxId "seu-inbox-id" `
    -AdminEmail "admin@exemplo.com"
```

**Parâmetros do Script:**

| Parâmetro | Obrigatório | Descrição | Padrão |
|-----------|-------------|-----------|--------|
| `ResourceGroupName` | Não | Nome do Resource Group | `feedback-rg` |
| `Location` | Não | Região do Azure onde os recursos serão criados | `northcentralus` |
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

### 1. Configurar Storage Connection String (Automático)

**Recomendado:** Use o script automatizado que descobre tudo automaticamente:

```powershell
.\scripts\configurar-storage-connection.ps1
```

**O que o script faz:**
- ✅ Descobre Resource Group, Function App e Storage Account automaticamente
- ✅ Verifica se `AZURE_STORAGE_CONNECTION_STRING` já está configurada
- ✅ Se não estiver, usa `AzureWebJobsStorage` como fallback (mais rápido)
- ✅ Se não encontrar, obtém connection string diretamente do Storage Account
- ✅ Configura ambas as variáveis na Function App

**Opção Manual (se necessário):**

Se preferir configurar manualmente:

```powershell
# Obter Connection String do Storage Account
$storageAccountName = "feedbackstorage<seu-sufixo>"
$storageConnectionString = az storage account show-connection-string `
    --name $storageAccountName `
    --resource-group "feedback-rg" `
    --query connectionString -o tsv

# Configurar na Function App
az functionapp config appsettings set `
    --name feedback-function-prod `
    --resource-group feedback-rg `
    --settings "AZURE_STORAGE_CONNECTION_STRING=$storageConnectionString"
```

### 2. Configurar Mailtrap (se não foi feito no Passo 1)

**Se você usou o script `criar-recursos-azure.ps1` com os parâmetros do Mailtrap**, as variáveis já estarão configuradas automaticamente. Pule para a seção de Deploy.

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

### 3. Configurar Agendamento do Relatório Semanal

Por padrão, o relatório é gerado **a cada 5 minutos** (`0 */5 * * * *`) para facilitar a visualização de resultados.

**Se desejar alterar o agendamento**, você pode configurar outras opções:

#### Opção A: A cada 5 minutos (padrão - para demonstração rápida)
```powershell
az functionapp config appsettings set `
    --name $functionAppName `
    --resource-group $resourceGroup `
    --settings "REPORT_SCHEDULE_CRON=0 */5 * * * *"
```

#### Opção B: A cada hora (para demonstração moderada)
```powershell
az functionapp config appsettings set `
    --name $functionAppName `
    --resource-group $resourceGroup `
    --settings "REPORT_SCHEDULE_CRON=0 0 * * * *"
```

#### Opção C: A cada 15 minutos (balanceado)
```powershell
az functionapp config appsettings set `
    --name $functionAppName `
    --resource-group $resourceGroup `
    --settings "REPORT_SCHEDULE_CRON=0 */15 * * * *"
```

#### Opção D: Voltar para semanal (produção)
```powershell
az functionapp config appsettings set `
    --name $functionAppName `
    --resource-group $resourceGroup `
    --settings "REPORT_SCHEDULE_CRON=0 0 8 * * MON"
```

**⚠️ Importante:**
- Após alterar o CRON, a Function App será reiniciada automaticamente
- O período do relatório continua sendo semanal (segunda até hoje), apenas a frequência de geração muda
- **Padrão configurado: a cada 5 minutos** para facilitar visualização de resultados
- Se desejar produção real, pode alterar para semanal (`0 0 8 * * MON`) para evitar custos desnecessários

#### Opção B: Re-executar o Script com Parâmetros do Mailtrap

Você pode executar o script novamente apenas para atualizar as configurações do Mailtrap (os recursos já existentes não serão recriados):

```powershell
# Usando região padrão (northcentralus)
.\scripts\criar-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Suffix "prod" `
    -MailtrapApiToken "seu-token-mailtrap" `
    -MailtrapInboxId "seu-inbox-id" `
    -AdminEmail "admin@exemplo.com"

# Ou especificando região personalizada
.\scripts\criar-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Location "westus2" `
    -Suffix "prod" `
    -MailtrapApiToken "seu-token-mailtrap" `
    -MailtrapInboxId "seu-inbox-id" `
    -AdminEmail "admin@exemplo.com"
```

**Nota**: O script detecta recursos existentes e apenas atualiza as configurações necessárias. Se você especificar uma região diferente da usada na criação inicial, o script avisará sobre a incompatibilidade.

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
    -FunctionAppName "feedback-function-prod" `
    -ResourceGroup "feedback-rg"
```

**Nota**: O script de deploy não requer o parâmetro `Location`, pois a Function App já foi criada na região correta pelo script `criar-recursos-azure.ps1`.

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
Invoke-RestMethod -Uri "$functionUrl/api/health" -Method Get
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

### Problema: Email não está sendo enviado para feedbacks críticos

**Sintomas:**
- Feedback crítico é criado com sucesso (retorna 201)
- Mas email não é recebido no Mailtrap
- Não há logs de envio de email

**Diagnóstico rápido:**

1. **Verificar variáveis do Mailtrap:**
   ```powershell
   .\scripts\verificar-variaveis-cloud.ps1
   ```
   Verifique se estão configuradas:
   - `MAILTRAP_API_TOKEN`
   - `MAILTRAP_INBOX_ID`
   - `ADMIN_EMAIL`

2. **Verificar logs em tempo real:**
   ```powershell
   az functionapp log tail --name feedback-function-prod --resource-group feedback-rg
   ```
   Ou acesse: Portal Azure → Function App → `feedback-function-prod` → Log stream
   
   Procure por:
   - `"Feedback crítico detectado"` → Confirma que feedback foi identificado como crítico
   - `"Enviando notificação por email"` → Confirma tentativa de envio
   - `"Email enviado com sucesso"` → Confirma envio bem-sucedido
   - `"ERRO"` → Indica problema (ver detalhes)

3. **Verificar se feedback é crítico:**
   - Apenas feedbacks com nota ≤ 3 disparam email
   - Teste com: `{"descricao":"Teste crítico","nota":2,"urgencia":"HIGH"}`

**Soluções:**

1. **Se variáveis não estão configuradas:**
   ```powershell
   az functionapp config appsettings set `
       --name feedback-function-prod `
       --resource-group feedback-rg `
       --settings `
           "MAILTRAP_API_TOKEN=seu-token" `
           "MAILTRAP_INBOX_ID=seu-inbox-id" `
           "ADMIN_EMAIL=seu-email@exemplo.com"
   ```

2. **Se há erro nos logs:**
   - Verifique se o token do Mailtrap está correto
   - Verifique se o Inbox ID está correto
   - Verifique se o email do admin está correto
   - Verifique se a conta Mailtrap está ativa

3. **Se não há logs de tentativa de envio:**
   - Verifique se o feedback tem nota ≤ 3
   - Verifique logs da `FeedbackHttpFunction` para confirmar processamento

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
- [ ] Health check `/api/health` responde
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
- [Mailtrap Documentation](https://mailtrap.io/docs/)

---

## 💰 Estimativa de Custos (Consumption Plan)

| Recurso | Custo Estimado (mensal) |
|---------|------------------------|
| Function App (Consumption) | ~$0.20 por 1M execuções |
| Storage Account (LRS) | ~$0.018/GB (inclui Table e Blob Storage) |
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
.\scripts\deletar-recursos-azure.ps1 `
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
.\scripts\deletar-recursos-azure.ps1 -ResourceGroupName "feedback-rg" -Suffix "prod"

# Destruição rápida (deleta apenas o Resource Group)
.\scripts\deletar-recursos-azure.ps1 -ResourceGroupName "feedback-rg" -Suffix "prod" -DeleteResourceGroupOnly

# Destruição sem confirmação (cuidado!)
.\scripts\deletar-recursos-azure.ps1 -ResourceGroupName "feedback-rg" -Suffix "prod" -Force
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
# Usando região padrão (northcentralus)
.\scripts\criar-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Suffix "prod"

# Ou especificando região personalizada
.\scripts\criar-recursos-azure.ps1 `
    -ResourceGroupName "feedback-rg" `
    -Location "westus2" `
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

