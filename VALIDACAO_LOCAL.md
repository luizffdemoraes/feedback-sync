# 🧪 Guia de Validação Local

Este guia explica como validar a implementação localmente usando Docker Compose e emuladores Azure.

## 📋 Pré-requisitos

- **Docker Desktop** instalado e rodando
- **Java 21** instalado
- **Maven** (ou use o `mvnw` incluído no projeto)
- **PowerShell** (Windows) ou **Bash** (Linux/Mac)

## 🚀 Iniciando o Ambiente Local

### Opção 1: Usando Scripts (Recomendado)

#### Windows (PowerShell)
```powershell
.\scripts\start-local.ps1
```

#### Linux/Mac (Bash)
```bash
chmod +x scripts/start-local.sh
./scripts/start-local.sh
```

### Opção 2: Manualmente

```bash
# Inicia os serviços Azure (Cosmos DB, Azurite, Service Bus)
docker-compose up -d

# Verifica se os serviços estão rodando
docker-compose ps

# Visualiza os logs
docker-compose logs -f
```

## 📦 Serviços Disponíveis

Após iniciar o Docker Compose, os seguintes serviços estarão disponíveis:

| Serviço | URL | Descrição |
|---------|-----|-----------|
| **Cosmos DB Emulator** | `https://localhost:8081` | Banco de dados NoSQL |
| **Azurite (Blob Storage)** | `http://localhost:10000` | Armazenamento de blobs |
| **Service Bus Emulator** | `http://localhost:8080` | Fila de mensagens (Management API) |
| | `localhost:5672` | AMQP endpoint |

## 🏃 Executando a Aplicação

### Modo Desenvolvimento (Hot Reload)

```bash
# Windows
.\mvnw.cmd quarkus:dev -Dquarkus.profile=local

# Linux/Mac
./mvnw quarkus:dev -Dquarkus.profile=local
```

A aplicação estará disponível em:
- **Azure Functions Local**: `http://localhost:7071`
- **Endpoint de Feedback**: `http://localhost:7071/api/avaliacao`

### Modo Produção (JAR)

```bash
# Compila o projeto
.\mvnw.cmd package -DskipTests

# Executa o JAR
java -jar target\feedback-sync-1.0.0-SNAPSHOT-runner.jar
```

## 🧪 Testando a API

### Opção 1: Usando Scripts de Teste

#### Windows (PowerShell)
```powershell
.\scripts\test-api.ps1
```

#### Linux/Mac (Bash)
```bash
chmod +x scripts/test-api.sh
./scripts/test-api.sh
```

### Opção 2: Usando cURL

#### Teste 1: Feedback Normal
```bash
curl -X POST http://localhost:7071/api/avaliacao \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Produto muito bom, recomendo!",
    "nota": 7,
    "urgencia": "LOW"
  }'
```

#### Teste 2: Feedback Crítico (Nota ≤ 3)
```bash
curl -X POST http://localhost:7071/api/avaliacao \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Produto com defeito grave, precisa de atenção urgente!",
    "nota": 2,
    "urgencia": "HIGH"
  }'
```

#### Teste 3: Validação - Nota Inválida
```bash
curl -X POST http://localhost:7071/api/avaliacao \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Teste de validação",
    "nota": 15
  }'
```

### Opção 3: Usando PowerShell (Invoke-RestMethod)

```powershell
$body = @{
    descricao = "Produto excelente!"
    nota = 8
    urgencia = "MEDIUM"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" `
  -Method Post `
  -Body $body `
  -ContentType "application/json"
```

## 🔍 Verificando os Dados

### Cosmos DB

1. Acesse o **Cosmos DB Data Explorer** em: `https://localhost:8081/_explorer/index.html`
2. Use a chave: `C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==`
3. Navegue até: `feedback-db` → `feedbacks`
4. Verifique os documentos salvos

### Azurite (Blob Storage)

Os relatórios semanais são salvos em:
- **Container**: `weekly-reports`
- **Endpoint**: `http://localhost:10000/devstoreaccount1/weekly-reports`

### Service Bus

1. Acesse o **Service Bus Management API**: `http://localhost:8080`
2. Verifique os tópicos e mensagens:
   - **Tópico**: `critical-feedbacks`
   - **Subscription**: `admin-notifications`

## 📊 Fluxo de Validação Completo

### 1. Iniciar Serviços
```bash
docker-compose up -d
```

### 2. Iniciar Aplicação
```bash
.\mvnw.cmd quarkus:dev -Dquarkus.profile=local
```

### 3. Criar Feedback Crítico
```bash
curl -X POST http://localhost:7071/api/avaliacao \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Feedback crítico para teste",
    "nota": 2,
    "urgencia": "HIGH"
  }'
```

### 4. Verificar Logs

**Aplicação**:
- Deve mostrar: "Feedback crítico detectado, enviando notificação"
- Deve mostrar: "Mensagem crítica publicada no Service Bus"

**Service Bus**:
- Verifique se a mensagem foi publicada no tópico `critical-feedbacks`

**Cosmos DB**:
- Verifique se o feedback foi salvo no container `feedbacks`

### 5. Verificar Notificação

A função `NotifyAdminFunction` deve processar a mensagem do Service Bus e:
- Logar: "Feedback crítico recebido"
- Enviar notificação ao admin

## 🛑 Parando o Ambiente

```bash
# Para os containers
docker-compose down

# Remove volumes (limpa dados)
docker-compose down -v
```

## ⚠️ Troubleshooting

### Docker não está rodando

**Problema**: Erro "unable to get image" ou "The system cannot find the file specified"

**Solução Rápida**:
```powershell
# Execute o script de diagnóstico e correção automática
.\scripts\fix-docker.ps1
```

Este script irá:
1. ✅ Verificar se o Docker está instalado
2. ✅ Verificar se o Docker está rodando
3. ✅ Verificar serviços Docker
4. ✅ Tentar iniciar o Docker Desktop automaticamente
5. ✅ Aguardar até o Docker estar pronto

**Solução Manual**:

1. **Verifique se o Docker Desktop está instalado e rodando**:
   ```powershell
   # Execute o script de verificação
   .\scripts\check-docker.ps1
   ```

2. **Se o Docker não estiver rodando**:
   ```powershell
   # Verificar serviços Docker
   Get-Service *docker*
   
   # Tentar iniciar o Docker Desktop
   Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
   
   # Aguarde 30-60 segundos até o Docker iniciar completamente
   # Procure pelo ícone da baleia na bandeja do sistema (deve ficar verde)
   ```

3. **Se o Docker Desktop não estiver instalado**:
   - Baixe em: https://www.docker.com/products/docker-desktop
   - Instale e reinicie o computador
   - Inicie o Docker Desktop

4. **Se o erro persistir**:
   ```powershell
   # Reinicie o Docker Desktop
   # Feche o Docker Desktop completamente
   # Abra novamente e aguarde iniciar
   
   # Ou reinicie os serviços Docker
   Restart-Service *docker*
   ```

### Cosmos DB não conecta

**Problema**: Erro de SSL/certificado

**Solução**: 
1. Baixe o certificado do emulador: `https://localhost:8081/_explorer/emulator.pem`
2. Importe no Java keystore ou configure `quarkus.tls.trust-all=true` (já configurado em `application-local.properties`)

### Service Bus não conecta

**Problema**: Erro de conexão na porta 5672

**Solução**:
1. Verifique se o container está rodando: `docker ps | grep servicebus`
2. Verifique os logs: `docker-compose logs servicebus`
3. Aguarde alguns segundos após iniciar (o emulador demora para inicializar)

### Azurite não conecta

**Problema**: Erro ao salvar blob

**Solução**:
1. Verifique se o container está rodando: `docker ps | grep azurite`
2. Verifique se a connection string está correta em `application-local.properties`
3. O container deve criar o container automaticamente, mas você pode criar manualmente se necessário

### Porta já em uso

**Problema**: Erro "port already in use"

**Solução**:
```bash
# Windows - Verificar processos nas portas
netstat -ano | findstr :8081
netstat -ano | findstr :10000
netstat -ano | findstr :5672

# Linux/Mac - Verificar processos nas portas
lsof -i :8081
lsof -i :10000
lsof -i :5672

# Parar containers conflitantes
docker-compose down
```

## 📝 Configurações Importantes

### application-local.properties

Este arquivo contém todas as configurações para ambiente local:
- Cosmos DB: endpoint, chave, database, container
- Service Bus: connection string, tópico
- Blob Storage: connection string, container
- SSL desabilitado para emuladores

### local.settings.json

Configurações específicas do Azure Functions:
- Porta local: `7071`
- CORS habilitado
- Connection strings dos serviços

## 🎯 Checklist de Validação

- [ ] Docker Compose iniciado com sucesso
- [ ] Todos os containers estão rodando (`docker-compose ps`)
- [ ] Aplicação iniciada sem erros
- [ ] Endpoint `/api/avaliacao` responde
- [ ] Feedback normal é salvo no Cosmos DB
- [ ] Feedback crítico dispara notificação no Service Bus
- [ ] Notificação é processada pela função `NotifyAdminFunction`
- [ ] Relatório semanal pode ser gerado (via timer ou manualmente)
- [ ] Blob Storage salva os relatórios corretamente

## 📚 Recursos Adicionais

- [Azure Cosmos DB Emulator](https://docs.microsoft.com/azure/cosmos-db/local-emulator)
- [Azurite Documentation](https://github.com/Azure/Azurite)
- [Azure Service Bus Emulator](https://github.com/Azure/azure-service-bus-emulator)
- [Quarkus Azure Functions](https://quarkus.io/guides/azure-functions-http)

