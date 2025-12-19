# Guia de Correção - Erro 404 (Função Não Registrada)

## Problema Identificado

O endpoint está retornando **404 - Função não registrada**, mesmo com a aplicação rodando.

## Causas Possíveis

1. **Múltiplos processos Java** rodando simultaneamente
2. **Funções não foram registradas** durante a inicialização
3. **Rota incorreta** sendo usada nos testes
4. **Cache do Maven** desatualizado

## Solução Passo a Passo

### 1. Execute o Script de Correção

```powershell
.\scripts\corrigir-problemas.ps1
```

Este script irá:
- Parar todos os processos Java
- Limpar o cache do Maven
- Recompilar o projeto
- Verificar containers Docker

### 2. Reinicie a Aplicação

```powershell
.\executar-app.ps1
```

**IMPORTANTE:** Aguarde ver a mensagem:
```
Listening on: http://localhost:7071
```

### 3. Verifique os Logs

Procure por estas mensagens nos logs:
- ✅ `Function submitFeedback registered`
- ✅ `Function notifyAdmin registered`
- ✅ `Function weeklyReport registered`

Se essas mensagens **NÃO aparecerem**, há um problema de inicialização.

### 4. Teste o Endpoint Correto

O endpoint correto é:
```
POST http://localhost:7071/api/avaliacao
```

**NÃO** use apenas `/avaliacao` - o Azure Functions adiciona o prefixo `/api` automaticamente.

### 5. Exemplo de Teste

```powershell
$body = @{
    descricao = "Teste de feedback"
    nota = 5
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" `
    -Method POST `
    -Body $body `
    -ContentType "application/json"
```

## Verificações Adicionais

### Verificar se a Porta está Livre

```powershell
Get-NetTCPConnection -LocalPort 7071
```

Se houver processos usando a porta, pare-os primeiro.

### Verificar Containers Docker

```powershell
docker ps
```

Todos os containers devem estar com status `Up` e `healthy`.

### Limpar e Recompilar Manualmente

Se o script não funcionar:

```powershell
# Parar processos Java
Get-Process -Name "java" | Stop-Process -Force

# Limpar cache
Remove-Item -Path "target" -Recurse -Force

# Recompilar
$env:QUARKUS_PROFILE="local"
.\mvnw.cmd clean compile

# Executar
.\executar-app.ps1
```

## Problemas Comuns

### Problema: Funções não aparecem nos logs

**Solução:**
1. Verifique se todas as classes estão anotadas com `@FunctionName`
2. Verifique se o `pom.xml` tem a dependência `quarkus-azure-functions-http`
3. Limpe e recompile o projeto

### Problema: Endpoint retorna 404 mesmo após reiniciar

**Solução:**
1. Verifique se está usando `/api/avaliacao` (com `/api`)
2. Verifique se a aplicação terminou de inicializar
3. Verifique os logs para erros de inicialização

### Problema: Múltiplos processos Java

**Solução:**
```powershell
# Ver processos
Get-Process -Name "java"

# Parar todos
Get-Process -Name "java" | Stop-Process -Force
```

## Próximos Passos

Após corrigir:
1. Execute `.\scripts\validar-fluxos.ps1` para testar todos os fluxos
2. Verifique os logs da aplicação
3. Confirme que os dados estão sendo salvos no Cosmos DB

