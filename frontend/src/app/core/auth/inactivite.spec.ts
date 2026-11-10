import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { CLE_ACTIVITE, DELAI_INACTIVITE, Inactivite, PREAVIS } from './inactivite';

const MINUTE = 60_000;

describe('Logout after inactivity', () => {
  let http: HttpTestingController;
  let service: Inactivite;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e7,
      profil: { nomUtilisateur: 'employe', role: 'EMPLOYE', employeId: 4, departementId: 1 },
    }));
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    service = TestBed.inject(Inactivite);
    service.demarrer();
  });
  afterEach(() => {
    service.arreter();
    vi.useRealTimers();
    localStorage.clear();
  });

  it('waits 15 minutes, warning one minute before', () => {
    expect(DELAI_INACTIVITE).toBe(15 * MINUTE);
    expect(PREAVIS).toBe(MINUTE);
    vi.advanceTimersByTime(13 * MINUTE + 59_000);
    expect(service.restant()).toBeNull();
    vi.advanceTimersByTime(1000);
    expect(service.restant()).toBe(60);
    vi.advanceTimersByTime(15_000);
    expect(service.restant()).toBe(45);
  });

  it('logs out at 15 minutes and tells the login page why', async () => {
    vi.advanceTimersByTime(15 * MINUTE);
    await vi.waitFor(() => http.expectOne(`${environment.apiUrl}/api/auth/logout`).flush({}));
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { idle: 1 } }));
    expect(TestBed.inject(AuthService).connecte()).toBe(false);
    expect(service.restant()).toBeNull();
  });

  it('starts over after a key press, a click, a scroll or a touch', () => {
    vi.advanceTimersByTime(10 * MINUTE);
    document.dispatchEvent(new KeyboardEvent('keydown'));
    vi.advanceTimersByTime(13 * MINUTE);
    expect(service.restant()).toBeNull();
    document.dispatchEvent(new Event('pointerdown'));
    vi.advanceTimersByTime(13 * MINUTE);
    expect(service.restant()).toBeNull();
    http.expectNone(`${environment.apiUrl}/api/auth/logout`);
  });

  it('counts activity in another tab', () => {
    vi.advanceTimersByTime(14 * MINUTE);
    expect(service.restant()).toBe(60);
    localStorage.setItem(CLE_ACTIVITE, String(Date.now())); // the other tab writes its activity
    vi.advanceTimersByTime(1000);
    expect(service.restant()).toBeNull();
  });

  it('closes the warning when the person chooses to stay', () => {
    vi.advanceTimersByTime(14 * MINUTE + 30_000);
    expect(service.restant()).toBe(30);
    service.rester();
    expect(service.restant()).toBeNull();
    vi.advanceTimersByTime(13 * MINUTE);
    expect(service.restant()).toBeNull();
  });

  it('stops counting once the session is gone (logged out in another tab)', () => {
    TestBed.inject(AuthService).oublier();
    vi.advanceTimersByTime(20 * MINUTE);
    expect(service.restant()).toBeNull();
    http.expectNone(`${environment.apiUrl}/api/auth/logout`);
  });
});
