package com.chaosreplay.repository;

import com.chaosreplay.domain.ReplayVerificationDifference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for persisting and querying replay verification differences.
 */
@Repository
public interface ReplayVerificationDifferenceRepository extends JpaRepository<ReplayVerificationDifference, Long> {

    List<ReplayVerificationDifference> findAllByVerificationIdOrderBySequenceNumberAsc(String verificationId);
}

