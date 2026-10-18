import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { SimpleChange } from '@angular/core';
import { ReplayVerificationComponent } from './replay-verification.component';
import { ApiService } from '../../core/services/api.service';
import { IncidentInvestigationResponse } from '../../core/models/incident.model';
import { ReplayVerificationResponse } from '../../core/models/replay.model';

describe('ReplayVerificationComponent', () => {
  let component: ReplayVerificationComponent;
  let fixture: ComponentFixture<ReplayVerificationComponent>;
  let apiServiceMock: jasmine.SpyObj<ApiService>;

  const baseInvestigation: IncidentInvestigationResponse = {
    traceId: 'trace-rep-1',
    timeline: {
      traceId: 'trace-rep-1',
      eventCount: 3,
      failureCount: 0,
      failureObserved: false,
      durationMs: 1200,
      analysisAvailable: false,
      verificationAvailable: false,
      timeline: []
    },
    correlationAvailable: true,
    scenarioAvailable: false,
    executionAvailable: false,
    verificationAvailable: false,
    analysisAvailable: false,
    candidates: [],
    evidenceSummary: [],
    limitations: []
  };

  const mockVerification: ReplayVerificationResponse = {
    verificationId: 'v-123',
    scenarioId: 'scen-456',
    status: 'PASSED',
    originalEventCount: 5,
    replayedEventCount: 5,
    originalFailureCount: 0,
    replayedFailureCount: 0,
    originalDurationMs: 1200,
    replayedDurationMs: 1225,
    eventsMatched: 5,
    eventsMissing: 0,
    eventsUnexpected: 0,
    severityMismatches: 0,
    eventTypeMismatches: 0,
    serviceMismatches: 0,
    createdAt: '2026-10-18T10:05:00Z',
    differences: []
  };

  beforeEach(async () => {
    apiServiceMock = jasmine.createSpyObj('ApiService', ['getReplayVerification']);

    await TestBed.configureTestingModule({
      imports: [ReplayVerificationComponent],
      providers: [
        { provide: ApiService, useValue: apiServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ReplayVerificationComponent);
    component = fixture.componentInstance;
  });

  it('should render fallback when verification is not available', () => {
    component.investigation = { ...baseInvestigation };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.no-verification-card')).toBeTruthy();
    expect(compiled.textContent).toContain('Replay Verification Not Run For This Trace');
  });

  it('should fetch verification details and render metrics when verification is available', () => {
    apiServiceMock.getReplayVerification.and.returnValue(of(mockVerification));

    component.investigation = {
      ...baseInvestigation,
      verificationAvailable: true,
      verificationId: 'v-123',
      scenarioId: 'scen-456',
      verificationStatus: 'PASSED'
    };
    component.ngOnInit();
    fixture.detectChanges();

    expect(apiServiceMock.getReplayVerification).toHaveBeenCalledWith('v-123');
    expect(component.verificationDetails).toEqual(mockVerification);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.metrics-grid')).toBeTruthy();
    expect(compiled.querySelector('.no-differences')).toBeTruthy();
    expect(compiled.textContent).toContain('Perfect fidelity reproduction');
  });

  it('should render differences table when discrepancies are detected', () => {
    const diffVerification: ReplayVerificationResponse = {
      ...mockVerification,
      status: 'FAILED',
      eventsMatched: 4,
      eventsMissing: 1,
      differences: [
        {
          sequenceNumber: 2,
          differenceType: 'EVENT_MISSING',
          expectedEventId: 'evt-exp-2',
          expectedServiceName: 'payment-service',
          expectedSeverity: 'ERROR',
          message: 'Expected payment exception event was not emitted during replay'
        }
      ]
    };

    apiServiceMock.getReplayVerification.and.returnValue(of(diffVerification));

    component.investigation = {
      ...baseInvestigation,
      verificationAvailable: true,
      verificationId: 'v-diff',
      scenarioId: 'scen-diff',
      verificationStatus: 'FAILED'
    };
    component.ngOnInit();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const rows = compiled.querySelectorAll('.differences-table tbody tr');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('EVENT_MISSING');
    expect(rows[0].textContent).toContain('payment-service');
  });

  it('should format duration values correctly', () => {
    expect(component.formatDuration(500)).toBe('500ms');
    expect(component.formatDuration(2500)).toBe('2.50s');
  });
});
