package com.chaosreplay.repository;

import com.chaosreplay.domain.ReplayVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for persisting and querying failure replay verifications.
 */
@Repository
public interface ReplayVerificationRepository extends JpaRepository<ReplayVerification, Long> {

    Optional<ReplayVerification> findByVerificationId(String verificationId);

    List<ReplayVerification> findAllByScenarioIdOrderByCreatedAtDesc(String scenarioId);

    boolean existsByVerificationId(String verificationId);
}

