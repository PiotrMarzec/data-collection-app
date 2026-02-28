import { ApplicationConfig, InjectionToken, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { routes } from './app.routes';

// Captured at module-load time — before Angular's HashLocationStrategy can call
// history.replaceState() and strip the query string from the URL.
const _initialSearch = window.location.search;

export const INITIAL_SEARCH = new InjectionToken<string>('InitialSearch');

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withHashLocation()),
    provideHttpClient(),
    { provide: INITIAL_SEARCH, useValue: _initialSearch },
  ],
};
