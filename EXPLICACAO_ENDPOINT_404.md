# Explicação: Onde está definido `/api/avaliacao` e por que retorna 404

## Onde está definido o endpoint?

### 1. Definição da Rota na Função

O endpoint está definido em:
**Arquivo:** `src/main/java/br/com/fiap/postech/feedback/infrastructure/handlers/SubmitFeedbackFunction.java`

**Linha 46:**
```java
@HttpTrigger(
    name = "req",
    methods = {HttpMethod.POST},
    route = "avaliacao"  // <-- AQUI está definida a rota
)
```

### 2. Como funciona o mapeamento

1. **Rota definida:** `route = "avaliacao"`
2. **Azure Functions adiciona prefixo:** `/api` (automático)
3. **Rota final:** `/api/avaliacao`

**IMPORTANTE:** O prefixo `/api` é adicionado **automaticamente** pelo Azure Functions Runtime para todas as rotas HTTP. Isso é padrão do Azure Functions e não precisa ser configurado.

## Por que está retornando 404?

O erro 404 significa que a **função não foi registrada** durante a inicialização. Possíveis causas:

### Causa 1: Função não foi registrada
- A classe não está sendo escaneada pelo Quarkus
- Problema de injeção de dependência
- Erro durante a inicialização

### Causa 2: Múltiplos processos Java
- Vários processos Java rodando simultaneamente
- Conflito de porta
- Cache desatualizado

### Causa 3: Aplicação não terminou de inicializar
- A aplicação ainda está compilando
- Funções ainda não foram registradas
- Aguardar mensagem "Listening on: http://localhost:7071"

## Como verificar se a função está registrada?

### 1. Verificar nos logs

Procure por estas mensagens nos logs da aplicação:

```
✅ Function submitFeedback registered
✅ Function notifyAdmin registered  
✅ Function weeklyReport registered
```

Se essas mensagens **NÃO aparecerem**, a função não foi registrada.

### 2. Verificar se a classe está sendo escaneada

A classe `SubmitFeedbackFunction` precisa estar:
- No pacote correto (está em `infrastructure.handlers`)
- Sem anotações de escopo que impeçam a criação (não tem `@ApplicationScoped`, está OK)
- Com injeção de dependência funcionando (tem `@Inject` no construtor)

### 3. Verificar se há erros de inicialização

Procure nos logs por:
- `ERROR` ou `Exception`
- `Failed to register function`
- `Connection refused` ou `Connection timeout`

## Solução

### Passo 1: Limpar e recompilar

```powershell
.\scripts\corrigir-problemas.ps1
```

### Passo 2: Reiniciar aplicação

```powershell
.\executar-app.ps1
```

### Passo 3: Aguardar inicialização completa

Aguarde ver nos logs:
```
Listening on: http://localhost:7071
```

### Passo 4: Verificar registro das funções

Procure nos logs por:
```
Function submitFeedback registered
```

### Passo 5: Testar endpoint

```powershell
$body = @{
    descricao = "Teste"
    nota = 5
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:7071/api/avaliacao" `
    -Method POST `
    -Body $body `
    -ContentType "application/json"
```

## Estrutura do Endpoint

```
http://localhost:7071  ← Porta do Azure Functions (definida em application-local.properties)
         /api          ← Prefixo automático do Azure Functions
            /avaliacao ← Rota definida na anotação @HttpTrigger
```

## Resumo

- **Definição:** `SubmitFeedbackFunction.java`, linha 46: `route = "avaliacao"`
- **Rota completa:** `/api/avaliacao` (prefixo `/api` é automático)
- **Problema:** Função não está sendo registrada (404)
- **Solução:** Limpar processos, recompilar e reiniciar

