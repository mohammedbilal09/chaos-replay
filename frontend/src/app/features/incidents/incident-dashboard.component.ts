import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, EMPTY } from 'rxjs';
import { switchMap, tap, catchError } from 'rxjs/operators';
import { ApiService } from '../../core/services/api.service';
import { IncidentInvestigationResponse } from '../../core/models/incident.model';
import { ApiErrorResponse } from '../../core/models/api-error.model';
import { HealthResponse } from '../../core/models/health.model';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import { LoadingSkeletonComponent } from '../../shared/loading-skeleton/loading-skeleton.component';
import { ErrorAlertComponent } from '../../shared/error-alert/error-alert.component';
import { TimelineComponent } from '../timeline/timeline.component';
import { EvidencePanelComponent } from '../evidence/evidence-panel.component';
import { ReplayVerificationComponent } from '../replay/replay-verification.component';

type ActiveTab = 'timeline' | 'evidence' | 'replay';

@Component({
  selector: 'app-incident-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    StatusBadgeComponent,
    LoadingSkeletonComponent,
    ErrorAlertComponent,
    TimelineComponent,
    EvidencePanelComponent,
    ReplayVerificationComponent
  ],
  template: `
    <div class="dashboard-layout">
      <!-- Top Navigation & Application Shell -->
      <header class="app-header">
        <div class="brand">
          <div class="logo-icon">⚡</div>
          <div class="brand-text">
            <h1 class="product-title">ChaosReplay</h1>
            <span class="product-tagline">Distributed Failure Replay & Incident Investigation</span>
          </div>
        </div>

        <div class="header-right">
          <!-- Backend Connectivity Status -->
          <div class="connectivity-pill" [class.up]="backendStatus === 'UP'" [class.down]="backendStatus === 'DOWN'">
            <span class="status-dot"></span>
            <span class="status-text">Backend: {{ backendStatus }}</span>
          </div>
        </div>
      </header>

      <!-- Main Container -->
      <main class="main-content">
        <!-- Search Bar Section -->
        <section class="search-section" aria-label="Trace Search">
          <form (ngSubmit)="onSearchSubmit()" class="search-form">
            <div class="input-wrapper">
              <label for="trace-id-input" class="sr-only">Trace Identifier</label>
              <span class="search-icon" aria-hidden="true">🔍</span>
              <input
                id="trace-id-input"
                type="text"
                name="traceId"
                [(ngModel)]="traceIdInput"
                placeholder="Enter distributed trace ID (e.g. 550e8400-e29b-41d4-a716...)"
                class="search-input code-mono"
                [disabled]="isLoading"
                autocomplete="off"
                spellcheck="false"
              />
              <button
                type="button"
                *ngIf="traceIdInput"
                (click)="traceIdInput = ''"
                class="clear-btn"
                title="Clear input"
              >
                ✕
              </button>
            </div>

            <button
              type="submit"
              class="submit-btn"
              [disabled]="isLoading || !traceIdInput.trim()"
            >
              {{ isLoading ? 'Investigating...' : 'Investigate Trace' }}
            </button>
          </form>

          <!-- Input Validation Message -->
          <div *ngIf="validationError" class="validation-msg" role="alert">
            ⚠️ {{ validationError }}
          </div>
        </section>

        <!-- Loading State -->
        <app-loading-skeleton *ngIf="isLoading"></app-loading-skeleton>

        <!-- Error State -->
        <app-error-alert
          *ngIf="currentError && !isLoading"
          [error]="currentError"
          [showRetry]="true"
          (retry)="onSearchSubmit()"
        ></app-error-alert>

        <!-- Empty Welcome State (Before any search) -->
        <section *ngIf="!investigation && !isLoading && !currentError" class="welcome-section">
          <div class="welcome-card">
            <span class="welcome-icon">🎯</span>
            <h2>Investigate Distributed System Incidents</h2>
            <p>
              Enter a known distributed trace ID to reconstruct its chronological telemetry timeline,
              inspect deterministic root-cause evidence, and review replay verification results.
            </p>
            <div class="features-list">
              <div class="feature-item">
                <span class="feat-icon">⏱️</span>
                <strong>Chronological Timeline:</strong> Trace-level sequencing with relative offsets.
              </div>
              <div class="feature-item">
                <span class="feat-icon">🔍</span>
                <strong>Evidence-Backed Root Cause:</strong> Ranked failure causes grounded in persisted telemetry.
              </div>
              <div class="feature-item">
                <span class="feat-icon">🔄</span>
                <strong>Deterministic Replay:</strong> Behavioral comparison between original and simulated runs.
              </div>
            </div>
          </div>
        </section>

        <!-- Loaded Investigation Dashboard -->
        <div *ngIf="investigation && !isLoading" class="investigation-view">
          <!-- Overview Banner -->
          <section class="overview-banner" aria-label="Incident Summary">
            <div class="banner-main">
              <div class="trace-title-row">
                <span class="trace-label">INVESTIGATION FOR TRACE:</span>
                <span class="trace-value code-mono">{{ investigation.traceId }}</span>
                <button
                  type="button"
                  class="copy-btn"
                  (click)="copyToClipboard(investigation.traceId)"
                  title="Copy Trace ID"
                >
                  📋
                </button>
              </div>

              <div class="summary-pills">
                <div class="pill-item">
                  <span class="pill-k">Status:</span>
                  <app-status-badge [value]="investigation.analysisStatus || 'UNKNOWN'" type="analysis"></app-status-badge>
                </div>

                <div class="pill-item" *ngIf="investigation.conclusion">
                  <span class="pill-k">Conclusion:</span>
                  <span class="conclusion-text code-mono">{{ investigation.conclusion }}</span>
                </div>

                <div class="pill-item" *ngIf="investigation.confidenceScore !== undefined">
                  <span class="pill-k">Confidence:</span>
                  <span class="confidence-val code-mono">{{ (investigation.confidenceScore * 100).toFixed(0) }}%</span>
                </div>

                <div class="pill-item">
                  <span class="pill-k">Replay Verification:</span>
                  <app-status-badge
                    [value]="investigation.verificationStatus || 'SKIPPED'"
                    type="verification"
                  ></app-status-badge>
                </div>
              </div>
            </div>

            <!-- Stats Grid -->
            <div class="stats-bar">
              <div class="stat-col">
                <span class="stat-k">Events</span>
                <span class="stat-v code-mono">{{ investigation.timeline.eventCount }}</span>
              </div>
              <div class="stat-col" [class.has-failures]="investigation.timeline.failureCount > 0">
                <span class="stat-k">Failures</span>
                <span class="stat-v code-mono">{{ investigation.timeline.failureCount }}</span>
              </div>
              <div class="stat-col">
                <span class="stat-k">Trace Duration</span>
                <span class="stat-v code-mono">{{ formatDuration(investigation.timeline.durationMs) }}</span>
              </div>
              <div class="stat-col" *ngIf="investigation.primaryServiceName">
                <span class="stat-k">Primary Service</span>
                <span class="stat-v service-txt code-mono">{{ investigation.primaryServiceName }}</span>
              </div>
            </div>
          </section>

          <!-- Tab Navigation -->
          <nav class="tabs-nav" aria-label="Investigation Sections">
            <button
              type="button"
              class="tab-btn"
              [class.active]="activeTab === 'timeline'"
              (click)="activeTab = 'timeline'"
              [attr.aria-selected]="activeTab === 'timeline'"
            >
              📅 Event Timeline ({{ investigation.timeline.timeline.length }})
            </button>

            <button
              type="button"
              class="tab-btn"
              [class.active]="activeTab === 'evidence'"
              (click)="activeTab = 'evidence'"
              [attr.aria-selected]="activeTab === 'evidence'"
            >
              🎯 Root Cause & Evidence ({{ investigation.candidates.length }})
            </button>

            <button
              type="button"
              class="tab-btn"
              [class.active]="activeTab === 'replay'"
              (click)="activeTab = 'replay'"
              [attr.aria-selected]="activeTab === 'replay'"
            >
              🔄 Replay Verification
            </button>
          </nav>

          <!-- Tab Content Views -->
          <div class="tab-content">
            <!-- Timeline Tab -->
            <div *ngIf="activeTab === 'timeline'">
              <app-timeline
                [events]="investigation.timeline.timeline"
                [highlightedEventId]="highlightedEventId"
              ></app-timeline>
            </div>

            <!-- Evidence Tab -->
            <div *ngIf="activeTab === 'evidence'">
              <app-evidence-panel
                [investigation]="investigation"
                (eventSelected)="onEventSelectedFromEvidence($event)"
              ></app-evidence-panel>
            </div>

            <!-- Replay Verification Tab -->
            <div *ngIf="activeTab === 'replay'">
              <app-replay-verification
                [investigation]="investigation"
              ></app-replay-verification>
            </div>
          </div>
        </div>
      </main>

      <!-- Footer -->
      <footer class="app-footer">
        <span>ChaosReplay v0.0.1 — Phase 11 Incident Investigation Console</span>
        <span class="footer-meta">Connected to Spring Boot REST API & PostgreSQL</span>
      </footer>
    </div>
  `,
  styles: [`
    .dashboard-layout {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      background: var(--bg-primary);
    }
    .app-header {
      background: var(--bg-surface);
      border-bottom: 1px solid var(--border-default);
      padding: 0.85rem 2rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .logo-icon {
      font-size: 1.5rem;
      background: rgba(56, 189, 248, 0.15);
      border: 1px solid rgba(56, 189, 248, 0.3);
      width: 36px;
      height: 36px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: var(--radius-md);
    }
    .brand-text {
      display: flex;
      flex-direction: column;
    }
    .product-title {
      font-size: 1.15rem;
      font-weight: 800;
      color: var(--text-primary);
      letter-spacing: -0.02em;
    }
    .product-tagline {
      font-size: 0.75rem;
      color: var(--text-muted);
    }
    .connectivity-pill {
      display: flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.25rem 0.65rem;
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-default);
      border-radius: 9999px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--text-muted);
    }
    .connectivity-pill.up .status-dot {
      background: var(--color-green);
      box-shadow: 0 0 6px var(--color-green);
    }
    .connectivity-pill.down .status-dot {
      background: var(--color-red);
      box-shadow: 0 0 6px var(--color-red);
    }
    .main-content {
      flex: 1 0 auto;
      max-width: 1280px;
      width: 100%;
      margin: 0 auto;
      padding: 1.5rem 2rem;
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }
    .search-section {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .search-form {
      display: flex;
      gap: 0.75rem;
    }
    .input-wrapper {
      position: relative;
      flex: 1 1 auto;
      display: flex;
      align-items: center;
    }
    .search-icon {
      position: absolute;
      left: 1rem;
      color: var(--text-muted);
      pointer-events: none;
    }
    .search-input {
      width: 100%;
      background: var(--bg-surface);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-md);
      padding: 0.75rem 2.25rem 0.75rem 2.5rem;
      color: var(--text-primary);
      font-size: 0.95rem;
      transition: border-color 0.15s, box-shadow 0.15s;
    }
    .search-input:focus {
      border-color: var(--border-focus);
      box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.25);
    }
    .clear-btn {
      position: absolute;
      right: 0.75rem;
      background: none;
      border: none;
      color: var(--text-muted);
      cursor: pointer;
      font-size: 0.85rem;
      padding: 0.25rem;
    }
    .clear-btn:hover {
      color: var(--text-primary);
    }
    .submit-btn {
      background: #0284c7;
      color: #fff;
      border: 1px solid #0369a1;
      padding: 0.75rem 1.5rem;
      border-radius: var(--radius-md);
      font-weight: 700;
      font-size: 0.9rem;
      cursor: pointer;
      white-space: nowrap;
      transition: background 0.15s;
    }
    .submit-btn:hover:not(:disabled) {
      background: #0369a1;
    }
    .submit-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .validation-msg {
      font-size: 0.8rem;
      color: var(--color-yellow);
      margin-left: 0.25rem;
    }
    .welcome-section {
      padding: 3rem 1rem;
      display: flex;
      justify-content: center;
    }
    .welcome-card {
      max-width: 650px;
      background: var(--bg-surface);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-lg);
      padding: 2.5rem;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 1rem;
    }
    .welcome-icon {
      font-size: 3rem;
      margin-bottom: 0.5rem;
    }
    .welcome-card h2 {
      font-size: 1.4rem;
      font-weight: 700;
      color: var(--text-primary);
    }
    .welcome-card p {
      font-size: 0.95rem;
      color: var(--text-secondary);
      line-height: 1.6;
    }
    .features-list {
      margin-top: 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      text-align: left;
      width: 100%;
    }
    .feature-item {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      background: var(--bg-primary);
      padding: 0.65rem 1rem;
      border-radius: var(--radius-sm);
      border: 1px solid var(--border-subtle);
      font-size: 0.85rem;
      color: var(--text-secondary);
    }
    .feature-item strong {
      color: var(--text-primary);
    }
    .investigation-view {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }
    .overview-banner {
      background: var(--bg-surface);
      border: 1px solid var(--border-default);
      border-radius: var(--radius-md);
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .trace-title-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 0.75rem;
      flex-wrap: wrap;
    }
    .trace-label {
      font-size: 0.75rem;
      font-weight: 700;
      color: var(--text-muted);
      letter-spacing: 0.05em;
    }
    .trace-value {
      font-size: 1.15rem;
      font-weight: 800;
      color: var(--color-cyan);
    }
    .copy-btn {
      background: none;
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-sm);
      padding: 0.2rem 0.4rem;
      cursor: pointer;
      font-size: 0.85rem;
    }
    .summary-pills {
      display: flex;
      align-items: center;
      gap: 1.25rem;
      flex-wrap: wrap;
    }
    .pill-item {
      display: flex;
      align-items: center;
      gap: 0.45rem;
      font-size: 0.8rem;
    }
    .pill-k {
      color: var(--text-muted);
    }
    .conclusion-text {
      background: rgba(192, 132, 252, 0.15);
      border: 1px solid rgba(192, 132, 252, 0.35);
      color: var(--color-purple);
      padding: 0.15rem 0.45rem;
      border-radius: var(--radius-sm);
      font-weight: 700;
    }
    .confidence-val {
      font-weight: 700;
      color: var(--color-green);
    }
    .stats-bar {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
      gap: 1rem;
      padding-top: 1rem;
      border-top: 1px solid var(--border-subtle);
    }
    .stat-col {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }
    .stat-k {
      font-size: 0.7rem;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .stat-v {
      font-size: 1.2rem;
      font-weight: 700;
      color: var(--text-primary);
    }
    .stat-col.has-failures .stat-v {
      color: var(--color-red);
    }
    .service-txt {
      color: #93c5fd;
      font-size: 1rem;
    }
    .tabs-nav {
      display: flex;
      gap: 0.5rem;
      border-bottom: 1px solid var(--border-default);
      padding-bottom: 0.25rem;
      overflow-x: auto;
    }
    .tab-btn {
      background: none;
      border: none;
      color: var(--text-secondary);
      font-size: 0.9rem;
      font-weight: 600;
      padding: 0.65rem 1.15rem;
      cursor: pointer;
      border-bottom: 2px solid transparent;
      transition: all 0.15s ease;
      white-space: nowrap;
    }
    .tab-btn:hover {
      color: var(--text-primary);
    }
    .tab-btn.active {
      color: var(--color-cyan);
      border-bottom-color: var(--color-cyan);
    }
    .app-footer {
      background: var(--bg-surface);
      border-top: 1px solid var(--border-subtle);
      padding: 1rem 2rem;
      display: flex;
      justify-content: space-between;
      font-size: 0.75rem;
      color: var(--text-muted);
      flex-wrap: wrap;
      gap: 0.5rem;
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
    @media (max-width: 768px) {
      .app-header {
        flex-direction: column;
        align-items: flex-start;
      }
      .search-form {
        flex-direction: column;
      }
      .submit-btn {
        width: 100%;
      }
    }
  `]
})
export class IncidentDashboardComponent implements OnInit, OnDestroy {
  traceIdInput: string = '';
  validationError: string = '';
  isLoading: boolean = false;
  backendStatus: 'UP' | 'DOWN' | 'CHECKING' = 'CHECKING';

  investigation?: IncidentInvestigationResponse;
  currentError?: ApiErrorResponse;

  activeTab: ActiveTab = 'timeline';
  highlightedEventId?: string;

  private searchSubject = new Subject<string>();
  private searchSubscription?: Subscription;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.checkBackendHealth();
    this.setupSearchStream();
  }

  ngOnDestroy(): void {
    this.searchSubscription?.unsubscribe();
  }

  checkBackendHealth(): void {
    this.api.getHealth().subscribe({
      next: res => {
        this.backendStatus = res.status === 'UP' ? 'UP' : 'DOWN';
      },
      error: () => {
        this.backendStatus = 'DOWN';
      }
    });
  }

  setupSearchStream(): void {
    // RxJS switchMap cancels inflight stale searches when a new search begins
    this.searchSubscription = this.searchSubject
      .pipe(
        tap(() => {
          this.isLoading = true;
          this.currentError = undefined;
        }),
        switchMap(traceId =>
          this.api.getIncidentInvestigation(traceId).pipe(
            catchError((err: ApiErrorResponse) => {
              this.currentError = err;
              this.investigation = undefined;
              this.isLoading = false;
              return EMPTY;
            })
          )
        )
      )
      .subscribe({
        next: data => {
          this.investigation = data;
          this.isLoading = false;
          this.activeTab = 'timeline';
          this.highlightedEventId = undefined;
        }
      });
  }

  onSearchSubmit(): void {
    const raw = this.traceIdInput || '';
    const cleanId = raw.trim();

    if (!cleanId) {
      this.validationError = 'Please enter a valid distributed trace ID.';
      return;
    }

    if (!/^[a-zA-Z0-9-_.:]{1,64}$/.test(cleanId)) {
      this.validationError = 'Trace ID must contain only alphanumeric characters, dashes, underscores, dots, or colons (max 64 chars).';
      return;
    }

    this.validationError = '';
    this.searchSubject.next(cleanId);
  }

  onEventSelectedFromEvidence(eventId: string): void {
    this.activeTab = 'timeline';
    this.highlightedEventId = eventId;

    // Smooth scroll to the timeline event row after DOM updates
    setTimeout(() => {
      const el = document.getElementById(`timeline-event-${eventId}`);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }, 50);
  }

  formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(2)}s`;
  }

  copyToClipboard(text: string): void {
    navigator.clipboard?.writeText(text);
  }
}

