export type PortalContext =
  | { type: 'PUBLIC' }
  | { type: 'PLATFORM' }
  | { type: 'MERCHANT'; merchantSlug: string }
  | { type: 'DEVELOPMENT'; merchantSlug?: string }
  | { type: 'UNKNOWN' };

const reserved = new Set(['www', 'api', 'platform', 'admin', 'app', 'portal', 'login', 'logout', 'signup', 'support', 'help', 'status', 'billing', 'docs', 'assets', 'static', 'mail', 'cdn']);
const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export function resolvePortalContext(hostname: string, publicBaseDomain = import.meta.env.VITE_PUBLIC_BASE_DOMAIN ?? 'merchtyl.com'): PortalContext {
  const host = hostname.trim().toLowerCase().replace(/\.$/, '');
  const base = publicBaseDomain.trim().toLowerCase();
  if (host === 'localhost' || host.endsWith('.localhost')) {
    const candidate = host === 'localhost' ? undefined : host.slice(0, -'.localhost'.length);
    return candidate && slugPattern.test(candidate) && !reserved.has(candidate)
      ? { type: 'DEVELOPMENT', merchantSlug: candidate }
      : { type: 'DEVELOPMENT' };
  }
  if (host === base || host === `www.${base}`) return { type: 'PUBLIC' };
  if (host === `platform.${base}`) return { type: 'PLATFORM' };
  if (!host.endsWith(`.${base}`)) return { type: 'UNKNOWN' };
  const slug = host.slice(0, -(base.length + 1));
  if (slug.includes('.') || reserved.has(slug) || !slugPattern.test(slug)) return { type: 'UNKNOWN' };
  return { type: 'MERCHANT', merchantSlug: slug };
}

export function currentPortalContext(): PortalContext {
  return resolvePortalContext(window.location.hostname);
}

export function merchantSlugForRequest(): string | undefined {
  const context = currentPortalContext();
  return context.type === 'MERCHANT' || context.type === 'DEVELOPMENT' ? context.merchantSlug : undefined;
}

export function portalStorageScope(): string {
  const context = currentPortalContext();
  return context.type === 'MERCHANT' ? `merchant:${context.merchantSlug}` : context.type.toLowerCase();
}
