package com.example.qldetai.feature.communication.repository;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Literal, case-insensitive text search; user input cannot become LIKE wildcards. */
public final class CommunicationSearch {
    private CommunicationSearch() {}

    public static String pattern(String search) {
        String value = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public static <T> Specification<T> text(String search, String... fields) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return cb.conjunction();
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            for (String field : fields) {
                jakarta.persistence.criteria.Path<?> path = root;
                for (String segment : field.split("\\.")) path = path.get(segment);
                predicates.add(cb.like(cb.lower(path.as(String.class)), pattern(search), '\\'));
            }
            return cb.or(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
