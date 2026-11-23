package br.ifgoiano.cpr.relatorios.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TituloResumoRepository extends JpaRepository<TituloResumo, TituloResumo.Chave> {

    /** Títulos não cancelados que vencem OU foram quitados dentro do período. */
    @Query("""
            select t from TituloResumo t
            where t.situacao <> 'CANCELADA'
              and ((t.vencimento between :inicio and :fim) or (t.dataQuitacao between :inicio and :fim))
            """)
    List<TituloResumo> doPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("""
            select t from TituloResumo t
            where t.natureza = :natureza
              and t.situacao in ('ABERTA', 'VENCIDA') and t.vencimento < :hoje
            order by t.vencimento, t.numeroTitulo
            """)
    List<TituloResumo> emAtraso(@Param("natureza") String natureza, @Param("hoje") LocalDate hoje);

    @Modifying
    @Query("update TituloResumo t set t.pessoaNome = :nome where t.pessoaId = :pessoaId")
    int atualizarNomePessoa(@Param("pessoaId") Long pessoaId, @Param("nome") String nome);

    @Modifying
    @Query("update TituloResumo t set t.planoContaCodigo = :codigo, t.planoContaDescricao = :descricao where t.planoContaId = :id")
    int atualizarCategoria(@Param("id") Long id, @Param("codigo") String codigo, @Param("descricao") String descricao);
}
