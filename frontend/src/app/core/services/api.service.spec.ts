import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiService } from './api.service';
import { ApiErrorResponse } from '../models/api-error.model';
import { HealthResponse } from '../models/health.model';
import { IncidentInvestigationResponse, IncidentTimelineResponse } from '../models/incident.model';
import { ReplayVerificationResponse } from '../models/replay.model';

describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ApiService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getHealth', () => {
    it('should return health status', () => {
      const mockHealth: HealthResponse = {
        status: 'UP',
        service: 'chaos-replay'
      };

      service.getHealth().subscribe({
        next: (res) => {
          expect(res.status).toBe('UP');
          expect(res.service).toBe('chaos-replay');
        }
      });

      const req = httpMock.expectOne('/api/v1/health');
      expect(req.request.method).toBe('GET');
      req.flush(mockHealth);
    });
  });

  describe('getIncidentInvestigation', () => {
    it('should query trace with encoded URI component and return investigation data', () => {
      const mockInvestigation: IncidentInvestigationResponse = {
        traceId: 'trace-123',
        timeline: {
          traceId: 'trace-123',
          eventCount: 1,
          failureCount: 0,
          failureObserved: false,
          durationMs: 150,
          analysisAvailable: true,
          verificationAvailable: false,
          timeline: []
        },
        correlationAvailable: true,
        scenarioAvailable: false,
        executionAvailable: false,
        verificationAvailable: false,
        analysisAvailable: true,
        analysisStatus: 'ANALYZED',
        conclusion: 'PRIMARY_FAILURE',
        candidates: [],
        evidenceSummary: [],
        limitations: []
      };

      service.getIncidentInvestigation('trace 123').subscribe({
        next: (res) => {
          expect(res.traceId).toBe('trace-123');
        }
      });

      const req = httpMock.expectOne('/api/v1/incidents/traces/trace%20123');
      expect(req.request.method).toBe('GET');
      req.flush(mockInvestigation);
    });
  });

  describe('getIncidentTimeline', () => {
    it('should query timeline endpoint and return timeline data', () => {
      const mockTimeline: IncidentTimelineResponse = {
        traceId: 'trace-abc',
        eventCount: 2,
        failureCount: 0,
        failureObserved: false,
        durationMs: 300,
        analysisAvailable: false,
        verificationAvailable: false,
        timeline: []
      };

      service.getIncidentTimeline('trace-abc').subscribe({
        next: (res) => {
          expect(res.eventCount).toBe(2);
        }
      });

      const req = httpMock.expectOne('/api/v1/incidents/traces/trace-abc/timeline');
      expect(req.request.method).toBe('GET');
      req.flush(mockTimeline);
    });
  });

  describe('getReplayVerification', () => {
    it('should query replay verification by ID', () => {
      const mockVerification: ReplayVerificationResponse = {
        verificationId: 'verif-1',
        scenarioId: 'scen-1',
        status: 'PASSED',
        originalEventCount: 5,
        replayedEventCount: 5,
        originalFailureCount: 0,
        replayedFailureCount: 0,
        originalDurationMs: 100,
        replayedDurationMs: 112,
        eventsMatched: 5,
        eventsMissing: 0,
        eventsUnexpected: 0,
        severityMismatches: 0,
        eventTypeMismatches: 0,
        serviceMismatches: 0,
        createdAt: '2026-10-18T10:00:00Z',
        differences: []
      };

      service.getReplayVerification('verif-1').subscribe({
        next: (res) => {
          expect(res.status).toBe('PASSED');
        }
      });

      const req = httpMock.expectOne('/api/v1/replay/verifications/verif-1');
      expect(req.request.method).toBe('GET');
      req.flush(mockVerification);
    });
  });

  describe('getVerificationsForScenario', () => {
    it('should query scenario verifications', () => {
      service.getVerificationsForScenario('scen-99').subscribe({
        next: (res) => {
          expect(res.length).toBe(1);
        }
      });

      const req = httpMock.expectOne('/api/v1/replay/scenarios/scen-99/verifications');
      expect(req.request.method).toBe('GET');
      req.flush([{ verificationId: 'v1' }]);
    });
  });

  describe('handleError', () => {
    it('should format status 0 as network error', (done) => {
      service.getHealth().subscribe({
        next: () => fail('should have failed with network error'),
        error: (err: ApiErrorResponse) => {
          expect(err.status).toBe(0);
          expect(err.error).toBe('NETWORK_ERROR');
          expect(err.errorCode).toBe('BACKEND_UNAVAILABLE');
          expect(err.message).toContain('Could not connect');
          done();
        }
      });

      const req = httpMock.expectOne('/api/v1/health');
      req.error(new ProgressEvent('error'), { status: 0 });
    });

    it('should preserve structured backend error details', (done) => {
      const backendError = {
        timestamp: '2026-10-18T10:00:00Z',
        status: 404,
        error: 'NOT_FOUND',
        errorCode: 'TRACE_NOT_FOUND',
        message: 'Trace trace-999 was not found',
        correlationId: 'req-corr-123'
      };

      service.getIncidentInvestigation('trace-999').subscribe({
        next: () => fail('should have failed with 404'),
        error: (err: ApiErrorResponse) => {
          expect(err.status).toBe(404);
          expect(err.errorCode).toBe('TRACE_NOT_FOUND');
          expect(err.message).toBe('Trace trace-999 was not found');
          expect(err.correlationId).toBe('req-corr-123');
          done();
        }
      });

      const req = httpMock.expectOne('/api/v1/incidents/traces/trace-999');
      req.flush(backendError, { status: 404, statusText: 'Not Found' });
    });

    it('should format generic HTTP string error fallback', (done) => {
      service.getHealth().subscribe({
        next: () => fail('should have failed with 500'),
        error: (err: ApiErrorResponse) => {
          expect(err.status).toBe(500);
          expect(err.errorCode).toBe('HTTP_500');
          expect(err.message).toBe('Internal Server Error Occurred');
          done();
        }
      });

      const req = httpMock.expectOne('/api/v1/health');
      req.flush('Internal Server Error Occurred', { status: 500, statusText: 'Internal Server Error' });
    });
  });
});
