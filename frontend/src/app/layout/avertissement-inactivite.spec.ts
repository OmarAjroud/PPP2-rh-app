import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Inactivite } from '../core/auth/inactivite';
import { AvertissementInactivite } from './avertissement-inactivite';

describe('Inactivity warning', () => {
  const restant = signal<number | null>(null);
  const rester = vi.fn(() => restant.set(null));
  const deconnecter = vi.fn();

  beforeEach(() => {
    restant.set(null);
    rester.mockClear();
    deconnecter.mockClear();
    TestBed.configureTestingModule({ providers: [{ provide: Inactivite, useValue: { restant, rester, deconnecter } }] });
  });

  function rendre() {
    const f = TestBed.createComponent(AvertissementInactivite);
    document.body.appendChild(f.nativeElement);
    f.detectChanges();
    return f;
  }

  it('stays hidden while the person is active', () => {
    const f = rendre();
    expect(f.nativeElement.querySelector('[role="alertdialog"]')).toBeNull();
    f.nativeElement.remove();
  });

  it('shows the countdown and puts the keyboard on "Rester connecté"', async () => {
    const f = rendre();
    restant.set(60);
    f.detectChanges();
    await f.whenStable();
    const dialogue = f.nativeElement.querySelector('[role="alertdialog"]') as HTMLElement;
    expect(dialogue.getAttribute('aria-modal')).toBe('true');
    expect(dialogue.textContent).toContain('60 s');
    expect(document.activeElement?.textContent?.trim()).toBe('Rester connecté');
    restant.set(42);
    f.detectChanges();
    expect(dialogue.textContent).toContain('42 s');
    f.nativeElement.remove();
  });

  it('keeps the session or logs out on request', () => {
    const f = rendre();
    restant.set(30);
    f.detectChanges();
    const bouton = (t: string) => [...f.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent?.trim() === t) as HTMLButtonElement;
    bouton('Se déconnecter').click();
    expect(deconnecter).toHaveBeenCalled();
    bouton('Rester connecté').click();
    expect(rester).toHaveBeenCalled();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role="alertdialog"]')).toBeNull();
    f.nativeElement.remove();
  });

  it('gives the keyboard back to where it was when the warning closes', async () => {
    const avant = document.createElement('button');
    avant.textContent = 'Enregistrer';
    document.body.appendChild(avant);
    avant.focus();
    const f = rendre();
    restant.set(20);
    f.detectChanges();
    await f.whenStable();
    expect(document.activeElement?.textContent?.trim()).toBe('Rester connecté');
    restant.set(null); // activity in another tab, or "Rester connecté"
    f.detectChanges();
    await f.whenStable();
    expect(document.activeElement).toBe(avant);
    avant.remove();
    f.nativeElement.remove();
  });
});
