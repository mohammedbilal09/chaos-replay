import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IncidentTimelineEntryResponse, Severity } from '../../core/models/incident.model';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';

@Component({
  selector: 'app-timeline',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  template: `
    <section class="timeline-container" aria-label="Incident Event Timeline">
      <!-- Controls & Filter Header -->
      <div class="timeline-controls">
        <div class="search-box">
          <label for="filter-text" class="sr-only">Filter events</label>
          <input
            id="filter-text"
            type="text"
            [(ngModel)]="filterText"
            (ngModelChange)="applyFilters()"
            placeholder="Filter message, operation, or event ID..."
            class="filter-input"
          />
        </div>

        <div class="filter-group">
          <label for="severity-filter" class="filter-label">Severity:</label>
          <select
            id="severity-filter"
            [(ngModel)]="selectedSeverity"
            (ngModelChange)="applyFilters()"
            class="filter-select"
          >
            <option value="ALL">All Severities</option>
            <option value="ERROR">Error & Fatal Only</option>
            <option value="WARN">Warnings Only</option>
            <option value="INFO">Info Only</option>
          </select>
        </div>

        <div class="filter-group" *ngIf="uniqueServices.length > 1">
          <label for="service-filter" class="filter-label">Service:</label>
          <select
            id="service-filter"
            [(ngModel)]="selectedService"
            (ngModelChange)="applyFilters()"
            class="filter-select"
          >
            <option value="ALL">All Services ({{ uniqueServices.length }})</option>
            <option *ngFor="let s of uniqueServices" [value]="s">{{ s }}</option>
          </select>
        </div>

        <div class="timeline-counter">
          Showing <strong>{{ filteredEvents.length }}</strong> of {{ events.length }} events
        </div>
      </div>

      <!-- Empty State -->
      <div *ngIf="filteredEvents.length === 0" class="timeline-empty">
        <span class="empty-icon">🔍</span>
        <p>No timeline events match the selected filters.</p>
      </div>

      <!-- Chronological Event List -->
      <div *ngIf="filteredEvents.length > 0" class="events-list" role="feed" aria-busy="false">
        <article
          *ngFor="let event of filteredEvents; trackBy: trackBySeq"
          class="event-row"
          [id]="'timeline-event-' + event.eventId"
          [class.highlighted]="highlightedEventId === event.eventId"
          [class.failure-event]="event.failureObserved"
          [class.first-failure]="event.entryCategory === 'FIRST_FAILURE'"
          [class.root-cause]="event.entryCategory === 'ROOT_CAUSE'"
          [class.propagation]="event.failurePropagation"
        >
          <!-- Left sequence & timing marker -->
          <div class="event-marker">
            <span class="seq-num code-mono">#{{ event.sequenceNumber }}</span>
            <span class="offset-badge code-mono">{{ formatOffset(event.offsetMs) }}</span>
          </div>

          <!-- Main Event Content -->
          <div class="event-main">
            <div class="event-top">
              <div class="badges-row">
                <app-status-badge [value]="event.severity" type="severity"></app-status-badge>

                <span class="service-pill code-mono" [title]="event.serviceInstance || event.serviceName">
                  {{ event.serviceName }}
                </span>

                <span class="type-pill code-mono">{{ event.eventType }}</span>

                <span *ngIf="event.entryCategory === 'DATABASE_FAILURE'" class="category-badge first-failure-badge">
                  🗄️ Database Failure
                </span>

                <span *ngIf="event.entryCategory === 'EXTERNAL_CALL_FAILURE'" class="category-badge first-failure-badge">
                  🌐 External Call Failure
                </span>

                <span *ngIf="event.entryCategory === 'TIMEOUT_SIGNAL'" class="category-badge first-failure-badge">
                  ⏱️ Timeout Signal
                </span>

                <span *ngIf="event.entryCategory === 'FATAL_EVENT'" class="category-badge first-failure-badge">
                  ⚡ Fatal Event
                </span>

                <span *ngIf="event.entryCategory === 'ERROR_EVENT'" class="category-badge first-failure-badge">
                  🚨 Error Event
                </span>

                <span *ngIf="event.entryCategory === 'ROOT_CAUSE'" class="category-badge root-cause-badge">
                  🎯 Root Cause Candidate
                </span>

                <span *ngIf="event.entryCategory === 'FIRST_FAILURE'" class="category-badge first-failure-badge">
                  🚨 First Failure
                </span>

                <span *ngIf="event.entryCategory === 'DOWNSTREAM_ERROR' || event.failurePropagation" class="category-badge propagation-badge">
                  ↳ Propagation
                </span>

                <span *ngIf="event.entryCategory === 'REPLAY_DIVERGENCE'" class="category-badge propagation-badge">
                  ⚠️ Replay Divergence
                </span>

                <span *ngIf="event.relatedCandidateRank" class="candidate-rank-pill">
                  Rank #{{ event.relatedCandidateRank }}
                </span>
              </div>

              <time class="event-time code-mono" [attr.datetime]="event.timestamp">
                {{ formatTimestamp(event.timestamp) }}
              </time>
            </div>

            <!-- Operation & Message -->
            <div class="event-body">
              <div class="event-operation code-mono" *ngIf="event.operation">
                <strong>op:</strong> {{ event.operation }}
              </div>
              <p class="event-message" *ngIf="event.message">
                {{ event.message }}
              </p>
            </div>

            <!-- Evidence References -->
            <div class="evidence-references" *ngIf="event.evidenceReferences && event.evidenceReferences.length > 0">
              <span class="evidence-tag" *ngFor="let ref of event.evidenceReferences">
                📌 {{ ref }}
              </span>
            </div>

            <!-- Expandable Details Accordion -->
            <div class="expand-wrapper">
              <button
                type="button"
                class="toggle-details-btn"
                (click)="toggleExpand(event.eventId)"
                [attr.aria-expanded]="isExpanded(event.eventId)"
              >
                {{ isExpanded(event.eventId) ? 'Hide Technical Metadata ▲' : 'Inspect Details ▼' }}
              </button>

              <div class="expanded-details" *ngIf="isExpanded(event.eventId)">
                <div class="detail-grid">
                  <div class="detail-item">
                    <span class="detail-key">Event ID:</span>
                    <span class="detail-val code-mono">{{ event.eventId }}</span>
                  </div>
                  <div class="detail-item" *ngIf="event.serviceInstance">
                    <span class="detail-key">Instance:</span>
                    <span class="detail-val code-mono">{{ event.serviceInstance }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-key">ISO UTC Time:</span>
                    <span class="detail-val code-mono">{{ event.timestamp }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-key">Exact Offset:</span>
                    <span class="detail-val code-mono">{{ event.offsetMs }} ms</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>
    </section>
  `,
  styles: [`
    .timeline-container {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .timeline-controls {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 0.85rem 1rem;
      background: var(--bg-surface);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-md);
      flex-wrap: wrap;
    }
    .search-box {
      flex: 1 1 240px;
    }
    .filter-input {
      width: 100%;
      background: var(--bg-primary);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-sm);
      padding: 0.45rem 0.75rem;
      color: var(--text-primary);
    }
    .filter-group {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .filter-label {
      color: var(--text-secondary);
      font-size: 0.8rem;
    }
    .filter-select {
      background: var(--bg-primary);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-sm);
      padding: 0.4rem 0.65rem;
      color: var(--text-primary);
    }
    .timeline-counter {
      margin-left: auto;
      font-size: 0.8rem;
      color: var(--text-secondary);
    }
    .timeline-empty {
      padding: 3rem 1rem;
      text-align: center;
      background: var(--bg-surface);
      border: 1px dashed var(--border-default);
      border-radius: var(--radius-md);
      color: var(--text-muted);
    }
    .empty-icon {
      font-size: 2rem;
      display: block;
      margin-bottom: 0.5rem;
    }
    .events-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .event-row {
      display: flex;
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      overflow: hidden;
      transition: border-color 0.15s, background-color 0.15s;
    }
    .event-row:hover {
      border-color: var(--border-default);
      background: #141d2e;
    }
    .event-row.highlighted {
      border-color: var(--color-cyan);
      box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.35);
      background: rgba(56, 189, 248, 0.05);
    }
    .event-row.first-failure {
      border-left: 4px solid var(--color-red);
    }
    .event-row.root-cause {
      border-left: 4px solid var(--color-purple);
    }
    .event-row.propagation {
      border-left: 4px solid var(--color-yellow);
    }
    .event-marker {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-start;
      padding: 0.85rem 0.75rem;
      background: rgba(0, 0, 0, 0.2);
      border-right: 1px solid var(--border-subtle);
      min-width: 75px;
      gap: 0.35rem;
    }
    .seq-num {
      font-size: 0.75rem;
      color: var(--text-muted);
      font-weight: 600;
    }
    .offset-badge {
      font-size: 0.75rem;
      color: var(--color-cyan);
      font-weight: 700;
    }
    .event-main {
      flex: 1 1 auto;
      padding: 0.85rem 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      min-width: 0;
    }
    .event-top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      flex-wrap: wrap;
    }
    .badges-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-wrap: wrap;
    }
    .service-pill {
      background: #1e3a8a;
      color: #93c5fd;
      padding: 0.15rem 0.5rem;
      border-radius: var(--radius-sm);
      font-size: 0.75rem;
      font-weight: 600;
    }
    .type-pill {
      background: rgba(255, 255, 255, 0.08);
      color: var(--text-secondary);
      padding: 0.15rem 0.45rem;
      border-radius: var(--radius-sm);
      font-size: 0.75rem;
    }
    .category-badge {
      font-size: 0.75rem;
      font-weight: 700;
      padding: 0.15rem 0.45rem;
      border-radius: var(--radius-sm);
    }
    .first-failure-badge {
      background: rgba(248, 113, 113, 0.2);
      color: var(--color-red);
      border: 1px solid rgba(248, 113, 113, 0.4);
    }
    .root-cause-badge {
      background: rgba(192, 132, 252, 0.2);
      color: var(--color-purple);
      border: 1px solid rgba(192, 132, 252, 0.4);
    }
    .propagation-badge {
      background: rgba(251, 191, 36, 0.2);
      color: var(--color-yellow);
      border: 1px solid rgba(251, 191, 36, 0.4);
    }
    .candidate-rank-pill {
      background: #065f46;
      color: #6ee7b7;
      font-size: 0.75rem;
      font-weight: 700;
      padding: 0.15rem 0.45rem;
      border-radius: var(--radius-sm);
    }
    .event-time {
      font-size: 0.75rem;
      color: var(--text-muted);
      white-space: nowrap;
    }
    .event-body {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .event-operation {
      color: var(--text-primary);
      font-size: 0.85rem;
      word-break: break-all;
    }
    .event-message {
      color: var(--text-secondary);
      font-size: 0.85rem;
      line-height: 1.4;
      word-break: break-word;
    }
    .evidence-references {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
      margin-top: 0.25rem;
    }
    .evidence-tag {
      font-size: 0.75rem;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--border-subtle);
      color: var(--text-secondary);
      padding: 0.1rem 0.4rem;
      border-radius: var(--radius-sm);
    }
    .toggle-details-btn {
      background: none;
      border: none;
      color: var(--color-cyan);
      font-size: 0.75rem;
      cursor: pointer;
      padding: 0.25rem 0;
      text-align: left;
    }
    .toggle-details-btn:hover {
      text-decoration: underline;
    }
    .expanded-details {
      margin-top: 0.5rem;
      padding: 0.75rem;
      background: var(--bg-primary);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-sm);
    }
    .detail-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 0.5rem 1rem;
    }
    .detail-item {
      display: flex;
      flex-direction: column;
      gap: 0.1rem;
    }
    .detail-key {
      font-size: 0.7rem;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .detail-val {
      font-size: 0.8rem;
      color: var(--text-primary);
      word-break: break-all;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      border: 0;
    }
  `]
})
export class TimelineComponent implements OnChanges {
  @Input() events: IncidentTimelineEntryResponse[] = [];
  @Input() highlightedEventId?: string;

  filterText: string = '';
  selectedSeverity: string = 'ALL';
  selectedService: string = 'ALL';

  filteredEvents: IncidentTimelineEntryResponse[] = [];
  uniqueServices: string[] = [];
  expandedEventIds = new Set<string>();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['events']) {
      this.extractUniqueServices();
      this.applyFilters();
    }
    if (changes['highlightedEventId'] && this.highlightedEventId) {
      this.expandedEventIds.add(this.highlightedEventId);
    }
  }

  extractUniqueServices(): void {
    const services = new Set<string>();
    for (const e of this.events) {
      if (e.serviceName) {
        services.add(e.serviceName);
      }
    }
    this.uniqueServices = Array.from(services).sort();
  }

  applyFilters(): void {
    const query = (this.filterText || '').toLowerCase().trim();

    this.filteredEvents = this.events.filter(event => {
      // Severity filter
      if (this.selectedSeverity === 'ERROR' && event.severity !== 'ERROR' && event.severity !== 'FATAL') {
        return false;
      }
      if (this.selectedSeverity === 'WARN' && event.severity !== 'WARN') {
        return false;
      }
      if (this.selectedSeverity === 'INFO' && event.severity !== 'INFO') {
        return false;
      }

      // Service filter
      if (this.selectedService !== 'ALL' && event.serviceName !== this.selectedService) {
        return false;
      }

      // Query filter
      if (query) {
        const inMsg = (event.message || '').toLowerCase().includes(query);
        const inOp = (event.operation || '').toLowerCase().includes(query);
        const inId = (event.eventId || '').toLowerCase().includes(query);
        const inSvc = (event.serviceName || '').toLowerCase().includes(query);
        if (!inMsg && !inOp && !inId && !inSvc) {
          return false;
        }
      }

      return true;
    });
  }

  formatOffset(offsetMs: number): string {
    if (offsetMs === 0) return '+0ms';
    if (offsetMs < 1000) return `+${offsetMs}ms`;
    const sec = (offsetMs / 1000).toFixed(2);
    return `+${sec}s`;
  }

  formatTimestamp(isoString: string): string {
    try {
      const d = new Date(isoString);
      return d.toTimeString().split(' ')[0] + '.' + String(d.getMilliseconds()).padStart(3, '0') + ' UTC';
    } catch {
      return isoString;
    }
  }

  isExpanded(eventId: string): boolean {
    return this.expandedEventIds.has(eventId);
  }

  toggleExpand(eventId: string): void {
    if (this.expandedEventIds.has(eventId)) {
      this.expandedEventIds.delete(eventId);
    } else {
      this.expandedEventIds.add(eventId);
    }
  }

  trackBySeq(index: number, item: IncidentTimelineEntryResponse): number {
    return item.sequenceNumber;
  }
}

