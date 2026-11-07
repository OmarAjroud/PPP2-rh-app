import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { environment } from '../../environments/environment';
import { Accueil } from './accueil';

describe('Accueil', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Accueil],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNativeDateAdapter()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render(): HTMLElement {
    const fixture = TestBed.createComponent(Accueil);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows UP when the API health check answers UP', () => {
    const el = render();
    http.expectOne(`${environment.apiUrl}/actuator/health`).flush({ status: 'UP' });
    TestBed.tick();
    expect(el.querySelector('.status')?.textContent).toContain('API : UP');
  });

  it('explains how to start the API when it cannot be reached', () => {
    const el = render();
    http.expectOne(`${environment.apiUrl}/actuator/health`).error(new ProgressEvent('error'));
    TestBed.tick();
    expect(el.querySelector('.status')?.textContent).toContain('API injoignable');
    expect(el.textContent).toContain('docker compose up -d');
  });
});
