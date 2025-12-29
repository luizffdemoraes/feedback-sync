# 📋 Guia de Deployment na Nuvem - Feedback Sync

## 🎯 Visão Geral

Este documento descreve o que **deve funcionar** e o que **precisa ser ajustado** para o pleno funcionamento da aplicação no ambiente Azure em nuvem.

---

## ✅ O QUE DEVE FUNCIONAR (Implementado e Pronto)

### 1. **Arquitetura Base**
- ✅ **Quarkus Framework** configurado para Azure Functions
- ✅ **Clean Architecture** implementada (Domain, Application, Infrastructure)
- ✅ **Dependency Injection** com CDI/Quarkus Arc
- ✅ **Health Checks** configurados (`/health`)

### 2. **Endpoints REST**
- ✅ **POST `/avaliacao`** - Recebe feedbacks via HTTP REST
- ✅ **Validação de entrada** (descrição, nota obrigatórias)
- ✅ **Tratamento de exceções** global (GlobalExceptionMapper)
- ✅ **Respostas JSON** padronizadas

### 3. **Persistência de Dados**
- ✅ **Azure Table Storage** implementado (`TableStorageFeedbackGatewayImpl`)
- ✅ **Criação automática de tabela** (`feedbacks`) se não existir
- ✅ **Salvamento de feedbacks** com ID e timestamp
- ✅ **Busca por período** (para relatórios semanais)

### 4. **Notificações Assíncronas**
- ✅ **Azure Service Bus** implementado (`ServiceBusNotificationGatewayImpl`)
- ✅ **Publicação de feedbacks críticos** (nota ≤ 3) no tópico `critical-feedbacks`
- ✅ **Envio assíncrono não-bloqueante** (não falha a requisição se Service Bus estiver indisponível)
- ✅ **Timeout configurado** (5 segundos)

### 5. **Azure Functions**
- ✅ **`notifyAdmin`** - Processa mensagens críticas do Service Bus
- ✅ **`weeklyReport`** - Gera relatórios semanais via Timer Trigger (segunda-feira 08:00)
- ✅ **Deserialização customizada** de Feedback (`FeedbackDeserializer`)

### 6. **Armazenamento de Relatórios**
- ✅ **Azure Blob Storage** implementado (`BlobReportStorageGatewayImpl`)
- ✅ **Criação automática de container** (`weekly-reports`) se não existir
- ✅ **Salvamento de relatórios JSON** com nome baseado em data
- ✅ **Geração de URL** do relatório salvo

### 7. **Configuração**
- ✅ **application.properties** preparado para produção (usa variáveis de ambiente)
- ✅ **application-local.properties** para desenvolvimento local
- ✅ **Suporte a múltiplos perfis** (local/production)

---

## ⚠️ O QUE PRECISA SER AJUSTADO PARA NUVEM

### 🔴 CRÍTICO - Configurações Obrigatórias

#### 1. **Variáveis de Ambiente no Azure Functions**

As seguintes variáveis de ambiente **DEVEM** ser configuradas no Azure Portal ou via Azure CLI:

```bash
# Azure Storage (Table + Blob)
AZURE_STORAGE_CONNECTION_STRING=<connection-string-da-storage-account>

# Azure Service Bus
AZURE_SERVICEBUS_CONNECTION_STRING=<connection-string-do-service-bus>

# Azure Functions Runtime
AzureWebJobsStorage=<connection-string-da-storage-account>
FUNCTIONS_WORKER_RUNTIME=java
FUNCTIONS_EXTENSION_VERSION=~4
```

**⚠️ IMPORTANTE:** A variável `AzureServiceBusConnection` também precisa ser configurada para o trigger do Service Bus funcionar:

```bash
AzureServiceBusConnection=<connection-string-do-service-bus>
```

**Localização:** Azure Portal → Function App → Configuration → Application Settings

---

#### 2. **Recursos Azure Necessários**

Certifique-se de que os seguintes recursos estão criados no Azure:

##### **Azure Storage Account**
- ✅ Criar Storage Account
- ✅ Habilitar **Table Storage** (não é habilitado por padrão)
- ✅ Habilitar **Blob Storage**
- ✅ Obter Connection String

##### **Azure Service Bus**
- ✅ Criar Service Bus Namespace
- ✅ Criar **Topic** chamado `critical-feedbacks`
- ✅ Criar **Subscription** chamada `admin-notifications` no tópico
- ✅ Obter Connection String (Shared Access Policy com permissões Send/Listen)

##### **Azure Functions**
- ✅ Criar Function App (Linux, Java 21, ~4 runtime)
- ✅ Configurar Application Settings (variáveis acima)
- ✅ Configurar Application Insights (recomendado)

---

#### 3. **Configuração do Service Bus Trigger**

O `NotifyAdminFunction` usa a anotação:
```java
@ServiceBusTopicTrigger(
    connection = "AzureServiceBusConnection"
)
```

**Ajuste necessário:**
- A variável `AzureServiceBusConnection` **deve** estar configurada nas Application Settings
- O valor deve ser a **connection string completa** do Service Bus
- Formato esperado: `Endpoint=sb://<namespace>.servicebus.windows.net/;SharedAccessKeyName=...;SharedAccessKey=...`

---

### 🟡 IMPORTANTE - Ajustes Recomendados

#### 4. **Performance do Table Storage**

**Problema atual:**
```143:154:src/main/java/br/com/fiap/postech/feedback/infrastructure/gateways/TableStorageFeedbackGatewayImpl.java
            // Table Storage não suporta queries complexas como Cosmos DB
            // Vamos buscar todas as entidades e filtrar em memória
            // Para produção com muitos dados, considere usar PartitionKey baseado em data
            
            List<Feedback> feedbacks = new ArrayList<>();
            
            logger.debug("Buscando feedbacks no período: {} até {}", fromDateTime, toDateTime);
            
            // Iterar sobre todas as entidades
            // Nota: Para grandes volumes, considere usar PartitionKey por data
            for (TableEntity entity : tableClient.listEntities()) {
```

**Recomendação:**
- Para produção com muitos dados, implementar **PartitionKey baseado em data** (ex: `YYYY-MM`)
- Isso permitirá queries mais eficientes e evitará buscar todas as entidades

**Impacto:** Alto volume de dados pode causar lentidão ou timeout

---

#### 5. **Tratamento de Erros no Service Bus**

**Comportamento atual:**
```64:73:src/main/java/br/com/fiap/postech/feedback/infrastructure/gateways/ServiceBusNotificationGatewayImpl.java
    public void publishCritical(Object payload) {
        // Verifica se o cliente está inicializado
        if (senderClient == null) {
            logger.warn("Service Bus não está disponível. Notificação crítica não será enviada.");
            return;
        }

        // Executa o envio de forma assíncrona para não bloquear a thread principal
        CompletableFuture.runAsync(() -> sendCriticalMessageAsync(payload), executorService);
    }
```

**Status:** ✅ Já implementado de forma resiliente - não bloqueia a requisição principal

---

#### 6. **Configuração de Logging**

**Ajuste recomendado:**
- Configurar **Application Insights** no Azure Functions
- Ajustar nível de log para `INFO` em produção (atualmente `DEBUG` no local)
- Configurar alertas para erros críticos

**Variável sugerida:**
```bash
quarkus.log.level=INFO
```

---

#### 7. **CORS (Cross-Origin Resource Sharing)**

**Se a aplicação será acessada de um frontend:**
- Configurar CORS no Azure Functions
- Adicionar headers CORS nas respostas do controller

**Exemplo de configuração:**
```java
@Path("/avaliacao")
@CrossOrigin(origins = "https://seu-frontend.com")
```

---

### 🟢 OPCIONAL - Melhorias Futuras

#### 8. **Monitoramento e Alertas**
- Configurar alertas no Application Insights
- Alertas para:
  - Falhas ao salvar feedbacks
  - Service Bus indisponível
  - Erros na geração de relatórios

#### 9. **Retry Policies**
- Implementar retry para operações do Table Storage
- Implementar retry para envio ao Service Bus (já tem timeout, mas pode ter retry)

#### 10. **Segurança**
- Usar **Managed Identity** ao invés de Connection Strings (mais seguro)
- Configurar **Key Vault** para armazenar secrets
- Implementar autenticação/autorização nos endpoints REST

#### 11. **Escalabilidade**
- Configurar **Auto-scaling** no Azure Functions
- Considerar **Premium Plan** se necessário maior performance
- Implementar **PartitionKey** no Table Storage (mencionado acima)

---

## 📝 Checklist de Deployment

### Pré-Deployment
- [ ] Criar Azure Storage Account (habilitar Table + Blob)
- [ ] Criar Azure Service Bus Namespace
- [ ] Criar Topic `critical-feedbacks` no Service Bus
- [ ] Criar Subscription `admin-notifications` no Topic
- [ ] Criar Azure Function App (Linux, Java 21, ~4)
- [ ] Obter todas as Connection Strings

### Configuração
- [ ] Configurar `AZURE_STORAGE_CONNECTION_STRING`
- [ ] Configurar `AZURE_SERVICEBUS_CONNECTION_STRING`
- [ ] Configurar `AzureServiceBusConnection` (mesmo valor do Service Bus)
- [ ] Configurar `AzureWebJobsStorage` (mesmo valor do Storage)
- [ ] Configurar `FUNCTIONS_WORKER_RUNTIME=java`
- [ ] Configurar `FUNCTIONS_EXTENSION_VERSION=~4`

### Validação
- [ ] Testar endpoint POST `/avaliacao` (deve retornar 201)
- [ ] Verificar se feedback foi salvo no Table Storage
- [ ] Verificar se feedback crítico foi publicado no Service Bus
- [ ] Verificar se `notifyAdmin` processou a mensagem
- [ ] Testar geração manual de relatório semanal
- [ ] Verificar se relatório foi salvo no Blob Storage
- [ ] Verificar logs no Application Insights

---

## 🔍 Troubleshooting

### Problema: Function não recebe mensagens do Service Bus
**Solução:**
- Verificar se `AzureServiceBusConnection` está configurada
- Verificar se Topic e Subscription existem
- Verificar permissões da Connection String (deve ter Listen)

### Problema: Erro ao salvar no Table Storage
**Solução:**
- Verificar se Table Storage está habilitado na Storage Account
- Verificar Connection String
- Verificar se tabela `feedbacks` foi criada (deve ser automático)

### Problema: Relatório semanal não é gerado
**Solução:**
- Verificar se Timer Trigger está configurado corretamente
- Verificar logs da Function `weeklyReport`
- Verificar se há feedbacks no período (última semana)

### Problema: Endpoint REST retorna 404
**Solução:**
- Verificar se está usando o caminho correto: `/api/avaliacao` (Azure Functions adiciona `/api` automaticamente)
- Verificar configuração do Quarkus REST path

---

## 📚 Referências

- [Azure Functions Java Documentation](https://docs.microsoft.com/azure/azure-functions/functions-reference-java)
- [Azure Table Storage Java SDK](https://docs.microsoft.com/azure/storage/tables/table-storage-overview)
- [Azure Service Bus Java SDK](https://docs.microsoft.com/azure/service-bus-messaging/service-bus-java-how-to-use-topics-subscriptions)
- [Quarkus Azure Functions Guide](https://quarkus.io/guides/azure-functions-http)

---

## 🎯 Resumo Executivo

### ✅ Funciona "Out of the Box"
1. Endpoint REST para receber feedbacks
2. Persistência no Table Storage
3. Notificações no Service Bus
4. Geração de relatórios semanais
5. Armazenamento de relatórios no Blob Storage

### ⚠️ Requer Configuração
1. **Variáveis de ambiente** no Azure Functions (CRÍTICO)
2. **Recursos Azure** criados (Storage, Service Bus, Functions)
3. **Topic e Subscription** do Service Bus criados

### 🔧 Melhorias Recomendadas
1. Implementar PartitionKey no Table Storage (performance)
2. Configurar Application Insights (monitoramento)
3. Considerar Managed Identity (segurança)

---

**Última atualização:** 2024

