package br.ifgoiano.cpr.contasreceber.integracao;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga os clientes Feign e o agendador. Fica fora da classe principal para que os testes
 * de fatia (@WebMvcTest) não tentem criar clientes HTTP de outros serviços.
 */
@Configuration
@EnableFeignClients
@EnableScheduling
public class IntegracaoConfig {
}
