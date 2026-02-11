package com.paucasesnoves.steamAPI.modules.games.repository;

import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    // Ya no necesitamos findByAppId() porque appId ES el @Id
    // JpaRepository.findById(Long id) ya funciona con appId

    // Pero podemos agregar métodos de búsqueda útiles:
    Optional<Game> findByTitle(String title);

    boolean existsByTitle(String title);

    boolean existsByAppId(Long appId);

    Optional<Game> findById(Long appId);
    long count();
    long countByTagsNotEmpty();
}