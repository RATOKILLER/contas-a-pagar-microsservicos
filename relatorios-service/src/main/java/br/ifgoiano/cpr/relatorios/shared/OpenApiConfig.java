package br.ifgoiano.cpr.relatorios.shared;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI(@Value("${spring.application.name}") String nome,
                           @Value("${cpr.api.descricao}") String descricao) {
        return new OpenAPI()
                .info(new Info().title("CPR · " + nome).description(descricao).version("2.0.0"))
                // "Try it out" do Swagger passa pelo gateway (mesma origem), não pelo IP interno do container.
                .servers(List.of(new Server().url("/").description("Via API Gateway")));
    }
}
