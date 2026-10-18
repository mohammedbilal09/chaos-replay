import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FailureAnalysisCandidateResponse,
  FailureAnalysisConclusion,
  FailureAnalysisStatus,
  IncidentInvestigationResponse
} from '../../core/models/incident.model';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';

@Component({
  selector: 'app-evidence-panel',
  standalone: true,
  imports: [CommonModule, StatusBadgeComponent],
  template: `
    <section class="evidence-panel-container" aria-label="Root-Cause Analysis & Evidence">
      <!-- Hypothesis & Conclusion Summary Card -->
      <div class="summary-card">
        <div class="summary-top">
          <div class="status-group">
            <span class="label">Analysis Status:</span>
            <app-status-badge [value]="investigation.analysisStatus || 'UNKNOWN'" type="analysis"></app-status-badge>
          </div>

          <div class="status-group" *ngIf="investigation.conclusion">
            <span class="label">Conclusion:</span>
            <span class="conclusion-pill code-mono">{{ investigation.conclusion }}</span>
          </div>

          <div class="confidence-group" *ngIf="investigation.confidenceScore !== undefined && investigation.confidenceScore !== null">
            <span class="label">Confidence Score:</span>
            <div class="confidence-bar-wrapper" [title]="(investigation.confidenceScore * 100).toFixed(0) + '% Confidence'">
              <div class="confidence-bar" [style.width.%]="investigation.confidenceScore * 100"></div>
            </div>
            <span class="confidence-val code-mono">{{ (investigation.confidenceScore * 100).toFixed(0) }}%</span>
          </div>
        </div>

        <div class="primary-focus" *ngIf="investigation.primaryServiceName">
          <div class="focus-item">
            <span class="focus-label">Primary Root-Cause Service:</span>
            <span class="focus-val code-mono">{{ investigation.primaryServiceName }}</span>
          </div>
          <div class="focus-item" *ngIf="investigation.primaryEventId">
            <span class="focus-label">Originating Event ID:</span>
            <button
              type="button"
              class="event-link-btn code-mono"
              (click)="onSelectEvent(investigation.primaryEventId!)"
            >
              📍 {{ investigation.primaryEventId }}
            </button>
          </div>
        </div>

        <!-- Evidence Summary Bullets -->
        <div class="evidence-summary" *ngIf="investigation.evidenceSummary && investigation.evidenceSummary.length > 0">
          <h4 class="summary-title">Telemetry Evidence Synthesis</h4>
          <ul class="summary-list">
            <li *ngFor="let item of investigation.evidenceSummary">
              {{ item }}
            </li>
          </ul>
        </div>
      </div>

      <!-- Ranked Failure Candidates -->
      <div class="candidates-section">
        <h3 class="section-title">
          Ranked Root-Cause Candidates ({{ investigation.candidates.length }})
        </h3>

        <div *ngIf="investigation.candidates.length === 0" class="no-candidates">
          <p>No explicit failure candidates were identified from the persisted telemetry.</p>
        </div>

        <div class="candidates-grid" *ngIf="investigation.candidates.length > 0">
          <article
            *ngFor="let candidate of investigation.candidates"
            class="candidate-card"
            [class.primary-candidate]="candidate.rank === 1"
          >
            <div class="candidate-header">
              <div class="rank-badge">
                <span class="rank-number">#{{ candidate.rank }}</span>
                <span class="rank-label">Rank</span>
              </div>

              <div class="candidate-meta">
                <div class="meta-line">
                  <span class="service-pill code-mono">{{ candidate.serviceName }}</span>
                  <app-status-badge [value]="candidate.severity" type="severity"></app-status-badge>
                  <span class="type-pill code-mono">{{ candidate.eventType }}</span>
                </div>
                <div class="candidate-type code-mono">{{ candidate.candidateType }}</div>
              </div>

              <div class="candidate-score code-mono">
                {{ (candidate.confidenceScore * 100).toFixed(0) }}%
              </div>
            </div>

            <p class="candidate-desc">{{ candidate.description }}</p>

            <!-- Supporting Evidence List -->
            <div class="candidate-evidence" *ngIf="candidate.evidence && candidate.evidence.length > 0">
              <h5 class="evidence-title">Supporting Telemetry Evidence:</h5>
              <div class="evidence-items">
                <div *ngFor="let ev of candidate.evidence" class="evidence-row">
                  <span class="evidence-type-badge code-mono">{{ ev.evidenceType }}</span>
                  <span class="evidence-val">{{ ev.evidenceValue }}</span>
                  <button
                    *ngIf="ev.eventId"
                    type="button"
                    class="event-jump-btn code-mono"
                    (click)="onSelectEvent(ev.eventId)"
                    title="Jump to this event in the timeline"
                  >
                    Event {{ ev.eventId }} ↗
                  </button>
                </div>
              </div>
            </div>
          </article>
        </div>
      </div>

      <!-- Explicit Investigation Limitations -->
      <div class="limitations-section" *ngIf="investigation.limitations && investigation.limitations.length > 0">
        <h4 class="limitations-title">
          ⚠️ Known Investigation Limitations & Inconclusive Points
        </h4>
        <p class="limitations-subtitle">
          The following facts are missing or uncertain based strictly on the available evidence:
        </p>
        <ul class="limitations-list">
          <li *ngFor="let lim of investigation.limitations">
            {{ lim }}
          </li>
        </ul>
      </div>
    </section>
  `,
  styles: [`
    .evidence-panel-container {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }
    .summary-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-md);
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .summary-top {
      display: flex;
      align-items: center;
      gap: 1.5rem;
      flex-wrap: wrap;
    }
    .status-group, .confidence-group {
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
    .conclusion-pill {
      background: rgba(192, 132, 252, 0.15);
      border: 1px solid rgba(192, 132, 252, 0.35);
      color: var(--color-purple);
      padding: 0.2rem 0.55rem;
      border-radius: var(--radius-sm);
      font-size: 0.8rem;
      font-weight: 700;
    }
    .confidence-bar-wrapper {
      width: 100px;
      height: 8px;
      background: var(--bg-primary);
      border-radius: 4px;
      overflow: hidden;
      border: 1px solid var(--border-subtle);
    }
    .confidence-bar {
      height: 100%;
      background: linear-gradient(90deg, #38bdf8, #34d399);
      border-radius: 4px;
    }
    .confidence-val {
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--color-green);
    }
    .primary-focus {
      display: flex;
      gap: 1.5rem;
      padding: 0.75rem 1rem;
      background: rgba(56, 189, 248, 0.05);
      border: 1px solid rgba(56, 189, 248, 0.2);
      border-radius: var(--radius-sm);
      flex-wrap: wrap;
    }
    .focus-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .focus-label {
      font-size: 0.8rem;
      color: var(--text-secondary);
    }
    .focus-val {
      font-weight: 700;
      color: var(--color-cyan);
    }
    .event-link-btn {
      background: rgba(56, 189, 248, 0.15);
      border: 1px solid rgba(56, 189, 248, 0.4);
      color: var(--color-cyan);
      padding: 0.15rem 0.5rem;
      border-radius: var(--radius-sm);
      cursor: pointer;
      font-size: 0.8rem;
      transition: background 0.15s;
    }
    .event-link-btn:hover {
      background: rgba(56, 189, 248, 0.25);
      text-decoration: underline;
    }
    .evidence-summary {
      border-top: 1px solid var(--border-subtle);
      padding-top: 0.75rem;
    }
    .summary-title {
      font-size: 0.85rem;
      color: var(--text-secondary);
      margin-bottom: 0.5rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .summary-list {
      margin-left: 1.25rem;
      font-size: 0.85rem;
      color: var(--text-primary);
      line-height: 1.6;
    }
    .candidates-section {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .section-title {
      font-size: 1.1rem;
      font-weight: 700;
      color: var(--text-primary);
    }
    .no-candidates {
      padding: 2rem;
      text-align: center;
      background: var(--bg-surface);
      border: 1px dashed var(--border-default);
      border-radius: var(--radius-md);
      color: var(--text-muted);
    }
    .candidates-grid {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .candidate-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-md);
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .candidate-card.primary-candidate {
      border-color: var(--color-purple);
      box-shadow: 0 0 0 1px rgba(192, 132, 252, 0.3);
    }
    .candidate-header {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .rank-badge {
      display: flex;
      flex-direction: column;
      align-items: center;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-sm);
      padding: 0.3rem 0.6rem;
      min-width: 48px;
    }
    .rank-number {
      font-size: 1rem;
      font-weight: 800;
      color: var(--color-purple);
    }
    .rank-label {
      font-size: 0.65rem;
      color: var(--text-muted);
      text-transform: uppercase;
    }
    .candidate-meta {
      flex: 1 1 auto;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .meta-line {
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
    .candidate-type {
      font-size: 0.75rem;
      color: var(--text-muted);
    }
    .candidate-score {
      font-size: 1.1rem;
      font-weight: 800;
      color: var(--color-green);
    }
    .candidate-desc {
      font-size: 0.9rem;
      color: var(--text-primary);
      line-height: 1.45;
    }
    .candidate-evidence {
      border-top: 1px solid var(--border-subtle);
      padding-top: 0.75rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .evidence-title {
      font-size: 0.75rem;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .evidence-items {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .evidence-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.8rem;
      background: var(--bg-primary);
      padding: 0.35rem 0.6rem;
      border-radius: var(--radius-sm);
      border: 1px solid var(--border-subtle);
      flex-wrap: wrap;
    }
    .evidence-type-badge {
      background: rgba(255, 255, 255, 0.08);
      padding: 0.1rem 0.35rem;
      border-radius: var(--radius-sm);
      font-size: 0.7rem;
      color: var(--text-secondary);
    }
    .evidence-val {
      flex: 1 1 auto;
      color: var(--text-primary);
    }
    .event-jump-btn {
      background: rgba(56, 189, 248, 0.15);
      border: 1px solid rgba(56, 189, 248, 0.35);
      color: var(--color-cyan);
      padding: 0.15rem 0.45rem;
      border-radius: var(--radius-sm);
      font-size: 0.75rem;
      cursor: pointer;
    }
    .event-jump-btn:hover {
      background: rgba(56, 189, 248, 0.25);
    }
    .limitations-section {
      background: rgba(251, 191, 36, 0.05);
      border: 1px solid rgba(251, 191, 36, 0.25);
      border-radius: var(--radius-md);
      padding: 1.25rem;
    }
    .limitations-title {
      font-size: 0.95rem;
      color: var(--color-yellow);
      margin-bottom: 0.25rem;
    }
    .limitations-subtitle {
      font-size: 0.8rem;
      color: var(--text-muted);
      margin-bottom: 0.75rem;
    }
    .limitations-list {
      margin-left: 1.25rem;
      font-size: 0.85rem;
      color: var(--text-secondary);
      line-height: 1.5;
    }
  `]
})
export class EvidencePanelComponent {
  @Input({ required: true }) investigation!: IncidentInvestigationResponse;
  @Output() eventSelected = new EventEmitter<string>();

  onSelectEvent(eventId: string): void {
    if (eventId) {
      this.eventSelected.emit(eventId);
    }
  }
}

