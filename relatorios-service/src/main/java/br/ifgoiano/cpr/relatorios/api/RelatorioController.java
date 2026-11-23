package br.ifgoiano.cpr.relatorios.api;

import br.ifgoiano.cpr.relatorios.api.RelatorioDtos.FluxoCaixa;
import br.ifgoiano.cpr.relatorios.api.RelatorioDtos.Inadimplencia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Relatórios", description = "Consultas financeiras sobre a base alimentada por eventos")
@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private final RelatorioService service;

    public RelatorioController(RelatorioService service) {
        this.service = service;
    }

    @Operation(summary = "Fluxo de caixa mensal: previsto x realizado",
            description = "Previsto pela data de vencimento, realizado pela data de quitação. Sem período: 2 meses atrás até 3 à frente.")
    @GetMapping("/fluxo-caixa")
    public FluxoCaixa fluxoCaixa(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.fluxoCaixa(inicio, fim);
    }

    @Operation(summary = "Títulos vencidos e não quitados",
            description = "natureza=RECEBER (padrão) lista clientes inadimplentes; natureza=PAGAR lista contas a pagar em atraso.")
    @GetMapping("/inadimplencia")
    public Inadimplencia inadimplencia(@RequestParam(required = false) String natureza) {
        return service.inadimplencia(natureza);
    }
}
