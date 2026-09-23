package com.chaosreplay.repository;

import com.chaosreplay.domain.ReplayScenarioEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ReplayScenarioEvent}.
 */
@Repository
public interface ReplayScenarioEventRepository extends JpaRepository<ReplayScenarioEvent, Long> {

    /**
     * Retrieves all events for a given scenario, deterministically ordered by sequence number ascending.
     *
     * @param scenarioId deterministic scenario identifier
     * @return sequence-ordered list of replay scenario events
     */
    List<ReplayScenarioEvent> findAllByScenarioIdOrderBySequenceNumberAsc(String scenarioId);
}

