# Teste de Envio de Email via Mailtrap

## Problema Identificado

O código atual não estava capturando a resposta do método `send()` do MailtrapClient, o que pode ocultar erros ou informações importantes sobre o envio do email.

## Melhorias Implementadas

1. **Captura da resposta do método `send()`**: Agora o código captura e loga a resposta retornada pelo MailtrapClient, similar ao exemplo oficial.

2. **Logs mais detalhados**: Adicionados logs com informações completas do email antes do envio.

3. **Classe de teste isolada**: Criada `MailtrapEmailTest.java` que replica exatamente o exemplo oficial do Mailtrap para validação.

## Como Usar o Teste Isolado

### Opção 1: Usando o Script PowerShell (Recomendado)

```powershell
# Configure as variáveis de ambiente
$env:MAILTRAP_API_TOKEN = "8aeda2e8618bbb4287959051394a0261"
$env:MAILTRAP_INBOX_ID = "4049775"
$env:ADMIN_EMAIL = "lffm1994@gmail.com"

# Execute o script de teste
.\scripts\testar-mailtrap.ps1
```

Ou passe os parâmetros diretamente:

```powershell
.\scripts\testar-mailtrap.ps1 -Token "8aeda2e8618bbb4287959051394a0261" -InboxId "4049775" -Email "lffm1994@gmail.com"
```

### Opção 2: Executando Diretamente com Java (Manual)

```powershell
# Configure as variáveis de ambiente
$env:MAILTRAP_API_TOKEN = "8aeda2e8618bbb4287959051394a0261"
$env:MAILTRAP_INBOX_ID = "4049775"
$env:ADMIN_EMAIL = "lffm1994@gmail.com"

# Compile o projeto
mvn clean compile test-compile

# Construa o classpath
mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=classpath.txt

# Execute a classe de teste
$classpath = Get-Content classpath.txt -Raw
java -cp "target/classes;target/test-classes;$classpath" br.com.fiap.postech.feedback.MailtrapEmailTest

# Limpe o arquivo temporário
Remove-Item classpath.txt
```

### Opção 3: Executando a Classe Java Diretamente

```powershell
# Configure as variáveis de ambiente
$env:MAILTRAP_API_TOKEN = "8aeda2e8618bbb4287959051394a0261"
$env:MAILTRAP_INBOX_ID = "4049775"
$env:ADMIN_EMAIL = "lffm1994@gmail.com"

# Compile e execute
mvn clean compile test-compile
java -cp "target/classes;target/test-classes;$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" br.com.fiap.postech.feedback.MailtrapEmailTest
```

## Comparação: Código Atual vs Exemplo Oficial

### Exemplo Oficial (Funcionando)
```java
final MailtrapMail mail = MailtrapMail.builder()
    .from(new Address("hello@example.com", "Mailtrap Test"))
    .to(List.of(new Address("lffm1994@gmail.com")))
    .subject("You are awesome!")
    .text("Congrats for sending test email with Mailtrap!")
    .category("Integration Test")
    .build();

System.out.println(client.send(mail));  // ← Captura e imprime a resposta
```

### Código Atual (Melhorado)
```java
final MailtrapMail mail = MailtrapMail.builder()
    .from(new Address("noreply@feedback-sync.com", "Feedback Sync"))
    .to(List.of(new Address(adminEmail)))
    .subject(subject)
    .text(content)
    .category("Notificações")
    .build();

Object response = mailtrapClient.send(mail);  // ← Agora captura a resposta
logger.debug("Resposta do Mailtrap API: {}", response);
```

## Possíveis Problemas e Soluções

### 1. Variáveis de Ambiente Não Configuradas

**Sintoma**: Logs mostram "Mailtrap não configurado completamente"

**Solução**: Configure as variáveis de ambiente:
```powershell
$env:MAILTRAP_API_TOKEN = "seu-token"
$env:MAILTRAP_INBOX_ID = "seu-inbox-id"
$env:ADMIN_EMAIL = "seu-email@gmail.com"
```

### 2. Inbox ID Inválido

**Sintoma**: Erro ao converter String para Long

**Solução**: Verifique se o `MAILTRAP_INBOX_ID` é um número válido (ex: `4049775`)

### 3. Token Inválido ou Expirado

**Sintoma**: Exceção ao enviar email com mensagem de autenticação

**Solução**: Verifique se o token está correto no painel do Mailtrap

### 4. Cliente Mailtrap Não Inicializado

**Sintoma**: `mailtrapClient` é null mesmo com configurações corretas

**Solução**: Verifique os logs durante a inicialização (`@PostConstruct`). O cliente pode não estar sendo criado corretamente.

## Validação com cURL (Referência)

O cURL fornecido funciona corretamente:

```bash
curl --location 'https://sandbox.api.mailtrap.io/api/send/4049775' \
--header 'Authorization: Bearer 8aeda2e8618bbb4287959051394a0261' \
--header 'Content-Type: application/json' \
--data-raw '{
  "from":{"email":"hello@example.com","name":"Mailtrap Test"},
  "to":[{"email":"lffm1994@gmail.com"}],
  "subject":"You are awesome!",
  "text":"Congrats for sending test email with Mailtrap!",
  "category":"Integration Test"
}'
```

Isso confirma que:
- O token está correto
- O inbox ID está correto
- A API do Mailtrap está funcionando

## Status do Teste

✅ **TESTE ISOLADO FUNCIONANDO PERFEITAMENTE**

O script `scripts/testar-mailtrap.ps1` está funcionando corretamente e enviando emails via Mailtrap com sucesso. Isso confirma que:
- ✅ As variáveis de ambiente estão configuradas corretamente
- ✅ O SDK do Mailtrap está funcionando
- ✅ A implementação do código está correta
- ✅ A API do Mailtrap está respondendo adequadamente

## Próximos Passos

1. ✅ Teste isolado executado com sucesso
2. Verifique se a aplicação Azure Functions também está funcionando ao executar `scripts/executar-azure-functions-local.ps1`
3. Se a aplicação não funcionar, verifique se as variáveis de ambiente estão sendo carregadas corretamente no contexto da aplicação
4. Compare os logs da aplicação com os logs do teste isolado para identificar diferenças

## Logs para Verificar

Ao executar a aplicação, verifique os seguintes logs:

```
=== Inicializando EmailNotificationGatewayImpl ===
mailtrapApiToken configurado: SIM (primeiros 8 chars: 8aeda2e8...)
adminEmail configurado: lffm1994@gmail.com
mailtrapInboxId configurado: 4049775
✓ Mailtrap client inicializado com sucesso
```

E ao enviar email:

```
=== sendEmailToAdmin iniciado ===
Construindo objeto MailtrapMail...
Enviando email via Mailtrap API...
Resposta do Mailtrap API: [resposta aqui]
✓ Email enviado com sucesso para lffm1994@gmail.com
```
