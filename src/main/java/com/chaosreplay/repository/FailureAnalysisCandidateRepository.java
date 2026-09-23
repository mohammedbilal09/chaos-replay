package com.chaosreplay.repository;

import com.chaosreplay.domain.FailureAnalysisCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for persisting and querying failure analysis candidates.
 */
@Repository
public interface FailureAnalysisCandidateRepository extends JpaRepository<FailureAnalysisCandidate, Long> {

    List<FailureAnalysisCandidate> findAllByAnalysisIdOrderByRankAsc(String analysisId);
}

