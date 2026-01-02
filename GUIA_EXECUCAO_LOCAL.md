# 🚀 Guia de Execução Local

Este guia explica como executar a aplicação localmente, incluindo REST API e Azure Functions para processamento de filas.

## 📋 Pré-requisitos

- **Java 21** instalado
- **Maven** instalado
- **Docker** rodando (para Azurite)
- **Azure Functions Core Tools** (opcional, apenas para testar Queue Triggers localmente)

## 🔧 Instalação do Azure Functions Core Tools

### Por que instalar?

O Azure Functions Core Tools é necessário para executar Queue Triggers localmente (como o envio de email quando um feedback crítico é criado). Sem ele, as mensagens ficam na fila mas não são processadas.

### Como instalar

**Opção 1: Via Instalador MSI (Recomendado)**
1. Baixe o instalador: https://github.com/Azure/azure-functions-core-tools/releases
2. Execute o instalador `func-cli-x64.msi`
3. ⚠️ **IMPORTANTE:** Feche TODOS os terminais PowerShell/CMD e abra um novo terminal
4. Teste: `func --version` (deve mostrar algo como `4.x.x`)

**Opção 2: Via npm**
```powershell
npm install -g azure-functions-core-tools@4 --unsafe-perm true
```

**Opção 3: Via Chocolatey**
```powershell
choco install azure-functions-core-tools-4
```

### Verificar Instalação

⚠️ **IMPORTANTE:** Após instalar, feche e reabra o terminal PowerShell!

```powershell
func --version
```

**Se não funcionar:**
- Feche TODOS os terminais e abra um novo
- Ou reinicie a máquina
- Ou use o script `executar-azure-functions-local.ps1` que encontra automaticamente o func.exe

### Adicionar ao PATH (se necessário)

Se o `func` não for reconhecido após reiniciar o terminal:

**Solução Temporária:**
```powershell
$env:Path += ';C:\Program Files\Microsoft\Azure Functions Core Tools'
func --version
```

**Solução Permanente:**
1. Pressione `Win + R` e digite: `sysdm.cpl`
2. Aba **"Avançado"** → **"Variáveis de Ambiente"**
3. Em **"Variáveis do sistema"**, edite **"Path"**
4. Adicione: `C:\Program Files\Microsoft\Azure Functions Core Tools`
5. Reinicie o terminal

---

## 🎯 Opções de Execução

### Opção 1: Apenas REST API (Quarkus)

**Quando usar:** Para testar endpoints REST, criar feedbacks, gerar relatórios.

**Limitação:** Queue Triggers NÃO funcionam (emails não serão enviados automaticamente).

```powershell
# 1. Iniciar Docker (Azurite)
docker compose up -d

# 2. Executar aplicação
.\scripts\executar-aplicacao.ps1

# Ou manualmente:
$env:QUARKUS_PROFILE = "local"
.\mvnw.cmd quarkus:dev
```

**Aguarde até ver:**
```
Listening on: http://localhost:7071
```

### Opção 2: REST API + Azure Functions (Recomendado para testes completos)

**Quando usar:** Para testar o fluxo completo, incluindo envio de email.

**Terminal 1 - REST API (Quarkus):**
```powershell
# 1. Iniciar Docker (Azurite)
docker compose up -d

# 2. Executar REST API
$env:QUARKUS_PROFILE = "local"
.\mvnw.cmd quarkus:dev
```

**Terminal 2 - Azure Functions (Queue Triggers):**
```powershell
# 1. Configure variáveis de ambiente
$env:MAILTRAP_API_TOKEN = "seu-token-aqui"
$env:ADMIN_EMAIL = "seu-email@exemplo.com"

# 2. Execute o script automatizado
.\scripts\executar-azure-functions-local.ps1

# OU manualmente:
# 2.1. Compile o projeto
.\mvnw.cmd clean package -DskipTests

# 2.2. Execute Azure Functions
cd target\azure-functions\feedback-service-app
func start
```

### Opção 3: Apenas Azure Functions (via Maven)

**Quando usar:** Para testar apenas as Azure Functions sem REST API.

```powershell
# 1. Iniciar Docker (Azurite)
docker compose up -d

# 2. Configure variáveis de ambiente
$env:MAILTRAP_API_TOKEN = "seu-token-aqui"
$env:ADMIN_EMAIL = "seu-email@exemplo.com"

# 3. Execute via Maven
.\mvnw.cmd azure-functions:run
```

---

## ⚙️ Configuração

### Variáveis de Ambiente

Para que o envio de email funcione, configure:

```powershell
$env:MAILTRAP_API_TOKEN = "seu-token-aqui"
$env:ADMIN_EMAIL = "seu-email@exemplo.com"
```

### Arquivo local.settings.json

O arquivo `src/main/resources/local.settings.json` já está configurado. Se necessário, edite para adicionar credenciais:

```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "FUNCTIONS_WORKER_RUNTIME": "java",
    "FUNCTIONS_EXTENSION_VERSION": "~4",
    
    "mailtrap.api-token": "SEU_TOKEN_AQUI",
    "admin.email": "seu-email@exemplo.com",
    
    "azure.storage.connection-string": "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;TableEndpoint=http://localhost:10002/devstoreaccount1;BlobEndpoint=http://localhost:10000/devstoreaccount1;QueueEndpoint=http://localhost:10001/devstoreaccount1;",
    "azure.storage.container-name": "weekly-reports",
    "azure.table.table-name": "feedbacks",
    
    "APP_ENVIRONMENT": "local",
    "APP_DEBUG": "true"
  },
  "Host": {
    "LocalHttpPort": 7071,
    "CORS": "*",
    "CORSCredentials": false
  }
}
```

---

## 🧪 Testando o Fluxo Completo

### Passo 1: Iniciar Serviços

```powershell
# Iniciar Docker (Azurite)
docker compose up -d

# Verificar status
docker compose ps
```

**Aguarde até ver:** Todos os serviços com status `healthy`

### Passo 2: Iniciar Aplicação

**Opção A: Apenas REST API**
```powershell
.\scripts\executar-aplicacao.ps1
```

**Opção B: REST API + Azure Functions (2 terminais)**
- Terminal 1: `.\scripts\executar-aplicacao.ps1`
- Terminal 2: `.\scripts\executar-azure-functions-local.ps1`

### Passo 3: Testar Endpoints

**Criar feedback normal:**
```powershell
Invoke-RestMethod -Uri "http://localhost:7071/avaliacao" `
  -Method Post `
  -Body '{"descricao":"Aula excelente!","nota":9,"urgencia":"LOW"}' `
  -ContentType "application/json"
```

**Criar feedback crítico (dispara email):**
```powershell
Invoke-RestMethod -Uri "http://localhost:7071/avaliacao" `
  -Method Post `
  -Body '{"descricao":"Aula muito confusa, não consegui entender o conteúdo. Preciso de ajuda urgente!","nota":2,"urgencia":"HIGH"}' `
  -ContentType "application/json"
```

**Gerar relatório:**
```powershell
Invoke-RestMethod -Uri "http://localhost:7071/relatorio" `
  -Method Post `
  -ContentType "application/json"
```

### Passo 4: Verificar Logs

**Se Azure Functions estiver rodando**, você verá nos logs quando um feedback crítico for criado:

```
[2026-01-02T...] Executing 'Functions.notifyAdmin' (Reason='New queue message detected on 'critical-feedbacks'.', Id=...)
[2026-01-02T...] Processando feedback crítico - ID: xxx, Nota: 2
[2026-01-02T...] Email enviado com sucesso para seu-email@exemplo.com
[2026-01-02T...] Executed 'Functions.notifyAdmin' (Succeeded, Id=...)
```

---

## 📊 Verificar Status dos Serviços

### Ver status dos containers:
```powershell
docker compose ps
```

### Ver logs de um serviço específico:
```powershell
# Azurite
docker compose logs -f azurite

# Todos os serviços
docker compose logs -f
```

### Verificar saúde dos serviços:
```powershell
# Azurite
curl http://localhost:10000/devstoreaccount1
```

---

## 🛑 Parar Serviços

### Parar aplicação:
Pressione `Ctrl+C` no terminal onde a aplicação está rodando.

### Parar serviços Docker:
```powershell
docker compose down
```

### Limpar tudo:
```powershell
# Parar e remover containers e volumes
docker compose down -v
```

---

## 🔄 Reiniciar Tudo

### Reiniciar serviços Docker:
```powershell
docker compose restart
```

### Reiniciar apenas um serviço:
```powershell
docker compose restart azurite
```

### Limpar e reiniciar:
```powershell
docker compose down -v
docker compose up -d
```

---

## 🐛 Troubleshooting

### Problema: `func` não é reconhecido como comando

**Solução:**
1. Feche TODOS os terminais e abra um novo
2. Ou reinicie a máquina
3. Ou use o script `executar-azure-functions-local.ps1` que encontra automaticamente o func.exe
4. Ou adicione ao PATH manualmente (veja seção "Adicionar ao PATH")

### Problema: Porta 7071 já está em uso

**Solução:**
```powershell
# Encontrar processo usando a porta
netstat -ano | findstr :7071

# Parar processo (substitua PID pelo número encontrado)
taskkill /PID <PID> /F
```

Ou altere a porta no `local.settings.json`:
```json
"Host": {
  "LocalHttpPort": 7072
}
```

### Problema: Aplicação não conecta aos serviços

**Solução:**
1. Verifique se os serviços estão rodando: `docker compose ps`
2. Verifique se a aplicação está usando o profile correto: `$env:QUARKUS_PROFILE = "local"`
3. Verifique as connection strings em `application-local.properties`

### Problema: Docker não está rodando

**Solução:**
```powershell
# Verificar se Docker está rodando
docker info

# Se não estiver, inicie o Docker Desktop manualmente
```

### Problema: Queue Trigger não está sendo executado

**Solução:**
1. Verifique se o Azure Functions está rodando (Terminal 2)
2. Verifique se o Azurite está rodando: `docker ps`
3. Verifique se a fila `critical-feedbacks` existe
4. Verifique os logs do Azure Functions para erros

### Problema: Email não está sendo enviado

**Solução:**
1. Verifique se o Azure Functions está rodando (necessário para Queue Triggers)
2. Verifique se `MAILTRAP_API_TOKEN` está configurado
3. Verifique se `ADMIN_EMAIL` está configurado
4. Verifique os logs para erros do Mailtrap

---

## 📝 Scripts Disponíveis

| Script | Descrição |
|--------|-----------|
| `.\scripts\executar-aplicacao.ps1` | Executa apenas a aplicação REST (assume Docker rodando) |
| `.\scripts\executar-azure-functions-local.ps1` | Compila e executa Azure Functions localmente |
| `.\scripts\implantar-azure.ps1` | Script para implantação no Azure |
| `.\scripts\testar-aplicacao.ps1` | Script para testar a aplicação completa |

---

## 🎯 Resumo Rápido

### Para começar do zero (REST API apenas):
```powershell
docker compose up -d
.\scripts\executar-aplicacao.ps1
```

### Para testar fluxo completo (REST API + Azure Functions):
```powershell
# Terminal 1
docker compose up -d
.\scripts\executar-aplicacao.ps1

# Terminal 2
$env:MAILTRAP_API_TOKEN = "seu-token"
$env:ADMIN_EMAIL = "seu-email@exemplo.com"
.\scripts\executar-azure-functions-local.ps1
```

### Para testar:
```powershell
# Feedback crítico (dispara email se Azure Functions estiver rodando)
Invoke-RestMethod -Uri "http://localhost:7071/avaliacao" `
  -Method Post `
  -Body '{"descricao":"Teste","nota":2,"urgencia":"HIGH"}' `
  -ContentType "application/json"
```

### Para parar:
```powershell
# Parar aplicação: Ctrl+C
# Parar Docker: docker compose down
```

---

## 📚 Referências

- [Guia Completo de Teste](GUIA_TESTE_COMPLETO.md)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

---

## ⚠️ Notas Importantes

1. **Azure Functions Core Tools** é necessário apenas para executar Queue Triggers e Timer Triggers localmente
2. **REST API** pode ser executada separadamente com `quarkus:dev`
3. **Em produção**, o Azure Functions runtime processa automaticamente os triggers
4. **local.settings.json** é usado apenas localmente, não é deployado para o Azure
5. **Sem Azure Functions Core Tools**: Queue Triggers NÃO funcionam localmente (emails não são enviados automaticamente)
