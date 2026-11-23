package br.ifgoiano.cpr.categorias.api;

import br.ifgoiano.cpr.categorias.domain.PlanoConta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs do plano de contas. A conta superior é referenciada pelo id: { "superior": { "id": 1 } }. */
public final class PlanoContaDtos {

    private PlanoContaDtos() {
    }

    public record Referencia(Long id) {}

    public record PlanoContaRequest(
            @NotBlank(message = "Informe o código.")
            @Pattern(regexp = "^\\d+(\\.\\d+)*$", message = "Use o formato 1, 1.1, 1.1.01 ...") String codigo,
            @NotBlank(message = "Informe a descrição.") @Size(min = 3, max = 60, message = "A descrição deve ter entre 3 e 60 caracteres.") String descricao,
            Referencia superior,
            @NotNull(message = "Informe se a conta é consolidada.") Boolean consolidada) {

        public Long superiorId() {
            return superior == null ? null : superior.id();
        }
    }

    public record SuperiorResumo(Long id, String codigo, String descricao) {}

    public record PlanoContaResponse(Long id, String codigo, String descricao, SuperiorResumo superior,
                                     boolean consolidada) {

        public static PlanoContaResponse de(PlanoConta p) {
            PlanoConta s = p.getSuperior();
            return new PlanoContaResponse(p.getId(), p.getCodigo(), p.getDescricao(),
                    s == null ? null : new SuperiorResumo(s.getId(), s.getCodigo(), s.getDescricao()),
                    p.isConsolidada());
        }
    }
}
