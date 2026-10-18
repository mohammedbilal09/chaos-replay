import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StatusBadgeComponent } from './status-badge.component';

describe('StatusBadgeComponent', () => {
  let component: StatusBadgeComponent;
  let fixture: ComponentFixture<StatusBadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusBadgeComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(StatusBadgeComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render correct class and icon for ERROR', () => {
    component.value = 'ERROR';
    fixture.detectChanges();
    expect(component.badgeClasses).toContain('badge-error');
    expect(component.icon).toBe('✖');
  });

  it('should render correct class and icon for PASSED', () => {
    component.value = 'PASSED';
    fixture.detectChanges();
    expect(component.badgeClasses).toContain('badge-success');
    expect(component.icon).toBe('✔');
  });

  it('should render correct class and icon for WARN', () => {
    component.value = 'WARN';
    fixture.detectChanges();
    expect(component.badgeClasses).toContain('badge-warn');
    expect(component.icon).toBe('▲');
  });

  it('should render fallback neutral badge for unknown value', () => {
    component.value = 'CUSTOM_VALUE';
    fixture.detectChanges();
    expect(component.badgeClasses).toContain('badge-neutral');
    expect(component.icon).toBe('●');
  });

  it('should display custom label when provided', () => {
    component.value = 'FIRST_FAILURE';
    component.label = 'Initial Failure Point';
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.badge-text')?.textContent).toBe('Initial Failure Point');
  });
});

