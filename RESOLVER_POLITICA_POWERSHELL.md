# 🔧 Resolver Política de Execução do PowerShell

## Problema

O PowerShell está bloqueando a execução de scripts com o erro:
```
a execução de scripts foi desabilitada neste sistema
```

## ✅ Solução Rápida (Recomendada)

### Opção 1: Executar com Bypass (Temporário)

Execute os scripts com bypass da política:

```powershell
# Verificar Docker
powershell -ExecutionPolicy Bypass -File .\scripts\fix-docker.ps1

# Iniciar serviços
powershell -ExecutionPolicy Bypass -File .\scripts\start-local.ps1

# Testar API
powershell -ExecutionPolicy Bypass -File .\scripts\test-api.ps1
```

### Opção 2: Alterar Política para o Usuário Atual (Permanente)

Execute no PowerShell **como Administrador**:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

Depois disso, você pode executar os scripts normalmente:
```powershell
.\scripts\fix-docker.ps1
.\scripts\start-local.ps1
```

### Opção 3: Executar Comandos Manualmente

Se preferir não alterar a política, execute os comandos diretamente:

#### 1. Verificar Docker
```powershell
docker info
```

Se não funcionar, abra o Docker Desktop manualmente.

#### 2. Iniciar Serviços
```powershell
docker-compose down
docker-compose up -d
```

#### 3. Verificar Containers
```powershell
docker ps
```

#### 4. Executar Aplicação
```powershell
.\mvnw.cmd quarkus:dev -Dquarkus.profile=local
```

#### 5. Testar API
```powershell
# Feedback normal
curl -X POST http://localhost:7071/api/avaliacao -H "Content-Type: application/json" -d '{\"descricao\": \"Teste\", \"nota\": 8}'

# Feedback crítico
curl -X POST http://localhost:7071/api/avaliacao -H "Content-Type: application/json" -d '{\"descricao\": \"Crítico\", \"nota\": 2}'
```

## 📋 Comandos Completos (Sem Scripts)

### Passo 1: Verificar Docker
```powershell
docker info
```

Se der erro, abra o Docker Desktop e aguarde iniciar.

### Passo 2: Iniciar Serviços
```powershell
docker-compose down
docker-compose up -d
```

### Passo 3: Aguardar Serviços (30 segundos)
```powershell
Start-Sleep -Seconds 30
docker ps
```

### Passo 4: Executar Aplicação (novo terminal)
```powershell
.\mvnw.cmd quarkus:dev -Dquarkus.profile=local
```

### Passo 5: Testar API (novo terminal)
```powershell
# Teste 1: Feedback normal
Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" -Method Post -Body '{"descricao":"Produto bom","nota":8,"urgencia":"LOW"}' -ContentType "application/json"

# Teste 2: Feedback crítico
Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" -Method Post -Body '{"descricao":"Produto com defeito","nota":2,"urgencia":"HIGH"}' -ContentType "application/json"
```

## 🔍 Verificar Política Atual

Para ver qual política está ativa:

```powershell
Get-ExecutionPolicy -List
```

## ⚠️ Importante

- **RemoteSigned**: Permite scripts locais e scripts assinados da internet (recomendado)
- **Bypass**: Remove todas as restrições (apenas para desenvolvimento)
- **Restricted**: Bloqueia todos os scripts (padrão no Windows)

A opção **RemoteSigned** é segura e permite executar scripts locais.

