# 📧 Guia: Criar Logic App para Enviar Email de Feedbacks Críticos

## 🎯 Objetivo

Criar um Logic App que escuta mensagens do Service Bus e envia email automaticamente aos administradores quando um feedback crítico é recebido.

---

## 📋 Pré-requisitos

- ✅ Service Bus criado no Azure
- ✅ Topic `critical-feedbacks` criado
- ✅ Subscription `admin-notifications` criada no Topic
- ✅ Conta de email (Office 365, Gmail, ou outro provedor)

---

## 🚀 Passo a Passo

### **Passo 1: Criar Logic App**

1. Acesse o **Azure Portal** (portal.azure.com)
2. Clique em **"Criar um recurso"** (+)
3. Busque por **"Logic App"**
4. Clique em **"Logic App"** → **"Criar"**

**Configurações:**
- **Nome:** `feedback-notification-app` (ou outro nome de sua escolha)
- **Assinatura:** Sua assinatura
- **Grupo de recursos:** Mesmo grupo do Service Bus
- **Tipo de plano:** `Consumption` (paga por execução - mais barato)
- **Região:** Mesma região do Service Bus (ex: `Brazil South`)
- Clique em **"Revisar + criar"** → **"Criar"**

---

### **Passo 2: Configurar Trigger (Service Bus)**

1. Após criar, vá para o recurso Logic App
2. Clique em **"Designer do Logic Apps"** (ou "Logic app designer")
3. Na tela inicial, escolha **"Quando uma mensagem é recebida em uma assinatura de tópico (bloqueio automático)"**
   - Se não aparecer, busque por "Service Bus" nos triggers

**Configurar Conexão:**
- **Nome da conexão:** `ServiceBusConnection` (ou outro nome)
- **Service Bus namespace:** Selecione seu Service Bus
- **Tipo de autenticação:** `Connection String`
- **Connection String:** Cole a connection string do Service Bus
  - (Você pode pegar em: Service Bus → Shared Access Policies → RootManageSharedAccessKey)
- Clique em **"Criar"**

**Configurar Trigger:**
- **Nome do tópico:** `critical-feedbacks`
- **Nome da assinatura:** `admin-notifications`
- **Tipo de conteúdo:** `application/json`
- Clique em **"Salvar"**

---

### **Passo 3: Adicionar Ação de Email**

1. Clique em **"+ Nova etapa"** (ou "+ New step")
2. Busque por **"Enviar um email"** ou **"Send an email"**
3. Escolha uma das opções:
   - **Office 365 Outlook** (se tiver conta corporativa)
   - **Gmail** (se usar Gmail)
   - **Outlook.com** (se usar Outlook pessoal)

**Conectar Conta:**
- Clique em **"Entrar"** e autentique com sua conta de email
- Autorize o Logic App a enviar emails em seu nome

---

### **Passo 4: Configurar Email**

**Campos do Email:**

1. **Para (To):**
   - Digite o email do administrador
   - Ex: `admin@exemplo.com`

2. **Assunto (Subject):**
   ```
   🚨 ALERTA: Feedback Crítico Recebido
   ```

3. **Corpo (Body):**
   Use o conteúdo dinâmico da mensagem do Service Bus. Clique em **"Ver mais"** ou **"Add dynamic content"** e selecione:
   
   **Opção 1: Usar conteúdo completo (JSON)**
   ```
   Um feedback crítico foi recebido:
   
   {{triggerBody()}}
   
   Por favor, verifique o sistema.
   ```

   **Opção 2: Formatar campos específicos (recomendado)**
   ```
   🚨 ALERTA: Feedback Crítico Recebido
   
   ID: {{triggerBody()['id']}}
   Descrição: {{triggerBody()['description']}}
   Nota: {{triggerBody()['score']['value']}}/10
   Urgência: {{triggerBody()['urgency']['value']}}
   Data de Envio: {{triggerBody()['createdAt']}}
   
   Por favor, verifique o sistema.
   ```

   **Nota:** Os campos podem variar conforme a estrutura do JSON. Se não aparecerem os campos, use `{{triggerBody()}}` para ver o JSON completo.

4. **Importância (Importance):** `Alta` (opcional)

5. Clique em **"Salvar"**

---

### **Passo 5: Testar Logic App**

1. **Ativar o Logic App:**
   - No topo da página, clique em **"Desabilitado"** → Mude para **"Habilitado"**
   - Ou vá em **"Visão geral"** → Toggle **"Habilitado"**

2. **Enviar feedback crítico:**
   - Use sua API para enviar um feedback com nota ≤ 3
   - Ou publique manualmente uma mensagem no Service Bus

3. **Verificar execução:**
   - Vá em **"Visão geral"** → **"Histórico de execuções"**
   - Clique na execução mais recente
   - Verifique se foi **"Bem-sucedida"**
   - Verifique se o email foi enviado

---

## 🔧 Configuração Avançada (Opcional)

### **Formatação HTML do Email**

Para email mais bonito, use HTML no corpo:

```html
<h2 style="color: red;">🚨 ALERTA: Feedback Crítico Recebido</h2>

<table border="1" cellpadding="5">
  <tr>
    <td><strong>ID:</strong></td>
    <td>{{triggerBody()['id']}}</td>
  </tr>
  <tr>
    <td><strong>Descrição:</strong></td>
    <td>{{triggerBody()['description']}}</td>
  </tr>
  <tr>
    <td><strong>Nota:</strong></td>
    <td>{{triggerBody()['score']['value']}}/10</td>
  </tr>
  <tr>
    <td><strong>Urgência:</strong></td>
    <td>{{triggerBody()['urgency']['value']}}</td>
  </tr>
  <tr>
    <td><strong>Data:</strong></td>
    <td>{{triggerBody()['createdAt']}}</td>
  </tr>
</table>

<p>Por favor, verifique o sistema.</p>
```

### **Adicionar Condições**

Se quiser filtrar apenas feedbacks com urgência alta:

1. Entre o Trigger e o Email, adicione **"Condição"**
2. Configure:
   - **Valor 1:** `triggerBody()['urgency']['value']`
   - **Operador:** `é igual a`
   - **Valor 2:** `HIGH`
3. No ramo **"Sim"**, coloque a ação de email
4. No ramo **"Não"**, pode deixar vazio ou adicionar log

---

## 📊 Estrutura JSON Esperada

O Logic App receberá um JSON no formato:

```json
{
  "id": "uuid-do-feedback",
  "description": "Descrição do feedback",
  "score": {
    "value": 2
  },
  "urgency": {
    "value": "HIGH"
  },
  "createdAt": "2024-01-15T10:30:00"
}
```

**Ajuste os campos no corpo do email conforme sua estrutura JSON real.**

---

## 🐛 Troubleshooting

### **Problema: Logic App não é triggerado**

**Soluções:**
- Verifique se o Logic App está **habilitado**
- Verifique se o Topic e Subscription estão corretos
- Verifique se há mensagens na subscription do Service Bus
- Veja os logs em **"Histórico de execuções"**

### **Problema: Email não é enviado**

**Soluções:**
- Verifique se a conexão de email está ativa
- Verifique se o email de destino está correto
- Veja os detalhes da execução para erros
- Verifique se a conta de email tem permissão para enviar

### **Problema: Campos não aparecem no conteúdo dinâmico**

**Soluções:**
- Use `{{triggerBody()}}` para ver o JSON completo
- Ajuste os caminhos dos campos conforme a estrutura real
- Use a função `json()` se necessário: `{{json(triggerBody())}}`

---

## 💰 Custos

**Logic App Consumption:**
- **Primeiras 5.000 execuções/mês:** Grátis
- **Após:** ~R$ 0,00025 por execução
- **Ações:** ~R$ 0,000025 por ação

**Exemplo:** 100 feedbacks críticos/mês = ~R$ 0,03/mês

---

## ✅ Checklist Final

- [ ] Logic App criado
- [ ] Trigger configurado (Service Bus)
- [ ] Conexão com Service Bus estabelecida
- [ ] Ação de email configurada
- [ ] Conta de email conectada
- [ ] Corpo do email formatado
- [ ] Logic App habilitado
- [ ] Teste realizado com sucesso
- [ ] Email recebido

---

## 📝 Notas Importantes

1. **O Logic App escuta automaticamente** - não precisa chamar manualmente
2. **Cada mensagem no Service Bus** triggera uma execução
3. **O Logic App processa mensagens em ordem** (FIFO)
4. **Mensagens processadas são removidas** da subscription automaticamente
5. **Você pode ver histórico** de todas as execuções no portal

---

## 🎬 Para o Vídeo de Demonstração

**Cenário a mostrar:**
1. Enviar feedback crítico via API
2. Mostrar mensagem chegando no Service Bus
3. Mostrar Logic App sendo triggerado (histórico de execuções)
4. Mostrar email recebido na caixa de entrada
5. Explicar o fluxo completo

---

**Última atualização:** 2024

