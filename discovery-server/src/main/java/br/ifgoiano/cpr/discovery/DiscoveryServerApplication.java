package br.ifgoiano.cpr.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Registro de serviços. Cada microsserviço se registra aqui pelo nome
 * (ex.: "pessoas-service"), e quem precisa chamá-lo pergunta o endereço ao Eureka.
 * Assim nenhum serviço guarda IP ou porta de outro.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
