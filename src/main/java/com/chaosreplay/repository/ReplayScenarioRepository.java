package com.chaosreplay.repository;

import com.chaosreplay.domain.ReplayScenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ReplayScenario}.
 */
@Repository
public interface ReplayScenarioRepository extends JpaRepository<ReplayScenario, Long> {

    /**
     * Finds a replay scenario by its unique deterministic scenario identifier.
     *
     * @param scenarioId deterministic scenario identifier
     * @return an Optional containing the scenario if found
     */
    Optional<ReplayScenario> findByScenarioId(String scenarioId);

    /**
     * Checks if a replay scenario exists by its unique scenario identifier.
     *
     * @param scenarioId deterministic scenario identifier
     * @return true if the scenario exists, false otherwise
     */
    boolean existsByScenarioId(String scenarioId);

    /**
     * Finds all replay scenarios generated from a specific source trace ID, ordered by creation time.
     *
     * @param sourceTraceId distributed trace identifier
     * @return list of replay scenarios derived from the trace
     */
    List<ReplayScenario> findBySourceTraceIdOrderByCreatedAtAsc(String sourceTraceId);

    /**
     * Finds the first replay scenario generated from a specific source trace ID.
     *
     * @param sourceTraceId distributed trace identifier
     * @return an Optional containing the earliest scenario for the trace
     */
    Optional<ReplayScenario> findFirstBySourceTraceIdOrderByCreatedAtAsc(String sourceTraceId);
}

