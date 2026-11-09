import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { environment } from '../../environments/environment';
import { AssistantPage } from './assistant-page';
import { Conversation, LIMITE_MESSAGES } from './conversation';

const api = `${environment.apiUrl}/api/assistant`;

describe('Page de l’assistant', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    localStorage.setItem('rh.session', JSON.stringify({
      accessToken: 'a', refreshToken: 'r', expiresAt: Date.now() + 1e6,
      profil: { nomUtilisateur: 'employe', role: 'EMPLOYE', employeId: 4, departementId: 1 },
    }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'assistant', component: AssistantPage }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(Conversation);
    TestBed.tick();
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/assistant');
  });
  afterEach(() => localStorage.clear());

  const el = () => harness.routeNativeElement!;
  const champ = () => el().querySelector<HTMLTextAreaElement>('textarea')!;
  const disponible = (oui = true) => { http.expectOne(`${api}/statut`).flush({ moteur: 'ollama', modele: 'qwen3', disponible: oui, outils: [] }); harness.detectChanges(); };
  const ecrire = (t: string) => { champ().value = t; champ().dispatchEvent(new Event('input')); harness.detectChanges(); };
  const entree = () => champ().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

  it('says so when the assistant is unavailable, and offers no input', () => {
    disponible(false);
    expect(el().textContent).toContain('L’assistant n’est pas disponible');
    expect(el().querySelector('textarea')).toBeNull();
  });

  it('suggests questions, and sends the one clicked', () => {
    disponible();
    const exemple = el().querySelector<HTMLButtonElement>('.exemples button')!;
    const question = exemple.textContent!.trim();
    exemple.click();
    expect(http.expectOne(`${api}/chat`).request.body.messages.at(-1)).toEqual({ role: 'utilisateur', contenu: question });
  });

  it('sends with Enter and shows the answer as text, with the tools used in French', async () => {
    disponible();
    ecrire('Combien de jours me reste-t-il ?');
    entree();
    harness.detectChanges();
    expect(el().textContent).toContain('L’assistant réfléchit');
    http.expectOne(`${api}/chat`).flush({ reponse: 'Il vous reste <b>13</b> jours.\n<img src=x onerror="alert(1)">', outilsUtilises: ['solde_conges'] });
    await harness.fixture.whenStable();
    harness.detectChanges();
    const reponse = el().querySelector('.message.assistant .texte')!;
    expect(reponse.textContent).toContain('Il vous reste <b>13</b> jours.');
    expect(reponse.querySelector('img, b')).toBeNull();
    expect(el().textContent).toContain('Soldes de congés');
    expect(champ().value).toBe('');
  });

  it('puts the question back in the field when the API refuses it', async () => {
    disponible();
    ecrire('Bonjour');
    entree();
    http.expectOne(`${api}/chat`).flush({ message: 'Trop de questions : réessayez dans une minute' }, { status: 429, statusText: 'Too Many Requests' });
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(champ().value).toBe('Bonjour');
    expect(el().querySelector('[role="alert"]')!.textContent).toContain('Trop de questions');
  });

  it('stops new questions once the conversation is full, and starts a new one on request', () => {
    disponible();
    TestBed.inject(Conversation).messages.set(Array.from({ length: LIMITE_MESSAGES }, (_, i) => ({ role: i % 2 ? 'assistant' : 'utilisateur', contenu: 'x' })));
    harness.detectChanges();
    expect(champ().disabled).toBe(true);
    expect(el().textContent).toContain('Nouvelle conversation');
    [...el().querySelectorAll('button')].find(b => b.textContent?.trim() === 'Nouvelle conversation')!.click();
    harness.detectChanges();
    expect(TestBed.inject(Conversation).messages()).toEqual([]);
    expect(champ().disabled).toBe(false);
  });
});
