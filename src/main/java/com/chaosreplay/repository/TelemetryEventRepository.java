package com.chaosreplay.repository;

import com.chaosreplay.domain.TelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

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
}

