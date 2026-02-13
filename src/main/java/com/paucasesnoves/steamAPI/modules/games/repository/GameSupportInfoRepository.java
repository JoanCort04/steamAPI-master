package com.paucasesnoves.steamAPI.modules.games.repository;

import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.domain.GameSupportInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameSupportInfoRepository extends JpaRepository<GameSupportInfo, Long> {
    boolean existsByGame(Game game);

    Optional<GameSupportInfo> findByGame(Game game);
}
