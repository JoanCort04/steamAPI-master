package com.paucasesnoves.steamAPI.modules.games.repository;

import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    Optional<Game> findByTitle(String title);

    boolean existsByTitle(String title);

    boolean existsByAppId(Long appId);

    Optional<Game> findById(Long appId);
    long count();
    long countByTagsNotEmpty();


    @EntityGraph(value = "Game.withDevelopersAndGenres", type = EntityGraph.EntityGraphType.FETCH)
    Page<Game> findAll(Specification<Game> spec, Pageable pageable);

}