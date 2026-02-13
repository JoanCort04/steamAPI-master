package com.paucasesnoves.steamAPI.modules.games.repository;

import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.domain.GameRequirements;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameRequirementsRepository extends JpaRepository<GameRequirements, Long> {
    boolean existsByGame(Game game);

    Optional<GameRequirements> findByGame(Game game);
}
