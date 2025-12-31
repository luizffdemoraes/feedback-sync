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

## 🎯 Opção 1: Execução Manual

### Executar apenas aplicação (assumindo Docker já está rodando):

```powershell
.\scripts\executar-aplicacao.ps1
```

Ou manualmente:

```powershell
$env:QUARKUS_PROFILE = "local"
.\mvnw.cmd quarkus:dev
```

## 🎯 Opção 2: Manual (Passo a Passo)

### Passo 1: Iniciar Serviços Docker

```powershell
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
- ✅ Azurite: `healthy` (Table Storage + Blob Storage)

### Passo 3: Executar Aplicação Localmente

```powershell
.\scripts\executar-aplicacao.ps1
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
# Testar endpoint de feedback
Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" `
  -Method Post `
  -Body '{"descricao":"Teste","nota":8,"urgencia":"MEDIUM"}' `
  -ContentType "application/json"
```

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

## 🛑 Parar Serviços

### Parar serviços Docker:

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
docker compose restart azurite
```

### Limpar e reiniciar tudo:

```powershell
# Parar e remover containers
docker compose down

# Remover volumes (dados serão perdidos)
docker compose down -v

# Reiniciar
docker compose up -d
```

## 🐛 Troubleshooting

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
| `.\scripts\executar-aplicacao.ps1` | Executa apenas a aplicação (assume Docker rodando) |
| `.\scripts\implantar-azure.ps1` | Script para implantação no Azure |
| `.\scripts\testar-aplicacao.ps1` | Script para testar a aplicação completa |

## 🎯 Resumo Rápido

**Para começar do zero:**
```powershell
docker compose up -d
.\scripts\executar-aplicacao.ps1
```

**Se Docker já está rodando:**
```powershell
.\scripts\executar-aplicacao.ps1
```

**Para testar:**
```powershell
Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" `
  -Method Post `
  -Body '{"descricao":"Teste","nota":8,"urgencia":"MEDIUM"}' `
  -ContentType "application/json"
```

**Para parar:**
```powershell
# Parar aplicação: Ctrl+C
# Parar Docker: docker compose down
```

## 📚 Referências

- [Guia Completo de Teste](GUIA_TESTE_COMPLETO.md)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

