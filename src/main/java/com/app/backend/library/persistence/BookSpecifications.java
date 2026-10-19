package com.app.backend.library.persistence;

import com.app.backend.library.domain.Book;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class BookSpecifications {

    private static final char ESCAPE = '\\';

    private BookSpecifications() {}

    public static Specification<Book> matching(String title, String author) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(title)) {
                predicates.add(containsIgnoreCase(cb, root.get("title"), title));
            }
            if (hasText(author)) {
                predicates.add(containsIgnoreCase(cb, root.get("author"), author));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate containsIgnoreCase(CriteriaBuilder cb, Expression<String> attribute, String value) {
        String pattern = "%" + escapeLikeWildcards(value.strip().toLowerCase(Locale.ROOT)) + "%";
        return cb.like(cb.lower(attribute), pattern, ESCAPE);
    }

    private static String escapeLikeWildcards(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
