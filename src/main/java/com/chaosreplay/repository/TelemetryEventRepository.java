package com.chaosreplay.repository;

import com.chaosreplay.domain.TelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link TelemetryEvent}.
 * Provides CRUD operations, unique event existence verification,
 * and dynamic specification querying with pagination.
 */
@Repository
public interface TelemetryEventRepository extends JpaRepository<TelemetryEvent, Long>, JpaSpecificationExecutor<TelemetryEvent> {

    /**
     * Checks if an event with the given client-side eventId already exists.
     * Serves as an early application-level validation optimization before persistence.
     *
     * @param eventId unique event identifier
     * @return true if an event with the eventId exists, false otherwise
     */
    boolean existsByEventId(String eventId);

    /**
     * Finds a telemetry event by its unique eventId.
     *
     * @param eventId unique event identifier
     * @return an Optional containing the event if found
     */
    Optional<TelemetryEvent> findByEventId(String eventId);

    /**
     * Finds all telemetry events for a specific traceId, ordered deterministically
     * by occurrence timestamp ascending, with eventId ascending as the tie-breaker.
     *
     * @param traceId distributed trace identifier
     * @return chronologically ordered list of events
     */
    List<TelemetryEvent> findAllByTraceIdOrderByTimestampAscEventIdAsc(String traceId);

    /**
     * Finds all telemetry events for a specific requestId, ordered deterministically
     * by occurrence timestamp ascending, with eventId ascending as the tie-breaker.
     *
     * @param requestId ingress request identifier
     * @return chronologically ordered list of events
     */
    List<TelemetryEvent> findAllByRequestIdOrderByTimestampAscEventIdAsc(String requestId);
}

