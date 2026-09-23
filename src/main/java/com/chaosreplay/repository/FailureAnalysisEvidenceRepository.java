package com.chaosreplay.repository;

import com.chaosreplay.domain.FailureAnalysisEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for persisting and querying failure analysis evidence.
 */
@Repository
public interface FailureAnalysisEvidenceRepository extends JpaRepository<FailureAnalysisEvidence, Long> {

    List<FailureAnalysisEvidence> findAllByAnalysisIdAndCandidateRankOrderBySequenceNumberAsc(
            String analysisId,
            Integer candidateRank
    );

    List<FailureAnalysisEvidence> findAllByAnalysisIdOrderByCandidateRankAscSequenceNumberAsc(
            String analysisId
    );
}

