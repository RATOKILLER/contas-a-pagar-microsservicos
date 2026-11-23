package br.ifgoiano.cpr.contaspagar.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.ZoneId;

/** Ao subir e a cada hora, títulos em aberto com vencimento passado viram VENCIDA. */
@Configuration
public class VencimentoAgendador {

    private static final Logger log = LoggerFactory.getLogger(VencimentoAgendador.class);

    private final ContaPagarService service;

    public VencimentoAgendador(ContaPagarService service) {
        this.service = service;
    }

    @Bean
    static Clock relogio() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 * * * *", zone = "America/Sao_Paulo")
    public void executar() {
        int n = service.marcarVencidas();
        if (n > 0) {
            log.info("{} título(s) marcado(s) como VENCIDA", n);
        }
    }
}
