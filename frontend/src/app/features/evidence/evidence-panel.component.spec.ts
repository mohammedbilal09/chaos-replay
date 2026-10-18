import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EvidencePanelComponent } from './evidence-panel.component';
import { IncidentInvestigationResponse } from '../../core/models/incident.model';

describe('EvidencePanelComponent', () => {
  let component: EvidencePanelComponent;
  let fixture: ComponentFixture<EvidencePanelComponent>;

  const mockInvestigation: IncidentInvestigationResponse = {
    traceId: 'trace-test-1',
    analysisStatus: 'ANALYZED',
    conclusion: 'PRIMARY_FAILURE',
    confidenceScore: 0.95,
    primaryServiceName: 'payment-service',
    primaryEventId: 'evt-payment-timeout',
    correlationAvailable: true,
    scenarioAvailable: false,
    executionAvailable: false,
    verificationAvailable: false,
    analysisAvailable: true,
    evidenceSummary: [
      'Downstream payment gateway responded with 504 Gateway Timeout',
      'Trace ended abruptly without compensation transaction'
    ],
    candidates: [
      {
        rank: 1,
        serviceName: 'payment-service',
        eventId: 'evt-payment-timeout',
        eventType: 'ERROR',
        severity: 'ERROR',
        candidateType: 'FIRST_OBSERVED_FAILURE',
        confidenceScore: 0.95,
        firstObservedAt: '2026-10-18T10:00:00Z',
        description: 'Payment gateway timed out after 5000ms',
        evidence: [
          {
            sequenceNumber: 1,
            eventId: 'evt-payment-timeout',
            evidenceType: 'FIRST_CHRONOLOGICAL_FAILURE',
            evidenceValue: 'SocketTimeoutException: Read timed out'
          }
        ]
      },
      {
        rank: 2,
        serviceName: 'order-service',
        eventId: 'evt-order-span',
        eventType: 'RPC_CALL',
        severity: 'WARN',
        candidateType: 'CORRELATED_ANOMALY',
        confidenceScore: 0.60,
        firstObservedAt: '2026-10-18T10:00:01Z',
        description: 'Order service did not retry failed charge',
        evidence: []
      }
    ],
    limitations: [
      'Downstream provider logs are unavailable (black-box service)',
      'Database connection pool metrics were not captured'
    ],
    timeline: {
      traceId: 'trace-test-1',
      eventCount: 2,
      failureCount: 1,
      failureObserved: true,
      durationMs: 5000,
      analysisAvailable: true,
      verificationAvailable: false,
      timeline: []
    }
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EvidencePanelComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(EvidencePanelComponent);
    component = fixture.componentInstance;
    component.investigation = { ...mockInvestigation };
    fixture.detectChanges();
  });

  it('should create and render summary card details', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.conclusion-pill')?.textContent).toContain('PRIMARY_FAILURE');
    expect(compiled.querySelector('.confidence-val')?.textContent).toContain('95%');
    expect(compiled.querySelector('.focus-val')?.textContent).toContain('payment-service');
  });

  it('should render ranked candidates', () => {
    const cards = fixture.nativeElement.querySelectorAll('.candidate-card');
    expect(cards.length).toBe(2);
    expect(cards[0].querySelector('.rank-number')?.textContent).toContain('#1');
    expect(cards[0].classList.contains('primary-candidate')).toBeTrue();
    expect(cards[1].querySelector('.rank-number')?.textContent).toContain('#2');
  });

  it('should render limitations list', () => {
    const limitations = fixture.nativeElement.querySelectorAll('.limitations-list li');
    expect(limitations.length).toBe(2);
    expect(limitations[0].textContent).toContain('Downstream provider logs are unavailable');
  });

  it('should emit eventSelected when primary event link is clicked', () => {
    let selectedId: string | undefined;
    component.eventSelected.subscribe((id) => selectedId = id);

    const linkBtn = fixture.nativeElement.querySelector('.event-link-btn');
    linkBtn.click();

    expect(selectedId).toBe('evt-payment-timeout');
  });

  it('should emit eventSelected when jump to event button is clicked in candidate evidence', () => {
    let selectedId: string | undefined;
    component.eventSelected.subscribe((id) => selectedId = id);

    const jumpBtn = fixture.nativeElement.querySelector('.event-jump-btn');
    jumpBtn.click();

    expect(selectedId).toBe('evt-payment-timeout');
  });
});
