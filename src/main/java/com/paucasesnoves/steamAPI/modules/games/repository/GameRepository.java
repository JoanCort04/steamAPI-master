package com.paucasesnoves.steamAPI.modules.games.repository;

import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Long>, JpaSpecificationExecutor<Game> {

    Optional<Game> findByAppId(Long appId);

    boolean existsByAppId(Long appId);

    long countByTagsNotEmpty();

    // Para listado con filtros y carga de colecciones básicas (si es necesario)
    @EntityGraph(value = "Game.withDevelopersAndGenres", type = EntityGraph.EntityGraphType.FETCH)
    Page<Game> findAll(Specification<Game> spec, Pageable pageable);

    // Para detalle: anulamos findOne de JpaSpecificationExecutor para cargar todas las colecciones
    @Override
    @EntityGraph(attributePaths = {
            "developers",
            "publishers",
            "genres",
            "tags",
            "platforms",
            "category"
    })
    Optional<Game> findOne(Specification<Game> spec);
}