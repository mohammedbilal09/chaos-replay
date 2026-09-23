package com.chaosreplay.repository;

import com.chaosreplay.domain.FailureAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for persisting and querying failure analyses.
 */
@Repository
public interface FailureAnalysisRepository extends JpaRepository<FailureAnalysis, Long> {

    Optional<FailureAnalysis> findByAnalysisId(String analysisId);

    Optional<FailureAnalysis> findFirstByTraceIdOrderByCreatedAtDesc(String traceId);

    List<FailureAnalysis> findAllByTraceIdOrderByCreatedAtDesc(String traceId);

    boolean existsByAnalysisId(String analysisId);
}

