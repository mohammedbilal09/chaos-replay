import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiErrorResponse } from '../../core/models/api-error.model';

@Component({
  selector: 'app-error-alert',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="error-container" role="alert" aria-live="assertive">
      <div class="error-header">
        <div class="error-badge">
          <span class="error-icon" aria-hidden="true">✕</span>
          <span class="error-status">{{ error.status > 0 ? 'HTTP ' + error.status : 'CONNECTION' }}</span>
        </div>
        <span class="error-code code-mono" *ngIf="error.errorCode">{{ error.errorCode }}</span>
      </div>

      <div class="error-body">
        <p class="error-message">{{ error.message }}</p>

        <ul class="error-details" *ngIf="error.details && error.details.length > 0">
          <li *ngFor="let d of error.details">
            <strong class="code-mono">{{ d.field }}:</strong> {{ d.message }}
          </li>
        </ul>

        <div class="error-meta" *ngIf="error.correlationId">
          <span class="meta-label">Correlation ID:</span>
          <code class="meta-value">{{ error.correlationId }}</code>
        </div>
      </div>

      <div class="error-footer" *ngIf="showRetry">
        <button class="retry-btn" (click)="retry.emit()" type="button">
          Retry Investigation
        </button>
      </div>
    </div>
  `,
  styles: [`
    .error-container {
      background: rgba(239, 68, 68, 0.08);
      border: 1px solid rgba(239, 68, 68, 0.3);
      border-radius: var(--radius-md);
      padding: 1.25rem;
      margin: 1.5rem 0;
      color: var(--text-primary);
    }
    .error-header {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 0.75rem;
    }
    .error-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      background: rgba(239, 68, 68, 0.2);
      color: var(--color-red);
      font-weight: 700;
      font-size: 0.8rem;
      padding: 0.2rem 0.5rem;
      border-radius: var(--radius-sm);
    }
    .error-code {
      font-size: 0.85rem;
      color: var(--text-secondary);
      background: var(--bg-surface);
      padding: 0.15rem 0.45rem;
      border-radius: var(--radius-sm);
      border: 1px solid var(--border-subtle);
    }
    .error-message {
      font-size: 0.95rem;
      line-height: 1.5;
      color: #fca5a5;
    }
    .error-details {
      margin: 0.75rem 0 0 1.25rem;
      font-size: 0.85rem;
      color: var(--text-secondary);
    }
    .error-meta {
      margin-top: 0.75rem;
      padding-top: 0.75rem;
      border-top: 1px solid rgba(239, 68, 68, 0.15);
      font-size: 0.8rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .meta-label {
      color: var(--text-muted);
    }
    .meta-value {
      color: var(--text-secondary);
      background: var(--bg-surface);
      padding: 0.15rem 0.4rem;
      border-radius: var(--radius-sm);
      font-family: var(--font-mono);
    }
    .error-footer {
      margin-top: 1rem;
    }
    .retry-btn {
      background: rgba(239, 68, 68, 0.2);
      border: 1px solid rgba(239, 68, 68, 0.4);
      color: #fca5a5;
      padding: 0.4rem 0.85rem;
      font-size: 0.85rem;
      font-weight: 600;
      border-radius: var(--radius-sm);
      cursor: pointer;
      transition: background 0.15s ease;
    }
    .retry-btn:hover {
      background: rgba(239, 68, 68, 0.3);
    }
  `]
})
export class ErrorAlertComponent {
  @Input({ required: true }) error!: ApiErrorResponse;
  @Input() showRetry: boolean = false;
  @Output() retry = new EventEmitter<void>();
}

