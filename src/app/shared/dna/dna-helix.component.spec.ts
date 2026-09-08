import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ATLAS_DNA } from '../../core/mock/atlas/dna.data';
import { DnaHelixComponent } from './dna-helix.component';

describe('DnaHelixComponent', () => {
  let fixture: ComponentFixture<DnaHelixComponent>;
  let element: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DnaHelixComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DnaHelixComponent);
    fixture.componentRef.setInput('dimensions', ATLAS_DNA.dimensions);
    fixture.detectChanges();
    element = fixture.nativeElement;
  });

  it('renders exactly one band per dimension', () => {
    expect(element.querySelectorAll('.band').length).toBe(
      ATLAS_DNA.dimensions.length,
    );
  });

  it('gives every band an accessible name carrying its score', () => {
    const hits = Array.from(element.querySelectorAll('.hit'));
    expect(hits.length).toBe(ATLAS_DNA.dimensions.length);

    const testing = hits.find((hit) =>
      hit.getAttribute('aria-label')?.startsWith('Testing'),
    );
    expect(testing?.getAttribute('aria-label')).toBe(
      'Testing, score 67 out of 100',
    );
  });

  it('makes every band keyboard reachable', () => {
    for (const hit of Array.from(element.querySelectorAll('.hit'))) {
      expect(hit.getAttribute('tabindex')).toBe('0');
      expect(hit.getAttribute('role')).toBe('button');
    }
  });

  it('colours a weak dimension differently from a strong one', () => {
    const bands = fixture.componentInstance.bands();
    const testing = bands.find((band) => band.key === 'testing');
    const dependencies = bands.find((band) => band.key === 'dependencies');

    expect(testing?.color).toBeDefined();
    expect(testing?.color).not.toBe(dependencies?.color);
  });

  it('lays bands out in order down the helix without gaps or overlap', () => {
    const bands = fixture.componentInstance.bands();
    for (let i = 1; i < bands.length; i++) {
      expect(bands[i].hitY).toBeCloseTo(
        bands[i - 1].hitY + bands[i - 1].hitHeight,
        5,
      );
    }
  });

  it('alternates label sides so consecutive labels never collide', () => {
    const bands = fixture.componentInstance.bands();
    for (let i = 1; i < bands.length; i++) {
      expect(bands[i].labelAnchor).not.toBe(bands[i - 1].labelAnchor);
    }
  });

  it('emits the dimension key when a band is activated', () => {
    const emitted: string[] = [];
    fixture.componentInstance.selected.subscribe((key) => emitted.push(key));

    const hit = element.querySelectorAll('.hit')[4] as SVGElement;
    hit.dispatchEvent(new MouseEvent('click'));

    expect(emitted).toEqual(['testing']);
  });

  it('mutes the other bands once one is active', () => {
    fixture.componentRef.instance.activeKey.set('testing');
    fixture.detectChanges();

    expect(element.querySelectorAll('.band.is-active').length).toBe(1);
    expect(element.querySelectorAll('.band.is-muted').length).toBe(
      ATLAS_DNA.dimensions.length - 1,
    );
  });

  it('renders nothing but stays stable when given no dimensions', () => {
    fixture.componentRef.setInput('dimensions', []);
    fixture.detectChanges();

    expect(element.querySelectorAll('.band').length).toBe(0);
    expect(element.querySelector('svg')).not.toBeNull();
  });
});
