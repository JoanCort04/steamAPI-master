package com.paucasesnoves.steamAPI.modules.games.repository;

import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.domain.GameDescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameDescriptionRepository extends JpaRepository<GameDescription, Long> {
    boolean existsByGame(Game game);

    Optional<GameDescription> findByGame(Game game);
}