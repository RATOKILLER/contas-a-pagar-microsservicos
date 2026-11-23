package br.ifgoiano.cpr.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto único de entrada da API. O front-end só conhece o gateway (porta 8080);
 * o gateway descobre os serviços pelo Eureka e repassa cada rota /api/** ao serviço certo.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
