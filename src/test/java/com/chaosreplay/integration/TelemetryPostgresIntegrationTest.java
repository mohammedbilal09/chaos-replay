package com.chaosreplay.integration;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.PagedResponse;
import com.chaosreplay.api.dto.TelemetryEventResponse;
import com.chaosreplay.domain.EventType;
import com.chaosreplay.domain.Severity;
import com.chaosreplay.domain.TelemetryEvent;
import com.chaosreplay.exception.DuplicateEventException;
import com.chaosreplay.repository.TelemetryEventRepository;
import com.chaosreplay.service.TelemetryQueryFilter;
import com.chaosreplay.service.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL integration test utilizing Testcontainers.
 * <p>
 * Does NOT skip when Docker is unavailable — fails explicitly to guarantee
 * genuine verification of Flyway migrations, database constraints, JSONB column
 * mapping, and repository specifications against a live PostgreSQL 16 instance.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class TelemetryPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TelemetryPostgresIntegrationTest.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TelemetryEventRepository repository;

    @Autowired
    private TelemetryService service;

    @BeforeEach
    void setUp() {
        log.info("==== VERIFIED: Testcontainers PostgreSQL is active at [{}] ====", postgres.getJdbcUrl());
        repository.deleteAll();
    }

    @Test
    @DisplayName("Flyway creates schema and TelemetryEvent persists to real PostgreSQL with JSONB metadata")
    void persistAndRetrieveTelemetryEvent_success() {
        Instant occurrence = Instant.parse("2026-09-21T18:00:00Z");
        CreateTelemetryEventRequest request = new CreateTelemetryEventRequest(
                "evt-pg-001",
                occurrence,
                "inventory-service",
                "inv-node-1",
                EventType.DATABASE,
                Severity.WARN,
                "trace-pg-99",
                "req-pg-77",
                "SELECT stock FROM warehouse",
                "Lock wait timeout approaching threshold",
                Map.of("warehouseId", "wh-south", "latencyMs", 1850)
        );

        TelemetryEventResponse ingested = service.ingestEvent(request);

        assertThat(ingested.id()).isNotNull();
        assertThat(ingested.eventId()).isEqualTo("evt-pg-001");
        assertThat(ingested.timestamp()).isEqualTo(occurrence);
        assertThat(ingested.createdAt()).isNotNull();
        assertThat(ingested.metadata())
                .containsEntry("warehouseId", "wh-south")
                .containsEntry("latencyMs", 1850);

        // Verify retrieval directly from repository
        Optional<TelemetryEvent> fetched = repository.findByEventId("evt-pg-001");
        assertThat(fetched).isPresent();
        TelemetryEvent entity = fetched.get();
        assertThat(entity.getServiceName()).isEqualTo("inventory-service");
        assertThat(entity.getEventType()).isEqualTo(EventType.DATABASE);
        assertThat(entity.getSeverity()).isEqualTo(Severity.WARN);
        assertThat(entity.getMetadata()).containsEntry("warehouseId", "wh-south");
    }

    @Test
    @DisplayName("Database-level UNIQUE constraint on event_id raises DataIntegrityViolationException on duplicate insert")
    void databaseLevelUniqueConstraint_enforced() {
        TelemetryEvent event1 = new TelemetryEvent(
                "evt-duplicate-db",
                Instant.now(),
                "order-service",
                "inst-1",
                EventType.REQUEST,
                Severity.INFO,
                "trace-1",
                "req-1",
                "POST /orders",
                "First event",
                Map.of()
        );
        repository.saveAndFlush(event1);

        TelemetryEvent event2 = new TelemetryEvent(
                "evt-duplicate-db",
                Instant.now(),
                "order-service",
                "inst-2",
                EventType.RESPONSE,
                Severity.INFO,
                "trace-2",
                "req-2",
                "POST /orders",
                "Conflicting event with identical eventId",
                Map.of()
        );

        // Direct persistence bypassing service layer check MUST fail at DB constraint
        assertThatThrownBy(() -> repository.saveAndFlush(event2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("TelemetryService rejects duplicate eventId with DuplicateEventException")
    void serviceLayerDuplicateDetection_rejectsDuplicate() {
        CreateTelemetryEventRequest request = new CreateTelemetryEventRequest(
                "evt-duplicate-svc",
                Instant.now(),
                "user-service",
                "user-01",
                EventType.LOG,
                Severity.INFO,
                "trace-abc",
                "req-def",
                "GET /users/me",
                "User login",
                Map.of()
        );

        service.ingestEvent(request);

        assertThatThrownBy(() -> service.ingestEvent(request))
                .isInstanceOf(DuplicateEventException.class)
                .hasMessageContaining("evt-duplicate-svc");
    }

    @Test
    @DisplayName("Specification query filters correctly against real PostgreSQL database")
    void queryTelemetryEvents_withFiltersAndPagination() {
        Instant t1 = Instant.parse("2026-09-21T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-21T12:00:00Z");
        Instant t3 = Instant.parse("2026-09-21T14:00:00Z");

        service.ingestEvent(new CreateTelemetryEventRequest(
                "evt-filter-1", t1, "auth-service", "auth-1", EventType.REQUEST, Severity.INFO,
                "trace-common", "req-1", "POST /login", "Login started", Map.of()
        ));
        service.ingestEvent(new CreateTelemetryEventRequest(
                "evt-filter-2", t2, "auth-service", "auth-1", EventType.ERROR, Severity.ERROR,
                "trace-common", "req-1", "POST /login", "Invalid credentials", Map.of()
        ));
        service.ingestEvent(new CreateTelemetryEventRequest(
                "evt-filter-3", t3, "payment-service", "pay-1", EventType.ERROR, Severity.ERROR,
                "trace-other", "req-2", "POST /charge", "Card declined", Map.of()
        ));

        // 1. Filter by serviceName + severity
        TelemetryQueryFilter filter1 = new TelemetryQueryFilter(
                "auth-service", null, Severity.ERROR, null, null, null, null
        );
        PagedResponse<TelemetryEventResponse> result1 = service.queryEvents(
                filter1, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "timestamp"))
        );
        assertThat(result1.content()).hasSize(1);
        assertThat(result1.content().get(0).eventId()).isEqualTo("evt-filter-2");

        // 2. Filter by traceId across services
        TelemetryQueryFilter filter2 = new TelemetryQueryFilter(
                null, null, null, "trace-common", null, null, null
        );
        PagedResponse<TelemetryEventResponse> result2 = service.queryEvents(
                filter2, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "timestamp"))
        );
        assertThat(result2.content()).hasSize(2);

        // 3. Filter by timestamp window
        TelemetryQueryFilter filter3 = new TelemetryQueryFilter(
                null, null, null, null, null,
                Instant.parse("2026-09-21T11:00:00Z"),
                Instant.parse("2026-09-21T13:00:00Z")
        );
        PagedResponse<TelemetryEventResponse> result3 = service.queryEvents(
                filter3, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "timestamp"))
        );
        assertThat(result3.content()).hasSize(1);
        assertThat(result3.content().get(0).eventId()).isEqualTo("evt-filter-2");
    }
}

