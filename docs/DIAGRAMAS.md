# 📊 Diagramas do Projeto Feedback Sync

Este documento contém todos os diagramas de arquitetura, fluxo e sequência do sistema.

---

## 🏗️ Diagrama de Arquitetura Azure

```mermaid
graph TB
    subgraph "Cliente"
        Estudante[👤 Estudante]
        Admin[👨‍💼 Administrador]
    end

    subgraph "Azure Function App"
        FuncApp[⚡ Function App<br/>Consumption Plan<br/>Linux + Java 21]
        
        subgraph "REST Endpoints"
            FeedbackCtrl[📝 FeedbackController<br/>POST /avaliacao]
            ReportCtrl[📊 ReportController<br/>POST /relatorio]
        end
        
        subgraph "Azure Functions"
            NotifyFunc[🔔 NotifyAdminFunction<br/>Service Bus Trigger]
            WeeklyFunc[📈 WeeklyReportFunction<br/>Timer Trigger CRON]
        end
        
        subgraph "Use Cases"
            CreateUC[CreateFeedbackUseCase]
            NotifyUC[NotifyAdminUseCase]
            ReportUC[GenerateWeeklyReportUseCase]
        end
    end

    subgraph "Azure Storage"
        TableStorage[(📋 Table Storage<br/>feedbacks)]
        BlobStorage[(📦 Blob Storage<br/>weekly-reports)]
    end

    subgraph "Azure Service Bus"
        ServiceBus[🚌 Service Bus<br/>Topic: critical-feedbacks<br/>Subscription: admin-notifications]
    end

    subgraph "Monitoramento"
        AppInsights[📊 Application Insights<br/>Logs e Métricas]
    end

    subgraph "Notificações"
        Email[📧 E-mail<br/>Administradores]
    end

    Estudante -->|POST /avaliacao| FeedbackCtrl
    FeedbackCtrl --> CreateUC
    CreateUC -->|Salvar| TableStorage
    CreateUC -->|Se crítico| ServiceBus
    
    ServiceBus -->|Trigger| NotifyFunc
    NotifyFunc --> NotifyUC
    NotifyUC --> Email
    
    WeeklyFunc -->|Timer CRON| ReportUC
    ReportUC -->|Buscar| TableStorage
    ReportUC -->|Salvar| BlobStorage
    
    Admin -->|POST /relatorio| ReportCtrl
    ReportCtrl --> ReportUC
    
    FuncApp -.->|Logs| AppInsights
    NotifyFunc -.->|Logs| AppInsights
    WeeklyFunc -.->|Logs| AppInsights

    style FuncApp fill:#0078D4,color:#fff
    style TableStorage fill:#0078D4,color:#fff
    style BlobStorage fill:#0078D4,color:#fff
    style ServiceBus fill:#0078D4,color:#fff
    style AppInsights fill:#0078D4,color:#fff
```

---

## 🔄 Diagrama de Sequência - Criação de Feedback

```mermaid
sequenceDiagram
    participant Estudante
    participant FeedbackController
    participant CreateFeedbackUseCase
    participant FeedbackGateway
    participant TableStorage
    participant NotificationGateway
    participant ServiceBus

    Estudante->>FeedbackController: POST /avaliacao<br/>{descricao, nota, urgencia}
    
    FeedbackController->>CreateFeedbackUseCase: execute(FeedbackRequest)
    
    CreateFeedbackUseCase->>CreateFeedbackUseCase: Validar dados<br/>(descrição, nota)
    CreateFeedbackUseCase->>CreateFeedbackUseCase: Criar entidade Feedback<br/>(Score, Urgency)
    
    CreateFeedbackUseCase->>FeedbackGateway: save(Feedback)
    FeedbackGateway->>TableStorage: Persistir feedback
    TableStorage-->>FeedbackGateway: Confirmação
    FeedbackGateway-->>CreateFeedbackUseCase: Feedback salvo
    
    alt Feedback é crítico (nota ≤ 3)
        CreateFeedbackUseCase->>NotificationGateway: publishCritical(Feedback)
        NotificationGateway->>ServiceBus: Publicar mensagem<br/>tópico: critical-feedbacks
        ServiceBus-->>NotificationGateway: Mensagem publicada
        NotificationGateway-->>CreateFeedbackUseCase: Sucesso
    end
    
    CreateFeedbackUseCase-->>FeedbackController: FeedbackResponse(id, status)
    FeedbackController-->>Estudante: HTTP 201 Created<br/>{id, status: "recebido"}
```

---

## 🔔 Diagrama de Sequência - Notificação de Feedback Crítico

```mermaid
sequenceDiagram
    participant ServiceBus
    participant NotifyAdminFunction
    participant NotifyAdminUseCase
    participant NotificationGateway
    participant EmailService
    participant Admin

    Note over ServiceBus: Mensagem publicada no tópico<br/>critical-feedbacks
    
    ServiceBus->>NotifyAdminFunction: Trigger<br/>Mensagem JSON (Feedback)
    
    NotifyAdminFunction->>NotifyAdminFunction: Deserializar Feedback<br/>(FeedbackDeserializer)
    
    NotifyAdminFunction->>NotifyAdminUseCase: execute(Feedback)
    
    NotifyAdminUseCase->>NotifyAdminUseCase: Preparar dados<br/>(descrição, urgência, data)
    
    NotifyAdminUseCase->>NotificationGateway: sendAdminNotification(Feedback)
    NotificationGateway->>EmailService: Enviar e-mail<br/>com dados do feedback crítico
    EmailService->>Admin: 📧 E-mail de notificação
    
    NotificationGateway-->>NotifyAdminUseCase: Notificação enviada
    NotifyAdminUseCase-->>NotifyAdminFunction: Sucesso
    NotifyAdminFunction-->>ServiceBus: Processamento concluído
```

---

## 📈 Diagrama de Sequência - Geração de Relatório Semanal

```mermaid
sequenceDiagram
    participant Timer
    participant WeeklyReportFunction
    participant GenerateWeeklyReportUseCase
    participant FeedbackGateway
    participant TableStorage
    participant ReportStorageGateway
    participant BlobStorage

    Note over Timer: CRON: 0 0 8 * * MON<br/>Toda segunda às 08:00
    
    Timer->>WeeklyReportFunction: Trigger Timer
    
    WeeklyReportFunction->>GenerateWeeklyReportUseCase: execute()
    
    GenerateWeeklyReportUseCase->>GenerateWeeklyReportUseCase: Calcular período<br/>(segunda até hoje)
    
    GenerateWeeklyReportUseCase->>FeedbackGateway: findByPeriod(start, end)
    FeedbackGateway->>TableStorage: Buscar feedbacks<br/>do período
    TableStorage-->>FeedbackGateway: Lista de Feedbacks
    FeedbackGateway-->>GenerateWeeklyReportUseCase: Feedbacks encontrados
    
    GenerateWeeklyReportUseCase->>GenerateWeeklyReportUseCase: Calcular métricas:<br/>- Total<br/>- Média<br/>- Por dia<br/>- Por urgência
    
    GenerateWeeklyReportUseCase->>ReportStorageGateway: saveWeeklyReport(JSON)
    ReportStorageGateway->>BlobStorage: Salvar arquivo JSON<br/>weekly-reports/relatorios/
    BlobStorage-->>ReportStorageGateway: URL do arquivo
    ReportStorageGateway-->>GenerateWeeklyReportUseCase: URL do relatório
    
    GenerateWeeklyReportUseCase-->>WeeklyReportFunction: WeeklyReportResponse<br/>(métricas + URL)
    
    WeeklyReportFunction->>WeeklyReportFunction: Log do relatório gerado
```

---

## 🏛️ Diagrama de Camadas - Clean Architecture

```mermaid
graph TB
    subgraph "Infrastructure Layer"
        Controllers[Controllers<br/>FeedbackController<br/>ReportController]
        Handlers[Azure Functions<br/>NotifyAdminFunction<br/>WeeklyReportFunction]
        GatewaysImpl[Gateways Implementations<br/>TableStorageFeedbackGatewayImpl<br/>ServiceBusNotificationGatewayImpl<br/>BlobReportStorageGatewayImpl]
        Config[Config<br/>GlobalExceptionMapper<br/>JacksonConfig]
    end

    subgraph "Application Layer"
        UseCases[Use Cases<br/>CreateFeedbackUseCase<br/>GenerateWeeklyReportUseCase<br/>NotifyAdminUseCase]
        DTOs[DTOs<br/>FeedbackRequest<br/>FeedbackResponse<br/>WeeklyReportResponse]
    end

    subgraph "Domain Layer"
        Entities[Entities<br/>Feedback]
        ValueObjects[Value Objects<br/>Score<br/>Urgency]
        Gateways[Gateways Interfaces<br/>FeedbackGateway<br/>NotificationGateway<br/>ReportStorageGateway]
        Exceptions[Domain Exceptions<br/>FeedbackDomainException<br/>NotificationException]
    end

    Controllers -->|usa| UseCases
    Handlers -->|usa| UseCases
    UseCases -->|usa| Entities
    UseCases -->|usa| ValueObjects
    UseCases -->|depende de| Gateways
    GatewaysImpl -->|implementa| Gateways
    UseCases -->|retorna| DTOs
    Entities -->|usa| ValueObjects
    Entities -->|lança| Exceptions

    style Domain fill:#4CAF50,color:#fff
    style Application fill:#2196F3,color:#fff
    style Infrastructure fill:#FF9800,color:#fff
```

---

## 🔧 Diagrama de Componentes

```mermaid
graph LR
    subgraph "Feedback Sync System"
        subgraph "API Layer"
            REST[REST API<br/>Quarkus]
        end
        
        subgraph "Business Logic"
            UC1[CreateFeedback<br/>UseCase]
            UC2[GenerateWeeklyReport<br/>UseCase]
            UC3[NotifyAdmin<br/>UseCase]
        end
        
        subgraph "Domain Model"
            FB[Feedback Entity]
            SC[Score VO]
            UR[Urgency VO]
        end
        
        subgraph "Infrastructure"
            TS[Table Storage<br/>Gateway]
            SB[Service Bus<br/>Gateway]
            BS[Blob Storage<br/>Gateway]
        end
        
        subgraph "Azure Functions"
            NF[NotifyAdmin<br/>Function]
            WF[WeeklyReport<br/>Function]
        end
        
        subgraph "External Services"
            AST[(Azure Table<br/>Storage)]
            ASB[Azure Service<br/>Bus]
            ABS[(Azure Blob<br/>Storage)]
            AI[Application<br/>Insights]
        end
    end

    REST --> UC1
    REST --> UC2
    UC1 --> FB
    UC2 --> FB
    UC3 --> FB
    FB --> SC
    FB --> UR
    UC1 --> TS
    UC1 --> SB
    UC2 --> TS
    UC2 --> BS
    UC3 --> SB
    TS --> AST
    SB --> ASB
    BS --> ABS
    ASB --> NF
    NF --> UC3
    WF --> UC2
    REST -.-> AI
    NF -.-> AI
    WF -.-> AI

    style REST fill:#4695EB,color:#fff
    style UC1 fill:#2196F3,color:#fff
    style UC2 fill:#2196F3,color:#fff
    style UC3 fill:#2196F3,color:#fff
    style FB fill:#4CAF50,color:#fff
    style AST fill:#0078D4,color:#fff
    style ASB fill:#0078D4,color:#fff
    style ABS fill:#0078D4,color:#fff
```

---

## 📊 Diagrama de Fluxo de Dados Completo

```mermaid
flowchart TD
    Start([Estudante cria feedback]) --> Input{POST /avaliacao}
    
    Input --> Validate[Validar dados<br/>descrição, nota 0-10]
    
    Validate -->|Inválido| Error1[400 Bad Request]
    Validate -->|Válido| Create[CreateFeedbackUseCase]
    
    Create --> Save[Salvar no Table Storage]
    Save --> Check{Feedback crítico?<br/>nota ≤ 3}
    
    Check -->|Não| Success1[201 Created<br/>ID retornado]
    Check -->|Sim| Publish[Publicar no Service Bus<br/>tópico: critical-feedbacks]
    
    Publish --> Success1
    
    Publish -.->|Mensagem| ServiceBus[Service Bus]
    ServiceBus -.->|Trigger| NotifyFunc[NotifyAdminFunction]
    NotifyFunc --> NotifyUC[NotifyAdminUseCase]
    NotifyUC --> Email[Enviar e-mail<br/>para administradores]
    
    Timer[Timer CRON<br/>Segunda 08:00] --> WeeklyFunc[WeeklyReportFunction]
    WeeklyFunc --> ReportUC[GenerateWeeklyReportUseCase]
    ReportUC --> Fetch[Buscar feedbacks<br/>da semana]
    Fetch --> Calc[Calcular métricas<br/>média, total, por dia, urgência]
    Calc --> SaveReport[Salvar JSON<br/>no Blob Storage]
    SaveReport --> Return[Retornar URL<br/>do relatório]
    
    Manual[POST /relatorio] --> ReportUC
    
    style Start fill:#E3F2FD
    style Success1 fill:#C8E6C9
    style Error1 fill:#FFCDD2
    style Email fill:#FFF9C4
    style Return fill:#C8E6C9
```

---

## 🗄️ Diagrama de Dados - Estrutura de Armazenamento

```mermaid
erDiagram
    FEEDBACK ||--o{ TABLE_STORAGE : "armazenado em"
    WEEKLY_REPORT ||--o{ BLOB_STORAGE : "armazenado em"
    
    FEEDBACK {
        string id PK
        string description
        int score
        string urgency
        datetime createdAt
    }
    
    TABLE_STORAGE {
        string PartitionKey "id"
        string RowKey "timestamp"
        string Description
        int Score
        string Urgency
        datetime CreatedAt
    }
    
    WEEKLY_REPORT {
        string fileName PK
        json reportData
        datetime generatedAt
        string url
    }
    
    BLOB_STORAGE {
        string container "weekly-reports"
        string blobName "relatorios/YYYY-MM-DD.json"
        json content
        datetime lastModified
    }
    
    SERVICE_BUS_TOPIC {
        string topicName "critical-feedbacks"
        string subscription "admin-notifications"
        json message
        datetime enqueuedTime
    }
```

---

## 🔐 Diagrama de Segurança e Acesso

```mermaid
graph TB
    subgraph "Camada Externa"
        Client[Cliente HTTP]
    end

    subgraph "Azure Function App"
        HTTPS[HTTPS Endpoint<br/>TLS/SSL]
        Auth[Validação de Request]
        Controller[Controllers]
    end

    subgraph "Application Settings"
        ConnStrings[Connection Strings<br/>Criptografadas]
        Secrets[Secrets<br/>Managed Identity]
    end

    subgraph "Azure Resources"
        Table[(Table Storage<br/>Acesso via<br/>Connection String)]
        Blob[(Blob Storage<br/>Acesso via<br/>Connection String)]
        ServiceBus[Service Bus<br/>Acesso via<br/>Connection String]
    end

    subgraph "Monitoramento"
        AppInsights[Application Insights<br/>Logs Seguros]
        Monitor[Azure Monitor<br/>Alertas]
    end

    Client -->|HTTPS| HTTPS
    HTTPS --> Auth
    Auth --> Controller
    Controller --> ConnStrings
    ConnStrings --> Table
    ConnStrings --> Blob
    ConnStrings --> ServiceBus
    Controller -.->|Logs| AppInsights
    AppInsights -.-> Monitor

    style HTTPS fill:#4CAF50,color:#fff
    style ConnStrings fill:#FF9800,color:#fff
    style AppInsights fill:#2196F3,color:#fff
```

---

## 📝 Legenda dos Diagramas

### Símbolos Utilizados

| Símbolo | Significado |
|---------|-------------|
| ⚡ | Azure Function |
| 📝 | Controller/Endpoint REST |
| 🔔 | Função de Notificação |
| 📈 | Função de Relatório |
| 📋 | Table Storage |
| 📦 | Blob Storage |
| 🚌 | Service Bus |
| 📊 | Application Insights |
| 📧 | E-mail |
| 👤 | Usuário/Cliente |
| 👨‍💼 | Administrador |

### Cores nos Diagramas

- **Azul (#0078D4)**: Serviços Azure
- **Verde (#4CAF50)**: Camada de Domínio / Segurança
- **Laranja (#FF9800)**: Camada de Infraestrutura
- **Azul Claro (#2196F3)**: Camada de Aplicação / Monitoramento

---

**Última atualização**: 2024
