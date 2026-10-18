import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IncidentInvestigationResponse } from '../../core/models/incident.model';
import { ReplayVerificationResponse } from '../../core/models/replay.model';
import { ApiService } from '../../core/services/api.service';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';

@Component({
  selector: 'app-replay-verification',
  standalone: true,
  imports: [CommonModule, StatusBadgeComponent],
  template: `
    <section class="replay-container" aria-label="Replay Verification & Behavioral Comparison">
      <!-- Scenario & Execution Status Overview -->
      <div class="overview-card">
        <div class="overview-header">
          <div class="status-box">
            <span class="label">Replay Execution:</span>
            <span class="value-pill code-mono">{{ investigation.executionStatus || 'NOT_EXECUTED' }}</span>
          </div>

          <div class="status-box">
            <span class="label">Verification Status:</span>
            <app-status-badge
              [value]="investigation.verificationStatus || 'SKIPPED'"
              type="verification"
            ></app-status-badge>
          </div>

          <div class="status-box" *ngIf="investigation.scenarioId">
            <span class="label">Scenario ID:</span>
            <span class="scenario-id code-mono">{{ investigation.scenarioId }}</span>
          </div>
        </div>

        <div class="summary-message" *ngIf="investigation.verificationSummary">
          <p>{{ investigation.verificationSummary }}</p>
        </div>
      </div>

      <!-- When verification details are loaded -->
      <div class="verification-details" *ngIf="verificationDetails">
        <!-- Verification Metrics Grid -->
        <div class="metrics-grid">
          <div class="metric-card matched">
            <span class="metric-label">Events Matched</span>
            <span class="metric-val code-mono">{{ verificationDetails.eventsMatched }} / {{ verificationDetails.originalEventCount }}</span>
          </div>

          <div class="metric-card" [class.danger]="verificationDetails.eventsMissing > 0">
            <span class="metric-label">Events Missing</span>
            <span class="metric-val code-mono">{{ verificationDetails.eventsMissing }}</span>
          </div>

          <div class="metric-card" [class.warning]="verificationDetails.eventsUnexpected > 0">
            <span class="metric-label">Events Unexpected</span>
            <span class="metric-val code-mono">{{ verificationDetails.eventsUnexpected }}</span>
          </div>

          <div class="metric-card" [class.warning]="verificationDetails.severityMismatches > 0">
            <span class="metric-label">Severity Mismatches</span>
            <span class="metric-val code-mono">{{ verificationDetails.severityMismatches }}</span>
          </div>

          <div class="metric-card" [class.danger]="verificationDetails.serviceMismatches > 0">
            <span class="metric-label">Service Mismatches</span>
            <span class="metric-val code-mono">{{ verificationDetails.serviceMismatches }}</span>
          </div>

          <div class="metric-card duration">
            <span class="metric-label">Replay Duration Drift</span>
            <span class="metric-val code-mono">
              {{ formatDuration(verificationDetails.originalDurationMs) }} → {{ formatDuration(verificationDetails.replayedDurationMs) }}
            </span>
          </div>
        </div>

        <!-- Behavioral Differences Table -->
        <div class="differences-section">
          <h3 class="section-title">
            Behavioral Differences & Discrepancies ({{ verificationDetails.differences.length }})
          </h3>

          <div *ngIf="verificationDetails.differences.length === 0" class="no-differences">
            <span class="check-icon">✔</span>
            <p>Perfect fidelity reproduction: no behavioral differences or sequence drifts were detected.</p>
          </div>

          <div class="table-wrapper" *ngIf="verificationDetails.differences.length > 0">
            <table class="differences-table">
              <thead>
                <tr>
                  <th scope="col">Seq</th>
                  <th scope="col">Difference Type</th>
                  <th scope="col">Expected (Original)</th>
                  <th scope="col">Observed (Replay)</th>
                  <th scope="col">Diagnostic Message</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let diff of verificationDetails.differences">
                  <td class="code-mono">#{{ diff.sequenceNumber }}</td>
                  <td>
                    <span class="diff-badge code-mono">{{ diff.differenceType }}</span>
                  </td>
                  <td class="code-mono">
                    <div *ngIf="diff.expectedEventId"><strong>id:</strong> {{ diff.expectedEventId }}</div>
                    <div *ngIf="diff.expectedServiceName"><strong>svc:</strong> {{ diff.expectedServiceName }}</div>
                    <div *ngIf="diff.expectedSeverity"><strong>sev:</strong> {{ diff.expectedSeverity }}</div>
                  </td>
                  <td class="code-mono">
                    <div *ngIf="diff.actualEventId"><strong>id:</strong> {{ diff.actualEventId }}</div>
                    <div *ngIf="diff.actualServiceName"><strong>svc:</strong> {{ diff.actualServiceName }}</div>
                    <div *ngIf="diff.actualSeverity"><strong>sev:</strong> {{ diff.actualSeverity }}</div>
                  </td>
                  <td class="diff-msg">{{ diff.message }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- When verification is not available -->
      <div class="no-verification-card" *ngIf="!investigation.verificationAvailable && !verificationDetails">
        <div class="info-icon">ℹ</div>
        <div class="info-content">
          <h4>Replay Verification Not Run For This Trace</h4>
          <p>
            A replay scenario {{ investigation.scenarioAvailable ? 'has been generated' : 'has not yet been generated' }} for this trace.
            Deterministic failure replay verification executes in the ChaosReplay simulation engine
            via the verification endpoint.
          </p>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .replay-container {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }
    .overview-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-md);
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .overview-header {
      display: flex;
      align-items: center;
      gap: 1.5rem;
      flex-wrap: wrap;
    }
    .status-box {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .label {
      font-size: 0.8rem;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .value-pill {
      background: rgba(255, 255, 255, 0.08);
      padding: 0.2rem 0.5rem;
      border-radius: var(--radius-sm);
      font-size: 0.8rem;
    }
    .scenario-id {
      color: var(--color-cyan);
      font-size: 0.85rem;
    }
    .summary-message {
      background: rgba(0, 0, 0, 0.2);
      border-radius: var(--radius-sm);
      padding: 0.75rem 1rem;
      font-size: 0.9rem;
      color: var(--text-secondary);
      border-left: 3px solid var(--color-cyan);
    }
    .verification-details {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 1rem;
    }
    .metric-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-md);
      padding: 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .metric-card.matched {
      border-color: rgba(52, 211, 153, 0.35);
    }
    .metric-card.danger {
      border-color: rgba(248, 113, 113, 0.5);
      background: rgba(248, 113, 113, 0.05);
    }
    .metric-card.warning {
      border-color: rgba(251, 191, 36, 0.5);
      background: rgba(251, 191, 36, 0.05);
    }
    .metric-label {
      font-size: 0.75rem;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .metric-val {
      font-size: 1.25rem;
      font-weight: 700;
      color: var(--text-primary);
    }
    .differences-section {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .section-title {
      font-size: 1.1rem;
      font-weight: 700;
      color: var(--text-primary);
    }
    .no-differences {
      padding: 2.5rem;
      text-align: center;
      background: rgba(52, 211, 153, 0.05);
      border: 1px solid rgba(52, 211, 153, 0.3);
      border-radius: var(--radius-md);
      color: var(--color-green);
    }
    .check-icon {
      font-size: 2rem;
      display: block;
      margin-bottom: 0.5rem;
    }
    .table-wrapper {
      overflow-x: auto;
      border: 1px solid var(--border-default);
      border-radius: var(--radius-md);
    }
    .differences-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.85rem;
    }
    .differences-table th, .differences-table td {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid var(--border-subtle);
    }
    .differences-table th {
      background: var(--bg-surface);
      color: var(--text-muted);
      text-transform: uppercase;
      font-size: 0.75rem;
      letter-spacing: 0.04em;
    }
    .differences-table tbody tr:hover {
      background: #141d2e;
    }
    .diff-badge {
      background: rgba(248, 113, 113, 0.15);
      color: var(--color-red);
      padding: 0.15rem 0.45rem;
      border-radius: var(--radius-sm);
      font-size: 0.75rem;
    }
    .diff-msg {
      color: var(--text-secondary);
    }
    .no-verification-card {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
      padding: 1.5rem;
      background: var(--bg-surface);
      border: 1px dashed var(--border-default);
      border-radius: var(--radius-md);
    }
    .info-icon {
      font-size: 1.5rem;
      color: var(--color-blue);
    }
    .info-content h4 {
      font-size: 0.95rem;
      margin-bottom: 0.35rem;
      color: var(--text-primary);
    }
    .info-content p {
      font-size: 0.85rem;
      color: var(--text-secondary);
      line-height: 1.5;
    }
  `]
})
export class ReplayVerificationComponent implements OnInit, OnChanges {
  @Input({ required: true }) investigation!: IncidentInvestigationResponse;

  verificationDetails?: ReplayVerificationResponse;
  isLoadingVerification: boolean = false;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadVerificationIfNeeded();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['investigation']) {
      this.loadVerificationIfNeeded();
    }
  }

  loadVerificationIfNeeded(): void {
    if (this.investigation.verificationAvailable && this.investigation.verificationId) {
      this.isLoadingVerification = true;
      this.api.getReplayVerification(this.investigation.verificationId).subscribe({
        next: data => {
          this.verificationDetails = data;
          this.isLoadingVerification = false;
        },
        error: () => {
          this.isLoadingVerification = false;
        }
      });
    } else {
      this.verificationDetails = undefined;
    }
  }

  formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(2)}s`;
  }
}

