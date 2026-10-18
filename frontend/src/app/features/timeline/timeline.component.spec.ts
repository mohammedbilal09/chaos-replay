import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { TimelineComponent } from './timeline.component';
import { IncidentTimelineEntryResponse } from '../../core/models/incident.model';

describe('TimelineComponent', () => {
  let component: TimelineComponent;
  let fixture: ComponentFixture<TimelineComponent>;

  const mockEvents: IncidentTimelineEntryResponse[] = [
    {
      sequenceNumber: 1,
      eventId: 'evt-1',
      timestamp: '2026-10-18T10:00:00.000Z',
      offsetMs: 0,
      serviceName: 'order-service',
      serviceInstance: 'order-1',
      eventType: 'RPC_CALL',
      severity: 'INFO',
      operation: 'POST /orders',
      message: 'Processing order request',
      entryCategory: 'CORRELATED_EVENT',
      failureObserved: false,
      failurePropagation: false,
      evidenceReferences: []
    },
    {
      sequenceNumber: 2,
      eventId: 'evt-2',
      timestamp: '2026-10-18T10:00:00.350Z',
      offsetMs: 350,
      serviceName: 'payment-service',
      serviceInstance: 'payment-1',
      eventType: 'ERROR',
      severity: 'ERROR',
      operation: 'chargeCard',
      message: 'Payment gateway timeout',
      entryCategory: 'ROOT_CAUSE',
      failureObserved: true,
      failurePropagation: false,
      relatedCandidateRank: 1,
      evidenceReferences: ['EV-001', 'TIMEOUT_EXCEPTION']
    },
    {
      sequenceNumber: 3,
      eventId: 'evt-3',
      timestamp: '2026-10-18T10:00:01.500Z',
      offsetMs: 1500,
      serviceName: 'order-service',
      serviceInstance: 'order-1',
      eventType: 'ERROR',
      severity: 'WARN',
      operation: 'fallbackHandling',
      message: 'Order fallback initiated',
      entryCategory: 'FAILURE_PROPAGATION',
      failureObserved: true,
      failurePropagation: true,
      evidenceReferences: []
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TimelineComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(TimelineComponent);
    component = fixture.componentInstance;
  });

  it('should create and initialize unique services and filtered events', () => {
    component.events = [...mockEvents];
    component.ngOnChanges({
      events: new SimpleChange(null, component.events, true)
    });
    fixture.detectChanges();

    expect(component.filteredEvents.length).toBe(3);
    expect(component.uniqueServices).toEqual(['order-service', 'payment-service']);
  });

  describe('formatOffset', () => {
    it('should format offsets correctly', () => {
      expect(component.formatOffset(0)).toBe('+0ms');
      expect(component.formatOffset(350)).toBe('+350ms');
      expect(component.formatOffset(1500)).toBe('+1.50s');
    });
  });

  describe('filtering', () => {
    beforeEach(() => {
      component.events = [...mockEvents];
      component.ngOnChanges({
        events: new SimpleChange(null, component.events, true)
      });
      fixture.detectChanges();
    });

    it('should filter by severity ERROR (including FATAL)', () => {
      component.selectedSeverity = 'ERROR';
      component.applyFilters();
      expect(component.filteredEvents.length).toBe(1);
      expect(component.filteredEvents[0].eventId).toBe('evt-2');
    });

    it('should filter by severity WARN', () => {
      component.selectedSeverity = 'WARN';
      component.applyFilters();
      expect(component.filteredEvents.length).toBe(1);
      expect(component.filteredEvents[0].eventId).toBe('evt-3');
    });

    it('should filter by severity INFO', () => {
      component.selectedSeverity = 'INFO';
      component.applyFilters();
      expect(component.filteredEvents.length).toBe(1);
      expect(component.filteredEvents[0].eventId).toBe('evt-1');
    });

    it('should filter by service', () => {
      component.selectedService = 'payment-service';
      component.applyFilters();
      expect(component.filteredEvents.length).toBe(1);
      expect(component.filteredEvents[0].serviceName).toBe('payment-service');
    });

    it('should filter by query text in message', () => {
      component.filterText = 'timeout';
      component.applyFilters();
      expect(component.filteredEvents.length).toBe(1);
      expect(component.filteredEvents[0].eventId).toBe('evt-2');
    });

    it('should filter by query text in operation', () => {
      component.filterText = 'POST';
      component.applyFilters();
      expect(component.filteredEvents.length).toBe(1);
      expect(component.filteredEvents[0].eventId).toBe('evt-1');
    });
  });

  describe('expansion and highlighting', () => {
    it('should toggle event expansion', () => {
      expect(component.isExpanded('evt-1')).toBeFalse();
      component.toggleExpand('evt-1');
      expect(component.isExpanded('evt-1')).toBeTrue();
      component.toggleExpand('evt-1');
      expect(component.isExpanded('evt-1')).toBeFalse();
    });

    it('should auto-expand highlighted event ID', () => {
      component.events = [...mockEvents];
      component.highlightedEventId = 'evt-2';
      component.ngOnChanges({
        highlightedEventId: new SimpleChange(null, 'evt-2', true)
      });
      expect(component.isExpanded('evt-2')).toBeTrue();
    });
  });
});
