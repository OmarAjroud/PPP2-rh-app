import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { ExportListe, nomFichier } from './export-liste';

const api = `${environment.apiUrl}/api/exports`;

describe('Export buttons', () => {
  let http: HttpTestingController;
  let clic: ReturnType<typeof vi.fn>;
  let telecharge: string[];

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    telecharge = [];
    // jsdom has no object URLs and does not download: record the file name given to the link
    URL.createObjectURL = vi.fn(() => 'blob:fichier');
    URL.revokeObjectURL = vi.fn();
    clic = vi.fn(function (this: HTMLAnchorElement) { telecharge.push(this.download); });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(clic as never);
  });
  afterEach(() => { http.verify(); vi.restoreAllMocks(); });

  function rendre(filtres: Record<string, string | number | null | undefined> = {}) {
    const f = TestBed.createComponent(ExportListe);
    f.componentRef.setInput('liste', 'employes');
    f.componentRef.setInput('filtres', filtres);
    f.detectChanges();
    return f;
  }
  const bouton = (el: HTMLElement, nom: string) => el.querySelector<HTMLButtonElement>(`button[aria-label="${nom}"]`)!;

  it('downloads the Excel file with the filters on screen, named by the server', async () => {
    const f = rendre({ recherche: 'lina', departementId: 2, roleId: null, vide: '' });
    bouton(f.nativeElement, 'Exporter en Excel').click();
    const req = http.expectOne(r => r.url === `${api}/employes`);
    expect(req.request.params.get('format')).toBe('xlsx');
    expect(req.request.params.get('recherche')).toBe('lina');
    expect(req.request.params.get('departementId')).toBe('2');
    expect(req.request.params.has('roleId')).toBe(false);
    expect(req.request.params.has('vide')).toBe(false);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['x']), { headers: { 'Content-Disposition': 'attachment; filename="employes-2026-11-11.xlsx"' } });
    await f.whenStable();
    expect(telecharge).toEqual(['employes-2026-11-11.xlsx']);
    f.detectChanges();
    expect(f.nativeElement.querySelector('[aria-live="polite"]').textContent).toContain('Fichier téléchargé : employes-2026-11-11.xlsx');
    // the link stays valid while the browser starts the download
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();
  });

  it('says so instead of saving an empty file', async () => {
    const f = rendre();
    bouton(f.nativeElement, 'Exporter en Excel').click();
    http.expectOne(r => r.url === `${api}/employes`).flush(null);
    await vi.waitFor(() => {
      f.detectChanges();
      expect(f.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('fichier reçu est vide');
    });
    expect(telecharge).toEqual([]);
  });

  it('shows the server message when the export is refused', async () => {
    const f = rendre();
    bouton(f.nativeElement, 'Exporter en PDF').click();
    const corps = new Blob([JSON.stringify({ message: 'L\'export est limité à 5 000 lignes : affinez les filtres' })], { type: 'application/json' });
    http.expectOne(r => r.url === `${api}/employes` && r.params.get('format') === 'pdf').flush(corps, { status: 400, statusText: 'Bad Request' });
    await vi.waitFor(() => {
      f.detectChanges();
      expect(f.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('limité à 5 000 lignes');
    });
    expect(telecharge).toEqual([]);
  });

  it('does not start a second download while one is running', () => {
    const f = rendre();
    bouton(f.nativeElement, 'Exporter en Excel').click();
    f.detectChanges();
    bouton(f.nativeElement, 'Exporter en PDF').click();
    expect(http.match(r => r.url === `${api}/employes`)).toHaveLength(1);
  });
});

describe('nomFichier', () => {
  it('reads the name the server gives, UTF-8 form first', () => {
    expect(nomFichier('attachment; filename="a.pdf"; filename*=UTF-8\'\'d%C3%A9partements.pdf', 'x.pdf')).toBe('départements.pdf');
    expect(nomFichier('attachment; filename="journal-audit-2026-11-11.xlsx"', 'x.xlsx')).toBe('journal-audit-2026-11-11.xlsx');
  });
  it('falls back to a name of its own when the header is hidden', () => {
    expect(nomFichier(null, 'employes.xlsx')).toBe('employes.xlsx');
  });
});
