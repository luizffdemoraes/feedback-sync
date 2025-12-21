# 🚀 Guia de Execução Local

Este guia explica como executar a aplicação localmente, separada dos serviços Docker.

## 📋 Fluxo de Execução

```
1. Iniciar Serviços Docker (emuladores Azure)
   ↓
2. Aguardar serviços estarem prontos
   ↓
3. Executar aplicação localmente (Quarkus)
   ↓
4. Testar a aplicação
```

## 🎯 Opção 1: Script Automatizado (Recomendado)

### Iniciar tudo de uma vez:

```powershell
.\scripts\iniciar-ambiente-local.ps1
```

Este script:
- ✅ Verifica se Docker está rodando
- ✅ Constrói a imagem customizada do Service Bus
- ✅ Inicia todos os serviços Docker
- ✅ Aguarda serviços estarem prontos (healthcheck)
- ✅ Prepara ambiente para executar aplicação
- ✅ Pergunta se deseja executar a aplicação agora

### Executar apenas Docker (sem aguardar):

```powershell
.\scripts\iniciar-ambiente-local.ps1 -ApenasDocker
```

### Executar apenas aplicação (assumindo Docker já está rodando):

```powershell
.\executar-app.ps1
```

## 🎯 Opção 2: Manual (Passo a Passo)

### Passo 1: Iniciar Serviços Docker

```powershell
# Construir imagem customizada do Service Bus (primeira vez)
docker compose build servicebus

# Iniciar todos os serviços
docker compose up -d

# Verificar status
docker compose ps
```

### Passo 2: Aguardar Serviços Estarem Prontos

Aguarde até ver todos os serviços com status `healthy`:

```powershell
# Verificar status continuamente
docker compose ps

# Ou ver logs
docker compose logs -f
```

**Serviços que devem estar prontos:**
- ✅ Cosmos DB: `healthy`
- ✅ Azurite: `healthy`
- ✅ SQL Server: `healthy`
- ✅ Service Bus: `healthy` (pode demorar ~2 minutos)

### Passo 3: Executar Aplicação Localmente

```powershell
.\executar-app.ps1
```

Ou manualmente:

```powershell
$env:QUARKUS_PROFILE = "local"
.\mvnw.cmd quarkus:dev
```

**Aguarde até ver:**
```
Listening on: http://localhost:7071
```

### Passo 4: Testar a Aplicação

```powershell
# Validar todos os fluxos
.\scripts\validar-fluxos.ps1
```

## 📊 Verificar Status dos Serviços

### Ver status dos containers:

```powershell
docker compose ps
```

### Ver logs de um serviço específico:

```powershell
# Service Bus
docker compose logs -f servicebus

# Cosmos DB
docker compose logs -f cosmosdb

# Todos os serviços
docker compose logs -f
```

### Verificar saúde dos serviços:

```powershell
# Service Bus
curl http://localhost:8080/health

# Cosmos DB
curl -k https://localhost:8081/_explorer/emulator.pem

# Azurite
curl http://localhost:10000/devstoreaccount1
```

## 🛑 Parar Serviços

### Parar serviços Docker:

```powershell
.\scripts\parar-servicos.ps1
```

Ou manualmente:

```powershell
docker compose down
```

### Parar aplicação:

Pressione `Ctrl+C` no terminal onde a aplicação está rodando.

## 🔄 Reiniciar Tudo

### Reiniciar serviços Docker:

```powershell
docker compose restart
```

### Reiniciar apenas um serviço:

```powershell
docker compose restart servicebus
```

### Limpar e reiniciar tudo:

```powershell
# Parar e remover containers
docker compose down

# Remover volumes (dados serão perdidos)
docker compose down -v

# Reiniciar
.\scripts\iniciar-ambiente-local.ps1
```

## 🐛 Troubleshooting

### Service Bus não inicia

```powershell
# Ver logs
docker compose logs servicebus

# Reconstruir imagem
docker compose build --no-cache servicebus
docker compose up -d servicebus
```

### Aplicação não conecta aos serviços

1. Verifique se os serviços estão rodando:
   ```powershell
   docker compose ps
   ```

2. Verifique se a aplicação está usando o profile correto:
   ```powershell
   # Deve estar usando application-local.properties
   $env:QUARKUS_PROFILE = "local"
   ```

3. Verifique as connection strings em `application-local.properties`

### Porta 7071 já em uso

```powershell
# Encontrar processo usando a porta
netstat -ano | findstr :7071

# Parar processo (substitua PID pelo número encontrado)
taskkill /PID <PID> /F
```

### Docker não está rodando

```powershell
# Verificar se Docker está rodando
docker info

# Se não estiver, inicie o Docker Desktop manualmente
```

## 📝 Scripts Disponíveis

| Script | Descrição |
|--------|-----------|
| `.\scripts\iniciar-ambiente-local.ps1` | Inicia Docker e prepara para executar aplicação |
| `.\executar-app.ps1` | Executa apenas a aplicação (assume Docker rodando) |
| `.\scripts\parar-servicos.ps1` | Para serviços Docker |
| `.\scripts\validar-fluxos.ps1` | Valida todos os fluxos da aplicação |
| `.\scripts\verificar-logs.ps1` | Ver logs dos serviços Docker |

## 🎯 Resumo Rápido

**Para começar do zero:**
```powershell
.\scripts\iniciar-ambiente-local.ps1
```

**Se Docker já está rodando:**
```powershell
.\executar-app.ps1
```

**Para testar:**
```powershell
.\scripts\validar-fluxos.ps1
```

**Para parar:**
```powershell
# Parar aplicação: Ctrl+C
# Parar Docker: .\scripts\parar-servicos.ps1
```

## 📚 Referências

- [Guia de Correção do Service Bus](GUIA_SERVICEBUS_FIX.md)
- [Validação Local](VALIDACAO_LOCAL.md)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

