package br.com.fiap.postech.feedback.infrastructure.config;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inicializador do Quarkus para Azure Functions.
 * 
 * Este componente força a inicialização do Arc container (CDI) durante o startup
 * da aplicação, garantindo que o container esteja disponível quando as requisições
 * HTTP chegarem.
 * 
 * Problema resolvido:
 * - NullPointerException: Arc.container() retornava null quando requisições HTTP
 *   chegavam antes da inicialização completa do Quarkus
 * 
 * Solução:
 * - Observa o StartupEvent do Quarkus
 * - Força a inicialização do Arc container explicitamente
 * - Garante que o container está pronto antes de processar requisições
 */
@ApplicationScoped
public class QuarkusInitializer {

    private static final Logger logger = LoggerFactory.getLogger(QuarkusInitializer.class);

    /**
     * Inicializa o Quarkus Arc container durante o startup da aplicação.
     * 
     * Este método é chamado automaticamente quando o Quarkus inicia,
     * garantindo que o Arc container esteja disponível antes de processar
     * requisições HTTP no Azure Functions.
     * 
     * @param event Evento de startup do Quarkus
     */
    void onStart(@Observes StartupEvent event) {
        logger.info("=== INICIALIZANDO QUARKUS ARC CONTAINER ===");
        
        try {
            // Força a inicialização do Arc container
            ArcContainer container = Arc.container();
            
            if (container == null) {
                logger.error("ERRO: Arc.container() retornou null durante inicialização");
                throw new IllegalStateException("Arc container não está disponível durante startup");
            }
            
            logger.info("Arc container inicializado com sucesso");
            logger.info("Container está ativo: {}", container.isRunning());
            
            // Verifica se o request context está disponível
            try {
                container.requestContext();
                logger.info("Request context está disponível");
            } catch (Exception e) {
                logger.warn("Request context não está disponível ainda (normal durante startup): {}", e.getMessage());
            }
            
            logger.info("=== QUARKUS INICIALIZADO COM SUCESSO ===");
            
        } catch (Exception e) {
            logger.error("ERRO ao inicializar Quarkus Arc container", e);
            throw new RuntimeException("Falha na inicialização do Quarkus", e);
        }
    }
}
