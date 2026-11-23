package br.ifgoiano.cpr.pessoas.api;

import br.ifgoiano.cpr.pessoas.domain.TipoPessoa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dados de entrada para cadastrar ou alterar uma pessoa. Máscaras de documento e CEP são aceitas. */
public record PessoaRequest(
        @NotNull(message = "Informe o tipo (CLIENTE, FORNECEDOR ou AMBOS).") TipoPessoa tipo,
        @NotBlank(message = "Informe o nome.") @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres.") String nome,
        @NotBlank(message = "Informe o CPF ou CNPJ.")
        @Pattern(regexp = "^(\\D*\\d){11}\\D*$|^(\\D*\\d){14}\\D*$", message = "Informe um CPF (11 dígitos) ou CNPJ (14 dígitos).")
        String documento,
        @Size(max = 15) String inscricaoEstadual,
        @Size(max = 120) String endereco,
        @Size(max = 50) String cidade,
        @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "UF deve ter 2 letras.") String uf,
        @Pattern(regexp = "^$|^(\\D*\\d){8}\\D*$", message = "CEP deve ter 8 dígitos.") String cep,
        @Size(max = 20) String telefone,
        @Email(message = "E-mail inválido.") @Size(max = 80) String email,
        @Size(max = 50) String contato) {
}
