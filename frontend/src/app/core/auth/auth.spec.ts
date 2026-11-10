import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { authInterceptor } from './auth.interceptor';
import { AuthResponse } from './auth.models';
import { AuthService } from './auth.service';

const API = environment.apiUrl;
const reponse = (n: number): AuthResponse => ({
  accessToken: `acces-${n}`,
  refreshToken: `refresh-${n}`,
  tokenType: 'Bearer',
  expiresIn: 900,
  profil: { nomUtilisateur: 'employe', role: 'EMPLOYE', employeId: 4, departementId: 1 },
});
/** Lets pending promise callbacks (firstValueFrom, from(promise)) run. */
const attendre = () => new Promise(r => setTimeout(r, 0));

describe('Session and token renewal', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    auth = TestBed.inject(AuthService);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  async function seConnecter(): Promise<void> {
    const p = auth.login('employe', 'Test1234!');
    http.expectOne(`${API}/api/auth/login`).flush(reponse(1));
    await p;
  }

  it('stores the session so other tabs and reloads share it', async () => {
    await seConnecter();
    expect(auth.connecte()).toBe(true);
    expect(JSON.parse(localStorage.getItem('rh.session')!).refreshToken).toBe('refresh-1');
  });

  it('sends the access token on API calls', async () => {
    await seConnecter();
    client.get(`${API}/api/employes/me`).subscribe();
    expect(http.expectOne(`${API}/api/employes/me`).request.headers.get('Authorization')).toBe('Bearer acces-1');
  });

  it('renews once for two concurrent 401s, then replays both requests with the new token', async () => {
    await seConnecter();
    const a = firstValueFrom(client.get(`${API}/api/a`));
    const b = firstValueFrom(client.get(`${API}/api/b`));
    http.expectOne(`${API}/api/a`).flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${API}/api/b`).flush(null, { status: 401, statusText: 'Unauthorized' });
    await attendre();

    const refresh = http.match(`${API}/api/auth/refresh`);
    expect(refresh.length).toBe(1); // a reused refresh token would close every session
    expect(refresh[0].request.body).toEqual({ refreshToken: 'refresh-1' });
    refresh[0].flush(reponse(2));
    await attendre();

    for (const url of ['a', 'b']) {
      const r = http.expectOne(`${API}/api/${url}`);
      expect(r.request.headers.get('Authorization')).toBe('Bearer acces-2');
      r.flush({ ok: url });
    }
    expect(await a).toEqual({ ok: 'a' });
    expect(await b).toEqual({ ok: 'b' });
  });

  it('uses the tokens another tab already renewed instead of renewing again', async () => {
    await seConnecter();
    // Another tab refreshed: the stored refresh token changed
    localStorage.setItem('rh.session', JSON.stringify({ ...JSON.parse(localStorage.getItem('rh.session')!), accessToken: 'acces-autre', refreshToken: 'refresh-autre' }));
    const jeton = await auth.refresh();
    expect(jeton).toBe('acces-autre');
    http.expectNone(`${API}/api/auth/refresh`);
  });

  it('ends the session and goes to the login page when renewal is refused', async () => {
    await seConnecter();
    const appel = firstValueFrom(client.get(`${API}/api/a`)).catch(e => e);
    http.expectOne(`${API}/api/a`).flush(null, { status: 401, statusText: 'Unauthorized' });
    await attendre();
    http.expectOne(`${API}/api/auth/refresh`).flush({ message: 'Jeton de rafraîchissement invalide ou expiré' }, { status: 401, statusText: 'Unauthorized' });
    await appel;
    expect(auth.connecte()).toBe(false);
    expect(localStorage.getItem('rh.session')).toBeNull();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login'], expect.objectContaining({ queryParams: expect.objectContaining({ expired: 1 }) }));
  });

  it('never renews on a failed login: wrong credentials are reported as such', async () => {
    const p = auth.login('employe', 'faux').catch(e => e);
    http.expectOne(`${API}/api/auth/login`).flush({ message: 'Identifiants invalides' }, { status: 401, statusText: 'Unauthorized' });
    await p;
    http.expectNone(`${API}/api/auth/refresh`);
    expect(auth.connecte()).toBe(false);
  });

  it('never brings back a session logged out while a renewal was on its way', async () => {
    await seConnecter();
    const enCours = auth.refresh().catch(e => e);
    await attendre();
    const req = http.expectOne(`${API}/api/auth/refresh`);
    auth.oublier(); // the idle logout happens meanwhile
    req.flush(reponse(2));
    await enCours;
    expect(auth.connecte()).toBe(false);
    expect(localStorage.getItem('rh.session')).toBeNull();
    // the new refresh token is revoked rather than left valid on the server
    expect(http.expectOne(`${API}/api/auth/logout`).request.body).toEqual({ refreshToken: 'refresh-2' });
  });

  it('tells every tab why when the session ended for inactivity', async () => {
    await seConnecter();
    const navigate = TestBed.inject(Router).navigate as unknown as ReturnType<typeof vi.fn>;
    // Another tab logs out for inactivity: it leaves the reason, then removes the session
    localStorage.setItem('rh.raison', 'inactivite');
    localStorage.removeItem('rh.session');
    window.dispatchEvent(new StorageEvent('storage', { key: 'rh.session', newValue: null }));
    await attendre();
    expect(auth.connecte()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { idle: 1 } });
  });

  it('forgets an old logout reason at the next login', async () => {
    localStorage.setItem('rh.raison', 'inactivite');
    await seConnecter();
    expect(localStorage.getItem('rh.raison')).toBeNull();
  });
});
