import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { AuthService } from '../core/auth/auth.service';
import { Conversation, LIMITE_MESSAGES } from './conversation';

const url = `${environment.apiUrl}/api/assistant/chat`;

describe('Conversation avec l’assistant', () => {
  let http: HttpTestingController;
  let conv: Conversation;

  beforeEach(() => {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'employe', role: 'EMPLOYE', employeId: 4, departementId: 1 },
    }));
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'login', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    conv = TestBed.inject(Conversation);
    TestBed.tick();
  });
  afterEach(() => localStorage.clear());

  it('sends the whole conversation with the new question last, then adds the answer and the tools used', async () => {
    const premiere = conv.envoyer('Combien de jours me reste-t-il ?');
    expect(conv.messages().at(-1)).toEqual({ role: 'utilisateur', contenu: 'Combien de jours me reste-t-il ?' });
    expect(conv.enCours()).toBe(true);
    const r1 = http.expectOne(url);
    expect(r1.request.body).toEqual({ messages: [{ role: 'utilisateur', contenu: 'Combien de jours me reste-t-il ?' }] });
    r1.flush({ reponse: 'Il vous reste 13 jours.', outilsUtilises: ['solde_conges'] });
    await premiere;
    expect(conv.enCours()).toBe(false);

    const seconde = conv.envoyer('Et en RTT ?');
    expect(http.expectOne(url).request.body.messages).toEqual([
      { role: 'utilisateur', contenu: 'Combien de jours me reste-t-il ?' },
      { role: 'assistant', contenu: 'Il vous reste 13 jours.' },
      { role: 'utilisateur', contenu: 'Et en RTT ?' },
    ]);
    expect(conv.messages()[1].outils).toEqual(['solde_conges']);
    void seconde;
  });

  it('takes the question back out when the API fails, and keeps it with the error (even if the page was left)', async () => {
    const envoi = conv.envoyer('Bonjour');
    http.expectOne(url).flush({ message: 'Trop de questions' }, { status: 429, statusText: 'Too Many Requests' });
    await envoi;
    expect(conv.messages()).toEqual([]);
    expect(conv.enCours()).toBe(false);
    expect(conv.erreur()).toContain('Trop de questions');
    expect(conv.questionRendue()).toBe('Bonjour');
  });

  it('refuses a second question while waiting for an answer', () => {
    void conv.envoyer('Une');
    void conv.envoyer('Deux');
    expect(http.match(url)).toHaveLength(1);
  });

  it('cancels the question being answered: request dropped, question given back', () => {
    void conv.envoyer('Longue question');
    const req = http.expectOne(url);
    conv.annuler();
    expect(req.cancelled).toBe(true);
    expect(conv.messages()).toEqual([]);
    expect(conv.enCours()).toBe(false);
    expect(conv.questionRendue()).toBe('Longue question');
  });

  it('shortens long answers sent back in the history, so later questions stay valid', async () => {
    conv.messages.set([{ role: 'utilisateur', contenu: 'Liste' }, { role: 'assistant', contenu: 'a'.repeat(5000) }]);
    void conv.envoyer('Et ensuite ?');
    const envoyes = http.expectOne(url).request.body.messages as { contenu: string }[];
    expect(envoyes[1].contenu.length).toBeLessThanOrEqual(4000);
    expect(conv.messages()[1].contenu).toHaveLength(5000); // still shown in full
  });

  it('is full once a new question would exceed the API limit', () => {
    conv.messages.set(Array.from({ length: LIMITE_MESSAGES - 1 }, (_, i) => ({ role: i % 2 ? 'assistant' : 'utilisateur', contenu: 'x' })));
    expect(conv.pleine()).toBe(false);
    conv.messages.set(Array.from({ length: LIMITE_MESSAGES }, (_, i) => ({ role: i % 2 ? 'assistant' : 'utilisateur', contenu: 'x' })));
    expect(conv.pleine()).toBe(true);
  });

  it('forgets the conversation on logout', () => {
    conv.messages.set([{ role: 'utilisateur', contenu: 'secret' }]);
    void TestBed.inject(AuthService).logout();
    http.match(() => true).forEach(r => r.flush({}));
    TestBed.tick();
    expect(conv.messages()).toEqual([]);
  });
  it('keeps the conversation when the same session is refreshed (new tokens, same person)', () => {
    conv.messages.set([{ role: 'utilisateur', contenu: 'garde-moi' }]);
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'b', refreshToken: 'r2', expiresAt: Date.now() + 2e6,
      profil: { nomUtilisateur: 'employe', role: 'EMPLOYE', employeId: 4, departementId: 1 },
    }));
    window.dispatchEvent(new StorageEvent('storage', { key: 'rh.session' }));
    TestBed.tick();
    expect(conv.messages()).toHaveLength(1);
  });
  for (const issue of ['succès', 'erreur'] as const) {
    it(`never lets a question still pending at logout reach the next person (${issue})`, async () => {
      const envoi = conv.envoyer('Question de la personne A');
      const req = http.expectOne(url);
      void TestBed.inject(AuthService).logout();
      http.match(r => r.url !== url).forEach(r => r.flush({}));
      TestBed.tick();
      expect(req.cancelled).toBe(true);
      expect(conv.messages()).toEqual([]);
      expect(conv.enCours()).toBe(false);
      expect(conv.questionRendue()).toBeNull();
      await envoi;
      void issue;
    });
  }
});
