import { versApi } from './auth.interceptor';

describe('versApi (which requests carry the token)', () => {
  it('with an absolute API address (dev server), only that origin', () => {
    expect(versApi('http://localhost:8080/api/employes', 'http://localhost:8080')).toBe(true);
    expect(versApi('http://localhost:8080.evil.com/api', 'http://localhost:8080')).toBe(false);
    expect(versApi('https://autre-site.com/api/x', 'http://localhost:8080')).toBe(false);
  });

  it('with the same-origin API (production build behind nginx), only our own /api and /actuator paths', () => {
    expect(versApi('/api/employes', '')).toBe(true);
    expect(versApi('/actuator/health', '')).toBe(true);
    expect(versApi('https://autre-site.com/api/x', '')).toBe(false);
    expect(versApi('//autre-site.com/api/x', '')).toBe(false);
    expect(versApi('/assets/logo.svg', '')).toBe(false);
  });
});
