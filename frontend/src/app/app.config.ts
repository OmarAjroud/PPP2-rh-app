import { ApplicationConfig, LOCALE_ID, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import { authInterceptor } from './core/auth/auth.interceptor';
import { RhPreset } from './core/theme/rh-preset';
import { PRIMENG_FR } from './core/theme/primeng-fr';
import { ThemeService } from './core/theme/theme.service';
import { routes } from './app.routes';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    // French everywhere: Angular pipes and PrimeNG components (dates jj/mm/aaaa, Monday first)
    { provide: LOCALE_ID, useValue: 'fr' },
    providePrimeNG({
      theme: { preset: RhPreset, options: { darkModeSelector: '.rh-dark', cssLayer: false } },
      translation: PRIMENG_FR,
      // Overlays open on <body>: panels (.card) clip their content, a calendar inside one was cut off
      overlayAppendTo: 'body',
      ripple: false,
    }),
    // Apply the remembered light/dark choice before the first paint
    provideAppInitializer(() => { inject(ThemeService); }),
  ],
};
