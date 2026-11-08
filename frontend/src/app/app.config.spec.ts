import { TestBed } from '@angular/core/testing';
import { PrimeNG } from 'primeng/config';
import { appConfig } from './app.config';

describe('appConfig: PrimeNG', () => {
  it('opens overlays (calendars, dropdowns) on <body>, so panels that clip their content cannot cut them off', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });
    expect(TestBed.inject(PrimeNG).overlayAppendTo()).toBe('body');
  });
});
