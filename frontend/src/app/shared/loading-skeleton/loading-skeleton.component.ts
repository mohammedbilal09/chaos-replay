import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-loading-skeleton',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="skeleton-container" aria-live="polite" aria-busy="true">
      <div class="skeleton-header">
        <div class="skeleton-block title"></div>
        <div class="skeleton-block subtitle"></div>
      </div>
      <div class="skeleton-grid">
        <div class="skeleton-card" *ngFor="let item of [1, 2, 3, 4, 5, 6]"></div>
      </div>
      <div class="skeleton-timeline">
        <div class="skeleton-row" *ngFor="let item of [1, 2, 3, 4, 5]"></div>
      </div>
    </div>
  `,
  styles: [`
    .skeleton-container {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
      padding: 1rem 0;
    }
    .skeleton-block {
      background: linear-gradient(90deg, #1f2937 25%, #374151 50%, #1f2937 75%);
      background-size: 200% 100%;
      animation: pulse 1.5s infinite;
      border-radius: var(--radius-sm);
    }
    .skeleton-block.title {
      height: 24px;
      width: 40%;
      margin-bottom: 0.5rem;
    }
    .skeleton-block.subtitle {
      height: 16px;
      width: 25%;
    }
    .skeleton-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 1rem;
    }
    .skeleton-card {
      height: 90px;
      background: linear-gradient(90deg, #1f2937 25%, #374151 50%, #1f2937 75%);
      background-size: 200% 100%;
      animation: pulse 1.5s infinite;
      border-radius: var(--radius-md);
      border: 1px solid var(--border-subtle);
    }
    .skeleton-timeline {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .skeleton-row {
      height: 56px;
      background: linear-gradient(90deg, #1f2937 25%, #374151 50%, #1f2937 75%);
      background-size: 200% 100%;
      animation: pulse 1.5s infinite;
      border-radius: var(--radius-sm);
    }
    @keyframes pulse {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }
  `]
})
export class LoadingSkeletonComponent {}

