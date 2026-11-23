package br.ifgoiano.cpr.contaspagar.integracao;

import br.ifgoiano.cpr.contaspagar.shared.NegocioException;
import br.ifgoiano.cpr.contaspagar.shared.ServicoIndisponivelException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * O que fazer quando a chamada a outro serviço falha:
 *  - 404: a pessoa/categoria não existe → regra de negócio (RN04), HTTP 422;
 *  - qualquer outra falha (fora do ar, timeout, circuito aberto) → HTTP 503 com mensagem clara.
 * Nunca se lança um título sem confirmar a pessoa e a categoria: na dúvida, a operação é recusada.
 */
public final class CadastrosFallbacks {

    private static final Logger log = LoggerFactory.getLogger(CadastrosFallbacks.class);

    private CadastrosFallbacks() {
    }

    @Component
    public static class PessoaFallback implements FallbackFactory<PessoaClient> {
        @Override
        public PessoaClient create(Throwable causa) {
            return id -> {
                if (causa instanceof FeignException.NotFound) {
                    throw new NegocioException("Pessoa " + id + " não está cadastrada (ou foi excluída). Cadastre-a antes de lançar o título.");
                }
                log.warn("Falha ao consultar pessoas-service para a pessoa {}: {}", id, causa.toString());
                throw new ServicoIndisponivelException(
                        "O serviço de Pessoas está indisponível; não foi possível confirmar a pessoa do título. Tente novamente em instantes.");
            };
        }
    }

    @Component
    public static class CategoriaFallback implements FallbackFactory<CategoriaClient> {
        @Override
        public CategoriaClient create(Throwable causa) {
            return id -> {
                if (causa instanceof FeignException.NotFound) {
                    throw new NegocioException("Conta " + id + " do plano de contas não está cadastrada (ou foi excluída).");
                }
                log.warn("Falha ao consultar categorias-service para a conta {}: {}", id, causa.toString());
                throw new ServicoIndisponivelException(
                        "O serviço de Plano de Contas está indisponível; não foi possível confirmar a categoria do título. Tente novamente em instantes.");
            };
        }
    }
}
