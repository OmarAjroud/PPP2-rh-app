import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { Notifications } from '../core/notifications/notifications';
import { ToastNotification } from './toast-notification';

const notif = { id: 9, type: 'CONGE_APPROUVE', titre: 'Demande approuvée', message: 'Votre congé du 14 déc. est approuvé.', lien: '/leave-requests/7', lue: false, dateCreation: '2026-11-08T10:00:00', dateLecture: null };

describe('Toast de notification', () => {
  let service: Notifications;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'leave-requests/:id', children: [] }]), provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(Notifications);
    TestBed.tick(); // the service starts (and resets) before any notification arrives
  });
  afterEach(() => vi.useRealTimers());

  function afficher(): HTMLElement {
    const f = TestBed.createComponent(ToastNotification);
    service.derniere.set(notif);
    f.detectChanges();
    return f.nativeElement as HTMLElement;
  }

  it('announces the new notification and closes by itself after 8 seconds', () => {
    const el = afficher();
    expect(el.querySelector('[role="status"]')?.textContent).toContain('Demande approuvée');
    vi.advanceTimersByTime(8000);
    expect(service.derniere()).toBeNull();
  });

  it('opens the link and marks the notification read', async () => {
    const el = afficher();
    service.nonLues.set(3);
    el.querySelector<HTMLButtonElement>('button.voir')!.click();
    TestBed.inject(HttpTestingController).expectOne(r => r.method === 'POST' && r.url === `${environment.apiUrl}/api/notifications/9/lecture`).flush({ ...notif, lue: true });
    await vi.runAllTimersAsync();
    expect(TestBed.inject(Router).url).toBe('/leave-requests/7');
    expect(service.nonLues()).toBe(2);
    expect(service.derniere()).toBeNull();
  });
});
