package br.ifgoiano.cpr.pessoas.api;

import br.ifgoiano.cpr.pessoas.domain.Pessoa;
import br.ifgoiano.cpr.pessoas.domain.TipoPessoa;

import java.time.LocalDateTime;

public record PessoaResponse(Long id, TipoPessoa tipo, String nome, String documento, String inscricaoEstadual,
                             String endereco, String cidade, String uf, String cep, String telefone, String email,
                             String contato, LocalDateTime criadoEm) {

    public static PessoaResponse de(Pessoa p) {
        return new PessoaResponse(p.getId(), p.getTipo(), p.getNome(), p.getDocumento(), p.getInscricaoEstadual(),
                p.getEndereco(), p.getCidade(), p.getUf(), p.getCep(), p.getTelefone(), p.getEmail(),
                p.getContato(), p.getCriadoEm());
    }
}
