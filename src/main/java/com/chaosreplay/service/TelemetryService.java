package com.chaosreplay.service;

import com.chaosreplay.api.dto.CreateTelemetryEventRequest;
import com.chaosreplay.api.dto.PagedResponse;
import com.chaosreplay.api.dto.TelemetryEventResponse;
import com.chaosreplay.domain.TelemetryEvent;
import com.chaosreplay.exception.DuplicateEventException;
import com.chaosreplay.repository.TelemetryEventRepository;
import com.chaosreplay.repository.TelemetryEventSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service orchestrating telemetry event ingestion and querying.
 * Enforces business rules, early duplicate rejection, concurrency protection,
 * and dynamic specification-based filtering.
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final TelemetryEventRepository repository;

    public TelemetryService(TelemetryEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Ingests a new telemetry event into the platform.
     * <p>
     * Employs a two-tier duplicate protection strategy:
     * 1. Application-level check via {@link TelemetryEventRepository#existsByEventId(String)} for early rejection.
     * 2. Database UNIQUE constraint as the authoritative safeguard against concurrent race conditions,
     *    catching {@link DataIntegrityViolationException} and transforming it into {@link DuplicateEventException}.
     *
     * @param request validated event ingestion payload
     * @return persisted telemetry event response DTO
     * @throws DuplicateEventException if an event with the same eventId already exists
     */
    @Transactional
    public TelemetryEventResponse ingestEvent(CreateTelemetryEventRequest request) {
        // Fast-path early duplicate check
        if (repository.existsByEventId(request.eventId())) {
            log.warn("Application-level duplicate event rejection for eventId [{}]", request.eventId());
            throw new DuplicateEventException(request.eventId());
        }

        TelemetryEvent entity = new TelemetryEvent(
                request.eventId().trim(),
                request.timestamp(),
                request.serviceName().trim(),
                request.serviceInstance() != null ? request.serviceInstance().trim() : null,
                request.eventType(),
                request.severity(),
                request.traceId() != null ? request.traceId().trim() : null,
                request.requestId() != null ? request.requestId().trim() : null,
                request.operation() != null ? request.operation().trim() : null,
                request.message(),
                request.metadata()
        );

        try {
            TelemetryEvent saved = repository.saveAndFlush(entity);
            log.info("Ingested telemetry event [eventId={}, service={}, type={}, severity={}]",
                    saved.getEventId(), saved.getServiceName(), saved.getEventType(), saved.getSeverity());
            return TelemetryEventResponse.fromEntity(saved);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Authoritative database UNIQUE constraint violation for eventId [{}]: {}",
                    request.eventId(), ex.getMessage());
            throw new DuplicateEventException(request.eventId(), ex);
        }
    }

    /**
     * Queries telemetry events matching optional search criteria with pagination.
     *
     * @param filter   query filter parameters (serviceName, eventType, severity, traceId, requestId, time window)
     * @param pageable pagination and sorting instructions
     * @return structured paginated telemetry events
     */
    @Transactional(readOnly = true)
    public PagedResponse<TelemetryEventResponse> queryEvents(TelemetryQueryFilter filter, Pageable pageable) {
        if (filter != null) {
            filter.validate();
        }

        Specification<TelemetryEvent> spec = TelemetryEventSpecification.withFilter(filter);
        Page<TelemetryEvent> page = repository.findAll(spec, pageable);

        return PagedResponse.fromPage(page.map(TelemetryEventResponse::fromEntity));
    }
}

