package com.paucasesnoves.steamAPI.modules.games.repository;

import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.domain.GameMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameMediaRepository extends JpaRepository<GameMedia, Long> {
    boolean existsByGame(Game game);

    Optional<GameMedia> findByGame(Game game);
}