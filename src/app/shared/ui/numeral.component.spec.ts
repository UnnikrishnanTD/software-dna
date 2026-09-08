import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NumeralComponent } from './numeral.component';

describe('NumeralComponent', () => {
  let fixture: ComponentFixture<NumeralComponent>;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NumeralComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(NumeralComponent);
    element = fixture.nativeElement;
  });

  /**
   * The regression this guards: the readout previously drove its text
   * through a signal written from a requestAnimationFrame callback, which
   * did not reliably reach the DOM. It rendered "0" while reporting the
   * right value to assistive technology.
   */
  it('renders its value immediately when animation is skipped', () => {
    fixture.componentRef.setInput('value', 87);
    fixture.componentRef.setInput('durationMs', 0);
    fixture.detectChanges();

    expect(element.textContent).toBe('87');
  });

  it('keeps the accessible label in step with the rendered text', () => {
    fixture.componentRef.setInput('value', 52);
    fixture.componentRef.setInput('durationMs', 0);
    fixture.componentRef.setInput('label', 'Engineering health');
    fixture.detectChanges();

    expect(element.getAttribute('aria-label')).toBe('Engineering health: 52');
  });

  it('falls back to the bare figure when no label is given', () => {
    fixture.componentRef.setInput('value', 9);
    fixture.componentRef.setInput('durationMs', 0);
    fixture.detectChanges();

    expect(element.getAttribute('aria-label')).toBe('9');
  });

  it('applies the suffix to both the text and the label', () => {
    fixture.componentRef.setInput('value', 71);
    fixture.componentRef.setInput('durationMs', 0);
    fixture.componentRef.setInput('suffix', '%');
    fixture.detectChanges();

    expect(element.textContent).toBe('71%');
    expect(element.getAttribute('aria-label')).toBe('71%');
  });

  it('honours the requested decimal places', () => {
    fixture.componentRef.setInput('value', 3.14159);
    fixture.componentRef.setInput('durationMs', 0);
    fixture.componentRef.setInput('decimals', 2);
    fixture.detectChanges();

    expect(element.textContent).toBe('3.14');
  });

  it('re-renders when the value changes', () => {
    fixture.componentRef.setInput('value', 10);
    fixture.componentRef.setInput('durationMs', 0);
    fixture.detectChanges();
    expect(element.textContent).toBe('10');

    fixture.componentRef.setInput('value', 42);
    fixture.detectChanges();
    expect(element.textContent).toBe('42');
  });

  it('animates towards the target and lands exactly on it', (done) => {
    fixture.componentRef.setInput('value', 100);
    fixture.componentRef.setInput('durationMs', 60);
    fixture.detectChanges();

    setTimeout(() => {
      expect(element.textContent).toBe('100');
      done();
    }, 220);
  });
});
