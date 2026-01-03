# 🧪 Guia Completo de Teste da Aplicação

## 📋 Visão Geral do Fluxo

```
1. Docker Desktop → 2. Docker Compose → 3. Aplicação → 4. Testes
   (Serviços Azure)    (Azurite)            (Quarkus)      (API)
```

## 🚀 Passo a Passo Completo

### **PASSO 1: Verificar/Iniciar Docker Desktop**

O Docker Desktop precisa estar rodando para executar os containers.

**Verificar:**
```powershell
docker info
```

**Se não estiver rodando:**
- Abra o Docker Desktop manualmente
- Aguarde até o ícone na bandeja ficar verde (pode levar 1-2 minutos)

**Verificar:**
```powershell
docker info
```

---

### **PASSO 2: Iniciar Serviços Azure (Docker Compose)**

Este passo inicia o serviço Azure em container:
- **Azurite** (Table Storage + Blob Storage)

**Execute:**
```powershell
docker-compose up -d
```

**Verificar se iniciou:**
```powershell
docker ps
```

Você deve ver 1 container:
```
NAMES                  STATUS              PORTS
azurite                Up                  0.0.0.0:10000->10000/tcp, 0.0.0.0:10001->10001/tcp, 0.0.0.0:10002->10002/tcp
```

**Aguardar serviços ficarem prontos:**
```powershell
Start-Sleep -Seconds 30
```

**Aguardar serviços ficarem prontos:**
```powershell
Start-Sleep -Seconds 30
docker compose ps
```

---

### **PASSO 3: Executar a Aplicação**

Agora vamos iniciar a aplicação Quarkus que vai se conectar aos serviços Azure.

**Execute em um NOVO terminal:**
```powershell
.\mvnw.cmd quarkus:dev -Dquarkus.profile=local
```

**Aguarde até ver:**
```
Listening on: http://localhost:7071
```

Isso significa que a aplicação está rodando e pronta para receber requisições.

---

### **PASSO 4: Testar a API**

Agora vamos testar se tudo está funcionando!

**Execute em um NOVO terminal:**

#### Teste 1: Feedback Normal
```powershell
Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" `
  -Method Post `
  -Body '{"descricao":"Produto muito bom!","nota":8,"urgencia":"MEDIUM"}' `
  -ContentType "application/json"
```

**Resultado esperado:**
```json
{
  "id": "algum-uuid",
  "status": "recebido"
}
```

#### Teste 2: Feedback Crítico (Nota ≤ 3)
```powershell
Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" `
  -Method Post `
  -Body '{"descricao":"Produto com defeito grave!","nota":2,"urgencia":"HIGH"}' `
  -ContentType "application/json"
```

**O que deve acontecer:**
1. ✅ Feedback é salvo no Table Storage (Azurite)
2. ✅ Mensagem é publicada na fila `critical-feedbacks` (Azure Queue Storage)
3. ✅ NotifyAdminFunction processa a mensagem automaticamente (se Azure Functions estiver rodando)
4. ✅ Email é enviado via Mailtrap ao administrador

#### Teste 3: Validação - Nota Inválida
```powershell
Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" `
  -Method Post `
  -Body '{"descricao":"Teste","nota":15}' `
  -ContentType "application/json"
```

**Resultado esperado:** Erro 400 (Bad Request) - Nota deve estar entre 0 e 10

---

## 🔍 Verificações

### Verificar se os dados foram salvos no Table Storage

Os dados são salvos no Azurite (Table Storage). Você pode verificar através dos logs da aplicação ou usando ferramentas como Azure Storage Explorer conectando-se ao Azurite local.

### Verificar logs da aplicação

No terminal onde a aplicação está rodando, você deve ver:
- `Feedback processado com sucesso`
- `Notificação crítica enviada por email` (para notas ≤ 3)
- `Email enviado com sucesso para: [admin email]` (se Mailtrap estiver configurado e Azure Functions rodando)
- `Mailtrap API Token não configurado` (se não estiver configurado - apenas em desenvolvimento)

### Verificar logs dos containers

```powershell
docker-compose logs -f
```

---

## 📊 Resumo dos Terminais

Você precisa de **3 terminais** abertos:

### Terminal 1: Docker Compose
```powershell
# Iniciar serviços
docker-compose up -d

# Ver logs
docker-compose logs -f

# Parar serviços
docker-compose down
```

### Terminal 2: Aplicação
```powershell
.\mvnw.cmd quarkus:dev -Dquarkus.profile=local
```

### Terminal 3: Testes
```powershell
# Testar API
Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" -Method Post -Body '{"descricao":"Teste","nota":8}' -ContentType "application/json"
```

---

## ✅ Checklist de Validação

- [ ] Docker Desktop está rodando (`docker info`)
- [ ] 3 containers estão rodando (`docker ps`)
- [ ] Aplicação iniciou sem erros (terminal mostra "Listening on: http://localhost:7071")
- [ ] Endpoint `/api/avaliacao` responde
- [ ] Feedback normal é salvo no Table Storage
- [ ] Feedback crítico (nota ≤ 3) publica mensagem na fila e dispara notificação via Mailtrap
- [ ] Logs da aplicação mostram as operações

---

## 🛑 Parar Tudo

### Parar aplicação
No terminal da aplicação, pressione `Ctrl+C`

### Parar containers
```powershell
docker-compose down
```

### Parar e remover volumes (limpar dados)
```powershell
docker-compose down -v
```

---

## 🐛 Problemas Comuns

### Docker não conecta
- Aguarde 30-60 segundos após iniciar o Docker Desktop
- Verifique: `docker info`

### Aplicação não conecta ao Table Storage
- Aguarde 30 segundos após `docker-compose up -d`
- Verifique se o container está rodando: `docker ps`

### Porta já em uso
```powershell
# Verificar processos
netstat -ano | findstr :7071
netstat -ano | findstr :10000
netstat -ano | findstr :10002

# Parar containers
docker-compose down
```

---

## 💡 Dica

Para facilitar, você pode criar um script que executa tudo:

```powershell
# Terminal 1: Iniciar tudo
docker-compose up -d
Start-Sleep -Seconds 30
.\mvnw.cmd quarkus:dev -Dquarkus.profile=local
```

Mas é melhor manter separado para ver os logs de cada componente.

