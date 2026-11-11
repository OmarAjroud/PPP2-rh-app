import { AfterViewInit, Directive, ElementRef, OnDestroy, effect, inject, input } from '@angular/core';
import { gsap } from 'gsap';

/** GSAP motion is skipped entirely when the user asks for reduced motion, or where matchMedia is missing (tests). */
export function mouvementAutorise(): boolean {
  return typeof window.matchMedia === 'function' && !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Page arrival: the direct children fade in (4px rise), 30ms apart, once (DESIGN.md, Motion).
 * Content is never left hidden: GSAP clears its inline styles when done.
 */
@Directive({ selector: '[rhApparition]' })
export class Apparition implements AfterViewInit, OnDestroy {
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private tween?: gsap.core.Tween;

  ngAfterViewInit(): void {
    if (!mouvementAutorise()) return;
    const enfants = Array.from(this.el.nativeElement.children);
    if (!enfants.length) return;
    this.tween = gsap.from(enfants, { y: 4, autoAlpha: 0, duration: 0.16, stagger: 0.03, ease: 'power2.out', clearProps: 'transform,opacity,visibility' });
  }

  ngOnDestroy(): void {
    this.tween?.kill();
  }
}

/** A number that counts to its value the first time it is shown, then follows changes (balances, totals). */
@Directive({ selector: '[rhCompteur]' })
export class Compteur implements OnDestroy {
  readonly rhCompteur = input.required<number>();
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly format = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 });
  private courant = 0;
  private tween?: gsap.core.Tween;

  constructor() {
    effect(() => {
      const cible = this.rhCompteur();
      this.tween?.kill();
      if (!mouvementAutorise()) { this.ecrire(cible); return; }
      const etat = { v: this.courant };
      this.tween = gsap.to(etat, {
        v: cible, duration: 0.6, ease: 'power2.out',
        onUpdate: () => this.ecrire(etat.v),
        onComplete: () => this.ecrire(cible),
      });
    });
  }

  private ecrire(v: number): void {
    this.courant = v;
    this.el.nativeElement.textContent = this.format.format(Math.round(v * 10) / 10);
  }

  ngOnDestroy(): void {
    this.tween?.kill();
  }
}
