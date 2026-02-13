package com.paucasesnoves.steamAPI.modules.games.repository;

import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public class GameSpecification {

    private GameSpecification() {}

    public static Specification<Game> hasGenre(String genre) {
        return (root, query, cb) -> {
            Join<Object, Object> genresJoin = root.join("genres");
            return cb.equal(cb.lower(genresJoin.get("name")), genre.toLowerCase());
        };
    }

    public static Specification<Game> hasDeveloper(String developer) {
        return (root, query, cb) -> {
            Join<Object, Object> developersJoin = root.join("developers");
            return cb.equal(cb.lower(developersJoin.get("name")), developer.toLowerCase());
        };
    }

    public static Specification<Game> priceBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("price"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("price"), min);
            } else if (max != null) {
                return cb.lessThanOrEqualTo(root.get("price"), max);
            } else {
                return cb.conjunction(); // siempre verdadero
            }
        };
    }
}