import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { gsap } from 'gsap';
import { Apparition } from './motion';

@Component({ imports: [Apparition], template: `<div rhApparition><p>a</p><p>b</p></div>` })
class Hote {}

function mouvementReduit(reduit: boolean): void {
  window.matchMedia = ((q: string) => ({ matches: reduit && q.includes('prefers-reduced-motion'), media: q, addEventListener() {}, removeEventListener() {} })) as unknown as typeof window.matchMedia;
}

describe('Apparition', () => {
  const origine = window.matchMedia;
  afterEach(() => { window.matchMedia = origine; vi.restoreAllMocks(); });

  it('ne lance aucune animation sous prefers-reduced-motion', () => {
    mouvementReduit(true);
    const from = vi.spyOn(gsap, 'from');
    TestBed.createComponent(Hote).detectChanges();
    expect(from).not.toHaveBeenCalled();
  });

  it('fait un fondu court, une fois', () => {
    mouvementReduit(false);
    const from = vi.spyOn(gsap, 'from');
    TestBed.createComponent(Hote).detectChanges();
    expect(from).toHaveBeenCalledTimes(1);
    expect(from.mock.calls[0][1]).toMatchObject({ autoAlpha: 0, y: 4, duration: 0.16 });
  });
});
