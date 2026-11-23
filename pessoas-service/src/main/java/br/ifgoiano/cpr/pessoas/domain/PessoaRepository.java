package br.ifgoiano.cpr.pessoas.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PessoaRepository extends JpaRepository<Pessoa, Long> {

    Optional<Pessoa> findByIdAndAtivoTrue(Long id);

    boolean existsByDocumento(String documento);

    boolean existsByDocumentoAndIdNot(String documento, Long id);

    @Query("""
            select p from Pessoa p
            where p.ativo = true
              and (:tipo is null or p.tipo = :tipo or p.tipo = br.ifgoiano.cpr.pessoas.domain.TipoPessoa.AMBOS)
              and (:nome is null or lower(p.nome) like lower(concat('%', cast(:nome as string), '%')))
            order by p.nome
            """)
    List<Pessoa> buscar(@Param("tipo") TipoPessoa tipo, @Param("nome") String nome);
}
