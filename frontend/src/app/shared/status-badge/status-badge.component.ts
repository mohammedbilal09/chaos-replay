import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span [class]="badgeClasses" [attr.aria-label]="ariaLabel">
      <span class="badge-icon" aria-hidden="true">{{ icon }}</span>
      <span class="badge-text">{{ label || value }}</span>
    </span>
  `,
  styles: [`
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.2rem 0.55rem;
      font-size: 0.75rem;
      font-weight: 600;
      border-radius: var(--radius-sm);
      text-transform: uppercase;
      letter-spacing: 0.03em;
      line-height: 1.2;
      white-space: nowrap;
    }
    .badge-icon {
      font-size: 0.8rem;
    }
    .badge-info {
      background: rgba(96, 165, 250, 0.15);
      color: var(--color-blue);
      border: 1px solid rgba(96, 165, 250, 0.3);
    }
    .badge-warn {
      background: rgba(251, 191, 36, 0.15);
      color: var(--color-yellow);
      border: 1px solid rgba(251, 191, 36, 0.3);
    }
    .badge-error {
      background: rgba(248, 113, 113, 0.15);
      color: var(--color-red);
      border: 1px solid rgba(248, 113, 113, 0.3);
    }
    .badge-fatal {
      background: rgba(192, 132, 252, 0.15);
      color: var(--color-purple);
      border: 1px solid rgba(192, 132, 252, 0.3);
    }
    .badge-success {
      background: rgba(52, 211, 153, 0.15);
      color: var(--color-green);
      border: 1px solid rgba(52, 211, 153, 0.3);
    }
    .badge-neutral {
      background: rgba(156, 163, 175, 0.15);
      color: var(--text-secondary);
      border: 1px solid rgba(156, 163, 175, 0.3);
    }
  `]
})
export class StatusBadgeComponent {
  @Input() value: string = '';
  @Input() label?: string;
  @Input() type?: 'severity' | 'verification' | 'analysis' | 'conclusion' | 'category' | 'generic';

  get badgeClasses(): string {
    const val = (this.value || '').toUpperCase();

    if (val === 'ERROR' || val === 'FAILED' || val === 'ERROR_EVENT' || val === 'DATABASE_FAILURE') return 'badge badge-error';
    if (val === 'FATAL' || val === 'FATAL_EVENT') return 'badge badge-fatal';
    if (val === 'WARN' || val === 'PARTIAL' || val === 'INSUFFICIENT_EVIDENCE' || val === 'DOWNSTREAM_ERROR' || val === 'TIMEOUT_SIGNAL' || val === 'EXTERNAL_CALL_FAILURE') return 'badge badge-warn';
    if (val === 'PASSED' || val === 'UP' || val === 'COMPLETED' || val === 'ANALYZED' || val === 'ROOT_CAUSE_CANDIDATE' || val === 'PRIMARY_FAILURE') return 'badge badge-success';
    if (val === 'INFO' || val === 'CREATED' || val === 'START' || val === 'END' || val === 'NORMAL_EVENT') return 'badge badge-info';
    if (val === 'ROOT_CAUSE' || val === 'FIRST_FAILURE') return 'badge badge-error';
    if (val === 'FAILURE_PROPAGATION') return 'badge badge-warn';

    return 'badge badge-neutral';
  }

  get icon(): string {
    const val = (this.value || '').toUpperCase();
    if (val === 'ERROR' || val === 'FAILED' || val === 'ERROR_EVENT') return '✖';
    if (val === 'FATAL' || val === 'FATAL_EVENT') return '⚡';
    if (val === 'WARN' || val === 'PARTIAL' || val === 'INSUFFICIENT_EVIDENCE' || val === 'DOWNSTREAM_ERROR') return '▲';
    if (val === 'PASSED' || val === 'UP' || val === 'COMPLETED' || val === 'ANALYZED') return '✔';
    if (val === 'INFO' || val === 'NORMAL_EVENT') return 'ℹ';
    if (val === 'ROOT_CAUSE' || val === 'ROOT_CAUSE_CANDIDATE' || val === 'PRIMARY_FAILURE') return '🎯';
    if (val === 'FIRST_FAILURE' || val === 'DATABASE_FAILURE') return '🚨';
    if (val === 'FAILURE_PROPAGATION') return '↳';
    return '●';
  }

  get ariaLabel(): string {
    return `${this.type || 'status'}: ${this.label || this.value}`;
  }
}

