import { describe, expect, it } from 'vitest';
import { resolvePortalContext } from './portalContext';

describe('resolvePortalContext', () => {
  it.each([
    ['merchtyl.com', { type: 'PUBLIC' }],
    ['www.merchtyl.com', { type: 'PUBLIC' }],
    ['platform.merchtyl.com', { type: 'PLATFORM' }],
    ['adviam.merchtyl.com', { type: 'MERCHANT', merchantSlug: 'adviam' }],
    ['patel-group.merchtyl.com', { type: 'MERCHANT', merchantSlug: 'patel-group' }],
    ['api.merchtyl.com', { type: 'UNKNOWN' }],
    ['evil-example.com', { type: 'UNKNOWN' }]
  ])('maps %s', (hostname, expected) => expect(resolvePortalContext(hostname)).toEqual(expected));
});
