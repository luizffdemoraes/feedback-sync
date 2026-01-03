package br.com.fiap.postech.feedback;

import io.mailtrap.client.MailtrapClient;
import io.mailtrap.config.MailtrapConfig;
import io.mailtrap.factory.MailtrapClientFactory;
import io.mailtrap.model.request.emails.Address;
import io.mailtrap.model.request.emails.MailtrapMail;

import java.util.List;

/**
 * Classe de teste isolada para validar o envio de emails via Mailtrap.
 * 
 * Este teste replica o exemplo oficial do Mailtrap para identificar problemas
 * na configuração ou uso do SDK.
 * 
 * Para executar:
 * 1. Configure as variáveis de ambiente:
 *    - MAILTRAP_API_TOKEN: seu token da API
 *    - MAILTRAP_INBOX_ID: ID da sua inbox (ex: 4049775)
 *    - ADMIN_EMAIL: email de destino (ex: lffm1994@gmail.com)
 * 
 * 2. Execute: mvn test -Dtest=MailtrapEmailTest
 */
public class MailtrapEmailTest {

    private static final String TOKEN = System.getenv("MAILTRAP_API_TOKEN");
    private static final String INBOX_ID_STR = System.getenv("MAILTRAP_INBOX_ID");
    private static final String TO_EMAIL = System.getenv("ADMIN_EMAIL");

    public static void main(String[] args) {
        System.out.println("=== Teste de Envio de Email via Mailtrap ===\n");
        
        // Validar configurações
        if (TOKEN == null || TOKEN.isBlank()) {
            System.err.println("❌ ERRO: MAILTRAP_API_TOKEN não configurado!");
            System.err.println("   Configure: $env:MAILTRAP_API_TOKEN = \"seu-token\"");
            System.exit(1);
        }
        
        if (INBOX_ID_STR == null || INBOX_ID_STR.isBlank()) {
            System.err.println("❌ ERRO: MAILTRAP_INBOX_ID não configurado!");
            System.err.println("   Configure: $env:MAILTRAP_INBOX_ID = \"4049775\"");
            System.exit(1);
        }
        
        if (TO_EMAIL == null || TO_EMAIL.isBlank()) {
            System.err.println("❌ ERRO: ADMIN_EMAIL não configurado!");
            System.err.println("   Configure: $env:ADMIN_EMAIL = \"seu-email@gmail.com\"");
            System.exit(1);
        }
        
        Long inboxId = null;
        try {
            inboxId = Long.parseLong(INBOX_ID_STR.trim());
        } catch (NumberFormatException e) {
            System.err.println("❌ ERRO: MAILTRAP_INBOX_ID inválido! Deve ser um número.");
            System.err.println("   Valor recebido: '" + INBOX_ID_STR + "'");
            System.exit(1);
        }
        
        // Garantir que inboxId foi inicializado corretamente
        if (inboxId == null) {
            System.err.println("❌ ERRO: Não foi possível inicializar o Inbox ID.");
            System.exit(1);
        }
        
        System.out.println("✓ Configurações validadas:");
        System.out.println("  - Token: " + TOKEN.substring(0, Math.min(8, TOKEN.length())) + "...");
        System.out.println("  - Inbox ID: " + inboxId);
        System.out.println("  - Email destino: " + TO_EMAIL);
        System.out.println();
        
        try {
            // Criar configuração do Mailtrap (seguindo o exemplo oficial)
            System.out.println("1. Criando configuração do Mailtrap...");
            final MailtrapConfig config = new MailtrapConfig.Builder()
                .sandbox(true)
                .inboxId(inboxId)
                .token(TOKEN)
                .build();
            System.out.println("   ✓ Configuração criada");
            
            // Criar cliente Mailtrap
            System.out.println("2. Criando cliente Mailtrap...");
            final MailtrapClient client = MailtrapClientFactory.createMailtrapClient(config);
            System.out.println("   ✓ Cliente criado");
            
            // Construir email (seguindo o exemplo oficial)
            System.out.println("3. Construindo email...");
            final MailtrapMail mail = MailtrapMail.builder()
                .from(new Address("hello@example.com", "Mailtrap Test"))
                .to(List.of(new Address(TO_EMAIL)))
                .subject("You are awesome!")
                .text("Congrats for sending test email with Mailtrap!")
                .category("Integration Test")
                .build();
            System.out.println("   ✓ Email construído:");
            System.out.println("     - De: hello@example.com (Mailtrap Test)");
            System.out.println("     - Para: " + TO_EMAIL);
            System.out.println("     - Assunto: You are awesome!");
            System.out.println("     - Categoria: Integration Test");
            
            // Enviar email
            System.out.println("4. Enviando email via Mailtrap API...");
            Object response = client.send(mail);
            System.out.println("   ✓ Email enviado!");
            System.out.println("   Resposta do Mailtrap: " + (response != null ? response.toString() : "null"));
            System.out.println();
            System.out.println("✅ SUCESSO! Verifique sua inbox no Mailtrap.");
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("❌ ERRO ao enviar email:");
            System.err.println("   Tipo: " + e.getClass().getName());
            System.err.println("   Mensagem: " + e.getMessage());
            System.err.println();
            System.err.println("Stack trace completo:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
