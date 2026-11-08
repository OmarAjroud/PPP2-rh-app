import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { Notification } from '../core/api/models';
import { Notifications } from '../core/notifications/notifications';
import { NotificationsPage } from './notifications-page';

const api = `${environment.apiUrl}/api/notifications`;
const n = (o: Partial<Notification> = {}): Notification => ({
  id: 9, type: 'CONGE_APPROUVE', titre: 'Demande approuvée', message: 'Votre congé du 14 déc. est approuvé.',
  lien: '/leave-requests/7', lue: false, dateCreation: '2026-11-08T10:00:00', dateLecture: null, ...o,
});
const page = (contenu: Notification[]) => ({ contenu, numeroPage: 0, taillePage: 10, totalElements: contenu.length, totalPages: 1, dernierePage: true });

describe('Page des notifications', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'notifications', component: NotificationsPage }, { path: 'leave-requests/:id', children: [] }]),
        provideHttpClient(), provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(Notifications);
    TestBed.tick();
    harness = await RouterTestingHarness.create();
  });

  const el = () => harness.routeNativeElement!;
  const liste = (nonLues: boolean) => http.expectOne(r => r.url === api && r.params.get('nonLues') === String(nonLues));

  it('lists my notifications, unread ones marked as such', async () => {
    await harness.navigateByUrl('/notifications');
    liste(false).flush(page([n(), n({ id: 8, titre: 'Demande refusée', lue: true })]));
    harness.detectChanges();
    expect(el().textContent).toContain('Demande approuvée');
    expect(el().textContent).toContain('Demande refusée');
    expect(el().querySelectorAll('.notif.non-lue')).toHaveLength(1);
  });

  it('shows only unread ones from the « Non lues » tab', async () => {
    await harness.navigateByUrl('/notifications?vue=non-lues');
    liste(true).flush(page([n()]));
  });

  it('marks a notification read and opens its link', async () => {
    await harness.navigateByUrl('/notifications');
    liste(false).flush(page([n()]));
    harness.detectChanges();
    TestBed.inject(Notifications).nonLues.set(2);
    el().querySelector<HTMLButtonElement>('.notif button')!.click();
    http.expectOne(r => r.method === 'POST' && r.url === `${api}/9/lecture`).flush(n({ lue: true }));
    await harness.fixture.whenStable();
    // the count comes from the server, never from local arithmetic (another tab may have read it already)
    http.expectOne(`${api}/non-lues/nombre`).flush({ nombre: 0 });
    expect(TestBed.inject(Router).url).toBe('/leave-requests/7');
    expect(TestBed.inject(Notifications).nonLues()).toBe(0);
  });

  it('marks a notification without link read and stays on the page', async () => {
    await harness.navigateByUrl('/notifications');
    liste(false).flush(page([n({ lien: null })]));
    harness.detectChanges();
    el().querySelector<HTMLButtonElement>('.notif button')!.click();
    http.expectOne(r => r.method === 'POST' && r.url === `${api}/9/lecture`).flush(n({ lue: true, lien: null }));
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/notifications');
    expect(el().querySelectorAll('.notif.non-lue')).toHaveLength(0);
  });

  it('marks everything read, then reloads', async () => {
    await harness.navigateByUrl('/notifications');
    liste(false).flush(page([n()]));
    harness.detectChanges();
    TestBed.inject(Notifications).nonLues.set(1);
    harness.detectChanges(); // the button is enabled once something is unread
    [...el().querySelectorAll('button')].find(b => b.textContent?.trim() === 'Tout marquer comme lu')!.click();
    http.expectOne(r => r.method === 'POST' && r.url === `${api}/lecture`).flush({ nombre: 1 });
    await harness.fixture.whenStable();
    liste(false).flush(page([n({ lue: true })]));
    http.expectOne(`${api}/non-lues/nombre`).flush({ nombre: 1 }); // a new one arrived meanwhile: it stays counted
    expect(TestBed.inject(Notifications).nonLues()).toBe(1);
  });
});
