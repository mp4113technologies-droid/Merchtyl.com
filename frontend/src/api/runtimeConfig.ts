export function resolveApiBaseUrl(configuredUrl: string | undefined, development: boolean): string {
  const configured = configuredUrl?.trim().replace(/\/+$/, '');
  if (configured) return `${configured}/api/v1`;
  if (development) return '/api/v1';
  throw new Error('VITE_API_BASE_URL must be configured for production builds');
}

export const API_BASE_URL = resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL, import.meta.env.DEV);
