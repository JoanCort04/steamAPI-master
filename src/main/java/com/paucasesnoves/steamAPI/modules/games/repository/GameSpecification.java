package com.paucasesnoves.steamAPI.modules.games.repository;

import com.paucasesnoves.steamAPI.modules.games.domain.Developer;
import com.paucasesnoves.steamAPI.modules.games.domain.Game;
import com.paucasesnoves.steamAPI.modules.games.domain.Genre;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

public class GameSpecification {

    public static Specification<Game> hasName(String name) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(name)) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get("title")), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<Game> hasGenre(String genre) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(genre)) {
                return cb.conjunction();
            }
            query.distinct(true); // Evita duplicats per múltiples gèneres
            Join<Game, Genre> genreJoin = root.join("genres");
            return cb.equal(genreJoin.get("name"), genre);
        };
    }

    public static Specification<Game> hasDeveloper(String developer) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(developer)) {
                return cb.conjunction();
            }
            query.distinct(true); // Evita duplicats per múltiples desenvolupadors
            Join<Game, Developer> developerJoin = root.join("developers");
            return cb.equal(developerJoin.get("name"), developer);
        };
    }

    public static Specification<Game> priceBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min == null && max == null) return cb.conjunction();
            if (min != null && max != null) return cb.between(root.get("price"), min, max);
            if (min != null) return cb.ge(root.get("price"), min);
            return cb.le(root.get("price"), max);
        };
    }
}