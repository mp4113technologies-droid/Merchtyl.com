import { describe, expect, it } from 'vitest';
import { resolveApiBaseUrl } from './runtimeConfig';

describe('resolveApiBaseUrl', () => {
  it('uses the configured shared production API', () => {
    expect(resolveApiBaseUrl('https://api.merchtyl.com', false)).toBe('https://api.merchtyl.com/api/v1');
  });

  it('does not derive an API hostname from a portal hostname', () => {
    expect(resolveApiBaseUrl('https://api.merchtyl.com/', false)).not.toContain('platform.merchtyl.com');
  });

  it('preserves the Vite proxy for local development', () => {
    expect(resolveApiBaseUrl(undefined, true)).toBe('/api/v1');
  });

  it('rejects a missing production API setting', () => {
    expect(() => resolveApiBaseUrl(undefined, false)).toThrow('VITE_API_BASE_URL');
  });
});
