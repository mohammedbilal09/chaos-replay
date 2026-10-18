import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError, Subject } from 'rxjs';
import { IncidentDashboardComponent } from './incident-dashboard.component';
import { ApiService } from '../../core/services/api.service';
import { IncidentInvestigationResponse } from '../../core/models/incident.model';
import { ApiErrorResponse } from '../../core/models/api-error.model';
import { HealthResponse } from '../../core/models/health.model';

describe('IncidentDashboardComponent', () => {
  let component: IncidentDashboardComponent;
  let fixture: ComponentFixture<IncidentDashboardComponent>;
  let apiServiceMock: jasmine.SpyObj<ApiService>;

  const mockHealth: HealthResponse = {
    status: 'UP',
    service: 'chaos-replay'
  };

  const mockInvestigation: IncidentInvestigationResponse = {
    traceId: 'trace-dash-1',
    analysisStatus: 'ANALYZED',
    conclusion: 'PRIMARY_FAILURE',
    confidenceScore: 0.9,
    primaryServiceName: 'order-service',
    primaryEventId: 'evt-order-1',
    correlationAvailable: true,
    scenarioAvailable: true,
    scenarioId: 'scen-1',
    executionAvailable: true,
    verificationAvailable: false,
    analysisAvailable: true,
    evidenceSummary: ['Downstream failure'],
    candidates: [],
    limitations: [],
    timeline: {
      traceId: 'trace-dash-1',
      eventCount: 4,
      failureCount: 1,
      failureObserved: true,
      durationMs: 850,
      analysisAvailable: true,
      verificationAvailable: false,
      timeline: []
    }
  };

  beforeEach(async () => {
    apiServiceMock = jasmine.createSpyObj('ApiService', [
      'getHealth',
      'getIncidentInvestigation'
    ]);
    apiServiceMock.getHealth.and.returnValue(of(mockHealth));
    apiServiceMock.getIncidentInvestigation.and.returnValue(of(mockInvestigation));

    await TestBed.configureTestingModule({
      imports: [IncidentDashboardComponent],
      providers: [
        { provide: ApiService, useValue: apiServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(IncidentDashboardComponent);
    component = fixture.componentInstance;
  });

  it('should initialize and check backend health', () => {
    fixture.detectChanges();
    expect(component.backendStatus).toBe('UP');
    expect(apiServiceMock.getHealth).toHaveBeenCalled();
  });

  describe('trace ID validation', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should reject empty or whitespace input', () => {
      component.traceIdInput = '   ';
      component.onSearchSubmit();
      expect(component.validationError).toBe('Please enter a valid distributed trace ID.');
      expect(apiServiceMock.getIncidentInvestigation).not.toHaveBeenCalled();
    });

    it('should reject invalid characters in trace ID', () => {
      component.traceIdInput = 'trace id with spaces!';
      component.onSearchSubmit();
      expect(component.validationError).toContain('Trace ID must contain only alphanumeric characters');
      expect(apiServiceMock.getIncidentInvestigation).not.toHaveBeenCalled();
    });

    it('should accept valid trace IDs and trigger search', fakeAsync(() => {
      component.traceIdInput = 'valid-trace_123.abc:0';
      component.onSearchSubmit();
      tick();

      expect(component.validationError).toBe('');
      expect(apiServiceMock.getIncidentInvestigation).toHaveBeenCalledWith('valid-trace_123.abc:0');
      expect(component.investigation).toEqual(mockInvestigation);
      expect(component.isLoading).toBeFalse();
    }));
  });

  describe('search and error handling', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should render error alert when investigation fails', fakeAsync(() => {
      const errorResponse: ApiErrorResponse = {
        status: 404,
        error: 'NOT_FOUND',
        errorCode: 'TRACE_NOT_FOUND',
        message: 'Trace not found in repository'
      };
      apiServiceMock.getIncidentInvestigation.and.returnValue(throwError(() => errorResponse));

      component.traceIdInput = 'missing-trace-999';
      component.onSearchSubmit();
      tick();

      expect(component.currentError).toEqual(errorResponse);
      expect(component.investigation).toBeUndefined();
      expect(component.isLoading).toBeFalse();
    }));

    it('should cancel stale requests when a new search is submitted quickly', fakeAsync(() => {
      const slowSubject = new Subject<IncidentInvestigationResponse>();
      apiServiceMock.getIncidentInvestigation.and.callFake((id: string) => {
        if (id === 'first-trace') {
          return slowSubject.asObservable();
        }
        return of({ ...mockInvestigation, traceId: 'second-trace' });
      });

      component.traceIdInput = 'first-trace';
      component.onSearchSubmit();

      component.traceIdInput = 'second-trace';
      component.onSearchSubmit();
      tick();

      expect(component.investigation?.traceId).toBe('second-trace');
    }));

    it('should successfully recover and search again after an initial error (retry capability)', fakeAsync(() => {
      const errorResponse: ApiErrorResponse = {
        status: 500,
        error: 'INTERNAL_ERROR',
        message: 'Database temporarily unavailable'
      };
      // First attempt fails
      apiServiceMock.getIncidentInvestigation.and.returnValue(throwError(() => errorResponse));

      component.traceIdInput = 'trace-retry-1';
      component.onSearchSubmit();
      tick();

      expect(component.currentError).toEqual(errorResponse);
      expect(component.investigation).toBeUndefined();

      // Second attempt (retry) succeeds
      apiServiceMock.getIncidentInvestigation.and.returnValue(of(mockInvestigation));
      component.onSearchSubmit();
      tick();

      expect(component.investigation).toEqual(mockInvestigation);
      expect(component.currentError).toBeUndefined();
      expect(component.isLoading).toBeFalse();
    }));
  });

  describe('navigation and tab switching', () => {
    beforeEach(fakeAsync(() => {
      fixture.detectChanges();
      component.traceIdInput = 'trace-dash-1';
      component.onSearchSubmit();
      tick();
      fixture.detectChanges();
    }));

    it('should switch tabs', () => {
      expect(component.activeTab).toBe('timeline');

      component.activeTab = 'evidence';
      expect(component.activeTab).toBe('evidence');

      component.activeTab = 'replay';
      expect(component.activeTab).toBe('replay');
    });

    it('should switch to timeline tab and set highlightedEventId when eventSelected emitted', () => {
      component.activeTab = 'evidence';
      component.onEventSelectedFromEvidence('evt-target-42');

      expect(component.activeTab).toBe('timeline');
      expect(component.highlightedEventId).toBe('evt-target-42');
    });

    it('should format duration values properly', () => {
      expect(component.formatDuration(850)).toBe('850ms');
      expect(component.formatDuration(3200)).toBe('3.20s');
    });
  });
});
