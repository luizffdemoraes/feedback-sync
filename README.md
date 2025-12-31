# 🎓 Feedback Sync - **Tech Challenge 4ª Fase**

Sistema de Feedback Serverless para avaliação de aulas, desenvolvido com Azure Functions, Quarkus e Clean Architecture.

**Repositório**: [https://github.com/luizffdemoraes/feedback-sync.git](https://github.com/luizffdemoraes/feedback-sync.git)

## 📑 ÍNDICE

* [Descrição do Projeto](#descrição-do-projeto)
* [Funcionalidades e Endpoints](#funcionalidades-e-endpoints)
* [Azure Functions Serverless](#azure-functions-serverless)
* [Tecnologias Utilizadas](#tecnologias-utilizadas)
* [Estrutura do Projeto](#estrutura-do-projeto)
* [Clean Architecture](#clean-architecture)
* [Arquitetura da Solução](#arquitetura-da-solução)
* [Diagramas do Sistema](#diagramas-do-sistema)
* [Requisitos](#requisitos)
* [Como Rodar o Projeto](#como-rodar-o-projeto)
* [Deploy no Azure](#deploy-no-azure)
* [Monitoramento e Segurança](#monitoramento-e-segurança)
* [Cobertura de Código](#cobertura-de-código)
* [Documentação Adicional](#documentação-adicional)

---

## 📌 Descrição do Projeto

O **Feedback Sync** é uma plataforma serverless hospedada no Azure que permite:

* **Estudantes** podem avaliar aulas através de feedbacks com descrição e nota (0 a 10)
* **Administradores** recebem notificações automáticas para feedbacks críticos (nota ≤ 3)
* **Relatórios semanais** são gerados automaticamente com métricas consolidadas
* **Monitoramento** completo da aplicação através do Azure Monitor

O sistema foi desenvolvido seguindo os princípios de **Clean Architecture** e **Serverless Computing**, utilizando **Azure Functions** para processamento assíncrono e escalável.

---

## ⚙️ Funcionalidades e Endpoints

### 📝 Feedback Service

| Operação | Descrição | Acesso |
|----------|-----------|--------|
| `POST /avaliacao` | Recebe feedback de avaliação de aula | Público |

**Exemplo de Requisição:**
```json
{
  "descricao": "Aula muito boa, conteúdo claro e didático",
  "nota": 8,
  "urgencia": "MEDIUM"
}
```

**Resposta:**
```json
{
  "id": "uuid-do-feedback",
  "status": "recebido"
}
```

### 📊 Report Service

| Operação | Descrição | Acesso |
|----------|-----------|--------|
| `POST /relatorio` | Gera relatório semanal manualmente | Administrador |

**Resposta:**
```json
{
  "periodo_inicio": "2024-01-15T00:00:00Z",
  "periodo_fim": "2024-01-21T23:59:59Z",
  "total_avaliacoes": 150,
  "media_avaliacoes": 7.5,
  "avaliacoes_por_dia": {
    "2024-01-15": 20,
    "2024-01-16": 25
  },
  "avaliacoes_por_urgencia": {
    "LOW": 100,
    "MEDIUM": 40,
    "HIGH": 10
  },
  "report_url": "https://storage.blob.core.windows.net/weekly-reports/relatorios/..."
}
```

---

## ⚡ Azure Functions Serverless

O sistema implementa **duas funções serverless** seguindo o princípio de **Responsabilidade Única**:

### 🔔 NotifyAdminFunction

**Tipo**: Service Bus Trigger  
**Responsabilidade**: Processar notificações críticas de feedbacks

**Fluxo:**
1. Recebe mensagem do tópico `critical-feedbacks` do Azure Service Bus
2. Deserializa o feedback crítico (nota ≤ 3)
3. Envia notificação para administradores via e-mail
4. Registra logs de processamento

**Configuração:**
- **Tópico**: `critical-feedbacks`
- **Subscription**: `admin-notifications`
- **Trigger**: Automático quando feedback crítico é publicado

**Dados da Notificação:**
- Descrição do feedback
- Urgência (LOW, MEDIUM, HIGH)
- Data de envio
- Nota da avaliação

### 📈 WeeklyReportFunction

**Tipo**: Timer Trigger  
**Responsabilidade**: Gerar relatórios semanais automaticamente

**Fluxo:**
1. Dispara automaticamente toda segunda-feira às 08:00 (CRON: `0 0 8 * * MON`)
2. Busca todos os feedbacks da semana anterior
3. Calcula métricas (total, média, por dia, por urgência)
4. Gera arquivo JSON e salva no Azure Blob Storage
5. Retorna URL de acesso ao relatório

**Configuração:**
- **Schedule**: `0 0 8 * * MON` (Toda segunda às 08:00)
- **Storage**: Azure Blob Storage (container: `weekly-reports`)

**Dados do Relatório:**
- Período (início e fim)
- Total de avaliações
- Média de avaliações
- Quantidade de avaliações por dia
- Quantidade de avaliações por urgência
- URL do arquivo JSON gerado

---

## 🛠️ Tecnologias Utilizadas

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)
![Quarkus](https://img.shields.io/badge/Quarkus-4695EB?style=for-the-badge&logo=quarkus&logoColor=white)
![Azure Functions](https://img.shields.io/badge/Azure_Functions-0062AD?style=for-the-badge&logo=azure-functions&logoColor=white)
![Azure Storage](https://img.shields.io/badge/Azure_Storage-0078D4?style=for-the-badge&logo=microsoft-azure&logoColor=white)
![Azure Service Bus](https://img.shields.io/badge/Azure_Service_Bus-0078D4?style=for-the-badge&logo=microsoft-azure&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)

### Stack Técnica

* **Runtime**: Java 21
* **Framework**: Quarkus 3.29.0
* **Serverless**: Azure Functions (Consumption Plan)
* **Persistência**: Azure Table Storage (feedbacks)
* **Armazenamento**: Azure Blob Storage (relatórios)
* **Mensageria**: Azure Service Bus (notificações)
* **Build**: Maven 3.8+
* **Testes**: JUnit 5, Mockito, JaCoCo

---

## 📂 Estrutura do Projeto

```
feedback-sync/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── br/com/fiap/postech/feedback/
│   │   │       ├── application/          # Camada de Aplicação
│   │   │       │   ├── dtos/
│   │   │       │   │   ├── requests/
│   │   │       │   │   │   └── FeedbackRequest.java
│   │   │       │   │   └── responses/
│   │   │       │   │       ├── FeedbackResponse.java
│   │   │       │   │       └── WeeklyReportResponse.java
│   │   │       │   └── usecases/
│   │   │       │       ├── CreateFeedbackUseCase.java
│   │   │       │       ├── CreateFeedbackUseCaseImpl.java
│   │   │       │       ├── GenerateWeeklyReportUseCase.java
│   │   │       │       ├── GenerateWeeklyReportUseCaseImpl.java
│   │   │       │       ├── NotifyAdminUseCase.java
│   │   │       │       └── NotifyAdminUseCaseImpl.java
│   │   │       ├── domain/              # Camada de Domínio
│   │   │       │   ├── entities/
│   │   │       │   │   └── Feedback.java
│   │   │       │   ├── values/
│   │   │       │   │   ├── Score.java
│   │   │       │   │   └── Urgency.java
│   │   │       │   ├── exceptions/
│   │   │       │   │   ├── FeedbackDomainException.java
│   │   │       │   │   ├── FeedbackPersistenceException.java
│   │   │       │   │   └── NotificationException.java
│   │   │       │   └── gateways/
│   │   │       │       ├── FeedbackGateway.java
│   │   │       │       ├── NotificationGateway.java
│   │   │       │       └── ReportStorageGateway.java
│   │   │       └── infrastructure/      # Camada de Infraestrutura
│   │   │           ├── config/
│   │   │           │   ├── GlobalExceptionMapper.java
│   │   │           │   └── JacksonConfig.java
│   │   │           ├── controllers/
│   │   │           │   ├── FeedbackController.java
│   │   │           │   └── ReportController.java
│   │   │           ├── handlers/         # Azure Functions
│   │   │           │   ├── NotifyAdminFunction.java
│   │   │           │   ├── WeeklyReportFunction.java
│   │   │           │   └── FeedbackDeserializer.java
│   │   │           ├── gateways/
│   │   │           │   ├── TableStorageFeedbackGatewayImpl.java
│   │   │           │   ├── ServiceBusNotificationGatewayImpl.java
│   │   │           │   └── BlobReportStorageGatewayImpl.java
│   │   │           └── mappers/
│   │   │               └── TableStorageFeedbackMapper.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-local.properties
│   │       └── local.settings.json
│   └── test/
│       └── java/... (estrutura espelhada)
├── scripts/
│   ├── criar-recursos-azure.ps1
│   ├── executar-aplicacao.ps1
│   ├── implantar-azure.ps1
│   └── testar-aplicacao.ps1
├── collection/                          # Postman Collections
├── docker-compose.yml                   # Emuladores Azure locais
├── pom.xml
├── README.md
├── GUIA_DEPLOY_AZURE.md
├── GUIA_EXECUCAO_LOCAL.md
└── GUIA_TESTE_COMPLETO.md
```

---

## 🧹 Clean Architecture

O projeto segue os princípios da **Clean Architecture**, garantindo:

* **Separação de responsabilidades** entre camadas
* **Independência de frameworks** (Quarkus, Azure)
* **Testabilidade** através de interfaces (Gateways)
* **Manutenibilidade** com código organizado e documentado

### Camadas

#### 1. **Domain** (Núcleo)
* **Entidades**: `Feedback`
* **Value Objects**: `Score`, `Urgency`
* **Interfaces (Gateways)**: `FeedbackGateway`, `NotificationGateway`, `ReportStorageGateway`
* **Exceções de Domínio**: `FeedbackDomainException`, `NotificationException`

#### 2. **Application** (Casos de Uso)
* **Use Cases**:
  - `CreateFeedbackUseCase` - Criar feedback e notificar se crítico
  - `GenerateWeeklyReportUseCase` - Gerar relatório semanal
  - `NotifyAdminUseCase` - Enviar notificação para administradores
* **DTOs**: Requests e Responses

#### 3. **Infrastructure** (Implementações)
* **Controllers**: Endpoints REST (`FeedbackController`, `ReportController`)
* **Handlers**: Azure Functions (`NotifyAdminFunction`, `WeeklyReportFunction`)
* **Gateways**: Implementações concretas (Table Storage, Service Bus, Blob Storage)
* **Config**: Configurações (Exception Mapper, Jackson)

---

## 🏗️ Arquitetura da Solução

> 📊 **Diagramas Completos**: Consulte [docs/DIAGRAMAS.md](./docs/DIAGRAMAS.md) para visualizações detalhadas de arquitetura, sequência, fluxo de dados e componentes.

### Componentes Azure

| Componente | Tipo | Finalidade |
|------------|------|------------|
| **Function App** | Consumption Plan (Linux) | Host da aplicação serverless |
| **Table Storage** | Standard LRS | Persistência de feedbacks |
| **Blob Storage** | Standard LRS | Armazenamento de relatórios semanais |
| **Service Bus** | Standard | Tópico para notificações críticas |
| **Application Insights** | Monitoramento | Logs, métricas e rastreamento |

### Fluxo de Dados Simplificado

```
┌─────────────┐
│  Estudante  │
└──────┬──────┘
       │ POST /avaliacao
       ▼
┌─────────────────────┐
│ FeedbackController  │
└──────┬──────────────┘
       │
       ▼
┌──────────────────────────┐
│ CreateFeedbackUseCase    │
└──────┬───────────────────┘
       │
       ├──► Table Storage (persistir feedback)
       │
       └──► Service Bus (se nota ≤ 3)
              │
              ▼
       ┌──────────────────────┐
       │ NotifyAdminFunction   │ ◄── Service Bus Trigger
       └──────┬────────────────┘
              │
              └──► E-mail para Administradores

┌──────────────────────┐
│ WeeklyReportFunction  │ ◄── Timer Trigger (CRON)
└──────┬────────────────┘
       │
       ├──► Table Storage (buscar feedbacks)
       │
       └──► Blob Storage (salvar relatório)
```

> 💡 **Para diagramas detalhados**: Veja [Diagrama de Arquitetura Azure](./docs/DIAGRAMAS.md#-diagrama-de-arquitetura-azure) e [Diagrama de Sequência](./docs/DIAGRAMAS.md#-diagrama-de-sequência---criação-de-feedback) no arquivo de diagramas.

---

## 📊 Diagramas do Sistema

O projeto possui um conjunto completo de diagramas em Mermaid que documentam a arquitetura, fluxos e componentes do sistema.

### Diagramas Disponíveis

📄 **[Ver todos os diagramas →](./docs/DIAGRAMAS.md)**

| Diagrama | Descrição |
|----------|-----------|
| 🏗️ [Arquitetura Azure](./docs/DIAGRAMAS.md#-diagrama-de-arquitetura-azure) | Componentes Azure e suas interações |
| 🔄 [Sequência - Criação de Feedback](./docs/DIAGRAMAS.md#-diagrama-de-sequência---criação-de-feedback) | Fluxo completo de criação de feedback |
| 🔔 [Sequência - Notificação Crítica](./docs/DIAGRAMAS.md#-diagrama-de-sequência---notificação-de-feedback-crítico) | Processamento de feedbacks críticos |
| 📈 [Sequência - Relatório Semanal](./docs/DIAGRAMAS.md#-diagrama-de-sequência---geração-de-relatório-semanal) | Geração automática de relatórios |
| 🏛️ [Camadas - Clean Architecture](./docs/DIAGRAMAS.md#-diagrama-de-camadas---clean-architecture) | Estrutura de camadas do projeto |
| 🔧 [Componentes](./docs/DIAGRAMAS.md#-diagrama-de-componentes) | Componentes e suas dependências |
| 📊 [Fluxo de Dados Completo](./docs/DIAGRAMAS.md#-diagrama-de-fluxo-de-dados-completo) | Fluxograma completo do sistema |
| 🗄️ [Estrutura de Dados](./docs/DIAGRAMAS.md#-diagrama-de-dados---estrutura-de-armazenamento) | Modelo de dados e armazenamento |
| 🔐 [Segurança e Acesso](./docs/DIAGRAMAS.md#-diagrama-de-segurança-e-acesso) | Camadas de segurança e autenticação |

### Visualização dos Diagramas

Os diagramas são renderizados automaticamente em:
- **GitHub**: Visualização nativa de Mermaid
- **VS Code**: Com extensão Mermaid Preview
- **Documentação**: Qualquer visualizador Markdown compatível

---

### Segurança e Governança

* **Connection Strings**: Armazenadas como Application Settings (criptografadas)
* **Managed Identity**: Para acesso seguro aos recursos Azure
* **Network Security**: VNet integration (opcional)
* **Monitoring**: Application Insights com alertas configurados
* **Backup**: Retenção automática de dados no Storage

---

## 📋 Requisitos

### Software Necessário

* [Java 21](https://adoptium.net/)
* [Maven 3.8+](https://maven.apache.org/download.cgi) (ou use `mvnw` incluído)
* [Azure CLI](https://aka.ms/installazurecliwindows)
* [Docker](https://www.docker.com/) (para emuladores locais)
* [Git](https://git-scm.com/downloads) (opcional)

### Conta Azure

* Conta Azure ativa com permissões para criar recursos
* Subscription ativa no Azure

---

## ▶️ Como Rodar o Projeto

### 1. Clone o Repositório

```bash
git clone https://github.com/luizffdemoraes/feedback-sync.git
cd feedback-sync
```

### 2. Inicie os Emuladores Azure (Local)

   ```powershell
   docker-compose up -d
   ```

Isso iniciará:
* **Azurite** (Table Storage + Blob Storage) - Portas 10000, 10002
* **Service Bus Emulator** - Porta 5672
* **SQL Server** (requerido pelo Service Bus) - Porta 1433

### 3. Execute a Aplicação Localmente

```powershell
   .\mvnw.cmd quarkus:dev -Dquarkus.profile=local
   ```

A aplicação estará disponível em: `http://localhost:7071`

### 4. Teste a API

   ```powershell
# Criar feedback
   Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" `
     -Method Post `
  -Body '{"descricao":"Aula excelente!","nota":9,"urgencia":"LOW"}' `
  -ContentType "application/json"

# Gerar relatório manualmente
Invoke-RestMethod -Uri "http://localhost:7071/api/relatorio" `
  -Method Post `
     -ContentType "application/json"
   ```

### 5. Parar os Serviços

```powershell
docker-compose down -v
```

---

## 🚀 Deploy no Azure

### Pré-requisitos

1. **Azure CLI instalado e configurado**
2. **Login no Azure**:
   ```powershell
   az login
   ```

### Deploy Automatizado

Execute o script de deploy:

```powershell
.\scripts\implantar-azure.ps1
```

O script irá:
1. Criar Resource Group
2. Criar Storage Account (Table + Blob)
3. Criar Service Bus (Tópico + Subscription)
4. Criar Function App
5. Configurar Application Settings
6. Fazer deploy da aplicação

### Deploy Manual

Consulte o guia completo: **[GUIA_DEPLOY_AZURE.md](./GUIA_DEPLOY_AZURE.md)**

---

## 📊 Monitoramento e Segurança

### Monitoramento

* **Application Insights**: Logs, métricas e rastreamento de requisições
* **Azure Monitor**: Alertas para erros e performance
* **Health Checks**: Endpoint `/health` para verificação de saúde

### Segurança

* **Connection Strings**: Armazenadas como Application Settings (criptografadas)
* **HTTPS**: Obrigatório em produção
* **CORS**: Configurado para domínios específicos
* **Rate Limiting**: Configurável via Azure Functions

### Configurações de Segurança

```properties
# Application Settings (Azure Portal)
AZURE_STORAGE_CONNECTION_STRING=<connection-string>
AZURE_SERVICEBUS_CONNECTION_STRING=<connection-string>
AzureWebJobsStorage=<storage-connection-string>
```

---

## 📈 Cobertura de Código

Gerada com **JaCoCo**.

```bash
mvn clean test
mvn jacoco:report
```

O relatório estará disponível em:
```
target/site/jacoco/index.html
```

### Exemplo da Cobertura Gerada:

![Cobertura de Código](images/coverage-feedback-sync.png)

---

## 📚 Documentação Adicional

* **[docs/DIAGRAMAS.md](./docs/DIAGRAMAS.md)** - Diagramas completos de arquitetura, sequência e fluxo de dados
* **[GUIA_EXECUCAO_LOCAL.md](./GUIA_EXECUCAO_LOCAL.md)** - Guia detalhado de execução local
* **[GUIA_TESTE_COMPLETO.md](./GUIA_TESTE_COMPLETO.md)** - Guia completo de testes
* **[GUIA_DEPLOY_AZURE.md](./GUIA_DEPLOY_AZURE.md)** - Guia completo de deploy no Azure

---

## 🎯 Atendimento aos Requisitos do Tech Challenge

### ✅ Requisitos Atendidos

| Requisito | Status | Implementação |
|-----------|--------|---------------|
| **Ambiente Cloud** | ✅ | Azure Functions (Consumption Plan) |
| **Serverless** | ✅ | 2 Azure Functions (NotifyAdmin, WeeklyReport) |
| **Responsabilidade Única** | ✅ | Cada função tem responsabilidade específica |
| **Deploy Automatizado** | ✅ | Script PowerShell + Azure Functions Maven Plugin |
| **Monitoramento** | ✅ | Application Insights + Azure Monitor |
| **Notificações Automáticas** | ✅ | Service Bus + NotifyAdminFunction |
| **Relatório Semanal** | ✅ | Timer Trigger + WeeklyReportFunction |
| **Segurança** | ✅ | Connection Strings criptografadas, HTTPS |
| **Governança** | ✅ | Resource Groups, Tags, Policies |

### 📝 Endpoint de Entrada

**POST /avaliacao**
```json
{
  "descricao": "string",
  "nota": 8  // int (0 a 10)
}
```

### 📧 Dados do E-mail de Urgência

* Descrição
* Urgência (LOW, MEDIUM, HIGH)
* Data de envio

### 📊 Dados do Relatório Semanal

* Descrição
* Urgência
* Data de envio
* Quantidade de avaliações por dia
* Quantidade de avaliações por urgência
* Média de avaliações

---

## 🔄 Fluxo de Mensagens Service Bus

| Evento | Tópico | Subscription | Ação |
|--------|--------|--------------|------|
| **Feedback Crítico** | `critical-feedbacks` | `admin-notifications` | NotifyAdminFunction processa e envia e-mail |

---

## 🛡️ Regras de Validação

| Validação | Descrição | Implementação |
|-----------|-----------|---------------|
| **Nota Obrigatória** | Nota deve estar entre 0 e 10 | `Score` Value Object |
| **Descrição Obrigatória** | Descrição não pode ser vazia | Validação no Use Case |
| **Urgência Padrão** | Se não informada, assume LOW | `Urgency.of()` |
| **Feedback Crítico** | Nota ≤ 3 dispara notificação | `Score.isCritical()` |

---

## 📞 Suporte

Para dúvidas ou problemas, consulte a documentação adicional ou abra uma issue no repositório.

---

**Desenvolvido para o Tech Challenge da 4ª Fase - FIAP Postech**
