import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorAlertComponent } from './error-alert.component';
import { ApiErrorResponse } from '../../core/models/api-error.model';

describe('ErrorAlertComponent', () => {
  let component: ErrorAlertComponent;
  let fixture: ComponentFixture<ErrorAlertComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorAlertComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ErrorAlertComponent);
    component = fixture.componentInstance;
  });

  it('should display error status, code and message', () => {
    component.error = {
      status: 404,
      error: 'NOT_FOUND',
      errorCode: 'TRACE_NOT_FOUND',
      message: 'Trace trace-000 not found',
      correlationId: 'corr-xyz'
    };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.error-status')?.textContent).toContain('HTTP 404');
    expect(compiled.querySelector('.error-code')?.textContent).toContain('TRACE_NOT_FOUND');
    expect(compiled.querySelector('.error-message')?.textContent).toContain('Trace trace-000 not found');
    expect(compiled.querySelector('.meta-value')?.textContent).toContain('corr-xyz');
  });

  it('should display validation details if present', () => {
    component.error = {
      status: 400,
      error: 'BAD_REQUEST',
      errorCode: 'VALIDATION_FAILED',
      message: 'Validation failed',
      details: [
        { field: 'traceId', message: 'must match pattern' }
      ]
    };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const detail = compiled.querySelector('.error-details li');
    expect(detail?.textContent).toContain('traceId:');
    expect(detail?.textContent).toContain('must match pattern');
  });

  it('should emit retry event when retry button clicked', () => {
    component.error = {
      status: 503,
      error: 'SERVICE_UNAVAILABLE',
      message: 'Service unavailable'
    };
    component.showRetry = true;
    fixture.detectChanges();

    let emitted = false;
    component.retry.subscribe(() => emitted = true);

    const btn = fixture.nativeElement.querySelector('.retry-btn');
    btn.click();

    expect(emitted).toBeTrue();
  });
});

