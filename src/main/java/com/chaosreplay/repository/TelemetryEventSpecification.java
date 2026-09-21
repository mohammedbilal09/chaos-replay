package com.chaosreplay.repository;

import com.chaosreplay.domain.TelemetryEvent;
import com.chaosreplay.service.TelemetryQueryFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for constructing dynamic, type-safe JPA criteria specifications
 * based on provided {@link TelemetryQueryFilter} parameters.
 */
public final class TelemetryEventSpecification {

    private TelemetryEventSpecification() {
        // Utility class
    }

    /**
     * Creates a {@link Specification} representing all predicates defined in the filter.
     *
     * @param filter query filters; if null, matches all records
     * @return composite Specification
     */
    public static Specification<TelemetryEvent> withFilter(TelemetryQueryFilter filter) {
        return (root, query, cb) -> {
            if (filter == null) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();

            if (filter.serviceName() != null && !filter.serviceName().isBlank()) {
                predicates.add(cb.equal(root.get("serviceName"), filter.serviceName().trim()));
            }

            if (filter.eventType() != null) {
                predicates.add(cb.equal(root.get("eventType"), filter.eventType()));
            }

            if (filter.severity() != null) {
                predicates.add(cb.equal(root.get("severity"), filter.severity()));
            }

            if (filter.traceId() != null && !filter.traceId().isBlank()) {
                predicates.add(cb.equal(root.get("traceId"), filter.traceId().trim()));
            }

            if (filter.requestId() != null && !filter.requestId().isBlank()) {
                predicates.add(cb.equal(root.get("requestId"), filter.requestId().trim()));
            }

            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), filter.from()));
            }

            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), filter.to()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

